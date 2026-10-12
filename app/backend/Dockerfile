# ============================================================
# Cameraman Photo Studio - Backend Dockerfile
# Multi-stage build for minimal production image
# ============================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and POM
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy source
COPY src src

# Build application
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl for healthcheck
RUN apk add --no-cache curl tzdata

# Set timezone
ENV TZ=Asia/Kolkata
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Create non-root user
RUN addgroup -S cameraman && adduser -S cameraman -G cameraman

WORKDIR /app

# Copy jar from builder
COPY --from=builder /app/target/*.jar app.jar

# Create logs directory
RUN mkdir -p logs && chown cameraman:cameraman logs

# Switch to non-root user
USER cameraman

# Expose port
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-Xms256m -Xmx512m \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
