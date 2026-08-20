## What and why

<!-- One or two sentences. What changed, and what it unblocks. -->

Closes #

## How to verify

<!-- The commands you ran, and what a reviewer should see. -->

## Definition of Done

Authoritative version is [`docs/charter.md`](../docs/charter.md) §6 — if this list ever
disagrees with it, the charter wins. Mark an item `N/A —` with a reason where it does not
apply (Increment 0 tasks have no UI or personal data, for example).

- [ ] Vertical slice complete: DB migration → service → API → Angular UI
- [ ] Unit tests for new logic — green
- [ ] Integration test for new endpoint(s) against real Postgres (Testcontainers) — green
- [ ] Core scenario (CS-1..6, charter §5) affected? Its E2E test is added/updated — green
- [ ] New UI passes axe-core (no critical/serious) at mobile + desktop viewports
- [ ] Personal data touched? Minimised, consent captured where required, covered by export/erasure (charter §7)
- [ ] CI green (build, lint, all test layers)
- [ ] Self-reviewed against this increment's acceptance criteria, as the target user
- [ ] Anything incomplete sits behind a feature flag