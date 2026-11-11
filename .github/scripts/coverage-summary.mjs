// Writes a coverage table to the GitHub Actions job summary (or stdout when run locally).
//
//   node .github/scripts/coverage-summary.mjs backend  backend/target/site/jacoco/jacoco.csv
//   node .github/scripts/coverage-summary.mjs frontend frontend/coverage/threet-run/coverage-summary.json
//
// Backend input is JaCoCo's CSV report; frontend input is Vitest's (istanbul) json-summary report.
import { appendFileSync, readFileSync } from 'node:fs';

const [kind, file] = process.argv.slice(2);
if (!kind || !file) {
  console.error('usage: coverage-summary.mjs <backend|frontend> <report file>');
  process.exit(2);
}

const pct = (covered, total) => (total === 0 ? 100 : (100 * covered) / total);

function fromJacocoCsv(text) {
  const [header, ...rows] = text.trim().split(/\r?\n/);
  const cols = header.split(',');
  const sums = {};
  for (const row of rows) {
    const cells = row.split(',');
    cols.forEach((col, i) => {
      if (col.endsWith('_MISSED') || col.endsWith('_COVERED')) {
        sums[col] = (sums[col] ?? 0) + Number(cells[i]);
      }
    });
  }
  const metric = (name) => {
    const covered = sums[`${name}_COVERED`];
    const total = covered + sums[`${name}_MISSED`];
    return { covered, total };
  };
  return {
    Lines: metric('LINE'),
    Branches: metric('BRANCH'),
    Methods: metric('METHOD'),
    Instructions: metric('INSTRUCTION'),
  };
}

function fromIstanbulSummary(text) {
  const { total } = JSON.parse(text);
  const metric = (m) => ({ covered: m.covered, total: m.total });
  return {
    Lines: metric(total.lines),
    Branches: metric(total.branches),
    Functions: metric(total.functions),
    Statements: metric(total.statements),
  };
}

const text = readFileSync(file, 'utf8');
const metrics = kind === 'backend' ? fromJacocoCsv(text) : fromIstanbulSummary(text);
const title = kind === 'backend' ? 'Backend coverage (JaCoCo)' : 'Frontend coverage (Vitest)';

const lines = [
  `### ${title}`,
  '',
  '| Metric | Coverage | Covered / total |',
  '| --- | ---: | ---: |',
  ...Object.entries(metrics).map(
    ([name, { covered, total }]) =>
      `| ${name} | ${pct(covered, total).toFixed(1)}% | ${covered} / ${total} |`,
  ),
  '',
];
const markdown = lines.join('\n');

if (process.env.GITHUB_STEP_SUMMARY) {
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, markdown + '\n');
}
console.log(markdown);
