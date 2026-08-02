# PocketOps Project Memory

## How to Use This Document
Read this file FIRST, every session, before touching the project. It is the high-value context-restoration file — not another architecture document. For details, consult `ARCHITECTURE.md`, `DESIGN.md`, `PHASES.md`, and `RULES.md` as needed.

## Product in 10 Lines
PocketOps is a mobile-first DevOps monitoring and infrastructure-control app. Developers connect Dockerized infrastructure — either self-hosted (via a small Go Agent on the host) or managed (via a provider API) — and monitor health, metrics, and logs, and safely start/stop/restart resources from their phone or an Android widget. It shows honest state at all times, including UNKNOWN/stale when connectivity is lost. It never queues destructive operations while offline. The backend is a Spring Boot modular monolith with MySQL. The mobile app is Flutter/Riverpod. Agent↔Backend uses gRPC/Protobuf; Flutter↔Backend uses REST + WebSocket. The reference self-hosted demo target is StormAPI on AWS EC2, left completely untouched by PocketOps. Scope is deliberately bounded — this is a portfolio-grade MVP, not an enterprise observability platform.

## Frozen Stack
| Layer | Technology |
|---|---|
| Mobile | Flutter / Dart |
| Mobile state | Riverpod |
| Backend | Java 21 + Spring Boot |
| Security | Spring Security |
| Persistence | Spring Data JPA |
| Database | MySQL |
| Auth | Email/password + GitHub OAuth |
| Sessions | Short-lived JWT + rotating refresh tokens |
| Flutter ↔ Backend | REST + WebSocket |
| Self-hosted agent | Go |
| Agent ↔ Backend | gRPC bidirectional streaming |
| Agent protocol | Protocol Buffers |
| Agent transport security | TLS + mutual identity verification |
| Docker integration | Local Docker API via Go Docker SDK |
| Push notifications | Firebase Cloud Messaging |
| Demo self-hosted target | StormAPI on AWS EC2 |
| Backend hosting | Managed hosting + Managed MySQL |

## Architecture Snapshot
```
Android (Flutter) <--REST/WS--> Spring Boot Control Plane <--JPA--> MySQL
                                        |          |
                                       FCM     Provider API (managed)
                                        ^
                                  gRPC/TLS
                                        |
                              Go Agent (on Docker host)
                                        |
                                 Docker Engine
```

## Most Important Decision
**The PocketOps Agent is host-level, application-agnostic software — not part of the monitored application.**
**DO NOT MODIFY STORMAPI. EVER.** The Agent lives alongside StormAPI's containers on the same EC2 host but is never added to StormAPI's repository, never requires StormAPI code changes, and never requires the developer to know or install Go.

## Self-Hosted Flow
Create infrastructure → PENDING → one-time registration credential issued → user runs install command on Docker host → Agent registers, credential invalidated → persistent Agent identity established → gRPC connection → snapshot → infrastructure ONLINE.

## Managed Flow
Add Infrastructure → choose Managed → select provider → connect/credential flow → capability discovery → validated → ONLINE, capability set determined entirely by the provider.

## Protocol Responsibilities
- **REST**: request/response, non-continuous operations (auth, infra CRUD, registration, actions, ack).
- **WebSocket**: continuous backend→Flutter push (metrics, logs, state, alerts).
- **gRPC**: the only Agent↔Backend channel (heartbeats, snapshots, metrics, events, logs, commands/results).

## Security Invariants
Ownership-scoped queries everywhere (never trust a client-supplied ID alone). Docker daemon never publicly exposed. No arbitrary command execution — allow-list only (`START_CONTAINER`, `STOP_CONTAINER`, `RESTART_CONTAINER`). No secrets in Git or logs. Mutual Agent/Backend TLS identity verification. Registration tokens are single-use, short-lived, infra-specific.

## State Semantics
**Agent OFFLINE ≠ Infrastructure FAILED.** Loss of connectivity always yields `UNKNOWN`, never `CRITICAL`. Stale data is always shown with an explicit "last checked" timestamp — never presented as live.

## Destructive Operation Rule
**Never queue offline.** If the Agent is not ONLINE, a start/stop/restart request fails immediately with `AGENT_OFFLINE`. No deferred/retroactive execution when the Agent returns.

## Authentication Summary
Email/password (Spring Security hashing) or GitHub OAuth → short-lived JWT access token + rotating refresh token + server-side session record supporting per-device or all-device revocation.

## Capability Model
`METRICS, LOGS, LIVE_LOGS, START, STOP, RESTART, NETWORK_STATS, CONTAINER_DISCOVERY` — UI and backend behavior are driven by capability sets, never by `if (provider == X)` branching in shared logic.

## Flutter UX Philosophy
Light-first, calm, professional, developer-oriented. No color-only status signaling. Skeletons over spinners. Honest stale-state UI. No fake success snackbars.

## Widget Philosophy
One medium widget, manual previous/next between infrastructures, no destructive controls, truthful freshness — never fabricate a "live-looking" state from stale data, and never assume Android widget platform supports automatic carousel animation.

## Current Demo Target
StormAPI on AWS EC2 — see `PRD.md` Demo Scenario for the exact acceptance path.

## Repository Map
```
PocketOps/
├── pocketops/     # Flutter app
├── backend/       # Spring Boot control plane
├── agent/         # Go host agent
├── proto/         # shared gRPC contracts (pocketops_agent.proto)
├── docs/          # this documentation set
├── docker-compose.yml
├── .env.example
└── README.md
```

## Out-of-Scope Reminder
No Kubernetes, Prometheus, Grafana, Kafka, RabbitMQ, ELK, Terraform, CI/CD engine, SSH/remote shell, `docker exec`, distributed tracing, time-series database, automatic Agent updater, full WebAuthn, enterprise KMS/HSM, or more than one managed provider — for MVP.

## AI Coding Instructions
Before changing anything architectural: **STOP.** Re-check `ARCHITECTURE.md` and `RULES.md` first. Do not introduce new dependencies or architectural patterns without explicit approval. Modify existing implementation rather than regenerating the project. Do not rewrite these six documents because a different design is preferred — they are frozen. Update this file's "Current Implementation State" section as real progress happens; do not mark anything implemented that isn't.

## Decision Log
- **Why Go Agent?** Small footprint, single static binary, easy to install on any Docker host without a heavy runtime, and clean separation from any JVM/Node dependency the monitored app might have.
- **Why gRPC?** Efficient bidirectional streaming with a strongly-typed shared schema (Protobuf) between Go and Java, well-suited to continuous heartbeats/metrics/commands over one long-lived connection.
- **Why modular monolith?** One deployable unit is simpler to build, test, and operate for this scope; microservices would add operational overhead with no corresponding benefit, and StormAPI already demonstrates that architectural style separately.
- **Why no public Docker API?** The Docker daemon is high-privilege; public exposure is a severe security risk regardless of authentication layered on top.
- **Why no metrics history DB?** MVP scope is live-first monitoring; a time-series database is a separate, larger engineering commitment not justified by current requirements.
- **Why WebSocket for Flutter?** Efficient continuous push for metrics/logs/alerts without polling overhead.
- **Why provider capabilities?** Managed providers vary in what they expose; a capability model avoids hardcoded provider-specific UI/business-logic branching.
- **Why UNKNOWN on connectivity loss?** PocketOps loses monitoring authority when the Agent/provider is unreachable; asserting CRITICAL would be a false claim about infrastructure state PocketOps cannot actually verify.

## Current Implementation State

### Phase 0 — Documentation / Contracts / Project Foundation
**Status: COMPLETE.**

Verified as of 2026-07-31 (session 2):

**Complete:**
- All six frozen docs exist and are consistent (`PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `MEMORY.md`, `PHASES.md`, `RULES.md`).
- Root repository structure created: `.gitignore`, `README.md`, `.env.example`, `docker-compose.yml`.
- `backend/` skeleton builds cleanly on Java 21 (`.\mvnw.cmd clean compile` with `JAVA_HOME` pointing to Temurin 21).
- All 14 backend module packages created as empty `package-info.java` stubs: `auth`, `user`, `session`, `infrastructure`, `agent`, `provider`, `docker`, `monitoring`, `logs`, `alert`, `notification`, `websocket`, `security`, `common`.
- `pocketops/` Flutter skeleton builds cleanly (`flutter pub get`, `flutter analyze`, `flutter build apk --debug`).
- Flutter feature-first directory structure created: `core/`, `features/{auth,home,infrastructure,service_details,alerts,settings,onboarding}/`, `test/`.
- `agent/` Go module structure created: `go.mod`, `main.go`, 9 empty package stubs (`registration`, `connection`, `docker`, `metrics`, `logs`, `events`, `commands`, `config`, `security`).
- `agent/` scaffold build is verified (`go test ./...`, `go build ./...`).
- `proto/` directory created (placeholder — real contract is Phase 4).
- `docker-compose.yml` MySQL service validated: container healthy and `pocketops` database accessible via configured application credentials.
- No secrets committed. `.env` is gitignored. `backend/target/` is gitignored.

**Environment note:**
- JDK 21 is installed (`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`) and used successfully for backend builds, but the default shell `java`/`javac` still resolve to JDK 17 unless `JAVA_HOME`/`PATH` are overridden.

**Phase 0 completion criteria per PHASES.md:**
- [x] All six docs exist and are internally consistent.
- [x] `backend/` builds with an empty Spring Boot app.
- [x] `pocketops/` builds with an empty Flutter app.
- [x] `agent/` builds as an empty Go binary.
- [x] `docker-compose.yml` brings up local MySQL.

### Phase 1 — Flutter + Spring Boot + MySQL Foundation
**Status: COMPLETE.**

Implemented and verified:
- Backend foundation in `common`/`security`: `GET /api/health`, request-id filter (`X-Request-Id`), consistent API error shape (`code`, `message`, `requestId`) for handled failure categories, and baseline security config permitting health while protecting other routes.
- MySQL wiring + migration tooling: datasource config in `application.properties`, Flyway integration, and baseline migration `V1__phase1_baseline.sql`.
- Flutter `core` foundation: app theme scaffold, route generation scaffold, API configuration, HTTP health client, and Riverpod-backed health-check screen proving mobile→backend connectivity path.
- Local infrastructure wiring: `docker-compose.yml` now supports MySQL + backend service; backend container build added at `backend/Dockerfile`.

Phase 1 validation completed:
- Backend tests pass on Java 21 (`.\mvnw.cmd clean test`).
- Backend runs against local MySQL and returns `{"status":"UP","database":"UP"}` from `/api/health`.
- `docker compose up -d backend` serves the same `/api/health` result end-to-end.
- Flutter builds and analyzes cleanly (`flutter analyze`, `flutter build apk --debug`).

### Phase 2 — Authentication + GitHub OAuth + Sessions
**Status: COMPLETE.**

Verified as of 2026-07-31 (session 3):

**Complete:**
- Backend auth/session implementation added: email/password registration and login, BCrypt password hashing, JWT access tokens, refresh-token issuance, refresh rotation, refresh-token reuse detection, server-side session revocation, session listing, current-device logout, specific-session revocation, and all-device logout.
- GitHub OAuth endpoint exists and is backed by a real GitHub authorization-code exchange client, but requires environment-provided GitHub OAuth client configuration.
- `users`, `user_sessions`, and `devices` schema exists in migration `V2__auth_and_session_tables.sql`.
- Security configuration is stateless, permits only health/auth bootstrap endpoints publicly, validates Bearer JWTs against an active server-side session, and returns the documented API error shape for auth failures.
- Flutter auth feature added: register/login screen, GitHub-code sign-in path, Riverpod auth controller, auth API client, and secure token storage via `flutter_secure_storage`.

Phase 2 validation completed:
- Backend tests pass on Java 21 (`.\mvnw.cmd test`): auth flow tests cover register, login, mocked GitHub OAuth profile login, refresh rotation, rotated-token reuse rejection/session revocation, session listing, and session revocation.
- Flutter checks pass (`flutter analyze`, `flutter test`, `flutter build apk --debug`).

**External configuration still required:**
- Real GitHub OAuth client ID/secret and mobile redirect setup are required before GitHub sign-in can be end-to-end verified against GitHub.

### Phase 3 — Infrastructure Domain + Ownership + Capabilities
**Status: COMPLETE.**

Verified as of 2026-07-31 (session 3):

**Complete:**
- Backend infrastructure domain added: create/list/get/delete endpoints, `SELF_HOSTED` and `MANAGED` infrastructure types, `UNKNOWN` initial health state, typed capability model, and ownership-scoped `findByIdAndUser_Id` resolution.
- Self-hosted infrastructures are created with the full MVP capability set as data; managed infrastructures are accepted as data with an empty capability set until a provider adapter is implemented.
- Database migration `V3__infrastructure_domain.sql` adds `infrastructures`, `infrastructure_capabilities`, `infrastructure_resources` schema placeholder, and `provider_credentials` schema placeholder.
- Flutter infrastructure feature added: authenticated API client/repository, Riverpod list provider, list/empty/error/skeleton states, create sheet, and navigation from the existing health screen.
- Android Gradle `ndkVersion` aligned to `27.0.12077973` after adding secure-storage-related plugins.

Phase 3 validation completed:
- Backend tests pass on Java 21 (`.\mvnw.cmd test`): infrastructure tests cover create/list/delete and cross-user access denial.
- Flutter checks pass (`flutter analyze`, `flutter test`, `flutter build apk --debug`).

### Phase 4 — Go Agent + Protocol Buffers + gRPC + Registration
**Status: COMPLETE.**

Verified as of 2026-08-01 (session 4):

**Complete:**
- Flyway applies all 4 migrations (V1–V4) before Hibernate, guaranteed by `DatabaseMigrationConfig.java` (`BeanFactoryPostProcessor` ordering).
- Hibernate schema validation (`ddl-auto=validate`) passes against the Flyway-migrated schema — root cause was `@Column(length=36)` defaulting to VARCHAR while migrations use `CHAR(36)`; fixed by adding `columnDefinition = "CHAR(36)"` to all ID `@Column` and FK `@JoinColumn` annotations across: `UserEntity`, `UserSessionEntity`, `DeviceEntity`, `InfrastructureEntity`, `AgentEntity`, `AgentRegistrationTokenEntity`.
- `AgentLifecycleService.recordHeartbeat` re-fetches the `AgentEntity` within the `@Transactional` boundary (avoids `LazyInitializationException` on the detached entity from `authenticate()`'s read-only transaction), and also sets infrastructure `HealthStatus.HEALTHY` on a valid heartbeat — ensuring reconnect after timeout restores the infrastructure to healthy state.
- Backend starts cleanly: Flyway applies 4 migrations → Hibernate validates → Tomcat on port 18080 → gRPC server on port 19092 → `Started BackendApplication` (confirmed via H2/MySQL-mode startup verification).
- `go build ./...` in `agent/` succeeds.
- `.\mvnw.cmd test` passes: 9 tests, 0 failures, 0 errors (includes all Phase 4 acceptance criterion tests).
- `.\mvnw.cmd -DskipTests package` succeeds: `backend-0.0.1-SNAPSHOT.jar` produced.

**Phase 4 acceptance criteria per PHASES.md:**
- [x] A real Go Agent binary can be built and run. (`go build ./...` passes)
- [x] Agent registers using a one-time token and establishes a persistent identity. (`registrationTokenIsSingleUseAndRevokedAgentCannotReconnect` test passes)
- [x] Heartbeats flow and are visible as Agent ONLINE in the backend. (`grpcHeartbeatKeepsAgentOnlineAndTimeoutMarksUnknown` test passes — gRPC stream verified)
- [x] Heartbeat timeout correctly drives Agent OFFLINE and Infrastructure UNKNOWN — never CRITICAL. (`markTimedOutAgentsOffline` sets OFFLINE + UNKNOWN; `CRITICAL` is never set)
- [x] Revoking an Agent permanently blocks future reconnects with the old identity. (`authenticate()` rejects REVOKED agents; revocation test passes)

**Additional tests verified:**
- Single-use registration token: second use returns `REGISTRATION_TOKEN_INVALID` (401)
- Expired registration token: returns `REGISTRATION_TOKEN_INVALID` (401)
- Heartbeat reconnect restores infrastructure to `HEALTHY` (via `recordHeartbeat` → `setHealthStatus(HEALTHY)`)

### Next Phase
Continue Phase 5 by installing the Go Agent on the StormAPI EC2 host and completing end-to-end registration, heartbeat verification, infrastructure ONLINE transition, and real Docker monitoring validation. StormAPI itself must remain completely unmodified.

### Phase 5 — Backend Deployment
**Status: COMPLETE.**

Verified as of 2026-08-02 (session 6):

**Additional completion verified:**
- Go Agent successfully cross-compiled as a standalone Linux binary and deployed independently to the StormAPI EC2 host without cloning the PocketOps repository.
- Agent and monitored application remain completely decoupled. StormAPI was left unmodified; the Agent runs alongside it as host-level software.
- PocketOps backend deployment updated to expose both HTTP (8080) and gRPC (9090) ports. Missing `9090:9090` Docker port mapping was identified as the root cause of initial gRPC connection failures.
- End-to-end registration flow verified across two independent AWS EC2 instances:
  - Flutter → Spring Boot REST
  - Spring Boot → MySQL
  - Go Agent → Spring Boot gRPC
- Agent successfully establishes a persistent gRPC connection with the backend.
- Heartbeats update the `agents` table (`ONLINE`, `last_seen_at`) and correctly transition the linked infrastructure to `HEALTHY`.
- Backend deployment verified through Elastic IP using public networking only (no localhost dependencies).

**Implementation notes discovered during validation:**
- The Agent persists its identity in `~/.pocketops-agent.json` after first registration.
- Current startup behavior always prefers the cached identity over a supplied registration token. Consequently, attempting to register the same machine to a new infrastructure reconnects the existing Agent instead of creating a new registration.
- This is considered an implementation bug/UX issue rather than an infrastructure issue and should be addressed in a future phase by giving explicit precedence to a supplied registration token (or by providing a `--force-register` / `--reset` option).
- The backend currently emits very little logging around Agent registration, gRPC lifecycle, and heartbeat processing, making integration debugging unnecessarily difficult.
- Docker Compose must publish both HTTP (8080) and gRPC (9090). Exposing only port 8080 allows registration via REST but prevents the Agent from establishing its persistent gRPC connection.
- Production deployment model is a standalone Agent binary copied to the monitored host. The monitored application's repository must never contain PocketOps source code; future installation should be performed through a one-command installer that downloads the Agent binary.

**Phase 5 acceptance criteria:**
- [x] PocketOps backend successfully deployed to AWS EC2.
- [x] MySQL and Spring Boot running in Docker.
- [x] Stable Elastic IP configured.
- [x] Go Agent installed on an independent EC2 host.
- [x] One-time registration flow verified.
- [x] Persistent Agent identity established.
- [x] End-to-end gRPC connectivity verified.
- [x] Heartbeats received and persisted.
- [x] Infrastructure transitions to `HEALTHY`.
- [x] StormAPI remains completely untouched by PocketOps.

### Next Phase
Begin Phase 6 — Host Discovery & Live Monitoring (Docker discovery, live metrics, capability reporting, resource synchronization, and UI updates driven by real Agent data).

### Phase 6 — Host Discovery & Live Monitoring
**Status: IN PROGRESS.**

Verified as of 2026-08-02 (session 7):

**Milestone complete: Backend receives and persists InfrastructureSnapshot.**
- Agent build verified after pinning `github.com/docker/go-connections` to `v0.6.0` for Docker SDK Windows compatibility.
- Backend gRPC stream now handles `infrastructure_snapshot` payloads and reconciles current resource state.
- `infrastructure_resources` rows are upserted by `(infrastructure_id, external_resource_id)` with display name, resource type, status, criticality, and `last_seen_at`.
- Existing `V3__infrastructure_domain.sql` table was reused; no new migration was needed.
- Focused gRPC test coverage verifies a snapshot persists a container resource.

Validation:
- `agent/`: `go build ./...` passes.
- `agent/`: `go test ./...` passes.
- `backend/`: `.\mvnw.cmd test` passes.

### End-to-end validation (2026-08-02)

- End-to-end verification completed against the real StormAPI EC2 host.
- Agent successfully discovered all running Docker containers using the Go Docker SDK.
- `InfrastructureSnapshot` was transmitted over gRPC and persisted into `infrastructure_resources`.
- Real StormAPI deployment produced 7 discovered Docker containers (nginx, gateway-service, execution-service, report-service, target-api, mysql, redis), confirming the discovery pipeline works against a production-like environment rather than only unit tests.

- End-to-end pipeline verified:

  Docker Engine
      ↓
  Go Agent discovery
      ↓
  InfrastructureSnapshot (gRPC)
      ↓
  Spring Boot reconciliation
      ↓
  MySQL (`infrastructure_resources`)

### Important deployment lesson

Agent source-code changes require rebuilding the Linux binary and redeploying it to the monitored host.

Workflow:

1. Build Linux binary (`GOOS=linux GOARCH=amd64 go build`)
2. Copy binary to monitored host
3. Replace existing `pocketops-agent`
4. Restart the Agent

Unlike the backend (Docker redeploy), Agent code changes are **not** reflected until the deployed binary itself is replaced.

Failure to redeploy the Agent binary can produce misleading debugging symptoms where backend changes appear correct but the monitored host is still running an older Agent implementation.

- During development, backend and Agent deployments are independent:
  - Backend changes → rebuild/redeploy Docker containers.
  - Agent changes → rebuild Linux binary, copy to monitored host, restart Agent.

### Phase 6 completion

**Status: COMPLETE.**

Verified as of 2026-08-02 (session 8):

- Persisted infrastructure resources are exposed through ownership-scoped REST at `/api/infrastructures/{id}/resources`.
- Backend resource reconciliation normalizes Docker state into the resource state model and computes infrastructure health from current resource state.
- Agent streams bounded live CPU, memory, network, and uptime samples via `ContainerMetric` gRPC messages.
- Backend handles live metric messages without writing per-sample history rows and fans them out over ownership-authorized WebSocket subscriptions.
- WebSocket subscription authorization rejects cross-user infrastructure subscriptions.
- Flutter infrastructure cards open a real service/resource detail view backed by live resource data.
- Flutter service detail view shows discovered containers, freshness, selected-resource overview, and bounded in-memory live metric samples.

Validation:
- `agent/`: `go build ./...` passes.
- `agent/`: `go test ./...` passes.
- `backend/`: `.\mvnw.cmd test` passes.
- `pocketops/`: `flutter analyze` passes.
- `pocketops/`: `flutter test` passes.
- `pocketops/`: `flutter build apk --debug` passes.

## Ambiguity / Open Questions Encountered
None blocking. Exact numeric defaults (heartbeat interval, JWT/refresh lifetimes, alert debounce/stabilization windows, reconnect backoff, metric sampling interval, log buffer size, cache TTL) are intentionally left as configuration defaults to be set during implementation (see `PHASES.md` Phase 0/1) rather than frozen architectural constants — do not treat any specific number for these as authoritative unless it is later recorded here after an explicit decision.
