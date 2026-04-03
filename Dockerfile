FROM eclipse-temurin:21-jdk-alpine AS base
RUN apk add --no-cache nodejs npm

FROM base AS deps
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

FROM base AS npm-deps
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

FROM deps AS build
ARG APP_VERSION=unknown
WORKDIR /app
COPY --from=npm-deps /app/frontend/node_modules frontend/node_modules
COPY frontend frontend
COPY src src
RUN APP_VERSION=${APP_VERSION} ./mvnw package -DskipTests -DskipGitPlugin=true -B

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
