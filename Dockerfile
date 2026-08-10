# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy only pom.xml first to leverage Docker layer caching for dependencies
COPY ecomm/pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the rest of the source and build
COPY ecomm/src ./src
RUN mvn clean package -DskipTests -B

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Render sets $PORT at runtime; Spring Boot must bind to it
ENV JAVA_OPTS=""

# Copy the built jar from the build stage (adjust name if your pom sets a fixed finalName)
COPY --from=build /app/target/*.jar app.jar

# Render injects PORT env var; Spring Boot reads server.port from it via application.properties
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]
