---
status: accepted
---

# Cancelling an event is distinct from deleting it

Admins can remove an event two ways, and the difference is deliberate. **Delete** is a hard
removal, allowed **only when the event has no registrations** — it is the "created in error"
escape hatch. **Cancel** is a soft, terminal state for an event people have already signed up for:
the event is marked `CANCELLED`, drops off the public homepage, stays visible (badged) in
registrants' dashboards until its date passes, and every registrant is emailed. We chose this split
so that removing an event **never silently destroys personal data** — the only path that deletes
registration rows is the one where none exist.

## Considered options

- **Hard cascade-delete always** (the original Increment 10 plan): one Delete action that removes
  the event and its registration rows even when populated, behind a count warning. Rejected —
  destroying the registrations of people who signed up is both poor UX (they're never told why the
  event vanished) and a muddy GDPR story (a routine admin action becomes a bulk erasure of others'
  personal data).
- **Block delete when registrations exist, no cancel concept:** honest but leaves an admin unable
  to call off a rained-out event that people had registered for.
- **Delete (empty only) + Cancel (soft, terminal, notifies):** chosen.

## Consequences

- `event` carries a `status` (`SCHEDULED` | `CANCELLED`); the public `GET /api/events` excludes
  `CANCELLED`, dashboards include it until the date passes.
- Cancel commits first; registrant emails are best-effort (failures logged, never roll back the
  cancel), reusing the confirmation-email seam.
- Cancel is terminal — no un-cancel, and a cancelled event is read-only. Reinstatement is out of
  scope for V1.
- GDPR (charter §7): cancel preserves registrant data (they are notified); hard delete only ever
  runs when no personal data is attached, so it stays consistent with account erasure.
