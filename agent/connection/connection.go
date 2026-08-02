// Package connection handles gRPC channel lifecycle, TLS, and reconnect/backoff.
package connection

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"log/slog"
	"time"

	"github.com/pocketops/agent/docker"
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
	MetricsInterval   time.Duration
	SnapshotInterval  time.Duration
	InsecureDev       bool
	Logger            *slog.Logger
	DockerClient      *docker.Client
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

	if err := sendSnapshot(ctx, stream, cfg, logger); err != nil {
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
	metricsTicker := time.NewTicker(cfg.MetricsInterval)
	defer metricsTicker.Stop()
	snapshotTicker := time.NewTicker(cfg.SnapshotInterval)
	defer snapshotTicker.Stop()
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
		case <-metricsTicker.C:
			if err := sendMetrics(ctx, stream, cfg, logger); err != nil {
				return err
			}
		case <-snapshotTicker.C:
			if err := sendSnapshot(ctx, stream, cfg, logger); err != nil {
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

func sendSnapshot(ctx context.Context, stream agentv1.AgentControl_ConnectClient, cfg Config, logger *slog.Logger) error {
	resources := []*agentv1.ResourceSnapshot{}
	if cfg.DockerClient != nil {
		containers, err := cfg.DockerClient.Snapshot(ctx)
		if err != nil {
			logger.Warn("docker snapshot failed, sending empty snapshot", "error", err)
		} else {
			for _, c := range containers {
				resources = append(resources, &agentv1.ResourceSnapshot{
					ExternalResourceId: c.ID,
					DisplayName:        c.DisplayName,
					ResourceType:       "CONTAINER",
					Status:             c.Status,
					StartedAtUnixMs:    c.StartedAtUnixMs,
				})
			}
			logger.Info("docker snapshot", "containers", len(resources))
		}
	}
	return stream.Send(baseEnvelope(cfg, &agentv1.AgentEnvelope{
		Payload: &agentv1.AgentEnvelope_InfrastructureSnapshot{
			InfrastructureSnapshot: &agentv1.InfrastructureSnapshot{Resources: resources},
		},
	}))
}

func sendMetrics(ctx context.Context, stream agentv1.AgentControl_ConnectClient, cfg Config, logger *slog.Logger) error {
	if cfg.DockerClient == nil {
		return nil
	}
	metrics, err := cfg.DockerClient.Metrics(ctx)
	if err != nil {
		logger.Warn("docker metrics failed", "error", err)
		return nil
	}
	for _, sample := range metrics {
		if err := stream.Send(baseEnvelope(cfg, &agentv1.AgentEnvelope{
			Payload: &agentv1.AgentEnvelope_ContainerMetric{
				ContainerMetric: &agentv1.ContainerMetric{
					ExternalResourceId: sample.ExternalResourceID,
					CpuPercent:         sample.CPUPercent,
					MemoryUsageBytes:   sample.MemoryUsageBytes,
					MemoryLimitBytes:   sample.MemoryLimitBytes,
					NetworkRxBytes:     sample.NetworkRxBytes,
					NetworkTxBytes:     sample.NetworkTxBytes,
					UptimeSeconds:      sample.UptimeSeconds,
					StartedAtUnixMs:    sample.StartedAtUnixMs,
				},
			},
		})); err != nil {
			return err
		}
	}
	return nil
}

func baseEnvelope(cfg Config, envelope *agentv1.AgentEnvelope) *agentv1.AgentEnvelope {
	envelope.MessageId = security.NewMessageID()
	envelope.AgentId = cfg.AgentID
	envelope.InfrastructureId = cfg.InfrastructureID
	envelope.TimestampUnixMs = time.Now().UnixMilli()
	return envelope
}
