// Hot path 1 (read): GET /api/events — the upcoming-events list every visitor loads first.
//
// Run locally:            k6 run perf/k6/events-read.js
// Capture baseline:       (no P95_BUDGET_MS) — records numbers, no latency gate.
// Gate in CI:             P95_BUDGET_MS=<baseline p95 * 1.2> k6 run perf/k6/events-read.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, buildOptions, envBudget } from './lib.js';

export const options = buildOptions({
  name: 'events_read',
  vus: Number(__ENV.VUS || 25),
  duration: __ENV.DURATION || '30s',
  p95Budget: envBudget('P95_BUDGET_MS'),
});

export default function () {
  const res = http.get(`${BASE_URL}/api/events`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body is a JSON array': (r) => Array.isArray(r.json()),
  });
}
