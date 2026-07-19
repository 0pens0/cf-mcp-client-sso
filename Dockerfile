# Multi-stage build for cf-mcp-client-k8s
# Build: docker build -t cf-mcp-client-k8s:1.6.0 .
# Run:  docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=k8s cf-mcp-client-k8s:1.6.0

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Node is required for the frontend Maven plugin
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src

RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --create-home --uid 10001 appuser
COPY --from=build /workspace/target/cf-mcp-client-*.jar /app/app.jar
RUN chown -R appuser:appuser /app

USER 10001
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=k8s \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    SERVICE_BINDING_ROOT=/bindings

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
