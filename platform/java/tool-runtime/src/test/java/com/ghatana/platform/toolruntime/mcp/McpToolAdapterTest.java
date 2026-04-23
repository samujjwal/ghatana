/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.mcp;

import com.ghatana.agent.framework.governance.ActionClass;
import com.ghatana.agent.framework.tools.ToolContract;
import com.ghatana.agent.framework.tools.ToolContractBuilder;
import com.ghatana.agent.framework.tools.ToolExecutionEnvelope;
import com.ghatana.agent.framework.tools.ToolExecutionResult;
import com.ghatana.agent.framework.tools.ToolExecutionStatus;
import com.ghatana.agent.framework.tools.ToolTransport;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link McpToolAdapter} using WireMock to simulate an MCP server.
 */
@DisplayName("McpToolAdapter")
class McpToolAdapterTest extends EventloopTestBase {

    private static WireMockServer wireMock;
    private static String baseUrl;

    /** Blocking executor shared across all tests (pools HTTP calls off the event loop). */ // GH-90000
    private static final Executor HTTP_EXECUTOR = Executors.newCachedThreadPool(); // GH-90000
    private static final HttpClient HTTP_CLIENT  = HttpClient.newHttpClient(); // GH-90000

    @BeforeAll
    static void startWireMock() { // GH-90000
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()); // GH-90000
        wireMock.start(); // GH-90000
        baseUrl = "http://localhost:" + wireMock.port(); // GH-90000
    }

    @AfterAll
    static void stopWireMock() { // GH-90000
        if (wireMock != null) { // GH-90000
            wireMock.stop(); // GH-90000
        }
    }

    @BeforeEach
    void reset() { // GH-90000
        wireMock.resetAll(); // GH-90000
    }

    /** Creates a transport that makes real HTTP calls to the WireMock server. */
    private static McpHttpTransport realTransport() { // GH-90000
        return (url, body, headers) -> Promise.ofBlocking(HTTP_EXECUTOR, () -> { // GH-90000
            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .uri(URI.create(url)) // GH-90000
                    .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                    .header("Content-Type", "application/json") // GH-90000
                    .build(); // GH-90000
            return HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).body(); // GH-90000
        });
    }

    private static ToolExecutionEnvelope envelope(String toolId) { // GH-90000
        return ToolExecutionEnvelope.of(toolId, "1.0.0", "agent-x", null, "tenant-1", // GH-90000
                ActionClass.CALL_EXTERNAL, "1.0", Map.of("query", "hello")); // GH-90000
    }

    private static ToolContract contract(String toolName, String endpoint) { // GH-90000
        return new ToolContractBuilder() // GH-90000
                .toolId(toolName) // GH-90000
                .name(toolName) // GH-90000
                .actionClass(ActionClass.CALL_EXTERNAL) // GH-90000
                .transport(ToolTransport.MCP) // GH-90000
                .remoteEndpoint(endpoint) // GH-90000
                .build(); // GH-90000
    }

    // ─── Success scenario ──────────────────────────────────────────────────

    @Nested
    @DisplayName("successful tool response")
    class Success {

        @Test
        @DisplayName("returns SUCCESS status with tool output text")
        void successfulToolResponse() { // GH-90000
            wireMock.stubFor(post(urlEqualTo("/mcp"))
                    .willReturn(aResponse() // GH-90000
                            .withStatus(200) // GH-90000
                            .withHeader("Content-Type", "application/json") // GH-90000
                            .withBody("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\"," // GH-90000
                                    + "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Hello from MCP\"}]}}")));

            McpToolAdapter adapter = new McpToolAdapter(realTransport()); // GH-90000
            ToolExecutionEnvelope e = envelope("web-search");
            ToolContract c = contract("web-search", baseUrl + "/mcp"); // GH-90000

            ToolExecutionResult result = runPromise(() -> adapter.handle(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
            assertThat(result.output()).isEqualTo("Hello from MCP");
        }
    }

    // ─── Error scenario ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("MCP error response")
    class McpError {

        @Test
        @DisplayName("returns FAILED status with error message on MCP error response")
        void mcpErrorResponse() { // GH-90000
            wireMock.stubFor(post(urlEqualTo("/mcp"))
                    .willReturn(aResponse() // GH-90000
                            .withStatus(200) // GH-90000
                            .withHeader("Content-Type", "application/json") // GH-90000
                            .withBody("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\"," // GH-90000
                                    + "\"error\":{\"code\":-32601,\"message\":\"Tool not found\"}}")));

            McpToolAdapter adapter = new McpToolAdapter(realTransport()); // GH-90000
            ToolContract c = contract("bad-tool", baseUrl + "/mcp"); // GH-90000

            ToolExecutionResult result = runPromise(() -> adapter.handle(envelope("bad-tool"), c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); // GH-90000
            assertThat(result.errorMessage()) // GH-90000
                    .contains("-32601")
                    .contains("Tool not found");
        }
    }

    // ─── Timeout / transport failure ─────────────────────────────────────────

    @Nested
    @DisplayName("transport failure")
    class TransportFailure {

        @Test
        @DisplayName("returns FAILED status when transport promise fails with a connection error")
        void transportException_producesFailedResult() { // GH-90000
            // Use a transport that always fails
            McpHttpTransport failingTransport =
                    (url, body, headers) -> Promise.ofException(new RuntimeException("connection refused"));

            McpToolAdapter adapter = new McpToolAdapter(failingTransport); // GH-90000
            ToolContract c = contract("any-tool", baseUrl + "/mcp"); // GH-90000
            ToolExecutionEnvelope e = envelope("any-tool");

            ToolExecutionResult result = runPromise(() -> adapter.handle(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); // GH-90000
            assertThat(result.errorMessage()).contains("connection refused");
        }

        @Test
        @DisplayName("returns TIMEOUT status when transport promise fails with a timeout error")
        void transportTimeout_producesTimeoutResult() { // GH-90000
            McpHttpTransport timeoutTransport =
                    (url, body, headers) -> Promise.ofException(new RuntimeException("Read timed out"));

            McpToolAdapter adapter = new McpToolAdapter(timeoutTransport); // GH-90000
            ToolContract c = contract("slow-tool", baseUrl + "/mcp"); // GH-90000

            ToolExecutionResult result = runPromise(() -> adapter.handle(envelope("slow-tool"), c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.TIMEOUT); // GH-90000
        }
    }

    // ─── Missing endpoint ────────────────────────────────────────────────────

    @Nested
    @DisplayName("missing remote endpoint")
    class MissingEndpoint {

        @Test
        @DisplayName("returns DENIED when ToolContract has no remoteEndpoint")
        void noEndpointReturnsDenied() { // GH-90000
            McpToolAdapter adapter = new McpToolAdapter(realTransport()); // GH-90000
            ToolContract c = new ToolContractBuilder() // GH-90000
                    .toolId("no-endpoint-tool").name("no-endpoint-tool")
                    .actionClass(ActionClass.CALL_EXTERNAL) // GH-90000
                    .transport(ToolTransport.MCP) // GH-90000
                    // remoteEndpoint intentionally not set
                    .build(); // GH-90000

            ToolExecutionResult result = runPromise(() -> adapter.handle(envelope("no-endpoint-tool"), c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED); // GH-90000
            assertThat(result.errorMessage()).contains("remoteEndpoint");
        }
    }

    // ─── McpToolRequest / McpToolResponse unit coverage ─────────────────────

    @Nested
    @DisplayName("McpToolRequest serialization")
    class RequestSerialization {

        @Test
        @DisplayName("toJson produces JSON-RPC 2.0 payload")
        void toJsonFormat() { // GH-90000
            McpToolRequest req = McpToolRequest.of("id-1", "web-search", Map.of("query", "test")); // GH-90000
            String json = req.toJson(); // GH-90000

            assertThat(json).contains("\"jsonrpc\":\"2.0\""); // GH-90000
            assertThat(json).contains("\"id\":\"id-1\""); // GH-90000
            assertThat(json).contains("\"method\":\"tools/call\""); // GH-90000
            assertThat(json).contains("\"name\":\"web-search\""); // GH-90000
            assertThat(json).contains("\"query\":\"test\""); // GH-90000
        }

        @Test
        @DisplayName("blank toolName throws IllegalArgumentException")
        void blankToolNameThrows() { // GH-90000
            org.assertj.core.api.Assertions.assertThatThrownBy( // GH-90000
                    () -> McpToolRequest.of("id-1", "  ", Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("McpToolResponse parsing")
    class ResponseParsing {

        @Test
        @DisplayName("parse extracts text content from MCP success response")
        void parseSuccessResponse() { // GH-90000
            String json = "{\"jsonrpc\":\"2.0\",\"id\":\"r1\","
                    + "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"output\"}]}}";
            McpToolResponse resp = McpToolResponse.parse("r1", json); // GH-90000
            assertThat(resp.success()).isTrue(); // GH-90000
            assertThat(resp.content()).isEqualTo("output");
        }

        @Test
        @DisplayName("parse extracts code and message from MCP error response")
        void parseErrorResponse() { // GH-90000
            String json = "{\"jsonrpc\":\"2.0\",\"id\":\"r2\","
                    + "\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
            McpToolResponse resp = McpToolResponse.parse("r2", json); // GH-90000
            assertThat(resp.success()).isFalse(); // GH-90000
            assertThat(resp.errorCode()).isEqualTo(-32601); // GH-90000
            assertThat(resp.errorMessage()).isEqualTo("Method not found");
        }

        @Test
        @DisplayName("parse handles empty response string")
        void parseEmptyResponse() { // GH-90000
            McpToolResponse resp = McpToolResponse.parse("r3", ""); // GH-90000
            assertThat(resp.success()).isFalse(); // GH-90000
            assertThat(resp.errorCode()).isEqualTo(-32700); // GH-90000
        }
    }
}
