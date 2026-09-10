# Handoff: 3T Run — "Floodlight" UI theme

## Overview

This package restyles the **3T Run** app (parkrun-style free weekly 5k events in Bucharest — "Time Trial Tuesdays") with a finished visual theme. It replaces the current placeholder styling across the whole V1 surface: the anonymous **upcoming-runs** list, the **register → confirmation** core loop, **sign up / sign in**, and the signed-in **dashboard**, plus loading / empty / error states.

The theme is **orange (#ff6d00) on ink (#1a1918)**, set in **Archivo**, flat, zero corner radius, strong 2px rules — derived from the Modernist design system, recoloured from its original red to orange. Its signature element is a dark **poster/hero field** with a diagonal weave, an orange corner glow, and a wavy fade to black.

## About the design files

The files in `references/` are **design references authored in HTML** (they open in a browser as static prototypes). They are **not production code to lift wholesale**. The 3T app is an **Angular** project (standalone components, `frontend/src/app/…`) — the task is to **recreate this theme inside that existing Angular codebase**, using its own components, templates and stylesheet, following the mapping in [**Files → where each screen lives**](#files--where-each-screen-lives) below. The screens, routes, forms and API calls already exist and work; this is a **re-skin**, not a rebuild — keep all existing logic, `data-testid`s, `routerLink`s, validators and HTTP calls intact.

`references/\*.dc.html` use inline styles + utility classes for prototyping speed. Translate those into the app's real stylesheet (`frontend/src/styles.css` and the per-component `\*.css`) and Angular templates. `theme.css` is a portable, framework-agnostic distillation of every token and recipe — start there.

## Fidelity

**High-fidelity.** Colors, type, spacing, states and copy are final and exact. Recreate pixel-for-pixel with the codebase's patterns. Where a value isn't stated, take it from `theme.css` or the reference HTML.

## Design tokens

Full set (with the exact CSS `:root` block) lives in **`theme.css`**. Summary:

|Role|Value|
|-|-|
|Ground (page bg)|`#f3f2f2`|
|Surface (cards / inputs)|`#eae9e9`|
|Ink (poster/hero fields)|`#1a1918`|
|Text|`#201e1d`|
|Accent (primary/orange)|`#ff6d00`|
|Accent hover / active|`#e86400` / `#c25200`|
|Accent tint fill / text-on-tint|`#fff1e6` / `#7a3600`|
|Text ON solid orange|`#201e1d` (dark — **not** white)|
|Divider (2px rules)|`rgba(32,30,29,.40)`|
|Radius|`0` everywhere|
|Font|Archivo — 400 body, 600 strong, **800 headings/labels**|
|Spacing|4 · 8 · 12 · 16 · 24 · 32 px|

Headings: `letter-spacing:-.015em`, `line-height:1.12`. Display poster type (`.htitle`): `line-height:.9`, `letter-spacing:-.03em`.

## The signature element — the poster / hero field

A dark ink block that carries display type. **Three stacked layers** (see `theme.css` for exact CSS + the SVG):

1. **Weave** — `repeating-linear-gradient(135deg, #252220 0 14px, #161514 14px 28px)` (13/26px on mobile). Subtle dark-on-dark diagonal.
2. **Ember** — `radial-gradient(80% 80% at -6% -10%, rgba(255,109,0,.36), transparent 56%)` — an orange glow from the **top-left** corner (`.38` / `-8% -8%` on mobile).
3. **Wave fade** — an inline `<svg>` (absolutely positioned, `preserveAspectRatio="none"`) drawing a wavy black shape with a vertical gradient: transparent at the crest (\~⅓ down, "under the wordmark") to solid `#1a1918` by mid-height (behind the CTA). Hero text is wrapped in `position:relative;z-index:1` so it sits above.

In Angular, factor this into a reusable **`<app-poster>`** component (renders the three layers + the `<svg>` + `<ng-content>`); the events hero, the confirmation banner, and the sign-up / sign-in side panels all use it.

## The brand mark

`3T RUN` — **no box**. `3T` in Archivo 800 **orange**, `RUN` in Archivo 800 **ink**, `gap:4px`, tucked together. Markup + sizes in `theme.css` (`.mk`). Used in the header on every page.

## Display-type detail — coloured T's

On the poster wordmark **TIME / TRIAL / TUESDAYS** (stacked, one word per line), the **leading T of each word is orange** (`.htitle .t`), the rest ground-white. This "three T's" echoes the 3T brand. On the confirmation poster the leading letter and the closing "!" carry the same emphasis. (In the mocks a couple of these emphasis letters were hand-set to ground-white instead of orange — treat exact per-letter colour as art-direction to match to the reference, not a rule.)

## Screens / views

All shown at **desktop 1180** and **mobile 390** in `references/3T Run — Build (2c).dc.html` (scroll: block 00 pattern study, then 01–07). `Directions.dc.html` holds the earlier exploration (three layout directions + four orange palettes) for context only.

### 01 · Upcoming runs — home  → `features/events/`

* **Purpose:** anonymous visitor sees upcoming runs, ordered by date; registers.
* **Layout:** header → **poster hero** (2-col on desktop: display type left, grayscale photo right; type-only on mobile) → "Upcoming runs" section with a **4-up card grid** (2px gaps on a divider background) on desktop; a stacked list on mobile.
* **Hero copy:** eyebrow "Free · Weekly · Timed 5K"; title **TIME / TRIAL / TUESDAYS**; sub "Bucharest · every Tuesday, 18:30 · est. 2024 · 214 editions run"; primary **Register for Tuesday →**, secondary text link **How it works**.
* **Card:** kicker (orange) "Tue 8 Sep · 18:30"; title e.g. "Herăstrău Park 5K"; body "Parcul Regele Mihai I · flat lakeside loop"; the soonest card gets a `.tag-accent` **This Tuesday** + a **primary** Register button, the rest a **secondary** Register. Keep the existing `\[routerLink]="\['/register', event.id]"` and `data-testid="event-item"`.

### 02 · Register (core loop)  → `features/register/`

* Page header (eyebrow "Register", `h1` = event name, sub = date · time · location) + a two-field form (**Name**, **Email**) → **primary Register →**. Fine print: "We'll email a confirmation. Registering shares only your name and email." Desktop pairs the form with a grayscale photo half.
* **Field-error state** (shown on mobile): invalid input gets `aria-invalid="true"` (orange border) + `.field-error` message ("Enter a valid email address."). Keep the existing client validators and server-error surfacing.

### 03 · Confirmation ("You're in!")  → `features/register/` (confirmation branch)

* A **poster hero** success moment: eyebrow "See you at the start line", giant `.htitle` **YOU'RE IN!**. Below: "You're registered for **{event}** — {date}. We've sent a confirmation to **{email}**." + **Back to upcoming runs** (primary) and **Add to calendar** (secondary), then a nudge to create an account.

### 04 · Sign up  → `features/auth/` (`signup.\*`, `auth.css`)

* Desktop **split**: ink **poster side panel** (eyebrow "Accounts", `.htitle` "Keep every **run.**") beside the form (Email, Password → **Create account** block button; "Already have an account? Sign in"). Mobile: form only.

### 05 · Sign in  → `features/auth/` (`login.\*`, `auth.css`)

* Same split; panel says "Pick up where you **left off.**" Shown in the **wrong-credentials error state**: a `.form-error` banner "Email or password is incorrect." above the fields. Keep the existing single generic credentials error.

### 06 · Dashboard  → `features/dashboard/`

* Header (eyebrow "Your dashboard", `h1` "Hi, {name}", sub "{email} · member since … · N runs logged").
* **Upcoming** section: a registered-run row with a `.tag-accent` **Registered**, **Add to calendar** (secondary) + **Cancel** (ghost). *(Add-to-calendar / cancel are new niceties — implement only if in scope; otherwise drop.)*
* **Past runs** section: a **table** (`Date · Run · Edition · Result`) with a `.tag-neutral` **Attended** per row. **Finish times are intentionally omitted — they're a V2 feature** (the charter keeps the results shape but no times in V1). Mobile collapses the table into rows.

### 07 · States  → wherever a list/lookup renders

* **Loading** "Loading upcoming runs…" (orange), **Empty** boxed "No upcoming runs yet / Check back soon", **Error** `.form-error` "We couldn't load upcoming runs. Please try again shortly." + Try again, **Run not found** "That run isn't open for registration." + See upcoming runs. These map to the existing `@if` load/empty/error branches in `events.html` and `register.html`.

### Header / nav  → `app.html`

* The **3T RUN** mark (links to `/`), primary nav, and the single auth action (**Log in** when signed out, **My dashboard** + **Sign out** when signed in). Nav bar: `border-bottom: 2px solid var(--divider)`. Keep the existing `@if (user())` logic and `data-testid`s.

## Interactions \& behavior

* **Buttons:** primary = solid orange, **dark** label, hover `#e86400`, active `#c25200`. Secondary = 1px divider border, hover ink-7% tint. Ghost = orange text. Block buttons are **flush-left** (label starts at the left padding edge, trailing arrow and all). Radius 0.
* **Focus:** `:focus-visible { outline: 2px solid #ff6d00; outline-offset: 2px }` — never the browser default.
* **Links:** default `#c25200` (deep orange for contrast at body size), hover `#ff6d00`.
* **Arrow icon** on CTAs is Lucide `arrow-right` (square linecaps). Use Lucide throughout.
* No animations are required; keep transitions subtle if the codebase already has them.
* **Responsive:** two-column hero / split panels / multi-col card grid collapse to single column at mobile; nav trims to mark + one action.

## State management

No new state. All state (events signal, register form + confirmation, auth user signal, route guard) already exists in the Angular components — this is styling only.

## Assets

* **Font:** Archivo (Google Fonts) — 400/600/800. Already the design's only family.
* **Icons:** Lucide (https://lucide.dev), inline SVG on `currentColor`.
* **Photography:** grayscale only. The mocks use striped placeholders labelled with intended content (start line, lake path). Supply real black-and-white photos: `filter: grayscale(1) contrast(1.08)`.
* No raster brand assets — the mark is pure type.

## Files

### In this bundle

* `theme.css` — portable tokens + every recipe (mark, buttons, tags, inputs, the hero/weave/ember/wave). **Start here.**
* `references/3T Run — Build (2c).dc.html` — all V1 screens, desktop + mobile (the primary reference).
* `references/modernist.css` — the underlying Modernist system stylesheet the theme is derived from (component classes; note its accent is the original red — override with the orange tokens above).

### Where each screen lives (target Angular repo `frontend/src/`)

|Screen|Template + styles|
|-|-|
|Header / nav|`app/app.html`, `app/app.css`|
|01 Upcoming runs|`app/features/events/events.html`, `events.css`|
|02 Register · 03 Confirmation|`app/features/register/register.html`, `register.css`|
|04 Sign up|`app/features/auth/signup.html`, `auth.css`|
|05 Sign in|`app/features/auth/login.html`, `auth.css`|
|06 Dashboard|`app/features/dashboard/dashboard.html`, `dashboard.css`|
|Tokens (global)|`frontend/src/styles/tokens.css` (imported by `frontend/src/styles.css`)|

Keep every existing `data-testid`, `routerLink`, form control and HTTP call — the Playwright/axe suite (`e2e/`) and the charter's CS-1…6 scenarios must still pass.

