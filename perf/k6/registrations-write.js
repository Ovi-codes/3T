// Hot path 2 (write): POST /api/registrations — the core-loop registration, which persists a
// row and sends its confirmation email inside one transaction. That email hop is part of the
// real write cost, so the baseline is captured with Mailpit wired exactly as in dev/CI.
//
// Each iteration uses a fresh email so the per-(event,email) uniqueness rule never turns a
// request into a 409 — the run measures the success path. A per-run id keeps emails unique
// across repeated runs against the same DB, too.
//
// Run locally:            k6 run perf/k6/registrations-write.js
// Gate in CI:             P95_BUDGET_MS=<baseline p95 * 1.2> k6 run perf/k6/registrations-write.js
import http from 'k6/http';
import { check, fail } from 'k6';
import { BASE_URL, buildOptions, envBudget } from './lib.js';

export const options = buildOptions({
  name: 'registrations_write',
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  p95Budget: envBudget('P95_BUDGET_MS'),
});

// Pick a real upcoming event to register against, once, before the load starts.
export function setup() {
  const res = http.get(`${BASE_URL}/api/events`);
  const events = res.json();
  if (!Array.isArray(events) || events.length === 0) {
    fail('No upcoming events returned by GET /api/events — is the seed data present?');
  }
  return { eventId: events[0].id, runId: Date.now().toString(36) };
}

export default function (data) {
  const email = `lt-${data.runId}-${__VU}-${__ITER}@loadtest.local`;
  const payload = JSON.stringify({ eventId: data.eventId, name: 'Load test runner', email });
  const res = http.post(`${BASE_URL}/api/registrations`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'status is 201': (r) => r.status === 201 });
}
