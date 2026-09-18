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
  `SecurityConfig`. Sign-up captures the person's `name` alongside email + password (Increment 7,
  #38); `/me` and `AccountResponse` carry it. `name` is `NOT NULL` on `app_user` and `@NotBlank` at
  the API — every account has one.
- **Registration linkage:** `POST /api/registrations` stays anonymous, but if the caller has a
  session the registration is attributed to that account (`registration.user_id`). Anonymous → null.
  For a signed-in user the registration form is prefilled from their account (name + email) so they
  don't retype it, but the fields stay editable and the anonymous form is unchanged (Increment 7, #38).
- **Roles (Increment 10a, #57):** authorities come from a local `user_roles` table (`role` +
  `user_roles`, a many-to-many with a FK to `app_user`), which is the real authority and stays after
  the future Entra swap
  `ADMIN_EMAILS` (comma-separated, defaulted empty) names the admin accounts. On **sign-up and login**
  `RoleService` lazily ensures the account's grants: baseline `ROLE_USER` for everyone, plus
  `ROLE_ADMIN` when the email is configured — so an admin gets its `ROLE_ADMIN` row the first time it
  authenticates after being listed.
- **Admin surface:** `/api/admin/**` requires `ROLE_ADMIN` in `SecurityConfig` — the real boundary. A
  non-admin gets **403**, an anonymous caller **401** 
  The Angular role guard only *hides* the controls; it is never the guard.
  Today the surface is `POST /api/admin/events` (create an event — name + future start; the location
  auto-binds to the sole Bucharest row and the entered time is read as Europe/Bucharest).
- **CSRF:** Spring's CSRF token machinery is **off** for the JSON API — it's served same-origin and the
  `SameSite=Lax` session cookie blocks the cross-site form POST tokens defend against, without forcing
  a token round-trip onto the anonymous registration POST. A token-based CSRF layer is a **pre-go-live
  hardening item, tracked in the charter's Increment 5 (Hardening).**
- **Logout:** `POST /api/auth/logout` invalidates the session, clears the security context, and
  expires the cookie (204). Handled by Spring Security's logout filter (configured in `SecurityConfig`).
