// Package commands handles receiving and executing allow-listed commands, returning results.
package commands

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/pocketops/agent/docker"
)

type Executor struct {
	DockerClient *docker.Client
	Logger       *slog.Logger
}

type Result struct {
	Succeeded   bool
	ErrorMsg    string
}

func (e *Executor) Execute(ctx context.Context, action, externalResourceID string) Result {
	if e.DockerClient == nil {
		return Result{Succeeded: false, ErrorMsg: "Docker client not available"}
	}

	switch action {
	case "START_CONTAINER":
		return e.startContainer(ctx, externalResourceID)
	case "STOP_CONTAINER":
		return e.stopContainer(ctx, externalResourceID)
	case "RESTART_CONTAINER":
		return e.restartContainer(ctx, externalResourceID)
	default:
		return Result{Succeeded: false, ErrorMsg: fmt.Sprintf("unsupported command: %s", action)}
	}
}

func (e *Executor) startContainer(ctx context.Context, externalResourceID string) Result {
	e.Logger.Info("starting container", "container", externalResourceID)
	if err := e.DockerClient.Start(ctx, externalResourceID); err != nil {
		e.Logger.Error("failed to start container", "container", externalResourceID, "error", err)
		return Result{Succeeded: false, ErrorMsg: err.Error()}
	}
	return Result{Succeeded: true}
}

func (e *Executor) stopContainer(ctx context.Context, externalResourceID string) Result {
	e.Logger.Info("stopping container", "container", externalResourceID)
	if err := e.DockerClient.Stop(ctx, externalResourceID); err != nil {
		e.Logger.Error("failed to stop container", "container", externalResourceID, "error", err)
		return Result{Succeeded: false, ErrorMsg: err.Error()}
	}
	return Result{Succeeded: true}
}

func (e *Executor) restartContainer(ctx context.Context, externalResourceID string) Result {
	e.Logger.Info("restarting container", "container", externalResourceID)
	if err := e.DockerClient.Restart(ctx, externalResourceID); err != nil {
		e.Logger.Error("failed to restart container", "container", externalResourceID, "error", err)
		return Result{Succeeded: false, ErrorMsg: err.Error()}
	}
	return Result{Succeeded: true}
}