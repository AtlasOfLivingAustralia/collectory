# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
# Warm the Maven cache by resolving deps + plugins without the full source tree.
# dependency:go-offline fails for ehcache:jakarta which transitively requires
# the dead http://maven.java.net repo (blocked by Maven 3.8+).
# resolve-plugins + dependency:resolve cover all compile/runtime/test deps cleanly.
RUN mvn -B dependency:resolve dependency:resolve-plugins
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S collectory && adduser -S collectory -G collectory
RUN mkdir -p /data/collectory/upload /data/collectory/data /data/collectory/config && \
    chown -R collectory:collectory /data/collectory

COPY --from=build --chown=collectory:collectory /app/target/*.jar app.jar

USER collectory

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+ParallelRefProcEnabled", \
  "-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags", \
  "-jar", "app.jar"]
