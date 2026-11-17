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
