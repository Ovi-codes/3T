# Roadmap — 3T Run

*Skimmable summary of what's now, next, and later. Read this before each planning loop.*
*Full context in [`charter.md`](./charter.md).*

## How it's tracked

- **GitHub Milestones** `V1`, `V2`, `V3`. Every idea → an issue assigned to its milestone *the moment it occurs*, then out of sight until pulled in.
- **[GitHub Project board](https://github.com/users/Ovi-codes/projects/1)** with **Now / Next / Later** columns for the visual roadmap.
- **This file** is the human-readable summary that mirrors intent; **GitHub tracks the live state.**

---

## Now — V1

Increments 0–5 (see [`charter.md`](./charter.md) §4).

## Next — V2

- User access management - admins should be able to CRUD events
- Create a design theme and logo
- Multi-location expansion *(cheap if `Location` is first-class from day one — see charter §3)*.
- Post-event time upload via Strava / Garmin *(needs the leaderboard-friendly results shape — charter §3)*.
- Leaderboards per run.
- Personal stats: placement, time trend, graphs.
- Kit dashboard / shoe closet + reviews & recommendations (data source e.g. runrepeat.com).

## Later — V3
- Implement JWT auth
- Azure full deployment
- Running tips + Bucharest running-community/club info (couch-to-5k build-up).
- Volunteering interest registration.
- Medical waiver users must accept before registering for a run.
- Integrate a free weather API that shows prediction for the next session.

---

**Architectural pre-work already scheduled into V1** so V2 is additive, not disruptive: `Location` entity, leaderboard-shaped results table, abstracted auth boundary.
