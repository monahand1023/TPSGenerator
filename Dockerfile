# syntax=docker/dockerfile:1

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# BuildKit cache mount keeps the local Maven repo across builds for fast rebuilds.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /work
RUN useradd -r -u 1001 appuser && chown appuser /work

# The shade plugin leaves both the shaded jar and original-*.jar, so name it explicitly.
COPY --from=build /app/target/tps-generator-1.0.0.jar /app/app.jar

USER appuser

# Args are <config.json> <output-dir> (or a subcommand: compare / merge).
# Mount your config in and point at it, e.g.:
#   docker run --rm -v "$PWD:/work" ghcr.io/monahand1023/tpsgenerator /work/config.json /work/results
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
