package com.ghatana.yappc.services.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Verifies that ProcessTsExtractorWorker enforces strict contract — no legacy field aliases accepted
 * @doc.layer test
 * @doc.pattern ContractTest
 */
@DisplayName("ProcessTsExtractorWorker Contract Tests")
class ProcessTsExtractorWorkerContractTest extends EventloopTestBase {

    private ProcessTsExtractorWorker worker;
    private java.lang.reflect.Method validateMethod;

    @BeforeEach
    void setUp() throws Exception {
      worker = new ProcessTsExtractorWorker(new ObjectMapper(), Runnable::run, "echo", 5);
        validateMethod = ProcessTsExtractorWorker.class.getDeclaredMethod(
            "validateExtractionResponseSchema", com.fasterxml.jackson.databind.JsonNode.class);
        validateMethod.setAccessible(true);
    }

    @Test
    @DisplayName("redacts credentials from worker diagnostics")
    void redactsCredentialsFromWorkerDiagnostics() {
        String output = """
            Authorization: Bearer github_pat_1234567890abcdefghijklmnopqrstuvwxyz
            token=ghp_1234567890abcdefghijklmnopqrstuvwxyz
            api_key=plain-secret-value
            """;

        String redacted = ProcessTsExtractorWorker.redactWorkerOutput(output);

        assertThat(redacted).doesNotContain("github_pat_1234567890abcdefghijklmnopqrstuvwxyz");
        assertThat(redacted).doesNotContain("ghp_1234567890abcdefghijklmnopqrstuvwxyz");
        assertThat(redacted).doesNotContain("plain-secret-value");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    @DisplayName("rejects edge with legacy 'source' alias instead of 'sourceNodeId'")
    void rejectsLegacySourceAlias() throws Exception {
        String json = """
            {
              "nodes": [
                {"id": "n1", "type": "class", "name": "Foo"},
                {"id": "n2", "type": "class", "name": "Bar"}
              ],
              "edges": [
                {"source": "n1", "targetNodeId": "n2", "relationshipType": "USES"}
              ],
              "residualIslands": [
                {
                  "id": "r1",
                  "islandType": "css_module",
                  "originalSource": ".x{}",
                  "sourceLocation": {"filePath": "src/App.css"},
                  "checksum": "sha256:1",
                  "rawFragmentRef": "blob:1"
                }
              ]
            }
            """;
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
        assertThatThrownBy(() -> invokeValidate(node))
          .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sourceNodeId");
    }

    @Test
    @DisplayName("rejects edge with legacy 'target' alias instead of 'targetNodeId'")
    void rejectsLegacyTargetAlias() throws Exception {
        String json = """
            {
              "nodes": [
                {"id": "n1", "type": "class", "name": "Foo"},
                {"id": "n2", "type": "class", "name": "Bar"}
              ],
              "edges": [
                {"sourceNodeId": "n1", "target": "n2", "relationshipType": "USES"}
              ],
              "residualIslands": [
                {
                  "id": "r1",
                  "islandType": "css_module",
                  "originalSource": ".x{}",
                  "sourceLocation": {"filePath": "src/App.css"},
                  "checksum": "sha256:1",
                  "rawFragmentRef": "blob:1"
                }
              ]
            }
            """;
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
        assertThatThrownBy(() -> invokeValidate(node))
          .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("targetNodeId");
    }

    @Test
    @DisplayName("rejects edge with legacy 'type' alias instead of 'relationshipType'")
    void rejectsLegacyTypeAlias() throws Exception {
        String json = """
            {
              "nodes": [
                {"id": "n1", "type": "class", "name": "Foo"},
                {"id": "n2", "type": "class", "name": "Bar"}
              ],
              "edges": [
                {"sourceNodeId": "n1", "targetNodeId": "n2", "type": "USES"}
              ],
              "residualIslands": [
                {
                  "id": "r1",
                  "islandType": "css_module",
                  "originalSource": ".x{}",
                  "sourceLocation": {"filePath": "src/App.css"},
                  "checksum": "sha256:1",
                  "rawFragmentRef": "blob:1"
                }
              ]
            }
            """;
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
        assertThatThrownBy(() -> invokeValidate(node))
          .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("relationshipType");
    }

    @Test
    @DisplayName("rejects edge whose sourceNodeId is not in declared nodes")
    void rejectsEdgeWithUndeclaredSourceNode() throws Exception {
        String json = """
            {
              "nodes": [{"id": "n1", "type": "class", "name": "Foo"}],
              "edges": [
                {"sourceNodeId": "GHOST", "targetNodeId": "n1", "relationshipType": "USES"}
              ],
              "residualIslands": [
                {
                  "id": "r1",
                  "islandType": "css_module",
                  "originalSource": ".x{}",
                  "sourceLocation": {"filePath": "src/App.css"},
                  "checksum": "sha256:1",
                  "rawFragmentRef": "blob:1"
                }
              ]
            }
            """;
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
        assertThatThrownBy(() -> invokeValidate(node))
          .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unresolvedEdges");
    }

    @Test
    @DisplayName("rejects residualIsland missing sourceSpan")
    void rejectsResidualIslandMissingSourceSpan() throws Exception {
        String json = """
            {
              "nodes": [],
              "edges": [],
              "residualIslands": [
                {"id": "r1", "islandType": "imperative_logic"}
              ]
            }
            """;
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
        assertThatThrownBy(() -> invokeValidate(node))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("originalSource");
    }

    @Test
    @DisplayName("accepts a fully compliant response")
    void acceptsCompliantResponse() throws Exception {
        String json = """
            {
              "nodes": [
                {"id": "n1", "type": "class", "name": "Foo"},
                {"id": "n2", "type": "class", "name": "Bar"}
              ],
              "edges": [
                {"sourceNodeId": "n1", "targetNodeId": "n2", "relationshipType": "EXTENDS"}
              ],
              "residualIslands": [
                {
                  "id": "r1",
                  "islandType": "css_module",
                  "originalSource": ".btn { color: red; }",
                  "sourceLocation": {"filePath": "src/App.css", "startLine": 1, "startColumn": 0},
                  "checksum": "sha256:abc",
                  "rawFragmentRef": "blob:abc"
                }
              ]
            }
            """;
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
        // Should not throw
        invokeValidate(node);
    }

    private void invokeValidate(com.fasterxml.jackson.databind.JsonNode node) throws Exception {
        try {
            validateMethod.invoke(worker, node);
        } catch (java.lang.reflect.InvocationTargetException e) {
        Throwable target = e.getTargetException();
        if (target instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        throw new RuntimeException(target);
        }
    }
}
