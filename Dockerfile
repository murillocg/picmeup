FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

RUN apk add --no-cache nodejs npm

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY frontend/package.json frontend/package-lock.json frontend/
RUN cd frontend && npm ci

COPY frontend frontend
COPY src src

RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN chown -R app:app /app
USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
