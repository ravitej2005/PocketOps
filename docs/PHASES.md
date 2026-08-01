# PocketOps Implementation Phases

## How to Use This Document

This is the build-order roadmap: **in what order** PocketOps gets implemented. For what/why see `PRD.md`. For system structure see `ARCHITECTURE.md`. For implementation-level behavior see `DESIGN.md`. For non-negotiable constraints see `RULES.md`. For rapid AI context restoration see `MEMORY.md`.

## Governing Rules for This Document

- **Every phase must leave the application buildable.** Backend compiles, Flutter builds, existing tests pass, before moving to the next phase.
- **Do not implement Phase N+1 functionality while working on Phase N**, unless it is a tiny, unavoidable prerequisite needed to keep the architecture clean (e.g., a shared DTO both phases will need). When in doubt, don't.
- **Do not mark a phase complete in `MEMORY.md`'s implementation-status section unless the functionality genuinely exists and its exit criteria are met.**
- Phase order below is frozen; do not reorder phases without an explicit, recorded architecture decision.

---

## PHASE 0 — Documentation / Contracts / Project Foundation

**Objective**: Establish the authoritative documentation set and empty project skeleton so later phases have a stable foundation.

**Prerequisites**: None.

**Scope**:
- Create the six docs (`PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `MEMORY.md`, `PHASES.md`, `RULES.md`) — already frozen by this point.
- Create repository skeleton: `pocketops/` (Flutter), `backend/` (Spring Boot), `agent/` (Go), `proto/` (shared contracts), `docs/`.
- Initialize `docker-compose.yml`, `.env.example`, `.gitignore`.
- No business logic yet.

**Backend work**: Empty Spring Boot project scaffold with module package structure (empty packages per `ARCHITECTURE.md`'s module list) but no implemented endpoints yet.

**Flutter work**: Empty Flutter project scaffold with the feature-first directory structure, no screens implemented yet.

**Agent work**: Empty Go module initialized with the package structure from `ARCHITECTURE.md`, no logic yet.

**Database work**: None yet (Phase 1 introduces first migrations).

**Infrastructure work**: `docker-compose.yml` skeleton for local MySQL only.

**Tests**: None required yet beyond "project builds."

**Completion criteria**:
- [ ] All six docs exist and are internally consistent.
- [ ] `backend/` builds with an empty Spring Boot app.
- [ ] `pocketops/` builds with an empty Flutter app.
- [ ] `agent/` builds as an empty Go binary.
- [ ] `docker-compose.yml` brings up local MySQL.

**Explicitly NOT included yet**: Any actual feature, auth, or domain logic.

**Risks**: Low. Main risk is skeleton structure needing adjustment once real code lands — acceptable at this stage.

**Files/modules expected to change**: New repository structure only.

---

## PHASE 1 — Flutter + Spring Boot + MySQL Foundation

**Objective**: Get the three core runtimes talking to each other with a trivial health-check round trip, and establish core scaffolding (theming, networking client, error model, base module wiring).

**Prerequisites**: Phase 0 complete.

**Scope**:
- Spring Boot: base config, `common` module (shared DTOs, consistent error response structure per `DESIGN.md`), a simple `/api/health` endpoint, Spring Data JPA + MySQL connection wired.
- Flutter: `core/` networking client, theming foundation (light-first per `DESIGN.md`), routing skeleton, a screen that calls `/api/health` to prove connectivity.
- Database: first migration tooling in place (e.g., versioned migrations), no domain tables yet beyond what health-check needs (none).

**Backend work**: `common` module, base exception handling → consistent error model, health endpoint.

**Flutter work**: `core` networking/theming/routing, one connectivity-proof screen.

**Agent work**: None yet.

**Database work**: Migration tooling configured; verify connectivity from Spring Boot.

**Infrastructure work**: `docker-compose.yml` runs backend + MySQL locally.

**Tests**: Backend health-check integration test; Flutter can reach backend in local dev.

**Completion criteria**:
- [ ] Backend compiles and serves `/api/health`.
- [ ] Flutter builds and successfully calls the backend.
- [ ] MySQL migrations run cleanly from empty.
- [ ] Consistent error response shape is defined and used by at least the health endpoint's error paths.

**Explicitly NOT included yet**: Any authentication, domain model, or business logic.

**Risks**: Low — mostly tooling/config risk (CORS, local networking between Flutter and backend in dev).

**Files/modules expected to change**: `backend/common`, `backend` base config, `pocketops/core`.

---

## PHASE 2 — Authentication + GitHub OAuth + Sessions

**Objective**: Implement full authentication: email/password, GitHub OAuth, JWT access tokens, rotating refresh tokens, server-side session tracking and revocation.

**Prerequisites**: Phase 1 complete.

**Scope**: Per FR-AUTH-001 through FR-AUTH-005 in `PRD.md` and the Authentication Architecture / Authentication Flows sections of `ARCHITECTURE.md` / `DESIGN.md`.

**Backend work**: `auth`, `user`, `session`, `security` modules. Password hashing via Spring Security. GitHub OAuth integration. JWT issuance, refresh rotation with reuse detection, `user_sessions` table and revocation endpoints (single device / specific device / all devices).

**Flutter work**: `features/auth` — registration, login, GitHub OAuth flow, secure token storage (platform secure storage, never plain preferences), automatic silent refresh, logout flows.

**Agent work**: None yet.

**Database work**: Migrations for `users`, `user_sessions`, `devices` (device record created here even though FCM tokens populate later).

**Infrastructure work**: None beyond existing.

**Tests**: Password hashing correctness, JWT issuance/validation, refresh rotation + reuse detection, revocation (single/other/all devices), GitHub OAuth flow (mocked), Flutter auth screen and secure-storage tests.

**Completion criteria**:
- [ ] User can register and log in with email/password.
- [ ] User can log in with GitHub OAuth.
- [ ] Access token expires and is silently refreshed using a valid refresh token.
- [ ] A rotated-away refresh token is rejected and revokes the session.
- [ ] User can log out current device, a specific other device, or all devices.
- [ ] No credential or token is ever logged.

**Explicitly NOT included yet**: Infrastructure domain, agent, monitoring.

**Risks**: Refresh-rotation edge cases (concurrent refresh requests); GitHub OAuth redirect handling on mobile.

**Files/modules expected to change**: `backend/auth`, `backend/user`, `backend/session`, `backend/security`, `pocketops/features/auth`, `pocketops/core` (secure storage, auth interceptor).

---

## PHASE 3 — Infrastructure Domain + Ownership + Capabilities

**Objective**: Implement the core `Infrastructure` domain: creation, listing, ownership-scoped resolution, and the capability model — without yet wiring a real Agent or provider behind it.

**Prerequisites**: Phase 2 complete.

**Scope**: Per FR-INFRA-001 through FR-INFRA-005 and the Capability Model / Authorization Architecture sections of `ARCHITECTURE.md`.

**Backend work**: `infrastructure` module — CRUD, ownership-scoped repository resolution pattern established here and reused everywhere downstream, capability representation, infrastructure state model (HEALTHY/DEGRADED/CRITICAL/UNKNOWN) as data only (no real transitions yet, since no Agent/provider exists).

**Flutter work**: `features/infrastructure` — list/create/detail screens against real endpoints, using stubbed/placeholder state since no live data source exists yet. `features/home` groundwork.

**Agent work**: None yet (Phase 4).

**Database work**: Migrations for `infrastructures`, `infrastructure_resources` (schema only), `provider_credentials` (schema only, unused yet).

**Infrastructure work**: None beyond existing.

**Tests**: Ownership-scoped resolution security tests (User A cannot access User B's infrastructure) — this is the first phase where this critical regression test class begins and must remain green for every subsequent phase.

**Completion criteria**:
- [ ] User can create, list, and delete infrastructures (both SELF_HOSTED and MANAGED types accepted as data, no real backing yet).
- [ ] Every infrastructure endpoint enforces ownership; cross-user access attempts fail.
- [ ] Capability model exists as a defined, typed structure ready to be populated by real sources later.
- [ ] Flutter can create and list infrastructures end-to-end against the real backend.

**Explicitly NOT included yet**: Real Agent connectivity, real provider connectivity, real monitoring data.

**Risks**: Getting the ownership-resolution pattern right here matters — it is reused by every later module. Do not proceed to Phase 4 until this pattern is solid and tested.

**Files/modules expected to change**: `backend/infrastructure`, `pocketops/features/infrastructure`, `pocketops/features/home`.

---

## PHASE 4 — Go Agent + Protocol Buffers + gRPC + Registration

**Objective**: Build the real PocketOps Agent and the gRPC contract, and implement the full registration flow, without yet doing real Docker monitoring.

**Prerequisites**: Phase 3 complete.

**Scope**: Per the Agent Registration Architecture, gRPC Stream Lifecycle, and Agent Authentication/Trust sections of `ARCHITECTURE.md`; FR-AGENT-001 through FR-AGENT-005 in `PRD.md`.

**Backend work**: `agent` module — one-time registration credential generation (single-use, short-lived, infrastructure-specific), gRPC server endpoint, persistent Agent identity establishment, Agent state model (PENDING/ONLINE/OFFLINE/REVOKED), heartbeat timeout handling.

**Flutter work**: Self-hosted infrastructure creation UX showing the generated installer command and a waiting-for-agent state.

**Agent work**: `registration`, `connection`, `config`, `security` packages. Agent binary can be built, accepts a one-time token, registers, establishes a persistent gRPC connection, sends heartbeats. No Docker interaction yet — heartbeat and empty snapshot only.

**Database work**: Migrations for `agents` table populated for real; registration token storage (short-lived, invalidated after use).

**Infrastructure work**: `proto/pocketops_agent.proto` defined and shared by both Java and Go builds.

**Tests**: Registration token single-use enforcement, token expiry enforcement, Agent reconnect after backend restart, heartbeat timeout → Agent OFFLINE → Infrastructure UNKNOWN, revoked Agent permanently rejected on reconnect.

**Completion criteria**:
- [ ] A real Go Agent binary can be built and run.
- [ ] Agent registers using a one-time token and establishes a persistent identity.
- [ ] Heartbeats flow and are visible as Agent ONLINE in the backend.
- [ ] Heartbeat timeout correctly drives Agent OFFLINE and Infrastructure UNKNOWN — never CRITICAL.
- [ ] Revoking an Agent permanently blocks future reconnects with the old identity.

**Explicitly NOT included yet**: Actual container discovery, metrics, logs, or commands — this phase proves the channel and identity model only.

**Risks**: TLS/mutual identity verification setup complexity; this is the most security-sensitive phase so far and deserves careful review before proceeding.

**Files/modules expected to change**: `backend/agent`, `proto/`, `agent/registration`, `agent/connection`, `agent/config`, `agent/security`, `pocketops/features/infrastructure` (self-hosted onboarding UX).

---

## PHASE 5 — Install Agent Independently on StormAPI EC2 (No StormAPI Source Modifications)

**Objective**: Prove the application-agnostic Agent model end-to-end against the real demo environment, using Phase 4's registration/heartbeat capability.

**Prerequisites**: Phase 4 complete.

**Scope**: This is a validation/integration phase, not primarily a new-feature phase.

**Backend work**: None beyond bugfixes surfaced by real-world testing.

**Flutter work**: None beyond bugfixes surfaced by real-world testing.

**Agent work**: Build the installer script/command referenced in Phase 4's onboarding UX; ensure the Agent runs correctly as a host-level process on Ubuntu/EC2 (e.g., as a system service) without any StormAPI repository, container, or configuration change.

**Database work**: None.

**Infrastructure work**: Actual installation on the StormAPI EC2 host, alongside — not inside — StormAPI's Docker Compose stack.

**Tests**: Manual/integration verification that the StormAPI repository has zero diffs after Agent installation; Agent reaches ONLINE against the real EC2 host.

**Completion criteria**:
- [ ] PocketOps Agent runs on the StormAPI EC2 host as independent host software.
- [ ] `git status` in the StormAPI repository shows no changes attributable to PocketOps.
- [ ] StormAPI's own Docker Compose file is unmodified.
- [ ] Infrastructure reaches ONLINE/registered state against the real host.

**Explicitly NOT included yet**: Container discovery/metrics (Phase 6).

**Risks**: Real-world host quirks (permissions, systemd vs. other init, firewall/outbound rules for the EC2 security group).

**Files/modules expected to change**: `agent/` (installer packaging only); no application code changes to StormAPI.

---

## PHASE 6 — Container Discovery + Health + Metrics

**Objective**: Implement real Docker-backed monitoring: container discovery, health/state evaluation, and live CPU/memory/network/uptime metrics flowing all the way to Flutter.

**Prerequisites**: Phase 5 complete.

**Scope**: Per FR-MON-001 through FR-MON-003, the Resource State Model, and Real-Time Data Architecture sections.

**Backend work**: `docker` and `monitoring` modules — translating Agent-reported container data into `infrastructure_resources`, computing infrastructure-level health from resource states, `websocket` module wired for metric/state push.

**Flutter work**: `features/infrastructure` detail screen showing discovered resources and overall health; `features/service_details` overview + metrics tab with bounded live charts.

**Agent work**: `docker` package (discovery, stats via Go Docker SDK), `metrics` package (sampling/shaping), snapshot and streaming messages populated for real.

**Database work**: `infrastructure_resources` populated and kept in sync (bounded — no per-sample history rows).

**Infrastructure work**: None beyond existing.

**Tests**: Snapshot-then-reconcile-on-reconnect behavior; resource state transitions match the state machine in `ARCHITECTURE.md`; WebSocket subscription authorization (no cross-user leakage) — extends the Phase 3 ownership test class to real-time channels.

**Completion criteria**:
- [ ] Connecting the StormAPI EC2 infrastructure surfaces all seven real containers.
- [ ] Overall infrastructure health reflects real resource states.
- [ ] Live CPU/memory/network/uptime metrics stream to Flutter and update in near-real-time.
- [ ] Reconnection after a dropped Agent connection always fetches a fresh snapshot before resuming live updates.

**Explicitly NOT included yet**: Start/stop/restart (Phase 7), logs (Phase 8), alerts (Phase 9).

**Risks**: Metric volume/backpressure; ensure bounded buffers per `RULES.md` performance principles.

**Files/modules expected to change**: `backend/docker`, `backend/monitoring`, `backend/websocket`, `agent/docker`, `agent/metrics`, `pocketops/features/infrastructure`, `pocketops/features/service_details`.

---

## PHASE 7 — Safe Start / Stop / Restart

**Objective**: Implement the bounded, allow-listed destructive operation flow end-to-end, including offline rejection and biometric/confirmation UX.

**Prerequisites**: Phase 6 complete.

**Scope**: Per FR-OPS-001 through FR-OPS-004 and the End-to-End Control Flow in `ARCHITECTURE.md`/`DESIGN.md`.

**Backend work**: Command dispatch in `agent` module (allow-listed: START_CONTAINER/STOP_CONTAINER/RESTART_CONTAINER only), `AGENT_OFFLINE` and `CAPABILITY_UNSUPPORTED` error responses, ownership + capability + agent-availability checks before dispatch.

**Flutter work**: Confirmation UX, device biometric gate, critical-resource stronger-warning UX, immediate failure UX when the Agent is offline (no queued/pending state shown).

**Agent work**: `commands` package — receive, validate against allow-list, execute via Docker SDK, return result.

**Database work**: None beyond existing (no persistent command history required for MVP unless trivially useful for correlation).

**Infrastructure work**: None.

**Tests**: Command succeeds when Agent online; command is immediately rejected (not queued) when Agent offline; arbitrary/non-allow-listed command cannot be submitted at any layer; critical resource requires the stronger confirmation path.

**Completion criteria**:
- [ ] User can start/stop/restart a real StormAPI container from the app.
- [ ] Attempting this while the Agent is offline fails immediately with a clear message — never queued for later execution.
- [ ] Biometric gate is required before submission.
- [ ] Critical resources show stronger warning UX than normal resources.

**Explicitly NOT included yet**: Logs (Phase 8), alerts (Phase 9).

**Risks**: Ensuring the "no offline queue" rule is truly enforced (easy to accidentally implement optimistic queuing) — treat this as a hard regression test, not just a manual check.

**Files/modules expected to change**: `backend/agent`, `agent/commands`, `pocketops/features/service_details`.

---

## PHASE 8 — Live Logs

**Objective**: Implement the live log streaming pipeline and viewer.

**Prerequisites**: Phase 7 complete.

**Scope**: Per FR-LOG-001/FR-LOG-002 and Logs Architecture.

**Backend work**: `logs` module — fan-out of Agent-streamed log lines to authorized WebSocket subscribers, no durable storage.

**Flutter work**: Log viewer: live stream, pause, resume, auto-scroll, search, copy, connection-state indicator.

**Agent work**: `logs` package — stream container logs upstream via gRPC.

**Database work**: None (explicitly no persistent log table).

**Infrastructure work**: None.

**Tests**: Log stream authorization (no cross-user leakage), pause/resume correctness, stream cleanup on screen dispose (no leaked subscriptions).

**Completion criteria**:
- [ ] User can view a live log tail for a real StormAPI service.
- [ ] Pause/resume/search/copy all work correctly.
- [ ] No logs are durably archived beyond what's needed for the live/recent view.

**Explicitly NOT included yet**: Alerts (Phase 9).

**Risks**: WebSocket/stream lifecycle bugs causing memory growth if not disposed correctly — watch this closely given Flutter's stream-disposal rule.

**Files/modules expected to change**: `backend/logs`, `agent/logs`, `pocketops/features/service_details`.

---

## PHASE 9 — Failure Detection + Alert Lifecycle + Flapping Protection + Stale-State Handling

**Objective**: Implement the alert system end-to-end, including debounce/flapping protection and auto-resolution, plus finalize stale/UNKNOWN UX across the app.

**Prerequisites**: Phase 8 complete.

**Scope**: Per FR-ALERT-001 through FR-ALERT-004, the Alert State Model, and Offline Requirements in `PRD.md`.

**Backend work**: `alert` module — OPEN/ACKNOWLEDGED/RESOLVED lifecycle, debounce/stability-window logic, occurrence-count aggregation for flapping resources, acknowledgement endpoint.

**Flutter work**: Alerts list/detail UX, acknowledge action, stale-state UI treatment finalized across home/infrastructure/service screens (last-known state + "last checked" timestamp + manual refresh with its own success/failure UX).

**Agent work**: None beyond what Phase 6's event reporting already provides.

**Database work**: `alerts` table populated for real, including `occurrence_count`.

**Infrastructure work**: None.

**Tests**: Simulated flapping does not produce a notification storm (single incident, incrementing occurrence count); alert auto-resolves after the stability window; stale state is never shown as live under any simulated disconnect.

**Completion criteria**:
- [ ] A simulated failure produces exactly one alert, not a flood.
- [ ] The alert auto-resolves once the resource is stable for the configured window.
- [ ] Acknowledging an alert works and is reflected in the UI.
- [ ] Every screen correctly shows stale/UNKNOWN state during a live connectivity interruption, with a working manual refresh.

**Explicitly NOT included yet**: Push notifications (Phase 10).

**Risks**: Getting debounce/stability windows right requires careful testing against real flapping scenarios, not just single clean failures.

**Files/modules expected to change**: `backend/alert`, `pocketops/features/alerts`, stale-state UI across `pocketops/features/home`, `infrastructure`, `service_details`.

---

## PHASE 10 — FCM + Deep Links

**Objective**: Wire real push notifications and deep-linking on top of the now-complete alert system.

**Prerequisites**: Phase 9 complete.

**Scope**: Per FR-NOTIF-001/FR-NOTIF-002.

**Backend work**: `notification` module — FCM integration, payload construction with deep-link data, triggered by alert open/resolve events (respecting flapping protection already in place).

**Flutter work**: FCM token registration on login/device, notification tap handling → deep link into the correct infrastructure/service screen.

**Agent work**: None.

**Database work**: `devices.fcm_token` populated for real.

**Infrastructure work**: Firebase project configuration.

**Tests**: Notification fires on real alert open (not on flapping), deep link opens the correct screen, no secret data is present in notification payloads.

**Completion criteria**:
- [ ] A real device receives a push notification when a StormAPI service fails.
- [ ] Tapping it opens the correct service screen directly.
- [ ] Flapping still does not produce a notification storm.

**Explicitly NOT included yet**: Widget (Phase 11).

**Risks**: Android notification permission handling, FCM token refresh edge cases.

**Files/modules expected to change**: `backend/notification`, `pocketops/features/settings` (device/notification registration), app-level deep-link routing.

---

## PHASE 11 — Android Home-Screen Widget

**Objective**: Implement the medium-sized Android home-screen widget.

**Prerequisites**: Phase 10 complete.

**Scope**: Per FR-WIDGET-001 through FR-WIDGET-003 and the Widget Design section of `DESIGN.md`.

**Backend work**: None beyond ensuring existing endpoints/state are sufficient for widget refresh needs.

**Flutter work**: Platform integration for the Android widget, manual previous/next infrastructure navigation (per real Android widget constraints — no fake automatic carousel), stale-state and refresh-failure treatment matching the main app's philosophy.

**Agent work**: None.

**Database work**: None.

**Infrastructure work**: Android widget provider configuration.

**Tests**: Widget never shows a destructive control; widget correctly shows staleness during a live disconnect; widget navigation works across multiple infrastructures.

**Completion criteria**:
- [ ] Widget displays name, overall status, service summary, compact metrics, and freshness for a real infrastructure.
- [ ] Manual previous/next works across multiple infrastructures.
- [ ] No destructive action is reachable from the widget.
- [ ] Stale data is clearly marked, consistent with in-app behavior.

**Explicitly NOT included yet**: Managed provider integration (Phase 12).

**Risks**: Real Android widget platform limitations (background refresh constraints, battery considerations) — do not over-promise animated/automatic behavior; see `DESIGN.md`.

**Files/modules expected to change**: `pocketops/` Android platform channel/widget code.

---

## PHASE 12 — ONE Managed Provider Integration

**Objective**: Prove the provider abstraction with exactly one real managed provider, without touching the Agent-based self-hosted path.

**Prerequisites**: Phase 11 complete.

**Scope**: Per the Provider Abstraction and Capability Model sections; MANAGED infrastructure type from `PRD.md`.

**Backend work**: `provider` module — one real provider adapter implementing `InfrastructureProvider` against a real provider API, encrypted credential storage, capability discovery reporting only what that provider actually supports.

**Flutter work**: Managed infrastructure connect flow (credential/connect UX), capability-driven UI rendering verified against a provider with a genuinely different capability set than self-hosted.

**Agent work**: None (managed infrastructure never uses the Agent).

**Database work**: `provider_credentials` populated for real, encrypted at rest, never returned to Flutter after creation.

**Infrastructure work**: Provider API account/credentials for development and demo.

**Tests**: Provider credential never appears in any response after creation or in logs; unsupported operations return `CAPABILITY_UNSUPPORTED` cleanly rather than failing unpredictably; UI never offers a control the provider doesn't support.

**Completion criteria**:
- [ ] A real managed infrastructure can be connected and shows truthful, capability-bounded status.
- [ ] Unsupported operations are never offered in the UI and are rejected cleanly if attempted server-side.
- [ ] Provider credentials are encrypted at rest and never leak.

**Explicitly NOT included yet**: Additional providers (out of MVP scope entirely, not just deferred).

**Risks**: Real third-party API rate limits/quirks during development.

**Files/modules expected to change**: `backend/provider`, `pocketops/features/infrastructure` (managed onboarding).

---

## PHASE 13 — Onboarding + Coach Marks + Final UX

**Objective**: Implement first-launch onboarding, coach marks, and close remaining UX gaps (empty states, loading states, accessibility) across the whole app.

**Prerequisites**: Phase 12 complete.

**Scope**: Per the UX Requirements in `PRD.md` and the design-system sections of `DESIGN.md`.

**Backend work**: None beyond minor support endpoints if needed (e.g., "has completed onboarding" flag).

**Flutter work**: Onboarding screens (Monitor/Understand/Act/Stay informed), one-time skippable/replayable coach marks, first-infrastructure empty state, consistent skeleton loading states, accessibility pass (touch targets, semantic labels, contrast, no color-only health indicators).

**Agent work**: None.

**Database work**: Minor — onboarding-completion flag if implemented server-side (or local-only, as appropriate).

**Infrastructure work**: None.

**Tests**: Coach marks are skippable and replayable; accessibility checks (contrast, touch target size, semantic labels present).

**Completion criteria**:
- [ ] First-launch onboarding and coach marks work correctly and are skippable/replayable.
- [ ] Empty, loading, and error states are consistent and polished across all major screens.
- [ ] Accessibility pass complete per `DESIGN.md`'s Accessibility section.

**Explicitly NOT included yet**: Production deployment (Phase 14).

**Risks**: Low technical risk; mostly design/polish iteration.

**Files/modules expected to change**: `pocketops/features/onboarding`, cross-cutting UX polish across other features.

---

## PHASE 14 — Managed Backend/Database Deployment

**Objective**: Deploy the PocketOps backend and MySQL to managed hosting for real, replacing local dev infrastructure as the primary environment.

**Prerequisites**: Phase 13 complete.

**Scope**: Per the Deployment Architecture section.

**Backend work**: Production configuration (secrets from managed hosting environment, not Git), production migration run.

**Flutter work**: Point production builds at the deployed backend URL.

**Agent work**: Point the StormAPI EC2 Agent at the deployed backend.

**Database work**: Managed MySQL provisioned; migrations applied.

**Infrastructure work**: Managed hosting account/environment setup, TLS certificates for the backend's public endpoint, environment/secret configuration.

**Tests**: Smoke test of the full demo scenario against the deployed environment.

**Completion criteria**:
- [ ] Backend and MySQL run on managed hosting.
- [ ] Flutter app and the StormAPI Agent both operate correctly against the deployed backend.
- [ ] No secret exists in Git; all secrets come from managed hosting configuration.

**Explicitly NOT included yet**: Formal security/performance testing (Phase 15).

**Risks**: Environment-specific configuration drift versus local dev; TLS/cert setup issues.

**Files/modules expected to change**: Deployment configuration only; no application logic changes expected.

---

## PHASE 15 — Security + Reliability + Performance Testing

**Objective**: Systematic verification pass against the security threat model, failure matrix, and performance principles defined in `ARCHITECTURE.md`/`DESIGN.md`/`RULES.md`.

**Prerequisites**: Phase 14 complete.

**Scope**: Execute the full testing requirements list from `DESIGN.md`, including every security regression test explicitly enumerated there (cross-user access, cross-user WebSocket subscription, revoked token/Agent rejection, registration-token reuse rejection, arbitrary command rejection, offline command rejection).

**Backend work**: Fix any issues found; add missing tests for any gap discovered.

**Flutter work**: Fix any issues found; verify stream disposal and no memory growth under sustained live-metric/log usage.

**Agent work**: Fix any issues found; verify graceful shutdown and reconnect backoff behavior under real network interruption.

**Database work**: Verify indexes exist for ownership/resource lookup patterns per `RULES.md`.

**Infrastructure work**: Rate limiting on sensitive endpoints if not already present.

**Tests**: All items in the Testing Strategy/Testing Requirements sections of `DESIGN.md` must pass.

**Completion criteria**:
- [ ] All security regression tests pass.
- [ ] Full failure matrix scenarios manually verified against the deployed environment.
- [ ] No performance regression under sustained live metrics/logs for the demo duration.

**Explicitly NOT included yet**: N/A — this phase's job is verification, not new features.

**Risks**: This phase may surface real bugs from earlier phases; treat findings as bugs to fix, not scope for redesign.

**Files/modules expected to change**: Targeted fixes across any module, as needed by findings.

---

## PHASE 16 — Documentation + Demo + Final Release

**Objective**: Finalize project documentation, prepare the demo scenario for presentation, and cut the MVP release.

**Prerequisites**: Phase 15 complete.

**Scope**: Update `MEMORY.md`'s implementation-status section to reflect true completion state. Prepare README and any resume/demo-facing material. Rehearse the full demo scenario from `PRD.md`.

**Backend work**: Final cleanup only.

**Flutter work**: Final cleanup only.

**Agent work**: Final cleanup only.

**Database work**: None.

**Infrastructure work**: Final production sanity check.

**Tests**: Full demo scenario walkthrough end-to-end, at least twice, including a deliberate connectivity interruption to prove stale-state handling live.

**Completion criteria**:
- [ ] `MEMORY.md` accurately reflects final implementation state.
- [ ] Demo scenario runs cleanly end-to-end, including the offline/stale segment.
- [ ] README and any public-facing project material are accurate and consistent with the six docs.

**Explicitly NOT included yet**: Any post-MVP feature — those require a new, explicitly approved scope decision outside this document.

**Risks**: Low — this is a wrap-up phase.

**Files/modules expected to change**: `docs/MEMORY.md` (status only, not architecture), `README.md`.
