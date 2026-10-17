FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY .mvn .mvn

COPY mvnw pom.xml ./

RUN ./mvnw -q dependency:go-offline

COPY src src

ENTRYPOINT ["./mvnw", "--batch-mode", "--no-transfer-progress", "gatling:test"]
