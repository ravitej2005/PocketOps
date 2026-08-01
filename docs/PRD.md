# PocketOps Product Requirements Document

## How to Use This Document

This is the product-level source of truth: **what** PocketOps is and **why** it exists. For system structure see `ARCHITECTURE.md`. For implementation-level behavior see `DESIGN.md`. For build order see `PHASES.md`. For non-negotiable constraints see `RULES.md`. For rapid AI context restoration see `MEMORY.md`.

## Document Status

**FROZEN for MVP.** The product scope, infrastructure model, and feature set defined here have completed design review. Do not expand scope without explicit approval recorded in `MEMORY.md`'s decision log.

---

## Product Vision

PocketOps is a mobile-first DevOps monitoring and infrastructure-control platform. It gives developers who run Dockerized services a single Android app to see whether their infrastructure is healthy, understand what's happening inside individual services, and take safe, bounded corrective action — without opening a laptop, SSHing into a box, or juggling multiple dashboards.

PocketOps treats infrastructure awareness as a mobile-native problem, not a shrunken desktop dashboard.

## Problem Statement

Developers running Dockerized services — solo builders, small teams, and self-hosters — routinely need quick answers to simple questions: *Is everything up? What broke? Can I safely restart it?* Today, answering these questions requires a laptop, an SSH session or web dashboard, and often several different tools (Docker CLI, cloud console, log aggregator, alerting tool). There is no lightweight, mobile-first way to get a trustworthy, real-time answer and take limited safe action from a phone.

Existing tools solve adjacent problems but not this one:

- **Portainer / Docker dashboards** are desktop-oriented and require exposing management surfaces.
- **Grafana / Prometheus** are metrics platforms, not mobile operational tools, and are heavy to run for small deployments.
- **PagerDuty**-style tools handle alert routing for large orgs, not lightweight personal/small-team infrastructure awareness.
- **Cloud provider apps** (where they exist) are locked to one provider and don't unify self-hosted Docker with managed platforms.

## Product Positioning

PocketOps is **mobile-first infrastructure awareness with bounded operational control.**

It is:
- A phone-native way to see infrastructure health at a glance.
- A safe, narrow channel for start/stop/restart operations — not a remote shell.
- A unifying layer across self-hosted Docker environments and select managed platforms.

It is explicitly **not**:
- A remote terminal or SSH replacement.
- A full observability/APM platform (no distributed tracing, no long-term metrics warehouse).
- A CI/CD or infrastructure-provisioning tool.
- An incident-management platform like PagerDuty.
- A general Docker management UI (like Portainer) intended for exhaustive control.

## Target Users

- Solo developers running personal projects on a VPS or home lab.
- Backend developers who want infrastructure status without a laptop.
- Small teams running Dockerized services without dedicated ops tooling.
- Developers maintaining self-hosted Docker environments (EC2, VPS, home servers).
- Developers who also use a supported managed hosting platform and want a unified view.

## Primary Use Cases

- Checking overall infrastructure health from a phone.
- Inspecting an individual service's status, metrics, and logs.
- Viewing live CPU/memory/network metrics for a running service.
- Streaming live container logs for debugging.
- Receiving push notifications when a service fails or recovers.
- Performing bounded container actions (start/stop/restart) safely.
- Monitoring infrastructure at a glance from an Android home-screen widget.
- Connecting a new self-hosted Docker environment without touching the monitored application.
- Connecting a supported managed infrastructure provider.

## Infrastructure Types

PocketOps supports exactly two infrastructure classes, and every infrastructure a user connects is one or the other.

### SELF_HOSTED / CONTROLLED

A Docker host (VM, EC2 instance, VPS, home server) that the user controls directly. Monitored via the **PocketOps Agent**, a standalone Go binary installed on the host itself — never on or inside the monitored application. See `ARCHITECTURE.md` for the full agent model.

### MANAGED

A supported third-party hosting platform accessed through a **Provider Adapter** talking to that platform's API. No PocketOps Agent is involved. Functionality is bounded by what the provider's API actually exposes.

### Behavioral Differences

| Aspect | SELF_HOSTED | MANAGED |
|---|---|---|
| Requires PocketOps Agent | Yes | No |
| Requires host installation step | Yes (installer command) | No (credential/connect flow) |
| Full capability set (start/stop/restart, live logs, live metrics) | Yes, if Agent online | Only if provider API supports it |
| Docker-level container discovery | Yes | No — provider's own resource model |
| Offline/unreachable semantics | Agent OFFLINE → Infrastructure UNKNOWN | Provider API unreachable → Infrastructure UNKNOWN |

Capabilities are always advertised truthfully per infrastructure; the UI never implies a control that isn't actually supported (see Capability Model in `ARCHITECTURE.md`).

## Functional Requirements

Requirements are grouped by domain and are written to be testable.

### Authentication & Sessions

- **FR-AUTH-001**: A user must be able to register and sign in using email and password.
- **FR-AUTH-002**: A user must be able to sign in using GitHub OAuth.
- **FR-AUTH-003**: A user must be able to log out a single device, a specific other device, or all devices.
- **FR-AUTH-004**: An expired access token must be silently refreshable using a valid, non-revoked refresh token without requiring re-login.
- **FR-AUTH-005**: A revoked or reused refresh token must be rejected and force re-authentication.

### Infrastructure Management

- **FR-INFRA-001**: A user must be able to register multiple infrastructures independently; adding, removing, or modifying one must never affect another.
- **FR-INFRA-002**: A user must be able to create a SELF_HOSTED infrastructure and receive a one-time installation credential.
- **FR-INFRA-003**: A user must be able to create a MANAGED infrastructure by connecting a supported provider.
- **FR-INFRA-004**: Every infrastructure operation must be resolved using both the resource identifier and the authenticated user's ownership — no infrastructure or resource may be accessed by ID alone.
- **FR-INFRA-005**: A user must be able to disconnect/remove an infrastructure without affecting other registered infrastructures.

### Agent (Self-Hosted)

- **FR-AGENT-001**: Connecting a self-hosted Docker environment must not require modifying, rebuilding, or instrumenting the monitored application's source code.
- **FR-AGENT-002**: The same precompiled Agent binary must be installable on any compatible Docker host without customization per user or per application.
- **FR-AGENT-003**: A one-time registration credential must be single-use, short-lived, and infrastructure-specific.
- **FR-AGENT-004**: An Agent that stops sending heartbeats within the configured timeout must be marked OFFLINE, and its infrastructure must become UNKNOWN — never CRITICAL, since PocketOps no longer has authoritative data.
- **FR-AGENT-005**: A revoked Agent identity must be permanently rejected on reconnection attempts.

### Monitoring

- **FR-MON-001**: PocketOps must display real-time CPU, memory, network, and uptime information for each monitored resource when the underlying infrastructure supports it.
- **FR-MON-002**: PocketOps must display overall infrastructure health derived from the state of its resources.
- **FR-MON-003**: After a reconnection (Agent or Flutter real-time channel), PocketOps must fetch a fresh authoritative snapshot and reconcile state before resuming live updates — it must never assume no state changes occurred while disconnected.

### Logs

- **FR-LOG-001**: A user must be able to view a live streaming log tail for a supported resource, with pause/resume, search, copy, and auto-scroll.
- **FR-LOG-002**: PocketOps must not retain logs as a permanent archive; retention is bounded to what is useful for live/recent viewing.

### Alerts

- **FR-ALERT-001**: PocketOps must open an alert when a monitored resource transitions to a failure state.
- **FR-ALERT-002**: PocketOps must automatically resolve an alert once the resource has remained healthy for a stability window, rather than requiring manual resolution.
- **FR-ALERT-003**: Repeated rapid state flapping must not generate a notification per flap; related occurrences must be deduplicated onto a single incident with an occurrence count.
- **FR-ALERT-004**: A user must be able to acknowledge an open alert.

### Notifications

- **FR-NOTIF-001**: PocketOps must send an Android push notification via FCM when an alert opens, subject to flapping protection.
- **FR-NOTIF-002**: Tapping a notification must deep-link directly into the relevant infrastructure/service screen.

### Operations (Destructive Actions)

- **FR-OPS-001**: PocketOps must support only three remote operations: start, stop, restart — no generic or arbitrary command execution.
- **FR-OPS-002**: PocketOps must reject a destructive operation immediately (not queue it) when the corresponding Agent is offline or the provider is unreachable.
- **FR-OPS-003**: A destructive operation must require explicit user confirmation and a device biometric gate before being submitted.
- **FR-OPS-004**: Operations on resources marked CRITICAL must present stronger warning UX than operations on NORMAL resources.

### Widget

- **FR-WIDGET-001**: An Android home-screen widget must display overall status, a service health summary, and a small metric summary for the user's infrastructures.
- **FR-WIDGET-002**: The widget must never present destructive controls.
- **FR-WIDGET-003**: The widget must clearly indicate when displayed data is stale rather than live.

## Non-Functional Requirements

### Security
Every user-scoped operation must be ownership-verified server-side. Secrets and credentials must never be logged, committed, or returned to the client after creation. The Docker daemon must never be exposed publicly. No arbitrary remote command execution is permitted under any framing.

### Performance
Mobile UI must remain responsive under live metric/log streams via bounded buffers and virtualization. The backend must not flood MySQL with high-frequency metric writes. The Agent must maintain a small footprint suitable for running alongside the monitored application on the same host.

### Reliability
Connectivity loss (Agent, WebSocket, provider API) must degrade to a clearly marked stale/UNKNOWN state rather than silently showing incorrect data or crashing.

### Mobile UX
The app must feel native, calm, and information-dense without clutter, consistent with the light-first visual identity (see `DESIGN.md`).

### Observability of PocketOps Itself
Enough structured backend logging must exist to diagnose issues in the control plane itself, without logging sensitive data.

### Maintainability & Extensibility
The backend remains a modular monolith with clear module boundaries so that a new managed provider or capability can be added without cross-cutting rewrites.

### Data Integrity
Alert, agent, and infrastructure state transitions must be consistent and never leave a resource in an ambiguous or contradictory state.

## Offline Requirements

When authoritative monitoring data is unavailable (Agent offline, provider unreachable, WebSocket disconnected), PocketOps must:
- Continue displaying the **last known state**, clearly labeled as stale, with a "last checked" timestamp.
- Never silently relabel stale data as live.
- Allow manual refresh, with an explicit failure state if the refresh itself fails.
- Immediately reject (never queue) any destructive operation attempted while offline.

## Notification Requirements

Notifications must be meaningful, not noisy: failures and stable recoveries generate a notification; flapping does not. Every notification must deep-link to the relevant screen. No notification may contain a raw secret or credential.

## Widget Requirements

One medium-sized Android home-screen widget capable of representing multiple infrastructures via manual previous/next navigation (not automatic carousel, per Android platform constraints — see `DESIGN.md`). Displays name, overall status, service summary, a compact metric summary, and freshness. Tapping opens the relevant screen in the app; no destructive action is available directly from the widget.

## UX Requirements

- **Onboarding**: short, skippable, visual, explaining Monitor / Understand / Act / Stay informed, followed by authentication.
- **Coach marks**: one-time, skippable, replayable, contextual explanations of the home, infrastructure, and alert surfaces on first meaningful use.
- **Empty states**: clear guidance when no infrastructure is yet connected.
- **Loading states**: skeleton UI preferred over spinners where practical.
- **Error states**: specific, truthful messaging tied to the actual failure (agent offline, provider unreachable, auth failure) — never a generic "something went wrong" when the cause is known.
- **Stale states**: persistent, unmistakable visual treatment distinct from live state.
- **Critical-state UX**: stronger visual weight and confirmation friction for CRITICAL resources and their destructive actions.
- **Confirmation behavior**: every destructive action requires explicit confirmation plus device biometrics before submission.

## Success Criteria

The MVP is demonstrably complete when a user can: connect the StormAPI EC2 environment as self-hosted infrastructure with zero StormAPI source changes; see all StormAPI containers and overall health; view live metrics and logs for an individual service; observe a simulated failure surface as an alert and push notification; safely restart the affected service through the app; watch the alert auto-resolve; and see the Android widget reflect the same state truthfully, including during a deliberate connectivity interruption.

## MVP Scope

Everything enumerated in the Functional Requirements above, implemented against exactly one self-hosted demo environment (StormAPI on AWS EC2) and exactly one managed provider integration, using the frozen technology stack in `ARCHITECTURE.md`.

## Explicit Non-Goals

PocketOps explicitly will **not**, in this MVP or without a separately approved scope change:

- Provide SSH, remote terminal, `docker exec`, or arbitrary shell/command execution.
- Function as a distributed tracing, APM, or long-term time-series metrics platform.
- Implement Kubernetes, Kafka, RabbitMQ, Prometheus, Grafana, ELK, or Terraform integration.
- Provide CI/CD pipeline functionality.
- Support more than one managed provider.
- Implement an automatic agent updater, full WebAuthn step-up authentication, or enterprise KMS/HSM integration.
- Become a general-purpose Docker management console (Portainer-equivalent) or incident-management suite (PagerDuty-equivalent).

## Demo Scenario

The canonical end-to-end demonstration path:

1. User signs in.
2. User connects the StormAPI EC2 environment as a self-hosted infrastructure.
3. The PocketOps Agent, installed independently on the EC2 host, registers.
4. Seven StormAPI containers appear, discovered automatically.
5. Overall infrastructure health is visible on the home screen.
6. User opens `execution-service` and views live metrics.
7. User views live logs for `execution-service`.
8. A failure is simulated; PocketOps opens an alert and marks the resource FAILED.
9. User receives a push notification.
10. User restarts `execution-service` from the app (confirmation + biometric gate).
11. The service recovers; after the stability window, the alert auto-resolves.
12. The Android widget reflects the same healthy state, including having correctly shown stale/UNKNOWN state during any connectivity gap in the demo.

## Acceptance Criteria

- **Infrastructure onboarding**: A new self-hosted infrastructure can be created, installed, and reach ONLINE/HEALTHY status using only the generated installer command — with zero edits to the monitored application's repository.
- **Monitoring**: Live CPU/memory/network/uptime figures update in the UI within the configured streaming cadence while the Agent is connected.
- **Logs**: A log viewer can stream, pause, resume, and search a live container log.
- **Alerts**: A simulated failure produces exactly one open alert (not a flood), a push notification, and an automatic resolution once stability is restored.
- **Destructive operations**: Restart succeeds when the Agent is online and is immediately rejected with a clear message when the Agent is offline — never queued.
- **Offline/stale UX**: Killing Agent connectivity causes the infrastructure to show UNKNOWN with a last-checked timestamp within one heartbeat-timeout interval, never CRITICAL.
- **Widget**: The widget reflects the same infrastructure state as the app, without ever exposing a destructive control.
- **Security**: A second test user cannot view, subscribe to, or act on the first user's infrastructure under any endpoint or WebSocket channel.
