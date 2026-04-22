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
    private static final String TEST_SCHEMA_JSON = "test.schema.json";
    private static final String TEST_JSON = "test.json";

    @TempDir Path tempDir;
    private Path schemaDir;
    private NodeBridge mockNodeBridge;
    private JsonYamlCodemods codemods;
    private PolyfixProjectContext mockContext;
    private Logger mockLogger;
    private ExecutorService executor;

    @BeforeEach
    void setUp() { // GH-90000
        schemaDir = tempDir.resolve("schemas [GH-90000]");

        mockNodeBridge = mock(NodeBridge.class); // GH-90000
        when(mockNodeBridge.executeScript(anyString(), anyString(), anyString())) // GH-90000
                .thenReturn(new NodeBridge.Result(0, "", "")); // GH-90000

        mockContext = mock(PolyfixProjectContext.class); // GH-90000
        mockLogger = mock(Logger.class); // GH-90000
        when(mockContext.log()).thenReturn(mockLogger); // GH-90000
        executor = Executors.newSingleThreadExecutor(); // GH-90000
        when(mockContext.exec()).thenReturn(executor); // GH-90000

        codemods = new JsonYamlCodemods(mockContext, mockNodeBridge); // GH-90000
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() { // GH-90000
        executor.shutdownNow(); // GH-90000
    }

    @Test
    void testEmptyFilesList() { // GH-90000
        List<UnifiedDiagnostic> results = runPromise(() -> codemods.normalizeAndValidate(List.of(), schemaDir)); // GH-90000
        assertThat(results).isEmpty(); // GH-90000
        verifyNoInteractions(mockNodeBridge); // GH-90000
    }

    @Test
    void testNonExistentFile() { // GH-90000
        Path nonExistent = tempDir.resolve("nonexistent.json [GH-90000]");
        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(nonExistent), schemaDir)); // GH-90000

        assertThat(results).hasSize(1); // GH-90000
        assertThat(results.get(0).getMessage()).contains("File not found: " + nonExistent); // GH-90000
    }

    @Test
    void testSchemaValidationSuccess() throws Exception { // GH-90000
        // Create a test JSON file
        Path jsonFile = tempDir.resolve(TEST_JSON); // GH-90000
        Files.writeString(jsonFile, "{\"name\": \"test\"}"); // GH-90000

        // Create a matching schema
        Path schemaFile = schemaDir.resolve(TEST_SCHEMA_JSON); // GH-90000
        Files.createDirectories(schemaDir); // GH-90000
        Files.writeString(schemaFile, "{\"type\": \"object\"}"); // GH-90000

        // Mock successful validation
        when(mockNodeBridge.executeScript( // GH-90000
                        "/bridges/ajv/ajv-validate.js", schemaFile.toString(), jsonFile.toString())) // GH-90000
                .thenReturn(new NodeBridge.Result(0, "", "")); // GH-90000

        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir)); // GH-90000

        assertThat(results).isEmpty(); // GH-90000
    }

    @Test
    void testSchemaValidationError() throws Exception { // GH-90000
        // Create a test JSON file
        Path jsonFile = tempDir.resolve(TEST_JSON); // GH-90000
        Files.writeString(jsonFile, "{\"name\": 123}"); // GH-90000

        // Create a schema that expects a string
        Path schemaFile = schemaDir.resolve(TEST_SCHEMA_JSON); // GH-90000
        Files.createDirectories(schemaDir); // GH-90000
        Files.writeString( // GH-90000
                schemaFile,
                "{\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}");

        // Mock validation error
        String errorOutput =
                "[{\"instancePath\":\"/name\",\"schemaPath\":\"#/properties/name/type\",\"keyword\":\"type\",\"params\":{\"type\":\"string\"},\"message\":\"must"
                    + " be string\"}]";

        when(mockNodeBridge.executeScript( // GH-90000
                        "/bridges/ajv/ajv-validate.js", schemaFile.toString(), jsonFile.toString())) // GH-90000
                .thenReturn(new NodeBridge.Result(0, errorOutput, "")); // GH-90000

        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir)); // GH-90000

        assertThat(results).hasSize(1); // GH-90000
        // The actual message includes the error details from the Node.js script
        assertThat(results.get(0).getMessage()).contains("must be string [GH-90000]");
    }

    @Test
    void testNodeBridgeError() throws Exception { // GH-90000
        // Create a test JSON file
        Path jsonFile = tempDir.resolve(TEST_JSON); // GH-90000
        Files.writeString(jsonFile, "{}"); // GH-90000

        // Create a schema file
        Path schemaFile = schemaDir.resolve(TEST_SCHEMA_JSON); // GH-90000
        Files.createDirectories(schemaDir); // GH-90000
        Files.writeString(schemaFile, "{\"type\": \"object\"}"); // GH-90000

        // Mock Node.js error
        when(mockNodeBridge.executeScript( // GH-90000
                        "/bridges/ajv/ajv-validate.js", schemaFile.toString(), jsonFile.toString())) // GH-90000
                .thenReturn(new NodeBridge.Result(1, "", "Invalid schema")); // GH-90000

        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir)); // GH-90000

        assertThat(results).hasSize(1); // GH-90000
        assertThat(results.get(0).getMessage()) // GH-90000
                .contains("Schema validation failed: Invalid schema [GH-90000]");
    }

    @Test
    void testSchemaDiscovery() throws Exception { // GH-90000
        // Test finding schemas with different extensions
        Files.createDirectories(schemaDir); // GH-90000

        // Create test files
        Path jsonFile = tempDir.resolve(TEST_JSON); // GH-90000
        Files.writeString(jsonFile, "{}"); // GH-90000

        // Create schemas with different extensions
        Path schemaJson = schemaDir.resolve(TEST_SCHEMA_JSON); // GH-90000
        Path schemaYaml = schemaDir.resolve("test.schema.yaml [GH-90000]");

        Files.writeString(schemaJson, "{}"); // GH-90000

        // Test finding .json schema
        List<UnifiedDiagnostic> results =
                runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir)); // GH-90000

        ArgumentCaptor<String> schemaPathCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
        verify(mockNodeBridge).executeScript(anyString(), schemaPathCaptor.capture(), anyString()); // GH-90000

        assertThat(schemaPathCaptor.getValue()).endsWith(TEST_SCHEMA_JSON); // GH-90000

        // Test finding .yaml schema when .json doesn't exist
        Files.delete(schemaJson); // GH-90000
        Files.writeString(schemaYaml, "type: object"); // GH-90000

        results = runPromise(() -> codemods.normalizeAndValidate(List.of(jsonFile), schemaDir)); // GH-90000

        verify(mockNodeBridge, times(2)) // GH-90000
                .executeScript(anyString(), schemaPathCaptor.capture(), anyString()); // GH-90000

        assertThat(schemaPathCaptor.getValue()).endsWith("test.schema.yaml [GH-90000]");
    }
}
