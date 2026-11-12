---
status: accepted
---

# One container on Azure Container Apps, not Static Web Apps + App Service

The app ships as **one Docker image**. Spring Boot serves the API and the Angular build from the
same origin. The image runs on **Azure Container Apps** (consumption plan, scale to zero) and is
stored on **GHCR**. The database is **Azure Postgres Flexible Server B1MS**. We chose this because
it is the only setup that costs ~€0/month **and** keeps the SPA and `/api` on one origin. The
session cookie (`SameSite=Lax`) and the CSRF double-submit cookie both depend on that one origin.
Decided in Increment 14 (#88).

## Context

The Increment 0 deploy skeleton targeted Static Web Apps (Angular) + App Service (Spring Boot). The
charter allowed "or a single container app". The auth model is a server session in an httpOnly
cookie, plus a CSRF token cookie that the SPA reads and echoes back (Increment 14, PR A). Both
only work if the browser sees the SPA and the API as the same site. The budget for a demo is €0.

## Considered options

- **SWA Free + App Service F1:** €0, but a Free SWA cannot proxy `/api` to an external backend.
  The SPA would call `*.azurewebsites.net` from `*.azurestaticapps.net`, which is cross-site. The
  session cookie would need `SameSite=None`, CORS with credentials, and a cross-site CSRF story.
  Safari and Firefox block third-party cookies anyway. F1 also caps CPU at 60 minutes a day, and
  one JVM start uses a large part of that. Rejected: it does not work with our auth.
- **SWA Standard + App Service B1:** works. The SWA "linked backend" keeps `/api` same-origin.
  But it costs ~$22/month (SWA Standard ~$9 + B1 Linux ~$13) for a demo, and it means two
  artifacts, two deploy jobs and two long-lived deploy secrets. Rejected on cost.
- **One image on Container Apps (consumption) + GHCR:** chosen. It stays inside the monthly free
  grant (180k vCPU-s, 360k GiB-s, 2M requests), keeps one origin, ships one artifact, and deploys
  with GitHub OIDC (no long-lived secrets). The same image runs locally with `docker run`.

## Consequences

- **Cold start.** After ~5 minutes idle the app scales to zero, and the next visitor waits
  ~20–40 s for the JVM and Flyway. The image runs an unpacked jar to cut this, and the startup
  probe allows 120 s. To remove the cold start, set `minReplicas: 1` (≈ €10–15/month).
- **Static files come from the JVM, not an edge CDN.** That is fine for a Romania-only audience.
  `SpaHostingConfig` serves the build with `Cache-Control: no-cache`, so a cached `index.html` never
  points at chunks that a new deploy removed. A CDN can be added later without changing the app.
- **Deep links need a server fallback.** Any GET outside `/api` and `/actuator` with no file
  extension gets `index.html`. `/api/**` stays deny-by-default (401), and a missing file stays 404.
- **Postgres is free for 12 months only.** B1MS is covered by the 12-month free services (750 h +
  32 GB). After that it costs ~€13/month. Revisit before the free year ends: keep it, move to a
  cheaper host, or stop the server between demos.
- **The database is reachable from any Azure IP.** Consumption Container Apps have no fixed
  outbound IP, so the server uses the "allow Azure services" firewall rule. TLS is required
  (`DB_SSLMODE=require`), and the password protects the rest. A VNet-integrated environment with
  private access to Postgres removes this. That is the step to take before a real go-live.
- **No Log Analytics workspace**, so there is no log-ingest cost. The live log stream still works.
  Add a workspace when we need retained logs or alerts.
- **Scaling.** One replica is the €0 ceiling. The full-target soak (#89) raises `maxReplicas`, and
  the Postgres tier with it, for its run only.
- **Portability.** It is a standard OCI image, so moving to another container host is a deploy
  change, not an app change.
- **Email** goes through Azure Communication Services over SMTP. The app only sees `MAIL_*`
  variables, so changing the provider is a config change.
