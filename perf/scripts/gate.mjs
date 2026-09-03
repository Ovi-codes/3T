// The regression gate (issue #31). For each endpoint, run the same load and fail if:
//   - p95 exceeds the endpoint's fixed p95_budget_ms (40ms read / 120ms write — headroom over
//     both laptop and CI-runner baselines), or
//   - the error rate exceeds max_error_rate (default 0.01 → ~1%).
//
// Both checks are enforced by k6 thresholds (so k6 itself exits non-zero), and re-checked here
// so the reason is printed plainly and one bad endpoint doesn't hide another.
//
//   BASE_URL=http://localhost:8080 node perf/scripts/gate.mjs
import { loadBaseline, runK6, round } from './lib.mjs';

const baseline = loadBaseline();
const maxErr = baseline.max_error_rate;

let failed = false;
for (const [key, ep] of Object.entries(baseline.endpoints)) {
  const budget = ep.p95_budget_ms;
  console.log(`\n=== gating ${key}: ${ep.label} ===`);
  console.log(`  budget ${budget}ms p95 (last recorded ${ep.p95_ms}ms); max error rate ${maxErr}`);
  const { p95, errorRate, rps, code } = runK6(ep.script, {
    VUS: String(ep.vus),
    DURATION: ep.duration,
    P95_BUDGET_MS: String(budget),
    MAX_ERROR_RATE: String(maxErr),
  });

  const p95Bad = p95 > budget;
  const errBad = errorRate > maxErr;
  console.log(`  measured p95=${round(p95)}ms  error_rate=${round(errorRate, 4)}  rps=${round(rps, 1)}`);
  if (p95Bad) console.log(`  ✗ p95 ${round(p95)}ms over budget ${budget}ms`);
  if (errBad) console.log(`  ✗ error rate ${round(errorRate, 4)} over ${maxErr}`);
  if (!p95Bad && !errBad) console.log('  ✓ within budget');
  // k6's own exit code is the authority; our comparison is a backstop.
  if (p95Bad || errBad || code !== 0) failed = true;
}

if (failed) {
  console.error('\nPerformance gate FAILED — see the endpoints marked ✗ above.');
  process.exit(1);
}
console.log('\nPerformance gate passed.');
