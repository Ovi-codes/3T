# Deployment

Two containers on AWS ECS behind CloudFront, in eu-central-1 (Increment 14b, #98). Why this
setup: [ADR 0003](../adr/0003-two-containers-on-ecs-behind-cloudfront.md).

`deploy.yml` ships `main` after green CI. It builds both images for arm64, pushes them to GHCR
(`ghcr.io/ovi-codes/3t-backend` and `3t-frontend`, tagged `:<sha>` and `:main`) and rolls them
out to ECS.

## Rules for app code

- **All config comes from env vars**, never hardcoded: DB URL and credentials, SMTP host,
  mail-from, weather API URL and TTL, admin emails.
  - Weather: `WEATHER_API_URL`, `WEATHER_FORECAST_HORIZON_DAYS`, `WEATHER_CACHE_TTL`. All have
    defaults that hit the public Open-Meteo endpoint, so prod works with none set.
  - `ADMIN_EMAILS`: comma-separated accounts granted `ROLE_ADMIN`. Empty by default, so there is
    no admin unless it is set.
  - `DEMO_DATA`: `true` adds the `db/demo` Flyway seed (fake leaderboard runners and a demo
    account with a run history). Local demos only, **never set in prod**.
- **`DB_SSLMODE`** (default `prefer`) is appended to the JDBC URL. Prod sets `require`.
- **Flyway migrations run on startup**, so a fresh database provisions itself.
- **The backend hosts no pages.** The Angular build is its own image (nginx), and any backend
  path outside the opened API is a 401. A new SPA route needs no backend change. A new top-level
  SPA path must not start with `/api` or `/actuator`, because CloudFront (and the ALB) route
  those to the backend.
- **No localhost URLs in app code.** The Angular app calls `/api`: through the dev proxy
  locally, same-origin in prod.

## How it works

`ScaleMode=single` (default, everyday production):

```
browser ─HTTPS─> CloudFront (https://dxxxx.cloudfront.net)
                   ├─ /api/*, /actuator/* ─HTTP:8080─┐
                   └─ default ────────────HTTP:80 ───┤
                                                     ▼
          EC2 t4g.small (ECS-optimised AL2023 arm64, Elastic IP, public subnet)
            ├─ ECS service "backend"  (bridge, host port 8080) ──TLS──> RDS PG16 (private subnets)
            └─ ECS service "frontend" (nginx, bridge, host port 80)
```

`ScaleMode=fargate` (soak and growth):

```
CloudFront ─VPC origin─> internal ALB ─ /api/*, /actuator/* ─> backend on Fargate
                                     │                         (autoscaling, sticky sessions)
                                     └─ default ─────────────> frontend on Fargate
```

- **The same container definitions run in both modes.** Only the network mode, the launch
  type and the origin behind CloudFront change.
- **Only CloudFront can reach the host.** The security groups admit only the CloudFront
  origin-facing managed prefix list.
- **The backend trusts the forwarded scheme.** TLS ends at CloudFront, which sends
  `CloudFront-Forwarded-Proto`. The backend sets `SERVER_TOMCAT_REMOTEIP_PROTOCOL_HEADER` to that
  header and trusts any proxy (`SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=.*`). That makes
  `request.isSecure()` true, so the CSRF cookie gets `Secure`. Trusting any proxy is safe: only
  CloudFront can reach the ports, and the app uses no client IPs.
- **Caching follows the origin's headers.** `/api/*` and `/actuator/*` are never cached, and all
  viewer headers and cookies are forwarded. nginx (`frontend/nginx/default.conf`) marks the
  hashed build files `immutable` and `index.html` `no-cache`, and CloudFront follows those
  headers. A missing file with an extension is a 404, so a browser never parses the shell as
  JavaScript.
- **No NAT gateway** (≈ $37/month). The host, or each Fargate task, has a public IPv4 for
  outbound calls to GHCR, SES and Open-Meteo.
- **Images stay on GHCR** as public packages, built natively for arm64 on GitHub's free arm
  runners.
- **Secrets are SSM Parameter Store SecureStrings** (free), not an RDS-managed secret, whose
  7-day rotation would break running tasks.
- **The database is private.** RDS admits port 5432 only from the backend's security groups,
  and TLS is required (`DB_SSLMODE=require`).
