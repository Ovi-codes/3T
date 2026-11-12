# Auth

Introduced in Increment 3 (accounts). Kept deliberately thin so the later Entra External ID swap
stays local (charter §3).

- **Seam:** everything above `ro.threet.run.auth.AuthProvider` deals in `AccountPrincipal`, never in
  the users table or BCrypt. The only implementation today is `LocalAuthProvider` (email + BCrypt on
  the `app_user` table). A future Entra provider is a new `AuthProvider` + a `SecurityConfig` change,
  not edits across the app. The `AuthProvider` does credential logic only — session mechanics live
  in `SessionAuthenticator` (web layer), so an OIDC provider that manages its own session drops in.
- **Session model:** **httpOnly cookie session** (Spring Security's default `SecurityContextRepository`),
  not a token in JS. The cookie is `HttpOnly; SameSite=Lax`; `Secure` is on by default and dropped
  only for local http via `SESSION_COOKIE_SECURE` (see `application.yml`). Session id is rotated on
  login (fixation defence).
- **Endpoints:** `POST /api/auth/signup` (201, logs in), `POST /api/auth/login` (200),
  `POST /api/auth/logout` (204), `GET /api/auth/me` (200 or 401). Authorisation is deny-by-default;
  the public API (events, anonymous registration, signup/login, health) is enumerated in
  `SecurityConfig`. `GET /api/events/**` also covers a past run's leaderboard,
  `GET /api/events/{id}/leaderboard` (#44) — public by design; it exposes only first name + last
  initial and finish times, and refuses upcoming / cancelled runs (409). Sign-up captures the person's `name` alongside email + password (Increment 7,
  #38); `/me` and `AccountResponse` carry it. `name` is `NOT NULL` on `app_user` and `@NotBlank` at
  the API — every account has one.
- **Registration linkage:** `POST /api/registrations` stays anonymous, but if the caller has a
  session the registration is attributed to that account (`registration.user_id`). Anonymous → null.
- **Roles (Increment 10a, #57):** authorities come from a local `user_roles` table (`role` +
  `user_roles`, a many-to-many with a FK to `app_user`), which is the real authority and stays after
  the future Entra swap. `ADMIN_EMAILS` (comma-separated, defaulted empty) names the admin accounts.
  On **sign-up and login** `RoleService` reconciles the account's grants against config: baseline
  `ROLE_USER` for everyone, plus `ROLE_ADMIN` when the email is configured — so an admin gets its
  `ROLE_ADMIN` row the first time it authenticates after being listed. The resolved roles are put on
  the `AccountPrincipal` and returned in `AccountResponse` (so `/me`, login and sign-up all carry
  `roles`); `AccountPrincipal.authorities()` maps them to Spring `GrantedAuthority`s. Roles resolve at
  authentication time and live in the session principal, so authorisation costs no per-request DB hit.
- **`ADMIN_EMAILS` is the source of truth — revocation included:** the reconcile runs on **every**
  authentication, not just the first, so removing an account from `ADMIN_EMAILS` **revokes** its
  `ROLE_ADMIN` (the join row is dropped) the next time it authenticates. A steady-state login with no
  change writes nothing. The grant and the revoke are idempotent SQL (`insert … on conflict do
  nothing`, a plain `delete`), so two logins of the same account at once can't fail on a duplicate
  row (#64). Demoting an admin is therefore: drop them from the config, and their next
  sign-in (or their session expiring) settles it — no manual row surgery.
- **Admin surface:** `/api/admin/**` requires `ROLE_ADMIN` in `SecurityConfig` — the real boundary. A
  non-admin gets **403**, an anonymous caller **401** (the deny-by-default 401, since this API has no
  login page to redirect to). The Angular role guard only *hides* the controls; it is never the guard.
  Today the surface is the event schedule:
  - `GET /api/admin/events` — the upcoming schedule as an admin sees it: **cancelled runs kept** (the
    public `GET /api/events` drops them) and each run's **registration count**. Neither ever appears
    in the public payload.
  - `POST /api/admin/events` — create an event (name ≤160 + future start; the location auto-binds to
    the sole Bucharest row and the entered wall-clock time is read as Europe/Bucharest, then
    persisted as an `OffsetDateTime`).
  - `PUT /api/admin/events/{id}` — rename / reschedule an upcoming run. **409** if it has been
    cancelled or has already taken place (both are read-only), **400** for a start in the past. If the
    edit **moves the start** (date or time), every registrant is emailed the old→new time (deduped,
    best-effort after commit — same seam and rationale as cancel); a **name-only** edit notifies nobody.
  - `DELETE /api/admin/events/{id}` — hard-remove a run, the "created in error" escape hatch. **409**
    as soon as anyone is registered.
  - `POST /api/admin/events/{id}/cancel` — call a run off. Body: a required free-text `reason`
    (**400** on `reason` if blank or omitted; capped at 200 chars). The backend doesn't enumerate
    reasons — the admin UI offers a picker of standard reasons plus a free-text "other", and whichever
    the admin lands on is sent as the finished wording, which the server relays verbatim into the email.
    Terminal (no un-cancel): the run keeps its registrations, drops off the public list, stays badged on
    each registrant's dashboard until its date passes, and every registrant is emailed **the reason**
    (deduped by email, best-effort after commit, so a mail failure never rolls the cancel back). Why the
    two removals differ: [ADR-0001](../adr/0001-cancel-vs-hard-delete-events.md).
- **CSRF (Increment 14, #88):** every state-changing request needs a token, the anonymous registration
  POST included. This is a double-submit cookie, set up with Spring's `csrf.spa()`:
  - The server sets a JS-readable `XSRF-TOKEN` cookie (`SameSite=Lax`; `Secure` over HTTPS). A write
    must echo it in the `X-XSRF-TOKEN` header. A missing or wrong token gets a **403**.
  - `CsrfCookieFilter` sets the cookie on **every** response, the first one included. Spring defers
    the token, and a JSON API would otherwise never load it.
  - Angular's `HttpClient` copies the cookie into the header for relative URLs by itself.
    `csrfBootstrapInterceptor` covers the gap before the first response lands (a fast submit, or a
    cold start): if a write has no cookie yet, it first calls `GET /api/auth/csrf` (public, 204), then
    sends the write with the header.
  - Login and logout each replace the token in the same response, so the next write already has the
    new one. Login does this in `SessionAuthenticator`, because our controller login bypasses
    Spring's login filters.
  - Tests: integration tests use `.with(csrf())`. `CsrfIntegrationTest` exercises the real cookie
    flow on a fresh context, because `csrf()` swaps the cached context's token repository.
    E2E API seeding uses `e2e/tests/support/csrf.ts`.
  - The servlet ERROR dispatch is `permitAll`. Otherwise deny-by-default would turn the CSRF 403 (or
    any other error) into a 401 for an anonymous caller.
- **No anonymous sessions:** the request cache is disabled, so a 401 doesn't open a session. A
  `JSESSIONID` exists only after sign-in. Anonymous visitors get only the `XSRF-TOKEN` cookie.
- **Logout:** `POST /api/auth/logout` invalidates the session, clears the security context, expires
  the session cookie, and issues a fresh CSRF token (204). Spring Security's logout filter handles it
  (configured in `SecurityConfig`).
