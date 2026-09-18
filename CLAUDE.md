# 3T Run — agent context

Parkrun-style website for weekly free 5k events in Romania. Runners view upcoming events,
register for one (anonymously, with a confirmation email — this is the **core loop**), and
signed-in users track past/upcoming runs on a dashboard.

## Source of truth

- **`docs/charter.md`** — scope, architecture decisions, RAD increment plan, testing strategy, Definition of Done, GDPR. **Consult before planning or implementing any feature.**
- **`docs/roadmap.md`** — V1/V2/V3 summary. Live tracking is in GitHub Milestones + the Project board.
- Increment task lists (e.g. Increment 0) are tracked as **GitHub issues** under the relevant milestone.
- **`docs/adr/`** — architecture decision records for hard-to-reverse, non-obvious calls. E.g.
  [`0001-cancel-vs-hard-delete-events.md`](./docs/adr/0001-cancel-vs-hard-delete-events.md)
  (why deleting an event and cancelling it are different actions).

Don't duplicate facts from those docs here — link to them so nothing drifts.

## Stack

- **Backend:** Spring Boot 4.x (Java 21), Maven via the wrapper (`mvnw`), Spring Web + Data JPA + Flyway.
- **DB:** PostgreSQL 16 (Testcontainers for integration tests).
- **Frontend:** Angular (standalone components, `@if`/`@for`), npm, Node 24 LTS.
- **Auth:** Spring Security (email + BCrypt), behind an abstracted `AuthProvider` seam. Core loop needs no auth.
- **Email:** Mailpit in dev/tests; a transactional provider in prod.
- **Weather:** Open-Meteo (free, no API key, EU-hosted) behind a `WeatherProvider` seam; forecasts
  cached in-process (Caffeine).
- **CI/CD:** GitHub Actions. **Cloud:** Azure (App Service + Static Web Apps + Postgres Flexible Server), EU region.

## Repo layout

```
backend/    frontend/    e2e/    docs/    .github/workflows/
docker-compose.yml   # Postgres + Mailpit for local dev
```

## Canonical commands

- `docker compose up -d` — local Postgres + Mailpit
- `./mvnw verify` (in `backend/`) — unit + Testcontainers integration
- `npm test` (in `frontend/`) — Vitest (Angular's built-in test runner)
- `npx playwright test` (in `e2e/`) — E2E, desktop + mobile viewports

## Conventions - these are critical, do not skip

- **Vertical slices only:** DB migration → service → API → Angular UI in one increment.
- **Test-first for core scenarios:** if a change touches CS-1..6 (see charter §5), add/update its E2E test.
- A feature isn't done until it meets the **Definition of Done** in charter §6 and CI is green.
- Personal data touched? Check the **GDPR** section (charter §7) before shipping.
- Incomplete work sits behind a feature flag on `main`.
- Before starting work on a task/increment, make sure to create a github issue (if one is not provided) and add it to the board.
- **Docs stay in sync:** when a task is done, before committing, check whether the implementation
  choices change any project doc — `charter.md`, `roadmap.md`, `README.md`, this file — and update
  them in the same commit. (E.g. a swapped tool, a new dependency file, a changed command.)
- **Branching:** one short-lived branch per task → PR → squash-merge to `main`. No long-lived
  feature branches; `main` stays releasable. The PR template carries the DoD checklist.
- **Review comments:** If you see a review comment that you don't agree with, reply explaining
  why you think it's not applicable, or ask for clarification, before making changes

## Auth

See [`docs/implementation_details/auth.md`](./docs/implementation_details/auth.md) for the session
model, the `AuthProvider` seam, endpoints, registration linkage, roles (`ROLE_ADMIN`/`ROLE_USER`,
`ADMIN_EMAILS`, the `/api/admin/**` boundary), and CSRF stance.

## Weather

See [`docs/implementation_details/weather.md`](./docs/implementation_details/weather.md) for the
`WeatherProvider` seam, endpoint, and caching.

## Design direction

The **"Floodlight"** theme is the finished design language (Increment 8, #39). **Source of truth:
[`docs/design/README.md`](./docs/design/README.md)** (the handoff, per-screen) and
**[`docs/design/theme.css`](./docs/design/theme.css)** (every token + recipe). Read those before
touching UI; the notes linked below are only the invariants that must hold in the code.

See [`docs/implementation_details/design-direction.md`](./docs/implementation_details/design-direction.md)
for the "Floodlight" theme invariants — tokens, palette, type, the poster element, quality floor, and copy style.

## Azure deployment
Azure is not fully up yet, so let's make sure we respect the following:
- All config (DB URL/creds, SMTP host, mail-from, weather API URL/TTL, admin emails) via env vars,
  never hardcoded. Weather vars: `WEATHER_API_URL`, `WEATHER_FORECAST_HORIZON_DAYS`, `WEATHER_CACHE_TTL`
  (all defaulted; the defaults hit the public Open-Meteo endpoint, so prod works with none set).
  `ADMIN_EMAILS`: comma-separated accounts granted `ROLE_ADMIN` (defaulted empty — no admin unless set).
- Flyway migrations run on startup so a fresh Azure DB self-provisions.
- No localhost-baked URLs in app code; the Angular app talks to /api (proxy in dev, same-origin in prod).