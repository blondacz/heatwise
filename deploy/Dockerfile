# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Copy ONLY what's needed to run the wrapper first (better cache)
COPY gradlew ./gradlew
COPY gradle/wrapper/ ./gradle/wrapper/
RUN chmod +x ./gradlew && sed -i 's/\r$//' ./gradlew

# Then copy the rest of the sources
COPY . .

# Sanity check (optional)
RUN ls -la && ls -la gradle && ls -la gradle/wrapper

# Build the runnable distribution of the :app module
RUN ./gradlew --no-daemon :app:installDist


# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /opt/heatwise

COPY --from=build /src/app/build/install/app/ ./
RUN mkdir -p /opt/heatwise/decisions && chown -R 10001:0 /opt/heatwise
USER 10001
EXPOSE 8080
# Distroless uses `java` entrypoint; run the app's main via its classpath
ENTRYPOINT ["java","-XX:+ExitOnOutOfMemoryError","-XX:MaxRAMPercentage=75.0","-cp","lib/*","dev.g4s.heatwise.app.Main"]
