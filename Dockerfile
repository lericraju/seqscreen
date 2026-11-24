# SeqScreen — container image for hosts that run a JVM (Render, Railway, Fly.io, Koyeb, a VPS).
# Vercel cannot run this: it has no Java runtime.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first, so the layer is cached across source changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/target/seqscreen-1.0.0.jar app.jar

# The generated dataset and its index live in the heap, so keep this generous.
ENV JAVA_OPTS="-Xmx768m"
# Most hosts inject PORT; Spring Boot reads SERVER_PORT, so pass it explicitly.
ENV SERVER_PORT=8080
EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]
