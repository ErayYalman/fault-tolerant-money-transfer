# syntax=docker/dockerfile:1.7

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN --mount=type=cache,id=money-transfer-maven,target=/root/.m2,sharing=locked \
    chmod +x ./mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/

RUN --mount=type=cache,id=money-transfer-maven,target=/root/.m2,sharing=locked \
    ./mvnw --batch-mode --no-transfer-progress -DskipTests package


# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN mkdir -p /app \
    && chown 10001:10001 /app

COPY --from=build --chown=10001:10001 \
    /workspace/target/transfer-service-0.0.1-SNAPSHOT.jar \
    /app/app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]