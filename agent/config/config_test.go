package config

import (
	"path/filepath"
	"testing"
)

func TestLoadPersistsAndReloadsIdentity(t *testing.T) {
	configPath := filepath.Join(t.TempDir(), "agent.json")
	cfg, err := Load([]string{
		"--backend", "http://localhost:8080",
		"--grpc", "localhost:9090",
		"--config", configPath,
		"--insecure-dev",
	})
	if err != nil {
		t.Fatalf("load initial config: %v", err)
	}
	cfg.ApplyRegistration("agent-1", "infra-1", "identity", "agent.example.com", 9443)
	if err := cfg.SaveIdentity(); err != nil {
		t.Fatalf("save identity: %v", err)
	}

	reloaded, err := Load([]string{"--config", configPath})
	if err != nil {
		t.Fatalf("reload config: %v", err)
	}
	if !reloaded.Registered() {
		t.Fatal("expected persisted identity to be registered")
	}
	if reloaded.AgentID != "agent-1" || reloaded.InfrastructureID != "infra-1" || reloaded.IdentityToken != "identity" {
		t.Fatalf("unexpected identity: %#v", reloaded)
	}
	if reloaded.GRPCAddress != "agent.example.com:9443" {
		t.Fatalf("unexpected grpc address %q", reloaded.GRPCAddress)
	}
	if !reloaded.InsecureDev {
		t.Fatal("expected insecure dev mode to persist only when explicitly configured")
	}
}
