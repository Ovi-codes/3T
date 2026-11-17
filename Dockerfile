# syntax=docker/dockerfile:1
#
# One image for the whole app (ADR 0002): Spring Boot serves the API and the Angular build
# from the same origin, so the session cookie and the CSRF token stay first-party.
#
#   docker build -t 3t-run .
#   docker run --rm -p 8080:8080 -e DB_HOST=host.docker.internal \
#     -e MAIL_HOST=host.docker.internal -e SESSION_COOKIE_SECURE=false 3t-run
#
# Every setting comes from env vars (see backend/src/main/resources/application.yml).

# ── 1. Angular production build ─────────────────────────────────────────────
FROM node:24-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ── 2. Spring Boot jar, with the Angular build in classpath:/static ─────────
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /build
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
# A checkout on Windows can lose the exec bit.
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /build/dist/threet-run/browser src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && cp target/*.jar app.jar \
 # Unpacked jar + lib/: starts faster than a nested fat jar (the cold start counts here).
 && java -Djarmode=tools -jar app.jar extract --destination extracted

# ── 3. Runtime: JRE only, non-root ──────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=backend --chown=app:app /build/extracted/ ./
USER 10001
EXPOSE 8080
# Heap follows the container's memory limit (1 GiB on Container Apps → ~750 MB heap).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
