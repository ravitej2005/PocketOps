package docker

import (
	"testing"
	"time"
)

func TestUptimeSecondsUsesContainerStartedAt(t *testing.T) {
	startedAt := time.Date(2026, time.October, 5, 12, 0, 0, 0, time.UTC)
	readAt := startedAt.Add(90 * time.Second)

	if got := uptimeSeconds(readAt, startedAt.UnixMilli()); got != 90 {
		t.Fatalf("uptimeSeconds() = %d, want 90", got)
	}
}

func TestUptimeSecondsReturnsZeroWithoutStartedAt(t *testing.T) {
	if got := uptimeSeconds(time.Now(), 0); got != 0 {
		t.Fatalf("uptimeSeconds() = %d, want 0", got)
	}
}
