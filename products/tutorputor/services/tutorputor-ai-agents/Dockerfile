FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
# Skip tests to speed up build in Docker
RUN ./gradlew :products:tutorputor:services:tutorputor-ai-agents:assemble --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/products/tutorputor/services/tutorputor-ai-agents/build/libs/tutorputor-ai-agents-1.0.0.jar app.jar
EXPOSE 50051
ENTRYPOINT ["java", "-jar", "app.jar"]
