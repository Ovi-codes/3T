#!/usr/bin/env bash
# Smoke-checks the deployed app through CloudFront. deploy.yml runs it after every deploy, and
# you can run it by hand after a ScaleMode switch:
#
#   infra/smoke-check.sh https://dxxxxxxxxxxxx.cloudfront.net
set -euo pipefail

url=${1:?usage: infra/smoke-check.sh <app url>}
fail() { echo "::error::$*"; exit 1; }
status() { curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$@"; }

# Up to 5 minutes for the backend to report UP with its database connected. In
# ScaleMode=single, a backend deploy has ~30–60 s of downtime.
for attempt in $(seq 1 30); do
  health=$(curl -fsS --max-time 30 "$url/actuator/health" || true)
  if grep -q '"status":"UP"' <<<"$health"; then
    echo "Health UP after attempt $attempt"
    break
  fi
  if [ "$attempt" = 30 ]; then fail "$url/actuator/health never reported UP"; fi
  echo "Attempt $attempt: not UP yet (${health:-no response})"
  sleep 10
done

# The frontend: a deep link gets the SPA shell, a stale chunk name does not.
curl -fsS --max-time 30 "$url/dashboard" | grep -q '<app-root>' \
  || fail "/dashboard did not return the SPA shell"
code=$(status "$url/main-STALE.js")
[ "$code" = 404 ] || fail "/main-STALE.js returned $code, expected 404"

# The backend: deny-by-default, and the CSRF layer works behind CloudFront.
code=$(status "$url/api/me/registrations")
[ "$code" = 401 ] || fail "anonymous /api/me/registrations returned $code, expected 401"
cookie=$(curl -s -D - -o /dev/null --max-time 30 "$url/api/events" | grep -i '^set-cookie: XSRF-TOKEN=' || true)
[ -n "$cookie" ] || fail "/api/events set no XSRF-TOKEN cookie"
grep -qi '; *secure' <<<"$cookie" \
  || fail "the XSRF-TOKEN cookie is not Secure: the backend does not trust CloudFront-Forwarded-Proto"
code=$(status -X POST -H 'Content-Type: application/json' -d '{}' "$url/api/registrations")
[ "$code" = 403 ] || fail "a POST without a CSRF token returned $code, expected 403"

echo "Smoke check passed on $url"
