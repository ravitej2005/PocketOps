package commands

import (
	"testing"

	"github.com/pocketops/agent/docker"
)

func TestExecutorRejectsUnknownCommand(t *testing.T) {
	executor := &Executor{}
	result := executor.Execute(t.Context(), "ARBITRARY_COMMAND", "")
	if result.Succeeded || result.ErrorMsg != "Docker client not available" {
		t.Fatalf("unexpected result: %+v", result)
	}
}

func TestExecutorRejectsUnknownCommandWhenDockerIsAvailable(t *testing.T) {
	executor := &Executor{DockerClient: &docker.Client{}}
	result := executor.Execute(t.Context(), "ARBITRARY_COMMAND", "")
	if result.Succeeded || result.ErrorMsg != "unsupported command: ARBITRARY_COMMAND" {
		t.Fatalf("unexpected result: %+v", result)
	}
}
