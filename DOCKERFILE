### Build the application ###
# Using Eclipse Temurin for the JDK
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy the build files first to leverage Docker layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (this layer is cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline

# Copy the source code and build the application
COPY src src
RUN ./mvnw package -DskipTests

### Create the production-ready runtime image ###
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy only the compiled JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the Spring Boot port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]