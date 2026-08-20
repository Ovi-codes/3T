# 3T Run

Parkrun-style website for weekly free 5k events in Romania. Runners view upcoming events,
register for one (anonymously, with a confirmation email), and signed-in users track their
past and upcoming runs on a dashboard.

- Scope, architecture and testing strategy: [`docs/charter.md`](docs/charter.md)
- What's now / next / later: [`docs/roadmap.md`](docs/roadmap.md)
- Agent context: [`CLAUDE.md`](CLAUDE.md)

## Prerequisites

| Tool | Version |
| :--- | :--- |
| JDK | 21 (LTS) |
| Node | 24 (LTS) |
| Docker | any recent version, with Compose v2 |
| Git | any recent version |

Check with `java -version`, `node -v`, `docker compose version`.

## Repo layout

```
backend/    Spring Boot (Maven) — Web + Data JPA + Flyway
frontend/   Angular (standalone components)
e2e/        Playwright — tests the whole system
docs/       charter + roadmap
.github/workflows/   GitHub Actions CI
docker-compose.yml   Postgres + Mailpit for local dev
```

## Run it locally

### 1. Start the local infrastructure

```bash
docker compose up -d
```

This starts:

| Service | Address | Notes |
| :--- | :--- | :--- |
| Postgres 16 | `localhost:5432` | db `runro`, user `runro`, password `runro` |
| Mailpit SMTP | `localhost:1025` | where the app sends mail in dev |
| Mailpit web UI | http://localhost:8025 | read the sent mail here |

Dev credentials are deliberately trivial and local-only — never reuse them anywhere else.

Check both are healthy:

```bash
docker compose ps
```

Stop with `docker compose down`, or `docker compose down -v` to also wipe the database volume.

Mailpit is not used until Increment 2 (confirmation email). It is wired up now so the seam
exists.

### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

Runs on http://localhost:8080. Flyway applies the migrations in
`backend/src/main/resources/db/migration` at startup, so start Postgres first.

### 3. Frontend

```bash
cd frontend
npm install
npm start
```

Runs on http://localhost:4200 and calls the backend at `localhost:8080`.

## Tests

| Layer | Command | Where |
| :--- | :--- | :--- |
| Backend unit + integration | `mvn verify` | `backend/` |
| Frontend unit/component | `npm test` | `frontend/` |
| End-to-end | `npx playwright test` | `e2e/` |

Backend integration tests use Testcontainers, so Docker must be running. They start their own
Postgres — they do not use the Compose one.

E2E tests expect the backend and frontend to be running (see above).

## Contributing

Read the Definition of Done in [`docs/charter.md`](docs/charter.md) §6 before opening a PR.
Work is tracked as GitHub issues under the V1/V2/V3 milestones.
