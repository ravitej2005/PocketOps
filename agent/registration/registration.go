// Package registration handles one-time token exchange and persistent identity establishment.
package registration

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

type Client struct {
	baseURL    string
	httpClient *http.Client
}

type Response struct {
	AgentID          string `json:"agentId"`
	InfrastructureID string `json:"infrastructureId"`
	IdentityToken    string `json:"identityToken"`
	GRPCHost         string `json:"grpcHost"`
	GRPCPort         int    `json:"grpcPort"`
}

func NewClient(baseURL string, httpClient *http.Client) Client {
	if httpClient == nil {
		httpClient = &http.Client{Timeout: 15 * time.Second}
	}
	return Client{
		baseURL:    strings.TrimRight(baseURL, "/"),
		httpClient: httpClient,
	}
}

func (c Client) Register(ctx context.Context, registrationToken, agentVersion string) (Response, error) {
	requestBody, err := json.Marshal(map[string]string{
		"registrationToken": registrationToken,
		"agentVersion":      agentVersion,
	})
	if err != nil {
		return Response{}, err
	}

	req, err := http.NewRequestWithContext(
		ctx,
		http.MethodPost,
		c.baseURL+"/api/agents/register",
		bytes.NewReader(requestBody),
	)
	if err != nil {
		return Response{}, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return Response{}, err
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if resp.StatusCode != http.StatusOK {
		return Response{}, fmt.Errorf("registration failed with status %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}

	var result Response
	if err := json.Unmarshal(body, &result); err != nil {
		return Response{}, err
	}
	if result.AgentID == "" || result.InfrastructureID == "" || result.IdentityToken == "" {
		return Response{}, fmt.Errorf("registration response missing persistent identity fields")
	}
	return result, nil
}
