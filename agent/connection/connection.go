// Package connection handles gRPC channel lifecycle, TLS, and reconnect/backoff.
package connection

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"log/slog"
	"time"

	agentv1 "github.com/pocketops/agent/gen/pocketops/agent/v1"
	"github.com/pocketops/agent/security"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
)

type Config struct {
	Address           string
	AgentID           string
	InfrastructureID  string
	IdentityToken     string
	AgentVersion      string
	HeartbeatInterval time.Duration
	InsecureDev       bool
	Logger            *slog.Logger
}

func Run(ctx context.Context, cfg Config) error {
	logger := cfg.Logger
	if logger == nil {
		logger = slog.Default()
	}
	for {
		if err := connectOnce(ctx, cfg, logger); err != nil {
			if ctx.Err() != nil {
				return ctx.Err()
			}
			logger.Warn("agent stream disconnected", "error", err, "agentId", cfg.AgentID)
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(3 * time.Second):
		}
	}
}

func connectOnce(ctx context.Context, cfg Config, logger *slog.Logger) error {
	if cfg.IdentityToken == "" {
		return fmt.Errorf("identity token is required")
	}

	dialOptions := []grpc.DialOption{}
	if cfg.InsecureDev {
		dialOptions = append(dialOptions, grpc.WithTransportCredentials(insecure.NewCredentials()))
	} else {
		dialOptions = append(dialOptions, grpc.WithTransportCredentials(credentials.NewTLS(&tls.Config{
			MinVersion: tls.VersionTLS12,
		})))
	}

	conn, err := grpc.NewClient(cfg.Address, dialOptions...)
	if err != nil {
		return err
	}
	defer conn.Close()

	client := agentv1.NewAgentControlClient(conn)
	streamCtx := metadata.AppendToOutgoingContext(ctx, "agent-identity-token", cfg.IdentityToken)
	stream, err := client.Connect(streamCtx)
	if err != nil {
		return err
	}

	if err := sendEmptySnapshot(stream, cfg); err != nil {
		return err
	}
	if err := sendHeartbeat(stream, cfg); err != nil {
		return err
	}
	logger.Info("agent connected", "grpc", cfg.Address, "agentId", cfg.AgentID, "insecureDev", cfg.InsecureDev)

	recvErr := make(chan error, 1)
	go func() {
		for {
			msg, err := stream.Recv()
			if err != nil {
				recvErr <- err
				return
			}
			if ack := msg.GetConfigAck(); ack != nil {
				logger.Debug("received config ack", "status", ack.Status)
			}
		}
	}()

	ticker := time.NewTicker(cfg.HeartbeatInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return stream.CloseSend()
		case err := <-recvErr:
			if err == io.EOF {
				return nil
			}
			return err
		case <-ticker.C:
			if err := sendHeartbeat(stream, cfg); err != nil {
				return err
			}
		}
	}
}

func sendHeartbeat(stream agentv1.AgentControl_ConnectClient, cfg Config) error {
	return stream.Send(baseEnvelope(cfg, &agentv1.AgentEnvelope{
		Payload: &agentv1.AgentEnvelope_Heartbeat{
			Heartbeat: &agentv1.Heartbeat{AgentVersion: cfg.AgentVersion},
		},
	}))
}

func sendEmptySnapshot(stream agentv1.AgentControl_ConnectClient, cfg Config) error {
	return stream.Send(baseEnvelope(cfg, &agentv1.AgentEnvelope{
		Payload: &agentv1.AgentEnvelope_InfrastructureSnapshot{
			InfrastructureSnapshot: &agentv1.InfrastructureSnapshot{},
		},
	}))
}

func baseEnvelope(cfg Config, envelope *agentv1.AgentEnvelope) *agentv1.AgentEnvelope {
	envelope.MessageId = security.NewMessageID()
	envelope.AgentId = cfg.AgentID
	envelope.InfrastructureId = cfg.InfrastructureID
	envelope.TimestampUnixMs = time.Now().UnixMilli()
	return envelope
}
