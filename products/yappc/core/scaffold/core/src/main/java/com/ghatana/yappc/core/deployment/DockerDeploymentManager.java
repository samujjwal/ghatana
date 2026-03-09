package com.ghatana.yappc.core.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Docker Deployment Manager Week 11 Day 53: Generate Docker containers and Compose configurations
 *
 * <p>Creates production-ready Docker configurations with: - Multi-stage builds for optimization -
 * Security hardening and best practices - Environment-specific configurations - Health checks and
 * monitoring
 *
 * @doc.type class
 * @doc.purpose Docker Deployment Manager Week 11 Day 53: Generate Docker containers and Compose configurations
 * @doc.layer platform
 * @doc.pattern Manager
 */
public class DockerDeploymentManager {
    private static final Logger logger = LoggerFactory.getLogger(DockerDeploymentManager.class);

    private final Path projectRoot;
    private final ObjectMapper yamlMapper;

    public DockerDeploymentManager(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
 * Generate complete Docker deployment configuration */
    public DockerConfiguration generateDockerConfiguration() throws IOException {
        logger.info("Generating Docker configuration for project: {}", projectRoot);

        ProjectAnalysis analysis = analyzeProject();
        DockerConfiguration config = new DockerConfiguration();

        // Generate Dockerfile for each service
        config.dockerfiles = generateDockerfiles(analysis);

        // Generate docker-compose configurations
        config.composeConfigs = generateComposeConfigurations(analysis);

        // Generate deployment scripts
        config.deploymentScripts = generateDeploymentScripts(analysis);

        // Generate monitoring configuration
        config.monitoringConfig = generateMonitoringConfiguration(analysis);

        return config;
    }

    /**
 * Write all Docker files to project */
    public void writeDockerConfiguration(DockerConfiguration config) throws IOException {
        logger.info("Writing Docker configuration to project");

        // Write Dockerfiles
        for (DockerfileSpec dockerfile : config.dockerfiles) {
            Path dockerfilePath = projectRoot.resolve(dockerfile.path);
            writeFile(dockerfilePath, dockerfile.content);
            logger.info("Created Dockerfile: {}", dockerfilePath);
        }

        // Write docker-compose files
        for (ComposeSpec compose : config.composeConfigs) {
            Path composePath = projectRoot.resolve(compose.path);
            writeFile(composePath, compose.content);
            logger.info("Created docker-compose: {}", composePath);
        }

        // Write deployment scripts
        for (DeploymentScript script : config.deploymentScripts) {
            Path scriptPath = projectRoot.resolve(script.path);
            writeFile(scriptPath, script.content);

            // Make script executable
            scriptPath.toFile().setExecutable(true);
            logger.info("Created deployment script: {}", scriptPath);
        }

        // Write monitoring configuration
        if (config.monitoringConfig != null) {
            Path monitoringPath = projectRoot.resolve("docker/monitoring");
            Files.createDirectories(monitoringPath);

            writeFile(
                    monitoringPath.resolve("prometheus.yml"),
                    config.monitoringConfig.prometheusConfig);
            writeFile(
                    monitoringPath.resolve("grafana-dashboard.json"),
                    config.monitoringConfig.grafanaDashboard);
            logger.info("Created monitoring configuration");
        }
    }

    private ProjectAnalysis analyzeProject() throws IOException {
        ProjectAnalysis analysis = new ProjectAnalysis();
        analysis.projectName = getProjectName();
        analysis.services = detectServices();
        analysis.dependencies = detectDependencies();
        analysis.buildTool = detectBuildTool();
        analysis.hasTests = hasTests();
        analysis.requiresDatabase = requiresDatabase();

        return analysis;
    }

    private List<ServiceSpec> detectServices() throws IOException {
        List<ServiceSpec> services = new ArrayList<>();

        // Main application service
        ServiceSpec mainService = new ServiceSpec();
        mainService.name = getProjectName();
        mainService.type = detectServiceType();
        mainService.port = detectServicePort();
        mainService.buildContext = ".";
        mainService.healthCheck = generateHealthCheck(mainService.type);
        services.add(mainService);

        // Additional services based on project structure
        if (hasSubprojects()) {
            services.addAll(detectSubprojectServices());
        }

        return services;
    }

    private List<DockerfileSpec> generateDockerfiles(ProjectAnalysis analysis) {
        List<DockerfileSpec> dockerfiles = new ArrayList<>();

        for (ServiceSpec service : analysis.services) {
            DockerfileSpec dockerfile = new DockerfileSpec();
            dockerfile.path = service.buildContext + "/Dockerfile";
            dockerfile.content = generateDockerfileContent(service, analysis);
            dockerfiles.add(dockerfile);
        }

        return dockerfiles;
    }

    private String generateDockerfileContent(ServiceSpec service, ProjectAnalysis analysis) {
        StringBuilder dockerfile = new StringBuilder();

        // Multi-stage build based on service type
        switch (service.type) {
            case "java-gradle" ->
                    dockerfile.append(generateJavaGradleDockerfile(service, analysis));
            case "java-maven" -> dockerfile.append(generateJavaMavenDockerfile(service, analysis));
            case "node" -> dockerfile.append(generateNodeDockerfile(service, analysis));
            case "python" -> dockerfile.append(generatePythonDockerfile(service, analysis));
            default -> dockerfile.append(generateGenericDockerfile(service, analysis));
        }

        return dockerfile.toString();
    }

    private String generateJavaGradleDockerfile(ServiceSpec service, ProjectAnalysis analysis) {
        return """
                # Multi-stage build for Java Gradle application
                # Build stage
                FROM gradle:8-jdk17-alpine AS builder

                WORKDIR /app

                # Copy gradle files first for better caching
                COPY gradle/ gradle/
                COPY gradlew build.gradle settings.gradle gradle.properties ./

                # Download dependencies
                RUN ./gradlew dependencies --no-daemon

                # Copy source code
                COPY src/ src/

                # Build application
                RUN ./gradlew build --no-daemon -x test

                # Runtime stage
                FROM eclipse-temurin:17-jre-alpine AS runtime

                # Security: Create non-root user
                RUN addgroup -g 1001 -S appgroup && \\
                    adduser -u 1001 -S appuser -G appgroup

                WORKDIR /app

                # Install security updates and tools
                RUN apk update && \\
                    apk upgrade && \\
                    apk add --no-cache curl && \\
                    rm -rf /var/cache/apk/*

                # Copy application JAR
                COPY --from=builder --chown=appuser:appgroup /app/build/libs/*.jar app.jar

                # Switch to non-root user
                USER appuser

                # Health check
                HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \\
                    CMD curl -f http://localhost:8080/actuator/health || exit 1

                # Expose port
                EXPOSE 8080

                # JVM optimizations and security settings
                ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

                # Run application
                ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
                """;
    }

    private String generateJavaMavenDockerfile(ServiceSpec service, ProjectAnalysis analysis) {
        return """
                # Multi-stage build for Java Maven application
                FROM maven:3.9-eclipse-temurin-17-alpine AS builder

                WORKDIR /app

                # Copy POM first for better caching
                COPY pom.xml ./
                RUN mvn dependency:go-offline -B

                # Copy source and build
                COPY src/ src/
                RUN mvn clean package -DskipTests=true -B

                # Runtime stage
                FROM eclipse-temurin:17-jre-alpine AS runtime

                # Security: Create non-root user
                RUN addgroup -g 1001 -S appgroup && \\
                    adduser -u 1001 -S appuser -G appgroup

                WORKDIR /app

                # Install security updates
                RUN apk update && \\
                    apk upgrade && \\
                    apk add --no-cache curl && \\
                    rm -rf /var/cache/apk/*

                # Copy application JAR
                COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

                USER appuser

                HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \\
                    CMD curl -f http://localhost:8080/actuator/health || exit 1

                EXPOSE 8080

                ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

                ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
                """;
    }

    private String generateNodeDockerfile(ServiceSpec service, ProjectAnalysis analysis) {
        return """
                # Multi-stage build for Node.js application
                FROM node:18-alpine AS builder

                WORKDIR /app

                # Copy package files
                COPY package*.json ./

                # Install dependencies
                RUN npm ci --only=production

                # Copy source code
                COPY . .

                # Build application if needed
                RUN npm run build 2>/dev/null || echo "No build script found"

                # Runtime stage
                FROM node:18-alpine AS runtime

                # Security: Create non-root user
                RUN addgroup -g 1001 -S appgroup && \\
                    adduser -u 1001 -S appuser -G appgroup

                WORKDIR /app

                # Security updates
                RUN apk update && \\
                    apk upgrade && \\
                    apk add --no-cache dumb-init curl && \\
                    rm -rf /var/cache/apk/*

                # Copy application
                COPY --from=builder --chown=appuser:appgroup /app/ ./

                USER appuser

                HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \\
                    CMD curl -f http://localhost:3000/health || exit 1

                EXPOSE 3000

                # Use dumb-init for proper signal handling
                ENTRYPOINT ["dumb-init", "--"]
                CMD ["node", "index.js"]
                """;
    }

    private String generatePythonDockerfile(ServiceSpec service, ProjectAnalysis analysis) {
        return """
                # Multi-stage build for Python application
                FROM python:3.11-alpine AS builder

                WORKDIR /app

                # Install build dependencies
                RUN apk add --no-cache gcc musl-dev linux-headers

                # Copy requirements
                COPY requirements.txt ./

                # Install Python dependencies
                RUN pip install --user --no-cache-dir -r requirements.txt

                # Runtime stage
                FROM python:3.11-alpine AS runtime

                # Security: Create non-root user
                RUN addgroup -g 1001 -S appgroup && \\
                    adduser -u 1001 -S appuser -G appgroup

                WORKDIR /app

                # Security updates
                RUN apk update && \\
                    apk upgrade && \\
                    apk add --no-cache curl && \\
                    rm -rf /var/cache/apk/*

                # Copy Python packages from builder
                COPY --from=builder --chown=appuser:appgroup /root/.local /home/appuser/.local

                # Copy application code
                COPY --chown=appuser:appgroup . .

                USER appuser

                # Update PATH to use user-local packages
                ENV PATH=/home/appuser/.local/bin:$PATH

                HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \\
                    CMD curl -f http://localhost:5000/health || exit 1

                EXPOSE 5000

                CMD ["python", "app.py"]
                """;
    }

    private String generateGenericDockerfile(ServiceSpec service, ProjectAnalysis analysis) {
        return """
                # Generic multi-stage Dockerfile
                FROM alpine:latest AS builder

                WORKDIR /app
                COPY . .

                # Add build steps here as needed

                # Runtime stage
                FROM alpine:latest AS runtime

                # Security: Create non-root user
                RUN addgroup -g 1001 -S appgroup && \\
                    adduser -u 1001 -S appuser -G appgroup

                WORKDIR /app

                # Security updates
                RUN apk update && \\
                    apk upgrade && \\
                    apk add --no-cache curl && \\
                    rm -rf /var/cache/apk/*

                COPY --from=builder --chown=appuser:appgroup /app/ ./

                USER appuser

                EXPOSE 8000

                CMD ["echo", "Configure this Dockerfile for your application"]
                """;
    }

    private List<ComposeSpec> generateComposeConfigurations(ProjectAnalysis analysis) {
        List<ComposeSpec> configs = new ArrayList<>();

        // Development docker-compose
        configs.add(generateDevelopmentCompose(analysis));

        // Production docker-compose
        configs.add(generateProductionCompose(analysis));

        // Testing docker-compose
        configs.add(generateTestingCompose(analysis));

        return configs;
    }

    private ComposeSpec generateDevelopmentCompose(ProjectAnalysis analysis) {
        ComposeSpec spec = new ComposeSpec();
        spec.path = "docker-compose.dev.yml";

        Map<String, Object> compose = new LinkedHashMap<>();
        compose.put("version", "3.8");

        Map<String, Object> services = new LinkedHashMap<>();

        // Main application service
        for (ServiceSpec service : analysis.services) {
            Map<String, Object> serviceConfig = new LinkedHashMap<>();
            serviceConfig.put(
                    "build", Map.of("context", service.buildContext, "target", "runtime"));
            serviceConfig.put("ports", List.of(service.port + ":" + service.port));
            serviceConfig.put("environment", generateEnvironmentVariables("development"));
            serviceConfig.put("volumes", List.of("./src:/app/src:ro"));
            serviceConfig.put("networks", List.of("app-network"));

            if (service.healthCheck != null) {
                serviceConfig.put("healthcheck", service.healthCheck);
            }

            services.put(service.name, serviceConfig);
        }

        // Add database if required
        if (analysis.requiresDatabase) {
            services.put("postgres", createPostgresService("development"));
            services.put("redis", createRedisService());
        }

        compose.put("services", services);
        compose.put("networks", Map.of("app-network", Map.of("driver", "bridge")));

        if (analysis.requiresDatabase) {
            compose.put(
                    "volumes",
                    Map.of(
                            "postgres_data", Map.of(),
                            "redis_data", Map.of()));
        }

        spec.content = convertToYaml(compose);
        return spec;
    }

    private ComposeSpec generateProductionCompose(ProjectAnalysis analysis) {
        ComposeSpec spec = new ComposeSpec();
        spec.path = "docker-compose.prod.yml";

        Map<String, Object> compose = new LinkedHashMap<>();
        compose.put("version", "3.8");

        Map<String, Object> services = new LinkedHashMap<>();

        // Application services
        for (ServiceSpec service : analysis.services) {
            Map<String, Object> serviceConfig = new LinkedHashMap<>();
            serviceConfig.put("image", "${REGISTRY:-}/" + service.name + ":${TAG:-latest}");
            serviceConfig.put("restart", "unless-stopped");
            serviceConfig.put("ports", List.of(service.port + ":" + service.port));
            serviceConfig.put("environment", generateEnvironmentVariables("production"));
            serviceConfig.put("networks", List.of("app-network"));
            serviceConfig.put(
                    "deploy",
                    Map.of(
                            "resources",
                                    Map.of(
                                            "limits", Map.of("cpus", "1.0", "memory", "1G"),
                                            "reservations",
                                                    Map.of("cpus", "0.5", "memory", "512M")),
                            "restart_policy",
                                    Map.of("condition", "on-failure", "max_attempts", 3)));

            if (service.healthCheck != null) {
                serviceConfig.put("healthcheck", service.healthCheck);
            }

            services.put(service.name, serviceConfig);
        }

        // Production database
        if (analysis.requiresDatabase) {
            services.put("postgres", createPostgresService("production"));
            services.put("redis", createRedisService());
        }

        // Reverse proxy
        services.put("nginx", createNginxService(analysis));

        compose.put("services", services);
        compose.put("networks", Map.of("app-network", Map.of("driver", "bridge")));
        compose.put(
                "volumes",
                Map.of(
                        "postgres_data", Map.of(),
                        "redis_data", Map.of(),
                        "nginx_certs", Map.of()));

        spec.content = convertToYaml(compose);
        return spec;
    }

    private ComposeSpec generateTestingCompose(ProjectAnalysis analysis) {
        ComposeSpec spec = new ComposeSpec();
        spec.path = "docker-compose.test.yml";

        Map<String, Object> compose = new LinkedHashMap<>();
        compose.put("version", "3.8");

        Map<String, Object> services = new LinkedHashMap<>();

        // Test service
        Map<String, Object> testService = new LinkedHashMap<>();
        testService.put("build", Map.of("context", ".", "target", "builder"));
        testService.put("environment", generateEnvironmentVariables("test"));
        testService.put("command", getTestCommand(analysis.buildTool));
        testService.put("networks", List.of("test-network"));

        services.put("test", testService);

        // Test database
        if (analysis.requiresDatabase) {
            services.put("test-postgres", createPostgresService("test"));
        }

        compose.put("services", services);
        compose.put("networks", Map.of("test-network", Map.of("driver", "bridge")));

        spec.content = convertToYaml(compose);
        return spec;
    }

    private Map<String, Object> createPostgresService(String environment) {
        Map<String, Object> postgres = new LinkedHashMap<>();
        postgres.put("image", "postgres:15-alpine");
        postgres.put("restart", "unless-stopped");
        postgres.put(
                "environment",
                Map.of(
                        "POSTGRES_DB", "${DB_NAME:-appdb}",
                        "POSTGRES_USER", "${DB_USER:-app}",
                        "POSTGRES_PASSWORD", "${DB_PASSWORD:-password}"));
        postgres.put("ports", List.of("5432:5432"));
        postgres.put("networks", List.of("app-network"));
        postgres.put("volumes", List.of("postgres_data:/var/lib/postgresql/data"));
        postgres.put(
                "healthcheck",
                Map.of(
                        "test",
                        List.of("CMD-SHELL", "pg_isready -U ${DB_USER:-app}"),
                        "interval",
                        "30s",
                        "timeout",
                        "10s",
                        "retries",
                        3));

        return postgres;
    }

    private Map<String, Object> createRedisService() {
        Map<String, Object> redis = new LinkedHashMap<>();
        redis.put("image", "redis:7-alpine");
        redis.put("restart", "unless-stopped");
        redis.put("ports", List.of("6379:6379"));
        redis.put("networks", List.of("app-network"));
        redis.put("volumes", List.of("redis_data:/data"));
        redis.put("command", "redis-server --appendonly yes");
        redis.put(
                "healthcheck",
                Map.of(
                        "test",
                        List.of("CMD", "redis-cli", "ping"),
                        "interval",
                        "30s",
                        "timeout",
                        "10s",
                        "retries",
                        3));

        return redis;
    }

    private Map<String, Object> createNginxService(ProjectAnalysis analysis) {
        Map<String, Object> nginx = new LinkedHashMap<>();
        nginx.put("image", "nginx:alpine");
        nginx.put("restart", "unless-stopped");
        nginx.put("ports", List.of("80:80", "443:443"));
        nginx.put("networks", List.of("app-network"));
        nginx.put(
                "volumes",
                List.of(
                        "./docker/nginx.conf:/etc/nginx/nginx.conf:ro",
                        "nginx_certs:/etc/nginx/certs"));
        nginx.put(
                "depends_on",
                analysis.services.stream().map(s -> s.name).collect(Collectors.toList()));

        return nginx;
    }

    private List<DeploymentScript> generateDeploymentScripts(ProjectAnalysis analysis) {
        List<DeploymentScript> scripts = new ArrayList<>();

        // Build script
        scripts.add(createBuildScript(analysis));

        // Deploy script
        scripts.add(createDeployScript(analysis));

        // Cleanup script
        scripts.add(createCleanupScript(analysis));

        return scripts;
    }

    private DeploymentScript createBuildScript(ProjectAnalysis analysis) {
        DeploymentScript script = new DeploymentScript();
        script.path = "scripts/build.sh";
        script.content =
                """
                #!/bin/bash
                set -e

                echo "🔨 Building Docker images..."

                # Build application images
                """
                        + analysis.services.stream()
                                .map(s -> "docker build -t " + s.name + ":latest " + s.buildContext)
                                .collect(Collectors.joining("\n"))
                        + """

                # Tag images with version if provided
                if [ ! -z "$TAG" ]; then
                """
                        + analysis.services.stream()
                                .map(s -> "  docker tag " + s.name + ":latest " + s.name + ":$TAG")
                                .collect(Collectors.joining("\n"))
                        + """
                fi

                echo "✅ Build completed successfully!"
                """;

        return script;
    }

    private DeploymentScript createDeployScript(ProjectAnalysis analysis) {
        DeploymentScript script = new DeploymentScript();
        script.path = "scripts/deploy.sh";
        script.content =
                """
                #!/bin/bash
                set -e

                ENVIRONMENT=${1:-development}

                echo "🚀 Deploying to $ENVIRONMENT environment..."

                # Load environment-specific variables
                if [ -f ".env.$ENVIRONMENT" ]; then
                    export $(cat .env.$ENVIRONMENT | xargs)
                fi

                # Deploy based on environment
                case $ENVIRONMENT in
                    "development")
                        docker-compose -f docker-compose.dev.yml up -d
                        ;;
                    "production")
                        docker-compose -f docker-compose.prod.yml up -d
                        ;;
                    "test")
                        docker-compose -f docker-compose.test.yml up --abort-on-container-exit
                        ;;
                    *)
                        echo "❌ Unknown environment: $ENVIRONMENT"
                        exit 1
                        ;;
                esac

                echo "✅ Deployment completed!"

                # Show status
                echo "📊 Container status:"
                docker-compose ps
                """;

        return script;
    }

    private DeploymentScript createCleanupScript(ProjectAnalysis analysis) {
        DeploymentScript script = new DeploymentScript();
        script.path = "scripts/cleanup.sh";
        script.content =
                """
                #!/bin/bash
                set -e

                echo "🧹 Cleaning up Docker resources..."

                # Stop and remove containers
                docker-compose -f docker-compose.dev.yml down --remove-orphans 2>/dev/null || true
                docker-compose -f docker-compose.prod.yml down --remove-orphans 2>/dev/null || true
                docker-compose -f docker-compose.test.yml down --remove-orphans 2>/dev/null || true

                # Remove unused images
                docker image prune -f

                # Remove unused volumes (optional - uncomment if needed)
                # docker volume prune -f

                echo "✅ Cleanup completed!"
                """;

        return script;
    }

    private MonitoringConfiguration generateMonitoringConfiguration(ProjectAnalysis analysis) {
        MonitoringConfiguration config = new MonitoringConfiguration();

        // Prometheus configuration
        Map<String, Object> prometheusConfig = new LinkedHashMap<>();
        prometheusConfig.put(
                "global",
                Map.of(
                        "scrape_interval", "15s",
                        "evaluation_interval", "15s"));

        List<Map<String, Object>> scrapeConfigs = new ArrayList<>();

        // Application metrics
        for (ServiceSpec service : analysis.services) {
            Map<String, Object> scrapeConfig = new LinkedHashMap<>();
            scrapeConfig.put("job_name", service.name);
            scrapeConfig.put(
                    "static_configs",
                    List.of(Map.of("targets", List.of("localhost:" + service.port))));
            scrapeConfigs.add(scrapeConfig);
        }

        prometheusConfig.put("scrape_configs", scrapeConfigs);
        config.prometheusConfig = convertToYaml(prometheusConfig);

        // Grafana dashboard (simplified)
        config.grafanaDashboard =
                """
                {
                  "dashboard": {
                    "title": "Application Monitoring",
                    "tags": ["yappc", "monitoring"],
                    "timezone": "browser",
                    "panels": [
                      {
                        "title": "Request Rate",
                        "type": "graph",
                        "targets": [
                          {
                            "expr": "rate(http_requests_total[5m])",
                            "legendFormat": "{{service}}"
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        return config;
    }

    // Utility methods

    private Map<String, String> generateEnvironmentVariables(String environment) {
        Map<String, String> env = new LinkedHashMap<>();

        switch (environment) {
            case "development" -> {
                env.put("ENVIRONMENT", "development");
                env.put("LOG_LEVEL", "DEBUG");
                env.put("DB_HOST", "postgres");
                env.put("REDIS_HOST", "redis");
            }
            case "production" -> {
                env.put("ENVIRONMENT", "production");
                env.put("LOG_LEVEL", "INFO");
                env.put("DB_HOST", "${DB_HOST}");
                env.put("REDIS_HOST", "${REDIS_HOST}");
            }
            case "test" -> {
                env.put("ENVIRONMENT", "test");
                env.put("LOG_LEVEL", "DEBUG");
                env.put("DB_HOST", "test-postgres");
            }
        }

        return env;
    }

    private String getTestCommand(String buildTool) {
        return switch (buildTool) {
            case "gradle" -> "./gradlew test";
            case "maven" -> "mvn test";
            case "npm" -> "npm test";
            case "python" -> "python -m pytest";
            default -> "echo 'Configure test command'";
        };
    }

    private String getProjectName() {
        return projectRoot.getFileName().toString().replaceAll("[^a-zA-Z0-9]", "");
    }

    private String detectServiceType() throws IOException {
        if (Files.exists(projectRoot.resolve("build.gradle"))) return "java-gradle";
        if (Files.exists(projectRoot.resolve("pom.xml"))) return "java-maven";
        if (Files.exists(projectRoot.resolve("package.json"))) return "node";
        if (Files.exists(projectRoot.resolve("requirements.txt"))) return "python";
        return "generic";
    }

    private int detectServicePort() {
        // Try to detect from configuration files, default to 8080
        return 8080;
    }

    private Map<String, String> detectDependencies() {
        // Simplified dependency detection
        return new HashMap<>();
    }

    private String detectBuildTool() throws IOException {
        if (Files.exists(projectRoot.resolve("build.gradle"))) return "gradle";
        if (Files.exists(projectRoot.resolve("pom.xml"))) return "maven";
        if (Files.exists(projectRoot.resolve("package.json"))) return "npm";
        if (Files.exists(projectRoot.resolve("requirements.txt"))) return "python";
        return "make";
    }

    private boolean hasTests() throws IOException {
        return Files.exists(projectRoot.resolve("src/test"))
                || Files.exists(projectRoot.resolve("test"))
                || Files.exists(projectRoot.resolve("tests"));
    }

    private boolean requiresDatabase() {
        // Simplified check - could be more sophisticated
        return true; // Assume most applications need a database
    }

    private boolean hasSubprojects() throws IOException {
        return Files.exists(projectRoot.resolve("settings.gradle"));
    }

    private List<ServiceSpec> detectSubprojectServices() {
        // Simplified - could parse settings.gradle for subprojects
        return Collections.emptyList();
    }

    private Map<String, Object> generateHealthCheck(String serviceType) {
        return Map.of(
                "test", List.of("CMD-SHELL", "curl -f http://localhost:8080/health || exit 1"),
                "interval", "30s",
                "timeout", "10s",
                "retries", 3,
                "start_period", "60s");
    }

    private String convertToYaml(Object data) {
        try {
            return yamlMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to YAML", e);
        }
    }

    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
                path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // Data classes

    public static class DockerConfiguration {
        public List<DockerfileSpec> dockerfiles;
        public List<ComposeSpec> composeConfigs;
        public List<DeploymentScript> deploymentScripts;
        public MonitoringConfiguration monitoringConfig;
    }

    public static class DockerfileSpec {
        public String path;
        public String content;
    }

    public static class ComposeSpec {
        public String path;
        public String content;
    }

    public static class DeploymentScript {
        public String path;
        public String content;
    }

    public static class MonitoringConfiguration {
        public String prometheusConfig;
        public String grafanaDashboard;
    }

    private static class ProjectAnalysis {
        public String projectName;
        public List<ServiceSpec> services;
        public Map<String, String> dependencies;
        public String buildTool;
        public boolean hasTests;
        public boolean requiresDatabase;
    }

    private static class ServiceSpec {
        public String name;
        public String type;
        public int port;
        public String buildContext;
        public Map<String, Object> healthCheck;
    }
}
