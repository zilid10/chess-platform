FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY .mvn .mvn

COPY mvnw pom.xml ./

RUN ./mvnw -q dependency:go-offline

COPY src src

ENTRYPOINT ["./mvnw", "-q", "gatling:test"]
