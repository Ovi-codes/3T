# 3T Run — agent context

Parkrun-style website for weekly free 5k events in Romania. Runners view upcoming events,
register for one (anonymously, with a confirmation email — this is the **core loop**), and
signed-in users track past/upcoming runs on a dashboard.

## Source of truth

- **`docs/charter.md`** — scope, architecture decisions, RAD increment plan, testing strategy, Definition of Done, GDPR. **Consult before planning or implementing any feature.**
- **`docs/roadmap.md`** — V1/V2/V3 summary. Live tracking is in GitHub Milestones + the Project board.
- Increment task lists (e.g. Increment 0) are tracked as **GitHub issues** under the relevant milestone.

Don't duplicate facts from those docs here — link to them so nothing drifts.

## Stack

- **Backend:** Spring Boot 3.x (Java 21), Maven, Spring Web + Data JPA + Flyway.
- **DB:** PostgreSQL 16 (Testcontainers for integration tests).
- **Frontend:** Angular (standalone components, `@if`/`@for`), npm, Node 20.
- **Auth:** Spring Security (email + BCrypt), behind an abstracted `AuthProvider` seam. Core loop needs no auth.
- **Email:** Mailpit in dev/tests; a transactional provider in prod.
- **CI/CD:** GitHub Actions. **Cloud:** Azure (App Service + Static Web Apps + Postgres Flexible Server), EU region.

## Repo layout

```
backend/    frontend/    e2e/    docs/    .github/workflows/
docker-compose.yml   # Postgres + Mailpit for local dev
```

## Canonical commands

- `docker compose up -d` — local Postgres + Mailpit
- `mvn verify` (in `backend/`) — unit + Testcontainers integration
- `npm test` (in `frontend/`) — Jest
- `npx playwright test` (in `e2e/`) — E2E, desktop + mobile viewports

## Conventions

- **Vertical slices only:** DB migration → service → API → Angular UI in one increment.
- **Test-first for core scenarios:** if a change touches CS-1..6 (see charter §5), add/update its E2E test.
- A feature isn't done until it meets the **Definition of Done** in charter §6 and CI is green.
- Personal data touched? Check the **GDPR** section (charter §7) before shipping.
- Incomplete work sits behind a feature flag on `main`.
