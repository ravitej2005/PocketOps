package registration

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestRegisterExchangesTokenForPersistentIdentity(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/agents/register" {
			t.Fatalf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		var body map[string]string
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		if body["registrationToken"] != "one-time" || body["agentVersion"] != "0.1.0" {
			t.Fatalf("unexpected request body: %#v", body)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"agentId":"agent-1","infrastructureId":"infra-1","identityToken":"identity","grpcHost":"localhost","grpcPort":9090}`))
	}))
	defer server.Close()

	result, err := NewClient(server.URL, server.Client()).Register(context.Background(), "one-time", "0.1.0")
	if err != nil {
		t.Fatalf("register: %v", err)
	}
	if result.AgentID != "agent-1" || result.InfrastructureID != "infra-1" || result.IdentityToken != "identity" {
		t.Fatalf("unexpected registration result: %#v", result)
	}
}

func TestRegisterRejectsErrorResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "invalid", http.StatusUnauthorized)
	}))
	defer server.Close()

	_, err := NewClient(server.URL, server.Client()).Register(context.Background(), "used", "0.1.0")
	if err == nil {
		t.Fatal("expected registration error")
	}
}
