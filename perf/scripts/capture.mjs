// Capture a fresh baseline: run every endpoint in perf/baseline.json against a running backend
// and rewrite the recorded numbers (p95, error rate, throughput). Run config (script, vus,
// duration), the fixed p95_budget_ms, and max_error_rate are preserved.
//
//   BASE_URL=http://localhost:8080 node perf/scripts/capture.mjs
//
// No P95_BUDGET_MS is set, so k6's latency threshold is inactive — this run only measures.
import { writeFileSync } from 'node:fs';
import { loadBaseline, runK6, baselinePath, round } from './lib.mjs';

const baseline = loadBaseline();
baseline.capture = {
  date: new Date().toISOString().slice(0, 10),
  k6_version: process.env.K6_VERSION || baseline.capture?.k6_version || 'unknown',
  environment: process.env.PERF_ENV || baseline.capture?.environment || 'unspecified',
};

for (const [key, ep] of Object.entries(baseline.endpoints)) {
  console.log(`\n=== capturing ${key}: ${ep.label} ===`);
  const { p95, errorRate, rps } = runK6(ep.script, { VUS: String(ep.vus), DURATION: ep.duration });
  ep.p95_ms = round(p95);
  ep.error_rate = round(errorRate, 4);
  ep.throughput_rps = round(rps, 1);
  console.log(`  p95=${ep.p95_ms}ms  error_rate=${ep.error_rate}  rps=${ep.throughput_rps}`);
}

writeFileSync(baselinePath, JSON.stringify(baseline, null, 2) + '\n');
console.log(`\nWrote ${baselinePath}`);
