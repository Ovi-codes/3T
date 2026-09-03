// Shared config for the two hot-path load tests, so read and write stay consistent and
// the CI gate is expressed in one place.
//
// Every knob is an env var with a sensible default, so the same script runs three ways:
//   - locally with no env (quick run against http://localhost:8080),
//   - in "baseline capture" mode (no P95_BUDGET_MS set — thresholds don't gate, we just
//     record the numbers), and
//   - in "gate" mode in CI (P95_BUDGET_MS set from perf/baseline.json — a regression fails k6).
//
// Each script drives ONE endpoint under one scenario, so http_req_duration's p95 is exactly
// that endpoint's p95 — no per-tag sub-metric needed.

// Base URL of the running backend.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// The regression gate: error rate must stay under this (issue #31: ~1%).
const MAX_ERROR_RATE = __ENV.MAX_ERROR_RATE ? Number(__ENV.MAX_ERROR_RATE) : 0.01;

// Trend stats printed in the end-of-test summary and captured into results JSON.
const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];

/**
 * Build k6 options for one endpoint.
 *
 * @param {object} cfg
 * @param {string} cfg.name       scenario name (shown in the summary).
 * @param {number} cfg.vus        constant virtual users.
 * @param {string} cfg.duration   hold time, e.g. '30s'.
 * @param {number|null} cfg.p95Budget  p95 ceiling in ms, or null to skip the latency gate
 *                                      (baseline-capture runs pass null).
 */
export function buildOptions({ name, vus, duration, p95Budget }) {
  const thresholds = {
    // A non-2xx/3xx response is a failed request; the write path's 201 and the read path's
    // 200 both count as success, so any validation/duplicate/5xx error moves this rate.
    http_req_failed: [`rate<${MAX_ERROR_RATE}`],
  };
  if (p95Budget) {
    thresholds['http_req_duration'] = [`p(95)<${p95Budget}`];
  }
  return {
    scenarios: {
      [name]: { executor: 'constant-vus', vus, duration, gracefulStop: '5s' },
    },
    thresholds,
    summaryTrendStats: SUMMARY_TREND_STATS,
  };
}

// Read a numeric env var, or null when unset/blank (so buildOptions skips the gate).
export function envBudget(key) {
  return __ENV[key] ? Number(__ENV[key]) : null;
}
