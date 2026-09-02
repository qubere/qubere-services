# syntax=docker/dockerfile:1
#
# Multi-stage build for qubere-document-agent.
#
# IMPORTANT: the build context must be the repository root, not this directory, because the
# module depends on sibling reactor modules (qubere-agent-api/-core/-storage) that are not
# published to any registry:
#
#   docker build -f qubere-document-agent/Dockerfile -t qubere-document-agent:local .
#
# The final image ships only the runtime JRE and the built jar; Maven, the JDK, and the full
# source tree never leave the build stage.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy every module's pom.xml first (and only the poms) so a change to application source code
# does not invalidate Maven's dependency-resolution layer on a rebuild.
COPY pom.xml ./
COPY qubere-agent-api/pom.xml qubere-agent-api/pom.xml
COPY qubere-agent-core/pom.xml qubere-agent-core/pom.xml
COPY qubere-agent-storage/pom.xml qubere-agent-storage/pom.xml
COPY qubere-echo-agent/pom.xml qubere-echo-agent/pom.xml
COPY qubere-document-agent/pom.xml qubere-document-agent/pom.xml

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -pl qubere-document-agent -am dependency:go-offline

COPY qubere-agent-api qubere-agent-api
COPY qubere-agent-core qubere-agent-core
COPY qubere-agent-storage qubere-agent-storage
COPY qubere-document-agent qubere-document-agent

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -pl qubere-document-agent -am package -DskipTests \
    && cp qubere-document-agent/target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

# curl is added deliberately for the HEALTHCHECK below; it is the only package installed on top
# of the base runtime image, keeping the attack surface close to the upstream JRE image's own.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 qubere \
    && useradd --system --uid 10001 --gid qubere --home-dir /app --shell /usr/sbin/nologin qubere

WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
RUN chown qubere:qubere /app/app.jar

USER qubere

# Every setting below is already externalized via environment variables in application.yml, so
# this image runs standalone out of the box (in-memory H2, permissive auth, no external model
# provider configured) and is reconfigured for a real deployment purely through env vars/secrets
# -- no image rebuild required.
ENV SERVER_PORT=8081
ENV JAVA_OPTS=""
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
