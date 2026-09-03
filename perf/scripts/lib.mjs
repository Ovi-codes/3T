// Shared helpers for the perf scripts (capture + gate). Node only, no deps, so the same code
// runs on a laptop and on the CI runner. k6 must be on PATH.
import { spawnSync } from 'node:child_process';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

export const perfDir = join(dirname(fileURLToPath(import.meta.url)), '..');
export const repoRoot = join(perfDir, '..');
export const baselinePath = join(perfDir, 'baseline.json');

export function loadBaseline() {
  return JSON.parse(readFileSync(baselinePath, 'utf8'));
}

/**
 * Run one k6 script and return its parsed end-of-test summary.
 *
 * @param {string} script  path to the k6 script, relative to the repo root.
 * @param {object} env     extra env vars for k6 (BASE_URL, P95_BUDGET_MS, VUS, ...).
 * @returns {{ p95: number, errorRate: number, rps: number, code: number }}
 *          code is k6's exit code — non-zero means a threshold failed (the gate).
 */
export function runK6(script, env = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'k6-'));
  const summaryFile = join(dir, 'summary.json');
  try {
    // Name the binary with its Windows extension so no shell is needed to resolve it (and no
    // shell-escaping deprecation warning); on Linux/macOS the bare name is on PATH.
    const k6bin = process.platform === 'win32' ? 'k6.exe' : 'k6';
    const res = spawnSync(
      k6bin,
      ['run', '--quiet', '--summary-export', summaryFile, join(repoRoot, script)],
      { stdio: ['ignore', 'inherit', 'inherit'], env: { ...process.env, ...env } },
    );
    if (res.error) throw res.error;
    const s = JSON.parse(readFileSync(summaryFile, 'utf8'));
    const d = s.metrics.http_req_duration;
    return {
      p95: d['p(95)'],
      errorRate: s.metrics.http_req_failed.value,
      rps: s.metrics.http_reqs.rate,
      code: res.status ?? 1,
    };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

export const round = (n, dp = 2) => Number(n.toFixed(dp));
