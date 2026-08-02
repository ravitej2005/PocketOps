package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/pocketops/agent/config"
	"github.com/pocketops/agent/connection"
	"github.com/pocketops/agent/docker"
	"github.com/pocketops/agent/registration"
	"github.com/pocketops/agent/security"
)

func main() {
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	if err := run(logger); err != nil && !errors.Is(err, context.Canceled) {
		logger.Error("agent stopped", "error", err)
		os.Exit(1)
	}
}

func run(logger *slog.Logger) error {
	cfg, err := config.Load(os.Args[1:])
	if err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	if !cfg.Registered() {
		if cfg.RegistrationToken == "" {
			return fmt.Errorf("agent is not registered; pass --token once to establish identity")
		}
		logger.Info("registering agent", "backend", cfg.BackendURL, "token", security.RedactSecret(cfg.RegistrationToken))
		result, err := registration.NewClient(cfg.BackendURL, nil).Register(ctx, cfg.RegistrationToken, cfg.AgentVersion)
		if err != nil {
			return err
		}
		cfg.ApplyRegistration(result.AgentID, result.InfrastructureID, result.IdentityToken, result.GRPCHost, result.GRPCPort)
		if err := cfg.SaveIdentity(); err != nil {
			return err
		}
		logger.Info("agent identity saved", "config", cfg.ConfigPath, "agentId", cfg.AgentID)
	}

	dockerClient, err := docker.New()
	if err != nil {
		logger.Warn("docker unavailable, running without container discovery", "error", err)
		dockerClient = nil
	} else {
		defer dockerClient.Close()
	}

	return connection.Run(ctx, connection.Config{
		Address:           cfg.GRPCAddress,
		AgentID:           cfg.AgentID,
		InfrastructureID:  cfg.InfrastructureID,
		IdentityToken:     cfg.IdentityToken,
		AgentVersion:      cfg.AgentVersion,
		HeartbeatInterval: cfg.HeartbeatInterval,
		InsecureDev:       cfg.InsecureDev,
		Logger:            logger,
		DockerClient:      dockerClient,
	})
}
