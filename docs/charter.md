# Project Charter — 3T Run (parkrun-style 5k events, Romania)

*A lean, RAD-style setup document. Requirements stay thin on purpose — we learn through prototype loops. The one place we add rigour is testing. This makes the project effectively **Acceptance-Test-Driven RAD**.*

> Roadmap and how it's tracked live in [`roadmap.md`](./roadmap.md).

---

## 1. Product overview

**Vision.** A parkrun-style website for weekly free 5k running events, tailored to Romania. Runners view upcoming events, register for one, and (if they hold an account) track their past and upcoming runs on a dashboard.

**Core loop (the riskiest, most valuable path).** A visitor registers for an upcoming run and receives a confirmation email. *No login required.*

**V1 scope.**

- Anonymous run registration + confirmation email (core loop).
- Account sign-up and login.
- Signed-in user dashboard: upcoming and past runs.
- Anonymous users can register for runs but are prompted to create an account to unlock the dashboard.

**V1 non-goals (explicitly out).** Results/timing upload, leaderboards, stats/graphs, multiple locations, kit closet, running tips, volunteering. All parked in the roadmap (see [`roadmap.md`](./roadmap.md)).

---

## 2. Users & primary journey

**Demographic.** Runners of all ages. **Job to be done:** register for a weekly 5k event.

**Happy path.** Visit site → (optionally) log in → pick the next event from a list of upcoming events → confirm → see confirmation (and, if signed in, land on a dashboard of their past and upcoming events). Confirmation email sent in all cases.

**Discipline note.** When giving feedback, judge each prototype against the *acceptance criteria for that increment* (§4), not against "what I'd personally like today." Ideas that arrive mid-loop go into the roadmap parking lot, not the current increment.

---

## 3. Architecture & stack decisions

| Area | Decision | Notes |
| :---- | :---- | :---- |
| Backend | Spring Boot (Java) |  |
| Frontend | Angular | Both mobile + desktop as first-class (responsive from the start). |
| DB | PostgreSQL | Runs in a container locally; Azure Database for PostgreSQL Flexible Server (Burstable / free-eligible tier) in cloud. |
| Auth | Spring Security, email + BCrypt | Swappable behind an interface; migrate to **Microsoft Entra External ID** before a real go-live if desired. **Core loop needs no auth.** |
| Email | Mailpit in dev/tests; Azure Communication Services Email (or a free-tier provider) in prod | Confirmation email is transactional → clean lawful basis. |
| Hosting | Local first → Azure | Azure App Service (backend) + Static Web Apps (Angular), or a single container app. Choose the free/low tiers; use new-account credits. Deploy to an **EU region** (North/West Europe) for GDPR. |
| CI/CD | GitHub Actions | Build, lint, all test layers, and a deploy step gated on green tests. |

### Seams to leave in **now** so V2 stays cheap

- **`Location` is a first-class entity from day one** (events belong to a location; Bucharest is just the only row). Multi-location expansion then becomes data, not schema surgery.
- **Store run participation/results in a leaderboard-friendly shape** (per-event, per-user, finish time nullable). Leaderboards and stats later read from this, no migration.
- **Keep the auth boundary abstracted** (a thin `AuthProvider` seam) so the Entra swap is local.

---

## 4. RAD increment plan (vertical slices)

Each increment is a **thin vertical slice** (DB → service → API → Angular UI), ends in a **demo checkpoint** (your RAD User-Design feedback loop), and ships only when it meets the Definition of Done (§6). Acceptance criteria are written Given/When/Then so they translate directly into tests and into instructions for your agent.

### Increment 0 — Walking skeleton *(de-risks everything)*

Repo, CI pipeline, Spring Boot + Angular "hello", Postgres via Testcontainers, one trivial end-to-end test, and a working deploy to Azure. Goal: prove the whole pipeline end-to-end before building any feature.

### Increment 1 — Upcoming events list (anonymous)

Seeded events for the Bucharest location; anonymous visitor sees a list of upcoming events.

- *Given* upcoming events exist, *when* a visitor opens the events page, *then* they see them ordered by date.

### Increment 2 — Anonymous run registration + confirmation email *(CORE LOOP)*

- *Given* an upcoming event, *when* a visitor submits a valid name + email, *then* a registration is recorded **and** a confirmation email is sent.
- *Given* an invalid/missing email, *when* they submit, *then* they see a validation error and no email is sent.

### Increment 3 — Accounts (sign-up + login)

- *Given* no account, *when* a visitor signs up with valid email + password, *then* the account is created and they are logged in.
- *Given* an existing account, *when* they log in with correct credentials, *then* they reach their dashboard.

### Increment 4 — Dashboard (upcoming + past)

- *Given* a logged-in user who registered for an upcoming event, *when* they open the dashboard, *then* it appears under "Upcoming".
- *Given* a logged-in user who attended a past event, *when* they open the dashboard, *then* it appears under "Past".
- *Given* an anonymous visitor, *when* they try to open the dashboard, *then* they are prompted to sign up / log in.

### Increment 5 — Hardening

GDPR essentials (§7), accessibility pass, performance baseline captured (§5), error/empty states.

---

## 5. Testing strategy

### Layers & recommended tooling

| Layer | Tool | Purpose |
| :---- | :---- | :---- |
| Backend unit | JUnit 5 + Mockito | Fast logic checks; no Spring context. |
| Backend integration | Spring Boot Test + **Testcontainers** (Postgres + Mailpit) | Real DB + real SMTP, no mocks for infra. |
| API | MockMvc / WebTestClient (or REST Assured) | Endpoint contracts, status codes, payloads. |
| Frontend unit/component | **Vitest** (Angular's built-in `@angular/build:unit-test`) | Fast component logic and rendering; the default runner scaffolded by Angular 21+. |
| End-to-end | **Playwright** | Cross-browser + mobile viewports; pairs well with an agent. |
| Accessibility | **axe-core** via Playwright | No critical/serious violations on new UI. |
| Performance | **k6** | Baseline + regression gate (below). |

### Core regression scenarios (the release gate)

These E2E scenarios must be **green before every release**. They are the contract that features don't deteriorate.

- **CS-1 (core loop):** anonymous registration for an event → registration recorded → confirmation email sent.
- **CS-2:** account sign-up creates an account and logs the user in.
- **CS-3:** login with correct credentials reaches the dashboard.
- **CS-4:** signed-in registration appears under "Upcoming" on the dashboard.
- **CS-5:** a past attended event appears under "Past" on the dashboard.
- **CS-6:** anonymous access to the dashboard is redirected to sign-up/login.

### Performance baseline (regression, not absolute)

You can't set a meaningful absolute threshold before measuring, so:

1. Pick representative endpoints: `GET /events` (read) and `POST /registrations` (write).
2. Run a k6 load test at a fixed, repeatable load, record **p95 latency + error rate** as the committed baseline (store the numbers in the repo).
3. **Gate:** each release re-runs the same test; fail if p95 regresses by **> 20%** vs baseline, or if error rate exceeds a small threshold (e.g. > 1%).
4. Scaling target is ~5,000 concurrent users — you don't need to load-test at full scale early; test at a repeatable fraction and watch the *trend*. Do at least one full-target soak test before any real go-live.

---

## 6. Definition of Done

A feature is shippable when:

- [ ] Vertical slice complete: DB migration → service → API → Angular UI.
- [ ] Unit tests for new logic — green.
- [ ] Integration test for new endpoint(s) against real Postgres (Testcontainers) — green.
- [ ] If a **core scenario (CS-1..6)** is affected, its E2E test is added/updated — green.
- [ ] New UI passes axe-core (no critical/serious) and works at mobile + desktop viewports.
- [ ] Personal data touched? → data minimised, consent captured where required, covered by export/erasure.
- [ ] CI green (build, lint, all test layers).
- [ ] Self-review against this feature's acceptance criteria, as the target user.
- [ ] Merged to `main`; anything incomplete sits behind a feature flag.

---

## 7. GDPR checklist (light, for V1)

- **Lawful basis:** run registration = contract/legitimate interest; any marketing email = explicit opt-in consent.
- **Data minimisation:** collect only name + email for registration.
- **Rights:** support access (export) and erasure (delete account + data) — build the seam early even if the UI is minimal.
- **Consent + cookies:** privacy policy page; cookie/consent banner only for non-essential cookies.
- **Storage:** EU region; encryption in transit (HTTPS) and at rest (managed DB default).
- **Transactional email** (confirmation) is fine without marketing consent.
