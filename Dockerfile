# Stage 1: build the React app into src/main/resources/static
FROM node:22-alpine AS web
WORKDIR /app/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# Stage 2: build the Spring Boot jar (static files included)
FROM eclipse-temurin:21-jdk AS jar
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
COPY --from=web /app/src/main/resources/static src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test

# Stage 3: small runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=jar /app/build/libs/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
