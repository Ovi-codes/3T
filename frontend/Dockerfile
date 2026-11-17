# syntax=docker/dockerfile:1
#
# The frontend image (ADR 0003): the Angular production build, served by nginx. The backend
# is a separate image. In production, CloudFront sends /api/* and /actuator/* to the backend
# and everything else here, so the browser sees one origin.
#
#   docker build -t 3t-frontend frontend
#   docker run --rm -p 4300:8080 3t-frontend
#
# This image serves only the SPA. To use the app locally, run `npm start`: the dev server
# proxies /api to the backend.

# ── 1. Angular production build ─────────────────────────────────────────────
FROM node:26-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ── 2. Runtime: nginx, non-root, port 8080 ──────────────────────────────────
FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /build/dist/threet-run/browser /usr/share/nginx/html
EXPOSE 8080