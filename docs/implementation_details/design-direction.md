# Design direction

The **"Floodlight"** theme is the finished V1 design language (Increment 8, #39). **Source of truth:
[`docs/design/README.md`](../design/README.md)** (the handoff, per-screen) and
**[`docs/design/theme.css`](../design/theme.css)** (every token + recipe). Read those before
touching UI; the notes below are only the invariants that must hold in the code.

- **Tokens are law.** All colours, fonts, spacing, and radii come from `frontend/src/styles/tokens.css`
  — **never hardcode a hex value or a raw px font size in a component.**
- **Palette:** bright **orange `#ff6d00`** on a cool near-white ground, with dark **ink `#1a1918`**
  poster/hero fields. Orange is for *fills* (with dark ink text) and accents on the dark poster; for
  orange *text on the light ground* use `--color-primary-ink` (the AA-passing deeper orange). Flat,
  **zero corner radius**, structure from strong 2px rules — no shadows. (Replaces the earlier
  pine-teal / sunrise-amber placeholder.)
- **Type:** **Archivo** throughout (400 body, 600 strong, 800 headings/labels), loading the **Latin +
  Latin-Extended** subsets so Romanian diacritics (ă â î ș ț) render. Times, dates and distances use
  tabular figures (`font-variant-numeric: tabular-nums`).
- **Signature element:** the dark **poster / hero field** — reusable `<app-poster>` (diagonal weave +
  top-left orange ember glow + wavy fade to ink), used by the events hero, the confirmation banner,
  and the auth side panels. Don't sprinkle it everywhere; it anchors statement moments.
- **Quality floor (non-negotiable, already in the DoD).** Responsive to mobile, visible keyboard focus,
  `prefers-reduced-motion` respected, axe-core clean. Motion stays subtle. A solid orange fill needs
  **dark (ink) text on it**, never white — check contrast.
- **Copy.** Sentence case, active voice, name things by what the user does ("Register", "Sign in", not
  "Submit"). Empty states invite action ("No upcoming runs yet — check back soon"), errors say what
  happened and how to fix it. Keep it plain; the audience is runners of all ages, many non-technical.
