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
ENTRYPOINT ["java", "-jar", "jobwarden.jar"]
