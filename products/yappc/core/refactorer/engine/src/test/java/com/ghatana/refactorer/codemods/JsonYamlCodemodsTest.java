package com.ghatana.refactorer.codemods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.ghatana.refactorer.diagnostics.jsonyaml.NodeBridge;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**

 * @doc.type class

 * @doc.purpose Handles json yaml codemods test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class JsonYamlCodemodsTest extends EventloopTestBase {
    @TempDir Path tempDir;
    private Path schemaDir;
    private NodeBridge mockNodeBridge;
    private JsonYamlCodemods codemods;
    private PolyfixProjectContext mockContext;
    private Logger mockLogger;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        schemaDir = tempDir.resolve("schemas");

        mockNodeBridge = mock(NodeBridge.class);
        when(mockNodeBridge.executeScript(anyString(), anyString(), anyString()))
                .thenReturn(new NodeBridge.Result(0, "", ""));

        mockContext = mock(PolyfixProjectContext.class);
        mockLogger = mock(Logger.class);
        when(mockContext.log()).thenReturn(mockLogger);
        executor = Executors.newSingleThreadExecutor();
        when(mockContext.exec()).thenReturn(executor);

        codemods = new JsonYamlCodemods(mockContext, mockNodeBridge);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void testEmptyFilesList() {
        List<UnifiedDiagnostic> results = runPromise(() -> codemods.normalizeAndValidate(List.of(), schemaDir));
        assertThat(results).isEmpty();
        verifyNoInteractions(mockNodeBridge);
    }

    @Test
    void testNonExistentFile() {
        Path nonExistent = tempDir.resolve("nonexistent.json");
        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(nonExistent), schemaDir));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage()).contains("File not found: " + nonExistent);
    }

    @Test
    void testSchemaValidationSuccess() throws Exception {
        // Create a test JSON file
        Path jsonFile = tempDir.resolve("test.json");
        Files.writeString(jsonFile, "{\"name\": \"test\"}");

        // Create a matching schema
        Path schemaFile = schemaDir.resolve("test.schema.json");
        Files.createDirectories(schemaDir);
        Files.writeString(schemaFile, "{\"type\": \"object\"}");

        // Mock successful validation
        when(mockNodeBridge.executeScript(
                        "/bridges/ajv/ajv-validate.js", schemaFile.toString(), jsonFile.toString()))
                .thenReturn(new NodeBridge.Result(0, "", ""));

        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir));

        assertThat(results).isEmpty();
    }

    @Test
    void testSchemaValidationError() throws Exception {
        // Create a test JSON file
        Path jsonFile = tempDir.resolve("test.json");
        Files.writeString(jsonFile, "{\"name\": 123}");

        // Create a schema that expects a string
        Path schemaFile = schemaDir.resolve("test.schema.json");
        Files.createDirectories(schemaDir);
        Files.writeString(
                schemaFile,
                "{\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}");

        // Mock validation error
        String errorOutput =
                "[{\"instancePath\":\"/name\",\"schemaPath\":\"#/properties/name/type\",\"keyword\":\"type\",\"params\":{\"type\":\"string\"},\"message\":\"must"
                    + " be string\"}]";

        when(mockNodeBridge.executeScript(
                        "/bridges/ajv/ajv-validate.js", schemaFile.toString(), jsonFile.toString()))
                .thenReturn(new NodeBridge.Result(0, errorOutput, ""));

        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir));

        assertThat(results).hasSize(1);
        // The actual message includes the error details from the Node.js script
        assertThat(results.get(0).getMessage()).contains("must be string");
    }

    @Test
    void testNodeBridgeError() throws Exception {
        // Create a test JSON file
        Path jsonFile = tempDir.resolve("test.json");
        Files.writeString(jsonFile, "{}");

        // Create a schema file
        Path schemaFile = schemaDir.resolve("test.schema.json");
        Files.createDirectories(schemaDir);
        Files.writeString(schemaFile, "{\"type\": \"object\"}");

        // Mock Node.js error
        when(mockNodeBridge.executeScript(
                        "/bridges/ajv/ajv-validate.js", schemaFile.toString(), jsonFile.toString()))
                .thenReturn(new NodeBridge.Result(1, "", "Invalid schema"));

        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage())
                .contains("Schema validation failed: Invalid schema");
    }

    @Test
    void testSchemaDiscovery() throws Exception {
        // Test finding schemas with different extensions
        Files.createDirectories(schemaDir);

        // Create test files
        Path jsonFile = tempDir.resolve("test.json");
        Files.writeString(jsonFile, "{}");

        // Create schemas with different extensions
        Path schemaJson = schemaDir.resolve("test.schema.json");
        Path schemaYaml = schemaDir.resolve("test.schema.yaml");

        Files.writeString(schemaJson, "{}");

        // Test finding .json schema
        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir));

        ArgumentCaptor<String> schemaPathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockNodeBridge).executeScript(anyString(), schemaPathCaptor.capture(), anyString());

        assertThat(schemaPathCaptor.getValue()).endsWith("test.schema.json");

        // Test finding .yaml schema when .json doesn't exist
        Files.delete(schemaJson);
        Files.writeString(schemaYaml, "type: object");

        results = runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir));

        verify(mockNodeBridge, times(2))
                .executeScript(anyString(), schemaPathCaptor.capture(), anyString());

        assertThat(schemaPathCaptor.getValue()).endsWith("test.schema.yaml");
    }
}
