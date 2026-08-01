// Package config handles local agent configuration (host identity, backend endpoint, intervals).
package config

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"time"
)

const defaultAgentVersion = "0.1.0"

type Config struct {
	BackendURL        string        `json:"backendUrl"`
	GRPCAddress       string        `json:"grpcAddress"`
	RegistrationToken string        `json:"-"`
	AgentID           string        `json:"agentId"`
	InfrastructureID  string        `json:"infrastructureId"`
	IdentityToken     string        `json:"identityToken"`
	AgentVersion      string        `json:"agentVersion"`
	ConfigPath        string        `json:"-"`
	HeartbeatInterval time.Duration `json:"-"`
	InsecureDev       bool          `json:"insecureDev"`
}

func Load(args []string) (Config, error) {
	home, _ := os.UserHomeDir()
	defaultConfigPath := filepath.Join(home, ".pocketops-agent.json")

	cfg := Config{
		BackendURL:        getenv("POCKETOPS_BACKEND_URL", "http://localhost:8080"),
		GRPCAddress:       getenv("POCKETOPS_AGENT_GRPC", "localhost:9090"),
		RegistrationToken: os.Getenv("POCKETOPS_REGISTRATION_TOKEN"),
		AgentVersion:      getenv("POCKETOPS_AGENT_VERSION", defaultAgentVersion),
		ConfigPath:        getenv("POCKETOPS_AGENT_CONFIG", defaultConfigPath),
		HeartbeatInterval: 10 * time.Second,
	}

	fs := flag.NewFlagSet("pocketops-agent", flag.ContinueOnError)
	fs.StringVar(&cfg.BackendURL, "backend", cfg.BackendURL, "PocketOps backend HTTP base URL")
	fs.StringVar(&cfg.GRPCAddress, "grpc", cfg.GRPCAddress, "PocketOps agent gRPC host:port")
	fs.StringVar(&cfg.RegistrationToken, "token", cfg.RegistrationToken, "one-time registration token")
	fs.StringVar(&cfg.AgentVersion, "version", cfg.AgentVersion, "agent version string")
	fs.StringVar(&cfg.ConfigPath, "config", cfg.ConfigPath, "local identity/config file path")
	fs.DurationVar(&cfg.HeartbeatInterval, "heartbeat-interval", cfg.HeartbeatInterval, "heartbeat interval")
	fs.BoolVar(&cfg.InsecureDev, "insecure-dev", false, "use plaintext gRPC for local development only")
	if err := fs.Parse(args); err != nil {
		return Config{}, err
	}

	if err := cfg.loadIdentity(); err != nil {
		return Config{}, err
	}
	if cfg.BackendURL == "" {
		return Config{}, errors.New("backend URL is required")
	}
	if _, err := url.ParseRequestURI(cfg.BackendURL); err != nil {
		return Config{}, fmt.Errorf("backend URL is invalid: %w", err)
	}
	if cfg.GRPCAddress == "" {
		return Config{}, errors.New("gRPC address is required")
	}
	if cfg.HeartbeatInterval <= 0 {
		return Config{}, errors.New("heartbeat interval must be positive")
	}
	return cfg, nil
}

func (c Config) Registered() bool {
	return c.AgentID != "" && c.InfrastructureID != "" && c.IdentityToken != ""
}

func (c Config) SaveIdentity() error {
	if c.ConfigPath == "" {
		return errors.New("config path is required")
	}
	if err := os.MkdirAll(filepath.Dir(c.ConfigPath), 0o700); err != nil {
		return err
	}
	body, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(c.ConfigPath, body, 0o600)
}

func (c *Config) ApplyRegistration(agentID, infrastructureID, identityToken, grpcHost string, grpcPort int) {
	c.AgentID = agentID
	c.InfrastructureID = infrastructureID
	c.IdentityToken = identityToken
	if grpcHost != "" && grpcPort > 0 {
		c.GRPCAddress = fmt.Sprintf("%s:%d", grpcHost, grpcPort)
	}
}

func (c *Config) loadIdentity() error {
	body, err := os.ReadFile(c.ConfigPath)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	var stored Config
	if err := json.Unmarshal(body, &stored); err != nil {
		return err
	}
	if stored.BackendURL != "" {
		c.BackendURL = stored.BackendURL
	}
	if stored.GRPCAddress != "" {
		c.GRPCAddress = stored.GRPCAddress
	}
	if stored.AgentID != "" {
		c.AgentID = stored.AgentID
	}
	if stored.InfrastructureID != "" {
		c.InfrastructureID = stored.InfrastructureID
	}
	if stored.IdentityToken != "" {
		c.IdentityToken = stored.IdentityToken
	}
	if stored.AgentVersion != "" {
		c.AgentVersion = stored.AgentVersion
	}
	c.InsecureDev = c.InsecureDev || stored.InsecureDev
	return nil
}

func getenv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
