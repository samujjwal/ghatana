package com.ghatana.yappc.adapters;

import com.ghatana.yappc.core.graph.TaskNode;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.TemplateEngine;
import com.ghatana.yappc.core.error.TemplateException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gradle Groovy DSL adapter that generates settings.gradle and version catalog
 * (libs.versions.toml). Implements Day 11 requirements for Gradle 8.x with version catalog support.
 
 * @doc.type class
 * @doc.purpose Handles gradle groovy adapter operations
 * @doc.layer core
 * @doc.pattern Adapter
*/
public final class GradleGroovyAdapter implements ProjectAdapter {
    private final TemplateEngine templateEngine;

    public GradleGroovyAdapter(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    // Default constructor for service loading
    public GradleGroovyAdapter() {
        this(null);
    }

    @Override
    public String id() {
        return "gradle-groovy";
    }

    @Override
    public List<TaskNode> describeTasks(WorkspaceSpec spec) {
        return List.of(
                new TaskNode(id(), "compileJava", List.of()),
                new TaskNode(id(), "compileTestJava", List.of("compileJava")),
                new TaskNode(id(), "test", List.of("compileTestJava")),
                new TaskNode(id(), "build", List.of("test")),
                new TaskNode(id(), "spotlessCheck", List.of("compileJava")),
                new TaskNode(id(), "jacocoTestReport", List.of("test")),
                new TaskNode(id(), "publish", List.of("build")));
    }

    /**
     * Generates Gradle build configuration files from workspace specification. Creates
     * settings.gradle and gradle/libs.versions.toml with proper module declarations.
     */
    public void generateBuildFiles(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        if (templateEngine == null) {
            throw new IllegalStateException("Template engine not available");
        }

        // Create gradle directory if it doesn't exist
        Path gradleDir = targetDirectory.resolve("gradle");
        Files.createDirectories(gradleDir);

        // Generate settings.gradle
        generateSettingsGradle(spec, targetDirectory);

        // Generate root build.gradle from template
        String buildTemplate = loadTemplate("gradle/build.gradle.hbs");
        String buildContent = templateEngine.render(buildTemplate, createTemplateContext(spec));
        Path buildFile = targetDirectory.resolve("build.gradle");
        Files.writeString(buildFile, buildContent);

        // Ensure a basic gradle.properties exists at the root so downstream tooling and tests can
        // find it
        Path gradleProperties = targetDirectory.resolve("gradle.properties");
        if (!Files.exists(gradleProperties)) {
            String props = "org.gradle.jvmargs=-Xmx2g\norg.gradle.parallel=true\n";
            Files.writeString(gradleProperties, props);
        }

        // Generate version catalog
        generateVersionCatalog(spec, gradleDir);
    }

    private void generateSettingsGradle(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createTemplateContext(spec);

        // Load and render settings.gradle template
        String template = loadTemplate("gradle/settings.gradle.hbs");
        String settingsContent = templateEngine.render(template, context);
        Path settingsFile = targetDirectory.resolve("settings.gradle");
        Files.writeString(settingsFile, settingsContent);
    }

    private void generateVersionCatalog(WorkspaceSpec spec, Path gradleDir)
            throws IOException, TemplateException {
        Map<String, Object> context = createVersionCatalogContext(spec);

        // Load and render libs.versions.toml template
        String template = loadTemplate("gradle/libs.versions.toml.hbs");
        String catalogContent = templateEngine.render(template, context);
        Path catalogFile = gradleDir.resolve("libs.versions.toml");
        Files.writeString(catalogFile, catalogContent);
    }

    private String loadTemplate(String templateName) throws IOException {
        var classLoader = getClass().getClassLoader();
        try (var inputStream = classLoader.getResourceAsStream("templates/" + templateName)) {
            if (inputStream == null) {
                throw new IOException("Template not found: " + templateName);
            }
            return new String(inputStream.readAllBytes());
        }
    }

    private Map<String, Object> createTemplateContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();
        context.put("projectName", spec.getName());
        context.put("mode", spec.getMode());

        // Generate module includes for Week 1 scaffolding compatibility
        if ("monorepo".equals(spec.getMode())) {
            StringBuilder moduleIncludes = new StringBuilder();
            List<String> modules = List.of("cli", "core", "adapters", "packs", "schemas");
            for (String module : modules) {
                moduleIncludes.append("include ':").append(module).append("'\n");
            }
            context.put("moduleIncludes", moduleIncludes.toString().trim());

            // Monorepo-specific settings
            String monorepoSettings =
                    """
                // Enable type-safe project accessors for multi-module builds
                enableFeaturePreview('TYPESAFE_PROJECT_ACCESSORS')

                // Configure build cache for faster builds
                buildCache {
                    local {
                        enabled = true
                    }
                }""";
            context.put("monorepoSettings", monorepoSettings);
        } else {
            context.put("moduleIncludes", "");
            context.put("monorepoSettings", "");
        }

        return context;
    }

    private Map<String, Object> createVersionCatalogContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();

        // Pre-generate version section for Java service pack requirements (Doc2 §1)
        StringBuilder versionsSection = new StringBuilder();
        versionsSection.append("java = \"21\"\n");
        versionsSection.append("gradle = \"8.14\"\n");
        versionsSection.append("activej = \"6.0\"\n");
        versionsSection.append("jackson = \"2.17.0\"\n");
        versionsSection.append("lombok = \"1.18.30\"\n");
        versionsSection.append("junit = \"5.10.1\"\n");
        versionsSection.append("mockito = \"5.8.0\"\n");
        versionsSection.append("assertj = \"3.24.2\"\n");
        versionsSection.append("awaitility = \"4.2.0\"\n");
        versionsSection.append("testcontainers = \"1.19.3\"\n");
        versionsSection.append("flyway = \"10.4.1\"\n");
        versionsSection.append("hikaricp = \"5.1.0\"\n");
        versionsSection.append("micrometer = \"1.12.1\"\n");
        versionsSection.append("opentelemetry = \"1.34.1\"\n");
        versionsSection.append("spotless = \"6.23.3\"\n");
        versionsSection.append("jacoco = \"0.8.11\"");
        context.put("versions", versionsSection.toString());

        // Pre-generate libraries section
        StringBuilder librariesSection = new StringBuilder();
        librariesSection.append(
                "activejHttp = { module = \"io.activej:activej-http\", version.ref = \"activej\""
                        + " }\n");
        librariesSection.append(
                "activejInject = { module = \"io.activej:activej-inject\", version.ref ="
                        + " \"activej\" }\n");
        librariesSection.append(
                "jacksonCore = { module = \"com.fasterxml.jackson.core:jackson-core\", version.ref"
                        + " = \"jackson\" }\n");
        librariesSection.append(
                "jacksonDatabind = { module = \"com.fasterxml.jackson.core:jackson-databind\","
                        + " version.ref = \"jackson\" }\n");
        librariesSection.append(
                "lombok = { module = \"org.projectlombok:lombok\", version.ref = \"lombok\" }\n");
        librariesSection.append(
                "junitJupiter = { module = \"org.junit.jupiter:junit-jupiter\", version.ref ="
                        + " \"junit\" }\n");
        librariesSection.append(
                "mockitoCore = { module = \"org.mockito:mockito-core\", version.ref = \"mockito\""
                        + " }\n");
        librariesSection.append(
                "assertjCore = { module = \"org.assertj:assertj-core\", version.ref = \"assertj\""
                        + " }\n");
        librariesSection.append(
                "awaitility = { module = \"org.awaitility:awaitility\", version.ref ="
                        + " \"awaitility\" }\n");
        librariesSection.append(
                "testcontainersJupiter = { module = \"org.testcontainers:junit-jupiter\","
                        + " version.ref = \"testcontainers\" }\n");
        librariesSection.append(
                "testcontainersPostgresql = { module = \"org.testcontainers:postgresql\","
                        + " version.ref = \"testcontainers\" }\n");
        librariesSection.append(
                "flywayCore = { module = \"org.flywaydb:flyway-core\", version.ref = \"flyway\""
                        + " }\n");
        librariesSection.append(
                "hikaricp = { module = \"com.zaxxer:HikariCP\", version.ref = \"hikaricp\" }\n");
        librariesSection.append(
                "micrometerCore = { module = \"io.micrometer:micrometer-core\", version.ref ="
                        + " \"micrometer\" }\n");
        librariesSection.append(
                "micrometerPrometheus = { module ="
                        + " \"io.micrometer:micrometer-registry-prometheus\", version.ref ="
                        + " \"micrometer\" }\n");
        librariesSection.append(
                "opentelemetryApi = { module = \"io.opentelemetry:opentelemetry-api\", version.ref"
                        + " = \"opentelemetry\" }\n");
        librariesSection.append(
                "opentelemetryExporter = { module ="
                        + " \"io.opentelemetry:opentelemetry-exporter-otlp\", version.ref ="
                        + " \"opentelemetry\" }");
        context.put("libraries", librariesSection.toString());

        // Pre-generate plugins section
        StringBuilder pluginsSection = new StringBuilder();
        pluginsSection.append(
                "spotless = { id = \"com.diffplug.spotless\", version.ref = \"spotless\" }\n");
        pluginsSection.append("jacoco = { id = \"jacoco\" }");
        context.put("plugins", pluginsSection.toString());

        // Pre-generate bundles section
        StringBuilder bundlesSection = new StringBuilder();
        bundlesSection.append("activej = [\n  \"activejHttp\",\n  \"activejInject\",\n]\n");
        bundlesSection.append("jackson = [\n  \"jacksonCore\",\n  \"jacksonDatabind\",\n]\n");
        bundlesSection.append(
                "testing = [\n"
                        + "  \"junitJupiter\",\n"
                        + "  \"mockitoCore\",\n"
                        + "  \"assertjCore\",\n"
                        + "  \"awaitility\",\n"
                        + "]\n");
        bundlesSection.append(
                "testcontainers = [\n"
                        + "  \"testcontainersJupiter\",\n"
                        + "  \"testcontainersPostgresql\",\n"
                        + "]\n");
        bundlesSection.append(
                "micrometer = [\n  \"micrometerCore\",\n  \"micrometerPrometheus\",\n]\n");
        bundlesSection.append(
                "opentelemetry = [\n  \"opentelemetryApi\",\n  \"opentelemetryExporter\",\n]");
        context.put("bundles", bundlesSection.toString());

        return context;
    }
}
