FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
# The wrapper pins the same Maven version CI uses; .mvn also holds the JVM flags Error Prone needs inside javac
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY ./src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests -B --no-transfer-progress \
    && java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted --application-filename app.jar

FROM eclipse-temurin:21-jre-alpine

ARG APP_USER=appuser
ARG APP_GROUP=appgroup
ARG APP_UID=996
ARG APP_GID=996

# Pass --build-arg APP_VERSION=... for release builds
ARG APP_VERSION=0.0.1-SNAPSHOT
LABEL maintainer="me@zilid.me" \
    org.opencontainers.image.title="chess-platform" \
    org.opencontainers.image.version=${APP_VERSION} \
    org.opencontainers.image.description="A simple online chess platform where people can play each other."

WORKDIR /app

RUN addgroup -S -g ${APP_GID} ${APP_GROUP} \
    && adduser -S -G ${APP_GROUP} -u ${APP_UID} ${APP_USER}

# Least to most frequently changed, so a code change only rebuilds the last layer
COPY --link --from=build /app/extracted/dependencies/ ./
COPY --link --from=build /app/extracted/spring-boot-loader/ ./
COPY --link --from=build /app/extracted/snapshot-dependencies/ ./
COPY --link --from=build /app/extracted/application/ ./

# Overridable at run time, e.g. from docker compose
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m"

# Training run for a Class Data Sharing archive, which speeds up startup. It only refreshes the context,
# so Flyway and Hibernate must not touch the database, which does not exist at build time. The base configuration
# requires a datasource URL and relay settings, so the run gets a placeholder URL and the in-memory broker.
RUN java -XX:ArchiveClassesAtExit=app.jsa \
    -Dspring.context.exit=onRefresh \
    -Dspring.datasource.url=jdbc:postgresql://build-time-placeholder/none \
    -Dapp.websocket.relay.enabled=false \
    -Dapp.websocket.relay.host=build-time-placeholder \
    -Dapp.websocket.relay.port=61613 \
    -Dapp.websocket.relay.login=build-time-placeholder \
    -Dapp.websocket.relay.passcode=build-time-placeholder \
    -Dspring.flyway.enabled=false \
    -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
    -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
    -Dspring.jpa.hibernate.ddl-auto=none \
    -jar app.jar

USER ${APP_USER}
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]
