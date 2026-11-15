# syntax=docker/dockerfile:1

# JobWarden's server, built from these sources: `docker build -t jobwarden .`
# It needs PostgreSQL; README.md ("Running with Docker") shows it with one.

# The jar, tests left to the build that checks them (CI). Node, for the CSS, is downloaded into web/target as usual.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests -pl app -am package \
    && cp app/target/jobwarden-app-*.jar /jobwarden.jar
# In layers, the libraries apart from JobWarden's own classes: an image of a new version shares them with the last one
RUN java -Djarmode=tools -jar /jobwarden.jar extract --layers --destination /extracted

FROM eclipse-temurin:25-jre
# Never as root. The key that encrypts the mailbox passwords and GitHub tokens is made in ~/.jobwarden on the first
# start: the volume keeps it, and with it what was encrypted, when the container is made anew.
RUN useradd --system --create-home --home-dir /home/jobwarden --uid 10001 jobwarden \
    && mkdir /home/jobwarden/.jobwarden && chown jobwarden /home/jobwarden/.jobwarden
USER jobwarden
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
VOLUME /home/jobwarden/.jobwarden
EXPOSE 8080
# Healthy once /actuator/health/liveness says UP. The image has no curl: bash's /dev/tcp asks instead, on the port the
# server listens on. In Docker, change that port with SERVER_PORT only: the check reads it, but not --server.port or a
# properties file. (Publishing the port elsewhere, -p 9090:8080, needs no change at all.)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8080} && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
# The heap as a share of the container's memory rather than of the machine's, and a clean exit (for Docker to restart
# it) rather than limping on after running out of memory. JAVA_TOOL_OPTIONS adds or overrides settings.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "jobwarden.jar"]
