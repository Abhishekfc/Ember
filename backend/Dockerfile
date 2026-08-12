# Two stages so the shipped image carries only a JRE and the built jar — not Gradle, the Kotlin
# compiler, or the source tree, which together are several hundred MB of attack surface and cold-
# start weight that a running server has no use for.

# ---------- build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# The wrapper and dependency declarations are copied on their own first, so Docker can cache the
# dependency download layer. Without this split, every source edit would re-download the whole
# dependency graph on each build.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet || true

COPY src ./src
# -x test because the image build is not the place to discover a failing test: CI (or a local
# ./gradlew test) owns that, and a deploy that silently skipped straight past a red test would be
# worse than one that never ran it. Run the tests before deploying.
RUN ./gradlew bootJar --no-daemon -x test

# ---------- run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Runs as a non-root user: a container process that never needs to write outside its own workdir
# has no reason to hold root, and this is the cheapest possible mitigation if the app is ever
# compromised through a dependency.
RUN useradd --system --create-home --shell /usr/sbin/nologin emigo
USER emigo

COPY --from=build --chown=emigo:emigo /build/build/libs/*.jar app.jar

# MaxRAMPercentage rather than a fixed -Xmx: the JVM otherwise sizes its heap against the *host's*
# memory, not the container's limit, and will happily grow past a small instance's allowance until
# the platform kills it. This keeps the heap proportional to whatever the container is actually
# given.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

# PORT is injected by the host (Render sets it); the app already reads it via server.port.
EXPOSE 8080

# Shell form so ${JAVA_OPTS} is expanded, and exec so the JVM becomes PID 1 and receives SIGTERM
# directly — otherwise shutdown signals never reach it and the platform hard-kills the container
# mid-request instead of letting Spring close its connection pool cleanly.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
