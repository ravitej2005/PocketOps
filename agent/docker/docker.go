// Package docker handles Docker Engine API access: discovery, stats, events, logs, start/stop/restart.
package docker

import (
	"context"
	"encoding/json"
	"strings"
	"time"

	dockertypes "github.com/docker/docker/api/types/container"
	"github.com/docker/docker/client"
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

// Metric is a bounded live stats sample for one container.
type Metric struct {
	ExternalResourceID string
	CPUPercent         float64
	MemoryUsageBytes   uint64
	MemoryLimitBytes   uint64
	NetworkRxBytes     uint64
	NetworkTxBytes     uint64
	UptimeSeconds      int64
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

// Metrics returns one current stats sample for each running container.
func (c *Client) Metrics(ctx context.Context) ([]Metric, error) {
	containers, err := c.cli.ContainerList(ctx, dockertypes.ListOptions{All: false})
	if err != nil {
		return nil, err
	}
	metrics := make([]Metric, 0, len(containers))
	for _, ctr := range containers {
		reader, err := c.cli.ContainerStatsOneShot(ctx, ctr.ID)
		if err != nil {
			return nil, err
		}
		var stats dockertypes.StatsResponse
		if err := json.NewDecoder(reader.Body).Decode(&stats); err != nil {
			reader.Body.Close()
			return nil, err
		}
		if err := reader.Body.Close(); err != nil {
			return nil, err
		}
		rx, tx := networkTotals(stats.Networks)
		metrics = append(metrics, Metric{
			ExternalResourceID: ctr.ID[:12],
			CPUPercent:         cpuPercent(stats),
			MemoryUsageBytes:   stats.MemoryStats.Usage,
			MemoryLimitBytes:   stats.MemoryStats.Limit,
			NetworkRxBytes:     rx,
			NetworkTxBytes:     tx,
			UptimeSeconds:      uptimeSeconds(stats.Read, ctr.Created),
		})
	}
	return metrics, nil
}

func cpuPercent(stats dockertypes.StatsResponse) float64 {
	cpuDelta := float64(stats.CPUStats.CPUUsage.TotalUsage - stats.PreCPUStats.CPUUsage.TotalUsage)
	systemDelta := float64(stats.CPUStats.SystemUsage - stats.PreCPUStats.SystemUsage)
	onlineCPUs := float64(stats.CPUStats.OnlineCPUs)
	if onlineCPUs == 0 {
		onlineCPUs = float64(len(stats.CPUStats.CPUUsage.PercpuUsage))
	}
	if cpuDelta <= 0 || systemDelta <= 0 || onlineCPUs <= 0 {
		return 0
	}
	return (cpuDelta / systemDelta) * onlineCPUs * 100
}

func networkTotals(networks map[string]dockertypes.NetworkStats) (uint64, uint64) {
	var rx uint64
	var tx uint64
	for _, network := range networks {
		rx += network.RxBytes
		tx += network.TxBytes
	}
	return rx, tx
}

func uptimeSeconds(read time.Time, created int64) int64 {
	if read.IsZero() || created <= 0 {
		return 0
	}
	uptime := read.Sub(time.Unix(created, 0))
	if uptime < 0 {
		return 0
	}
	return int64(uptime.Seconds())
}
