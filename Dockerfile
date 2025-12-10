FROM maven:3.9.11-amazoncorretto-25-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY ./src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine

ARG APP_USER=appuser
ARG APP_GROUP=appgroup
ARG APP_UID=996
ARG APP_GID=996

LABEL maintainer="me@zilid.me" \
    org.opencontainers.image.title="chess-platform" \
    org.opencontainers.image.version="0.0.1" \
    org.opencontainers.image.description="A simple chess platform."

WORKDIR /app

RUN addgroup -S -g ${APP_GID} ${APP_GROUP} \
    && adduser -S -g ${APP_GID} -u ${APP_UID} ${APP_USER} \
    && chown -R ${APP_USER}:${APP_GROUP} .

COPY --chown=${APP_UID}:${APP_GID} --link --from=build /app/target/*.jar app.jar

USER ${APP_USER}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["-Xms256m", "-Xmx512m"]
