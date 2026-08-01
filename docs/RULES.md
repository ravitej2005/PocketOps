# PocketOps Engineering Rules

## How to Use This Document

This file is the non-negotiable rulebook for anyone — human or AI coding
agent — implementing PocketOps. It does not explain *why* the system is
structured the way it is (see `ARCHITECTURE.md`), and it does not explain
detailed component behavior (see `DESIGN.md`). It exists to draw hard lines
that must never be crossed during implementation.

- For architectural rationale → `ARCHITECTURE.md`
- For implementation-level behavior → `DESIGN.md`
- For build order → `PHASES.md`
- For fast context restoration → `MEMORY.md`
- For product scope/why → `PRD.md`

If any other document, prompt, convenience, or "better idea" conflicts with
this file, **this file wins**, unless a human maintainer explicitly approves
an architecture change and updates the frozen documents accordingly.

---

## 0. Rule Priority

1. `RULES.md` (this file) and the frozen architecture in `ARCHITECTURE.md`
2. `DESIGN.md`
3. `PHASES.md` (for what to build *now*)
4. `MEMORY.md` (for fast orientation)
5. Coding-agent preference — **lowest priority, always**

A coding agent's stylistic or architectural preference never overrides a
frozen decision. If a rule seems inconvenient for a specific implementation
detail, the rule still wins — flag the friction instead of silently working
around it.

---

## 1. Architecture Rules

- The backend is a **modular monolith** (Spring Boot). It is never split into
  microservices.
- The self-hosted integration mechanism is a **standalone Go Agent**, never an
  SDK, library, sidecar-injected-into-the-app, or embedded dependency.
- Agent ↔ Backend communication is **gRPC bidirectional streaming** over TLS,
  using **Protocol Buffers** as the shared contract. No alternate protocol
  (REST polling, MQTT, raw sockets, etc.) replaces this.
- Flutter ↔ Backend communication is **REST + WebSocket** only. gRPC is never
  exposed directly to the mobile client.
- Infrastructure integrations are abstracted behind a **capability model**.
  Provider- or agent-specific branching never leaks into shared business
  logic or UI code.
- The Agent is **application-agnostic**. It must never be designed, coded, or
  documented as if it understands or depends on the specifics of any
  monitored application (including StormAPI).

---

## 2. Monitored Application Isolation

- **DO NOT modify StormAPI** (or any other monitored application) to support
  PocketOps. Not its repository, backend code, frontend code, database,
  Docker Compose files, or container images.
- The PocketOps Agent lives on the **host**, alongside Docker, never inside
  the monitored application's repository or container graph.
- No monitored application may ever be required to import a PocketOps SDK,
  add PocketOps environment variables to its own containers, expose custom
  health endpoints for PocketOps, or be recompiled/rebuilt on PocketOps's
  behalf.
- If implementing a feature seems to require touching the monitored
  application, the feature is out of scope until redesigned to avoid that.

---

## 3. Infrastructure Isolation

- One Infrastructure's (and its Agent's) registration, connection state,
  failures, or removal must **never** affect any other Infrastructure.
- Revoking or deleting Agent A must have zero effect on Agent B, C, D, etc.
- Infrastructure onboarding is **additive configuration**, not a
  code-changing event. Adding a new Infrastructure must never require a
  backend redeploy, schema hack, or hardcoded branch.

---

## 4. Security Rules

- Every infrastructure-scoped or resource-scoped query **must** resolve
  through authenticated user ownership (e.g. `findByIdAndUserId(...)`).
  Externally supplied IDs are never trusted on their own — no
  "resolve first, authorize later" patterns.
- The Docker daemon/API is **never** exposed publicly or to the internet
  directly. Only the Agent talks to the local Docker Engine.
- **No arbitrary command execution.** No `docker exec`, no shell access, no
  SSH, no generic "run this command" endpoint, on the Agent or the backend.
  Only the explicit allow-listed commands (`START_CONTAINER`,
  `STOP_CONTAINER`, `RESTART_CONTAINER`) may ever reach Docker.
- Secrets, tokens, and credentials (JWTs, refresh tokens, Agent identity
  material, provider credentials, OAuth secrets) are **never** written to
  logs, committed to Git, or returned to the client after initial creation.
- No custom cryptography. Use Spring Security's supported, vetted mechanisms
  for password hashing, token handling, and encryption at rest.
- Stored provider credentials are **never** returned to Flutter once saved —
  only their existence/status, never the secret value.
- Agent registration (setup) tokens are **single-use**, **short-lived**, and
  **infrastructure-specific**. A used or expired token must be rejected —
  replay must fail.
- Revoked Agents must be permanently unable to reconnect using their old
  identity; revocation is not advisory, it is enforced at the connection
  layer.
- WebSocket subscriptions must be authorized per user/infrastructure before
  any event is delivered. Cross-user event leakage is a critical defect, not
  an edge case.

---

## 5. Authentication Rules

- Supported authentication methods are **email/password** and **GitHub
  OAuth** only. Both terminate in the same PocketOps session model — no
  parallel auth systems.
- Sessions use a **short-lived access JWT** plus a **rotating refresh
  token**, backed by **server-side session tracking** that supports
  revocation (single device, other device, or all devices).
- Passwords are hashed using a Spring Security–supported strong hashing
  algorithm. Never store or compare plaintext passwords.
- Refresh tokens are stored server-side only as hashed/derived values, never
  in recoverable plaintext.

---

## 6. Docker Rules

**Allowed** Agent-to-Docker operations:

- inspect (container/state discovery)
- stats (CPU/memory/network)
- events (Docker event stream)
- logs (streamed, allow-listed containers)
- start
- stop
- restart

**Forbidden**, unconditionally:

- `exec` into a container
- shell or terminal access of any kind
- arbitrary/free-form command strings
- any mechanism that exposes the raw Docker API to the backend, Flutter, or
  the internet

---

## 7. State Rules

- `Infrastructure.UNKNOWN` is the correct state whenever PocketOps loses
  authoritative information (Agent offline, provider unreachable). It is
  **not** the same as `CRITICAL`, and confusing the two is a defect.
- Stale (last-known) data must always be visibly marked as stale, with a
  "last checked/confirmed" indicator. It is never presented as live.
- A manual refresh failure must show a clear retry affordance and must not
  silently fall back to fabricated "everything is fine" UI.
- Reconnection (Agent or Flutter real-time channel) must fetch a fresh
  authoritative snapshot and reconcile state before resuming live streaming —
  never assume no events were missed.

---

## 8. Command Rules

- Only allow-listed commands may be dispatched:
  `START_CONTAINER`, `STOP_CONTAINER`, `RESTART_CONTAINER`. No generic
  command execution endpoint exists anywhere in the system.
- **No offline destructive queue.** If the Agent is offline when a
  destructive command is requested, the operation fails immediately
  (`AGENT_OFFLINE`). It is never queued, buffered, or executed later when
  the Agent reconnects.
- Destructive commands require, in order: authentication → ownership check →
  capability check → Agent-online check → explicit user confirmation
  (stronger confirmation for `CRITICAL` resources) → device biometric gate →
  dispatch.
- Command correlation/result tracking is used only as needed to report
  success/failure back to the client — do not build a persistent command
  history subsystem beyond what's needed for MVP traceability.

---

## 9. Alert Rules

- Alert transitions use debounce/stability rules to avoid flapping
  (`OPEN → RESOLVED → OPEN → RESOLVED` spam is a defect).
- A recovery must remain stable for a short, configurable window before an
  alert is marked `RESOLVED`.
- Repeated occurrences of the same underlying issue update `occurrence_count`
  on the existing incident rather than creating duplicate alerts.
- Notification delivery must be useful, not noisy — no notification storms
  from flapping states.
- PocketOps's alerting is intentionally simple. Do not build
  PagerDuty-style escalation policies, on-call schedules, or multi-channel
  routing.

---

## 10. Flutter Rules

- State management is **Riverpod**. No parallel state management library is
  introduced.
- Code organization is feature-first (see `ARCHITECTURE.md`), not an
  over-layered clean-architecture structure with unnecessary ceremony.
- Business logic does not live inside widget build methods — widgets render
  state, they don't compute it.
- Streams (WebSocket subscriptions, log streams, etc.) must be properly
  disposed when no longer needed — no leaked subscriptions.
- No secrets, refresh tokens, or other sensitive session material go into
  ordinary `SharedPreferences`-style storage. Sensitive data goes into
  platform secure storage only.
- No destructive action (start/stop/restart) is ever triggered directly from
  the home-screen widget.
- Provider (managed-infrastructure-provider) branching (`if (provider ==
  ...)`) must not appear scattered through UI or business logic — it stays
  behind the capability/provider abstraction.

---

## 11. UX Rules

- The UI is **light-first**: professional, calm, precise, developer-oriented.
  No cyberpunk-terminal aesthetic, no gratuitous gradients/glassmorphism, no
  generic "AI dashboard" look.
- Health/status is never communicated by color alone — pair color with
  icon/text for accessibility.
- Persistent or critical states (stale data, critical alerts) are not shown
  via a transient Snackbar; Snackbars are for temporary action feedback only.
- Never present a false success state, and never show generic "Something
  went wrong" messaging when the actual cause is known.

---

## 12. Widget Rules

- One conceptual medium-sized Android home-screen widget, capable of
  representing multiple Infrastructures via manual previous/next navigation.
- No destructive controls (start/stop/restart) are exposed from the widget —
  tapping content opens the app instead.
- The widget must truthfully reflect freshness (e.g. "last checked Nm ago")
  and must never imply all containers failed just because connectivity to
  the backend/Agent was lost.
- Widget behavior must respect actual Android widget platform constraints.
  Do not design or implement animations/auto-carousel behavior that Android
  widgets cannot reliably or efficiently support.

---

## 13. Backend Rules

- Controllers are thin; business logic lives in the service/use-case layer.
- Ownership/authorization enforcement is centralized, not duplicated ad hoc
  per endpoint.
- Repositories must not provide a path that bypasses ownership-scoped
  queries (e.g. an unscoped `findById` used where an owned lookup was
  intended).
- No single "god service" holding unrelated responsibilities.
- No artificial microservice decomposition — the backend stays a modular
  monolith with clear internal module boundaries (`auth/`, `infrastructure/`,
  `agent/`, `provider/`, `docker/`, `monitoring/`, `logs/`, `alert/`,
  `notification/`, `websocket/`, `security/`, `common/`).

---

## 14. Agent Rules

- Small memory/CPU footprint; the Agent must not become a burden on the
  monitored host.
- No dependency on, or awareness of, any specific monitored application.
- No modification of, or requirement to modify, the monitored application's
  source, containers, or Compose files.
- Bounded buffers for metrics/logs/events — no unbounded growth.
- Reconnect uses backoff, not tight retry loops.
- Graceful shutdown on termination signals.
- No secrets or credentials written to Agent logs.
- No arbitrary command support — only the allow-listed Docker operations
  defined in Rule 6.

---

## 15. Database Rules

- Schema changes go through migrations only — no manual production schema
  edits.
- Indexes exist to support ownership- and resource-scoped lookups
  efficiently.
- No high-frequency metric ingestion into MySQL. PocketOps is not building a
  time-series database; live metrics are primarily streamed, with only
  bounded/recent state cached as needed.
- No permanent, unbounded log-archival tables (`container_logs` as a
  long-term store is out of scope).

---

## 16. API Rules

- A consistent error response model is used across all endpoints (stable
  error codes, safe messages, no leaking internals).
- No stack traces or internal exception details returned to clients.
- All input is validated at the API boundary.
- Rate limiting is applied to sensitive endpoints (auth, registration) where
  appropriate.

---

## 17. Logging Rules

- Never log secrets: passwords, JWTs, refresh tokens, Agent credentials,
  provider credentials, OAuth secrets.
- Logs are structured and useful for debugging — not noise, not silence.

---

## 18. Dependency Rules

- No library or framework is added merely to save a few lines of code or
  because it's trendy.
- Every non-trivial dependency requires a concrete, stated architectural
  reason (see `ARCHITECTURE.md` §"Portfolio Quality Without Resume-Driven
  Engineering" for the reasoning behind each core technology choice).
- No message broker (Kafka, RabbitMQ), no Redis, no time-series database, no
  Kubernetes, no Prometheus/Grafana, no ELK stack, no Terraform, no CI/CD
  engine — unless an explicit, approved architecture change introduces it.

---

## 19. Scope Rules

The following are explicitly **forbidden** unless a human maintainer
explicitly approves an architecture change:

- Kubernetes
- Prometheus / Grafana
- Kafka / RabbitMQ
- ELK stack
- Terraform
- A custom CI/CD engine
- SSH-based remote management
- `docker exec` / remote shell
- Distributed tracing infrastructure
- A dedicated time-series database
- An automatic Agent updater (MVP)
- A full WebAuthn system (MVP)
- Enterprise KMS/HSM integration (MVP)
- Support for multiple managed providers simultaneously (MVP — one is
  sufficient to prove the abstraction)
- Turning PocketOps into Portainer, Grafana, PagerDuty, Jenkins, or a
  general cloud-management suite

If a request would require any of the above, treat it as out of scope and
say so rather than implementing it.

---

## 20. AI Coding Rules

Before making any change, an AI coding agent must:

1. Read `MEMORY.md`.
2. Read the relevant section of `PHASES.md` for the current phase.
3. Read the relevant section(s) of `ARCHITECTURE.md` / `DESIGN.md`.
4. Inspect the existing code before writing new code.
5. Modify the existing implementation rather than regenerating the project
   from scratch.
6. Preserve currently working behavior.
7. Never delete working code purely because a different design is
   personally preferred.
8. Never rewrite unrelated files as a side effect of an unrelated task.
9. Run relevant tests/builds after making changes, and confirm the project
   still builds.
10. Update `MEMORY.md` only when actual implementation state changes, or
    when an explicitly approved architectural decision changes — not
    speculatively.

**The six frozen documents (`PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`,
`MEMORY.md`, `PHASES.md`, `RULES.md`) must never be rewritten by an AI coding
agent merely because it prefers a different architecture.** Architecture
changes require explicit human approval and a deliberate update to these
documents — not a silent drift introduced while implementing a feature.

---

## 21. Enforcement Summary

If any proposed change, feature, or "improvement" conflicts with a rule in
this document:

1. Stop.
2. Do not implement it.
3. State clearly which rule it conflicts with.
4. Propose the smallest change that satisfies the underlying need without
   violating the rule, or flag it for explicit human review if no such
   change exists.

These rules exist so that PocketOps remains buildable, secure, honest about
its own state, and free of scope creep — for every phase, by every
contributor, human or AI.
