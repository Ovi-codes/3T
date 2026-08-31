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
- **Auth:** Spring Security (email + BCrypt), behind an abstracted `AuthProvider` seam. Core loop needs no auth. See [Auth](#auth) for the session model and the seam boundary.
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

## Conventions - these are critical, do not skip

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
- **Review comments:** If you see a review comment that you don't agree with, reply explaining
  why you think it's not applicable, or ask for clarification, before making changes

## Auth

Introduced in Increment 3 (accounts). Kept deliberately thin so the later Entra External ID swap
stays local (charter §3).

- **Seam:** everything above `ro.threet.run.auth.AuthProvider` deals in `AccountPrincipal`, never in
  the users table or BCrypt. The only implementation today is `LocalAuthProvider` (email + BCrypt on
  the `app_user` table). A future Entra provider is a new `AuthProvider` + a `SecurityConfig` change,
  not edits across the app. The `AuthProvider` does credential logic only — session mechanics live
  in `SessionAuthenticator` (web layer), so an OIDC provider that manages its own session drops in.
- **Session model:** **httpOnly cookie session** (Spring Security's default `SecurityContextRepository`),
  not a token in JS. The cookie is `HttpOnly; SameSite=Lax`; `Secure` is on by default and dropped
  only for local http via `SESSION_COOKIE_SECURE` (see `application.yml`). Session id is rotated on
  login (fixation defence).
- **Endpoints:** `POST /api/auth/signup` (201, logs in), `POST /api/auth/login` (200),
  `POST /api/auth/logout` (204), `GET /api/auth/me` (200 or 401). Authorisation is deny-by-default;
  the public API (events, anonymous registration, signup/login, ping, health) is enumerated in
  `SecurityConfig`.
- **Registration linkage:** `POST /api/registrations` stays anonymous, but if the caller has a
  session the registration is attributed to that account (`registration.user_id`). Anonymous → null.
- **CSRF:** Spring's CSRF token machinery is **off** for the JSON API — it's served same-origin and the
  `SameSite=Lax` session cookie blocks the cross-site form POST tokens defend against, without forcing
  a token round-trip onto the anonymous registration POST. A token-based CSRF layer is a **pre-go-live
  hardening item, tracked in the charter's Increment 5 (Hardening).**
- **Logout:** `POST /api/auth/logout` invalidates the session, clears the security context, and
  expires the cookie (204). Handled by Spring Security's logout filter (configured in `SecurityConfig`).

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