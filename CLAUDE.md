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

- **Backend:** Spring Boot 4.x (Java 21), Maven via the wrapper (`mvnw`), Spring Web + Data JPA + Flyway.
- **DB:** PostgreSQL 16 (Testcontainers for integration tests).
- **Frontend:** Angular (standalone components, `@if`/`@for`), npm, Node 24 LTS.
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
- `./mvnw verify` (in `backend/`) — unit + Testcontainers integration
- `npm test` (in `frontend/`) — Vitest (Angular's built-in test runner)
- `npx playwright test` (in `e2e/`) — E2E, desktop + mobile viewports

## Conventions

- **Vertical slices only:** DB migration → service → API → Angular UI in one increment.
- **Test-first for core scenarios:** if a change touches CS-1..6 (see charter §5), add/update its E2E test.
- A feature isn't done until it meets the **Definition of Done** in charter §6 and CI is green.
- Personal data touched? Check the **GDPR** section (charter §7) before shipping.
- Incomplete work sits behind a feature flag on `main`.
- **Docs stay in sync:** when a task is done, before committing, check whether the implementation
  choices change any project doc — `charter.md`, `roadmap.md`, `README.md`, this file — and update
  them in the same commit. (E.g. a swapped tool, a new dependency file, a changed command.)
- **Branching:** one short-lived branch per task → PR → squash-merge to `main`. No long-lived
  feature branches; `main` stays releasable. The PR template carries the DoD checklist.

## Design direction

Light direction only — enough to keep the UI coherent and *not* look like a default template.
Don't spend real design effort before flows settle (that's Increment 5); do apply the tokens and
this direction consistently from Increment 1. All colours, fonts, spacing, and radii come from
`frontend/src/styles/tokens.css` — **never hardcode a hex value or a raw px font size in a component.**

**Palette** (see tokens for the values): deep **pine teal** as the brand/primary, a warm **sunrise
amber** as the single accent (used sparingly — the next upcoming run, key marks), on a cool
near-white surface. Avoid warm-cream backgrounds and terracotta accents — that combination is a
common AI-generated tell.

**Type.** Display **Bricolage Grotesque** (event names, headings), body **Hanken Grotesk** (UI, all-ages
legibility), mono **JetBrains Mono** with tabular figures for **times, dates, distances** — the mono
ties the interface to the timing/pace world. All three must load the **Latin Extended** subset so
Romanian diacritics (ă â î ș ț) render.

**Signature element.** A *course-line divider*: a thin rule with five kilometre ticks (0→5k), used
sparingly as the section rule and as the anchor on the "next run" hero. It encodes something true (a
5k course), so it's structure, not decoration — don't sprinkle it everywhere.

**Quality floor (non-negotiable, already in the DoD).** Responsive to mobile, visible keyboard focus,
`prefers-reduced-motion` respected, axe-core clean. Motion stays subtle. Accent amber needs **dark
(ink) text on it**, never white — check contrast.

**Copy.** Sentence case, active voice, name things by what the user does ("Register", "Sign in", not
"Submit"). Empty states invite action ("No upcoming runs yet — check back soon"), errors say what
happened and how to fix it. Keep it plain; the audience is runners of all ages, many non-technical.

## Azure deployment
Azure is not fully up yet, so let's make sure we respect the following:
- All config (DB URL/creds, SMTP host, mail-from) via env vars, never hardcoded.
- Flyway migrations run on startup so a fresh Azure DB self-provisions.
- No localhost-baked URLs in app code; the Angular app talks to /api (proxy in dev, same-origin in prod).