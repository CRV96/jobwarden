# syntax=docker/dockerfile:1

# JobWarden's server, built from these sources: `docker build -t jobwarden .`
# It needs PostgreSQL; README.md ("Running with Docker") shows it with one.

# The jar, tests left to the build that checks them (CI). Node, for the CSS, is downloaded into web/target as usual.
# On the builder's own platform, whichever the image is for: a jar runs on every one, so the image is built once,
# natively, and only the runtime stage below is made for each platform.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
# Maven itself first, as the wrapper names it, tried again after 10 and 30 seconds: the wrapper downloads it once, with
# no second try, and a passing 404 from Maven Central failed a build that way. The build below finds it in the cache.
RUN --mount=type=cache,target=/root/.m2 for delay in 0 10 30; do \
        sleep "$delay"; ./mvnw -B -q --version > /dev/null && exit 0; \
    done; exit 1
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests -pl app -am package \
    && cp app/target/jobwarden-app-*.jar /jobwarden.jar
# In layers, the libraries apart from JobWarden's own classes: an image of a new version shares them with the last one
RUN java -Djarmode=tools -jar /jobwarden.jar extract --layers --destination /extracted

# For each platform the image is published for, those Java 25's runtime image has: amd64 (Intel and AMD), arm64 (Apple
# Silicon, ARM laptops, Raspberry Pi 4 and 5, ARM NAS), ppc64le and s390x. Not 32-bit ARM, which Java 25 isn't built for.
FROM eclipse-temurin:25-jre
# curl, for Anthropic's installer of Claude Code, which a profile may put in the container for Run it here (Settings:
# docker compose exec jobwarden bash -c "… curl -fsSL https://claude.ai/install.sh | bash"). Claude Code itself isn't
# in the image: each installs their own.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
# Never as root. The key that encrypts the mailbox passwords and GitHub tokens is made in ~/.jobwarden on the first
# start: the volume keeps it, and with it what was encrypted, when the container is made anew. So does it keep what's
# installed in ~/.local (Claude Code: the installer makes ~/.jobwarden/local first), and Claude Code's sign-in and
# settings (CLAUDE_CONFIG_DIR).
RUN useradd --system --create-home --home-dir /home/jobwarden --uid 10001 jobwarden \
    && mkdir /home/jobwarden/.jobwarden && chown jobwarden /home/jobwarden/.jobwarden \
    && ln -s /home/jobwarden/.jobwarden/local /home/jobwarden/.local && chown -h jobwarden /home/jobwarden/.local
ENV CLAUDE_CONFIG_DIR=/home/jobwarden/.jobwarden/claude
# Where the installer puts claude, so docker compose exec finds it to sign it in
ENV PATH=/home/jobwarden/.local/bin:$PATH
USER jobwarden
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
VOLUME /home/jobwarden/.jobwarden
EXPOSE 9273
# Healthy once /actuator/health/liveness says UP. The image has no curl: bash's /dev/tcp asks instead, on the port the
# server listens on. In Docker, change that port with SERVER_PORT only: the check reads it, but not --server.port or a
# properties file. (Publishing the port elsewhere, -p 9090:9273, needs no change at all.)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-9273} && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
# The heap as a share of the container's memory rather than of the machine's, and a clean exit (for Docker to restart
# it) rather than limping on after running out of memory. JAVA_TOOL_OPTIONS adds or overrides settings.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "jobwarden.jar"]
