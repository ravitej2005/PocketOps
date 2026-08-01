# PocketOps

Mobile-first DevOps monitoring and infrastructure-control platform.

PocketOps gives developers who run Dockerized services a single Android app to see whether their infrastructure is healthy, understand what is happening inside individual services, and take safe, bounded corrective action — without opening a laptop.

## Repository Structure

```
PocketOps/
├── pocketops/     # Flutter mobile application
├── backend/       # Spring Boot control plane (modular monolith)
├── agent/         # Go host agent (self-hosted infrastructure)
├── proto/         # Shared gRPC/Protobuf contracts
├── docs/          # Architecture and design documentation
├── docker-compose.yml
├── .env.example
└── README.md
```

## Documentation

See the `docs/` directory for the full documentation set:

- **PRD.md** — Product requirements and success criteria
- **ARCHITECTURE.md** — System architecture and component boundaries
- **DESIGN.md** — Detailed technical and UX design
- **PHASES.md** — Implementation phases and build order
- **RULES.md** — Non-negotiable engineering constraints
- **MEMORY.md** — Context restoration and implementation state

## Local Development

### Prerequisites

- Java 21 (JDK)
- Flutter SDK (^3.7.0)
- Go (latest stable)
- Docker and Docker Compose
- MySQL (via Docker Compose)

### Start Local MySQL

```bash
docker compose up -d
```

### Backend

```bash
cd backend
./mvnw clean compile
```

### Flutter

```bash
cd pocketops
flutter pub get
flutter analyze
```

### Agent

```bash
cd agent
go build ./...
```
