// Package security handles credential storage and TLS identity material handling.
package security

import (
	"crypto/rand"
	"encoding/hex"
	"strings"
)

func NewMessageID() string {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return "message"
	}
	return hex.EncodeToString(bytes)
}

func RedactSecret(value string) string {
	if value == "" {
		return ""
	}
	if len(value) <= 8 {
		return strings.Repeat("*", len(value))
	}
	return value[:4] + strings.Repeat("*", len(value)-8) + value[len(value)-4:]
}
