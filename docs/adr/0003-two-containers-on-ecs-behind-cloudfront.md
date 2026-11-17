---
status: accepted
---

# Two containers on ECS behind CloudFront, with a switch to Fargate for scale

The app ships as **two Docker images**:
- the Spring Boot API (`backend/Dockerfile`);
- the Angular build served by nginx (`frontend/Dockerfile`).

Both run on **AWS ECS in eu-central-1 (Frankfurt)**. **CloudFront** is the only public entry
point. It sends `/api/*` and `/actuator/*` to the backend and everything else to the frontend, so
the browser still sees one origin.

Day to day, both containers run on **one EC2 t4g.small** (Graviton, arm64). One template
parameter, `ScaleMode`, moves the same stack to an **internal ALB with Fargate autoscaling**. We
use that mode for the full-target soak (#89) and when real traffic needs it. The database is
**RDS PostgreSQL 16 db.t4g.micro**. Email is **SES** over SMTP.

Decided in Increment 14b (#98). This supersedes [ADR 0002](0002-single-container-on-container-apps.md).

## Context

ADR 0002 put one image on Azure Container Apps. That Azure setup was never provisioned. We
then got **$100 in AWS free-plan credits**. These are the constraints:

- **Budget:** the credits are all the money. The free plan suspends the account when they run
  out, or after 6 months, unless we upgrade to the Paid plan.
- **No domain.** HTTPS must come from an AWS-provided hostname.
- **Always on, on-demand capacity.** No Spot, and no stop schedule.
- **Traffic:** about 100–200 users a month at first. The setup must be able to scale, and the
  soak (#89) must run against production at ~5,000 concurrent users.
- **One origin.** The session cookie is `SameSite=Lax`, and the CSRF double-submit cookie must be
  readable by the SPA. Both need the SPA and `/api` on the same origin.
- **EU region** for GDPR (charter §7). Frankfurt has the lowest latency to Romania.

We also wanted the frontend and the backend as separate images. Then a frontend change does not
rebuild the JVM image, each one scales on its own, and the static files can be cached at the
edge.

## Considered options

Monthly costs are for Frankfurt, 24/7, on-demand, from the AWS Price List API (November 2026).

- **ECS on one EC2 host + CloudFront, with a Fargate switch (≈ $38/month):** chosen. It is the
  cheapest setup that keeps one origin, runs two containers and can scale. CloudFront gives
  HTTPS on `*.cloudfront.net` with no domain, path routing, and edge caching for the hashed
  Angular files. Its always-free tier (1 TB, 10M requests a month) covers our traffic.
- **ALB + Fargate from day one (≈ $70/month):** the same app, scaled from the start. The ALB
  alone is ~$20 a month, and each Fargate task needs a public IPv4. That leaves ~40 days of
  credits. Kept as the `fargate` mode of the chosen option instead.
- **API Gateway HTTP API + Fargate (≈ $50/month):** cheaper than an ALB at low traffic, but it
  needs Cloud Map for service discovery and has no sticky sessions. Sessions live in the JVM, so
  more than one backend replica needs stickiness. Rejected.
- **Lightsail containers + Lightsail Postgres (≈ $30–40/month):** simple, but no autoscaling, and
  a later move to ECS means rewriting the infrastructure. Rejected.
- **Lambda (SnapStart) for the API:** JVM cold starts, a connection to Postgres per concurrent
  execution, and a Lambda in a VPC needs NAT to reach SES and Open-Meteo (~$37/month). Rejected.
- **App Runner:** closed to new customers on 2026-04-30. Not available.
- **ECS Express Mode:** routes per host name, not per path, so it cannot put the SPA and `/api`
  on one origin without a domain. Rejected.

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
  viewer headers and cookies are forwarded. nginx marks the hashed build files `immutable` and
  `index.html` `no-cache`, and CloudFront follows those headers.
- **No NAT gateway** (≈ $37/month). The host, or each Fargate task, has a public IPv4 for
  outbound calls to GHCR, SES and Open-Meteo.
- **Images stay on GHCR** as public packages, built natively for arm64 on GitHub's free arm
  runners.
- **Secrets are SSM Parameter Store SecureStrings** (free), not an RDS-managed secret, whose
  7-day rotation would break running tasks.
- **The database is private.** RDS admits port 5432 only from the backend's security groups,
  and TLS is required (`DB_SSLMODE=require`).

## Cost

| Item | Monthly |
| :--- | ---: |
| EC2 t4g.small | ≈ $14.0 |
| EBS 30 GB gp3 (host root volume) | ≈ $2.9 |
| Elastic IP (public IPv4, $0.005/h) | $3.65 |
| RDS db.t4g.micro + 20 GB gp3 | $16.61 |
| CloudFront (inside the always-free tier) | $0 |
| CloudWatch Logs (~1 GB, 7-day retention) | ≈ $1 |
| **Total** | **≈ $38 (≈ $1.25/day)** |

That is ~2.4 months of runway on $100, including one soak (≈ $6–10). The console's "Explore
AWS" activities add up to $20 each. The EC2, RDS and Budgets activities fit this setup, which
gives +$60 and ~4 months. `fargate` mode costs ≈ $70/month, so we use it only for the soak day,
or when traffic pays for it.

## Consequences

- **The backend hosts no pages.** `SpaHostingConfig` is gone, and any path outside the
  explicitly opened API is a 401. A new SPA route needs no backend change. A new top-level SPA
  path must not start with `/api` or `/actuator`, because CloudFront sends those to the backend.
- **Deep links and stale chunks are nginx's job.** An extensionless path gets `index.html`. A
  missing file with an extension is a 404, so a browser never parses the shell as JavaScript.
- **No cold start.** The containers run all the time, unlike scale-to-zero in ADR 0002.
- **A deploy in `single` mode has ~30–60 s of backend downtime.** The host ports are fixed, so
  ECS stops the old task before it starts the new one. `fargate` mode deploys with no downtime.
- **One host is a single point of failure.** EC2 auto-recovery restarts it on a hardware fault,
  and RDS is single-AZ. That is fine for a demo, but not for a real go-live.
- **Switching `ScaleMode` takes a few minutes of disruption.** CloudFront changes origin, and the
  ECS services are replaced. Do it in a planned window.
- **Sessions are in memory.** With several backend replicas, the ALB keeps each browser on one
  replica (sticky sessions). Spring Session JDBC removes that need, and it is the step to take
  before scaling out for real.
- **The credits run out.** Before that, decide whether to upgrade to the Paid plan, move, or
  stop. An AWS Budget alerts at 50/80/100%.
- **Portability.** Both images are standard OCI images, and all config comes from env vars, so
  moving to another container host is still a deploy change, not an app change.
