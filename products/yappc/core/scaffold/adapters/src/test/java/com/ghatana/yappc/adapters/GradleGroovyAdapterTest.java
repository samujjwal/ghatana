package com.ghatana.yappc.adapters;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.yappc.core.graph.TaskNode;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for GradleGroovyAdapter implementing Day 11 requirements. Week 3 Day 11: Gradle adapter
 * (settings.gradle, version catalog).
 
 * @doc.type class
 * @doc.purpose Handles gradle groovy adapter test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class GradleGroovyAdapterTest {

    private GradleGroovyAdapter adapter;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        // Use SimpleTemplateEngine for testing
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        adapter = new GradleGroovyAdapter(templateEngine);
    }

    @Test
    void shouldReturnCorrectId() {
        assertEquals("gradle-groovy", adapter.id());
    }

    @Test
    void shouldDescribeTasksForMonorepo() {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-project");

        List<TaskNode> tasks = adapter.describeTasks(spec);

        assertFalse(tasks.isEmpty());
        assertTrue(tasks.stream().anyMatch(task -> "build".equals(task.taskId())));
        assertTrue(tasks.stream().anyMatch(task -> "test".equals(task.taskId())));
        assertTrue(tasks.stream().anyMatch(task -> "spotlessCheck".equals(task.taskId())));
        assertTrue(tasks.stream().anyMatch(task -> "jacocoTestReport".equals(task.taskId())));
    }

    @Test
    void shouldGenerateSettingsGradleForMonorepo() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-monorepo");

        adapter.generateBuildFiles(spec, tempDir);

        Path settingsFile = tempDir.resolve("settings.gradle");
        assertTrue(Files.exists(settingsFile));

        String content = Files.readString(settingsFile);
        assertNotNull(content);
        assertTrue(content.contains("rootProject.name = 'test-monorepo'"));
        assertTrue(content.contains("include ':cli'"));
        assertTrue(content.contains("include ':core'"));
        assertTrue(content.contains("include ':adapters'"));
        assertTrue(content.contains("include ':packs'"));
        assertTrue(content.contains("include ':schemas'"));
        assertTrue(content.contains("enableFeaturePreview('TYPESAFE_PROJECT_ACCESSORS')"));
        assertTrue(content.contains("buildCache"));
    }

    @Test
    void shouldGenerateVersionCatalog() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-project");

        adapter.generateBuildFiles(spec, tempDir);

        Path gradleDir = tempDir.resolve("gradle");
        Path catalogFile = gradleDir.resolve("libs.versions.toml");
        assertTrue(Files.exists(catalogFile));

        String content = Files.readString(catalogFile);
        assertNotNull(content);

        // Verify version definitions (Doc2 §1 Java service pack requirements)
        assertTrue(content.contains("java = \"21\""));
        assertTrue(content.contains("gradle = \"8.14\""));
        assertTrue(content.contains("activej = \"6.0\""));
        assertTrue(content.contains("jackson = \"2.17.0\""));
        assertTrue(content.contains("junit = \"5.10.1\""));
        assertTrue(content.contains("testcontainers = \"1.19.3\""));
        assertTrue(content.contains("micrometer = \"1.12.1\""));
        assertTrue(content.contains("opentelemetry = \"1.34.1\""));
        assertTrue(content.contains("spotless = \"6.23.3\""));
        assertTrue(content.contains("jacoco = \"0.8.11\""));

        // Verify library definitions
        assertTrue(content.contains("activejHttp = { module = \"io.activej:activej-http\""));
        assertTrue(
                content.contains(
                        "jacksonCore = { module = \"com.fasterxml.jackson.core:jackson-core\""));
        assertTrue(
                content.contains("junitJupiter = { module = \"org.junit.jupiter:junit-jupiter\""));

        // Verify bundle definitions
        assertTrue(content.contains("activej = ["));
        assertTrue(content.contains("testing = ["));
        assertTrue(content.contains("micrometer = ["));
    }

    @Test
    void shouldCreateGradleDirectory() throws Exception {
        WorkspaceSpec spec = new WorkspaceSpec("single-project", "single");

        adapter.generateBuildFiles(spec, tempDir);

        Path gradleDir = tempDir.resolve("gradle");
        assertTrue(Files.exists(gradleDir));
        assertTrue(Files.isDirectory(gradleDir));
    }

    @Test
    void shouldHandleNullTemplateEngine() {
        GradleGroovyAdapter adapterWithoutEngine = new GradleGroovyAdapter();
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test");

        assertThrows(
                IllegalStateException.class,
                () -> adapterWithoutEngine.generateBuildFiles(spec, tempDir));
    }
}
