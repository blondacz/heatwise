# ---- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
# Copy only gradle wrapper & metadata first for better caching
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts settings.gradle.kts
COPY buildSrc buildSrc
COPY gradle/libs.versions.toml gradle/libs.versions.toml

RUN ./gradlew --version
# Copy sources last (better cache)
COPY . .
RUN ./gradlew :view:bootJar --no-daemon

# ---- Runtime stage ----------------------------------------------------------
FROM gcr.io/distroless/java21:nonroot AS run
WORKDIR /app
# Spring Boot fat jar:
COPY --from=build /app/view/build/libs/view-*.jar /app/app.jar

# App (8080) + Actuator (8081)
EXPOSE 8080 8081

# (Optional) Set default JVM opts; can be overridden via JAVA_TOOL_OPTIONS
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC"
ENV SPRING_MAIN_BANNER-MODE=off

USER nonroot:nonroot
ENTRYPOINT ["java","-jar","/app/app.jar"]
