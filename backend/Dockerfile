# syntax=docker/dockerfile:1
#
# The backend image (ADR 0003): the Spring Boot API only. The Angular build is a separate
# image (frontend/Dockerfile). In production, CloudFront sends /api/* and /actuator/* here.
#
#   docker build -t 3t-backend backend
#   docker run --rm -p 8080:8080 -e DB_HOST=host.docker.internal \
#     -e MAIL_HOST=host.docker.internal -e SESSION_COOKIE_SECURE=false 3t-backend
#
# Every setting comes from env vars (see src/main/resources/application.yml).

# ── 1. Spring Boot jar ──────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
# A checkout on Windows can lose the exec bit.
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && cp target/*.jar app.jar \
 # Unpacked jar + lib/: starts faster than a nested fat jar.
 && java -Djarmode=tools -jar app.jar extract --destination extracted

# ── 2. Runtime: JRE only, non-root ──────────────────────────────────────────
FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /build/extracted/ ./
USER 10001
EXPOSE 8080
# Heap follows the container's memory limit (e.g. 1 GiB → ~750 MB heap).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
