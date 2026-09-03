# Performance baseline & regression gate

Repeatable load tests for the two hot paths, with a CI gate that fails on regression
(issue #31, part of Increment 5 — Hardening).

- **Read:** `GET /api/events` — the upcoming-events list every visitor loads first.
- **Write:** `POST /api/registrations` — the core loop: persist a registration and send its
  confirmation email, both inside one transaction. The email hop is part of the real write cost,
  so it is measured with Mailpit wired exactly as in dev/CI.

The machine-readable source of truth is [`baseline.json`](./baseline.json); this file is its
human-readable companion.

## Recorded baseline

Captured 2026-09-03 with k6 v2.2.0.

| Path                       | Load          | p95     | Error rate | Throughput | p95 budget |
| -------------------------- | ------------- | ------- | ---------- | ---------- | ---------- |
| `GET /api/events`          | 25 VUs, 30s   | 32.6 ms | 0%         | ~980 req/s | 40 ms      |
| `POST /api/registrations`  | 10 VUs, 30s   | 97.3 ms | 0%         | ~120 req/s | 120 ms     |

**Environment:** local dev — Docker Desktop (Postgres 16 + Mailpit), backend via
`spring-boot:run`, JDK 21. The GitHub runner is in fact faster (read p95 ~19 ms, write ~55 ms), so
the fixed budgets clear both environments with room to spare.

## The gate

For each path, [`scripts/gate.mjs`](./scripts/gate.mjs) reruns the same load and fails when:

- **p95 > the endpoint's `p95_budget_ms`** (40 ms read, 120 ms write), or
- **error rate > `max_error_rate`** (default 0.01 → ~1%).

The budgets are fixed policy ceilings — ~20% over the recorded p95, rounded — not a moving multiple
of the last capture, so re-recording the baseline never silently loosens the gate. Both are enforced
as k6 thresholds, so k6 itself exits non-zero; `gate.mjs` re-checks them so the reason prints plainly
and one failing path never masks another. It runs in CI on every pull request and on pushes to `main`
via [`.github/workflows/perf.yml`](../.github/workflows/perf.yml), and is a **required status check**
on `main`.

## Running it locally

Needs [k6](https://k6.io/) v2.2.0 on `PATH`, plus the local stack up.

```bash
docker compose up -d                 # Postgres + Mailpit
cd backend && ./mvnw spring-boot:run  # backend on :8080

# one endpoint, ad hoc:
k6 run perf/k6/events-read.js

# the full gate against the committed baseline:
node perf/scripts/gate.mjs

# re-capture the baseline (rewrites baseline.json):
K6_VERSION=v2.2.0 PERF_ENV="local dev" node perf/scripts/capture.mjs
```

Every knob (`BASE_URL`, `VUS`, `DURATION`, `P95_BUDGET_MS`, `MAX_ERROR_RATE`) is an env var with a
default, so the scripts run the same way locally and in CI.

## Not in scope

One full-target soak toward ~5,000 concurrent users is deferred to before launch, alongside the
Azure stand-up — not this increment (see [`docs/roadmap.md`](../docs/roadmap.md)).
