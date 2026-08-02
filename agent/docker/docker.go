// Package docker handles Docker Engine API access: discovery, stats, events, logs, start/stop/restart.
package docker

import (
	"context"
	"strings"

	"github.com/docker/docker/client"
	dockertypes "github.com/docker/docker/api/types/container"
)

// Client wraps the Docker Engine client for container operations.
type Client struct {
	cli *client.Client
}

// New creates a Client connected to the local Docker socket using the DOCKER_HOST
// environment variable or the default socket path.
func New() (*Client, error) {
	cli, err := client.NewClientWithOpts(client.FromEnv, client.WithAPIVersionNegotiation())
	if err != nil {
		return nil, err
	}
	return &Client{cli: cli}, nil
}

// Close releases the underlying Docker client resources.
func (c *Client) Close() error {
	return c.cli.Close()
}

// Resource is a simplified container snapshot for reporting.
type Resource struct {
	ID          string // Docker container ID (short)
	DisplayName string // human-readable: first name without leading slash
	Status      string // "running", "exited", "paused", etc.
}

// Snapshot returns the current list of all containers on the Docker host.
func (c *Client) Snapshot(ctx context.Context) ([]Resource, error) {
	containers, err := c.cli.ContainerList(ctx, dockertypes.ListOptions{All: true})
	if err != nil {
		return nil, err
	}
	resources := make([]Resource, 0, len(containers))
	for _, ctr := range containers {
		name := ctr.ID[:12]
		if len(ctr.Names) > 0 {
			name = strings.TrimPrefix(ctr.Names[0], "/")
		}
		resources = append(resources, Resource{
			ID:          ctr.ID[:12],
			DisplayName: name,
			Status:      ctr.State, // "running", "exited", …
		})
	}
	return resources, nil
}
