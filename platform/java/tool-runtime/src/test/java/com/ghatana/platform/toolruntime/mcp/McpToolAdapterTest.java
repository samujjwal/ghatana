/*
 * Copyright (c) 2026 Ghatana Inc.
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

    /** Blocking executor shared across all tests (pools HTTP calls off the event loop). */
    private static final Executor HTTP_EXECUTOR = Executors.newCachedThreadPool();
    private static final HttpClient HTTP_CLIENT  = HttpClient.newHttpClient();

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    /** Creates a transport that makes real HTTP calls to the WireMock server. */
    private static McpHttpTransport realTransport() {
        return (url, body, headers) -> Promise.ofBlocking(HTTP_EXECUTOR, () -> {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();
            return HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).body();
        });
    }

    private static ToolExecutionEnvelope envelope(String toolId) {
        return ToolExecutionEnvelope.of(toolId, "1.0.0", "agent-x", null, "tenant-1",
                ActionClass.CALL_EXTERNAL, "1.0", Map.of("query", "hello"));
    }

    private static ToolContract contract(String toolName, String endpoint) {
        return new ToolContractBuilder()
                .toolId(toolName)
                .name(toolName)
                .actionClass(ActionClass.CALL_EXTERNAL)
                .transport(ToolTransport.MCP)
                .remoteEndpoint(endpoint)
                .build();
    }

    // ─── Success scenario ──────────────────────────────────────────────────

    @Nested
    @DisplayName("successful tool response")
    class Success {

        @Test
        @DisplayName("returns SUCCESS status with tool output text")
        void successfulToolResponse() {
            wireMock.stubFor(post(urlEqualTo("/mcp"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\","
                                    + "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Hello from MCP\"}]}}")));

            McpToolAdapter adapter = new McpToolAdapter(realTransport());
            ToolExecutionEnvelope e = envelope("web-search");
            ToolContract c = contract("web-search", baseUrl + "/mcp");

            ToolExecutionResult result = runPromise(() -> adapter.handle(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
            assertThat(result.output()).isEqualTo("Hello from MCP");
        }
    }

    // ─── Error scenario ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("MCP error response")
    class McpError {

        @Test
        @DisplayName("returns FAILED status with error message on MCP error response")
        void mcpErrorResponse() {
            wireMock.stubFor(post(urlEqualTo("/mcp"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\","
                                    + "\"error\":{\"code\":-32601,\"message\":\"Tool not found\"}}")));

            McpToolAdapter adapter = new McpToolAdapter(realTransport());
            ToolContract c = contract("bad-tool", baseUrl + "/mcp");

            ToolExecutionResult result = runPromise(() -> adapter.handle(envelope("bad-tool"), c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED);
            assertThat(result.errorMessage())
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
        void transportException_producesFailedResult() {
            // Use a transport that always fails
            McpHttpTransport failingTransport =
                    (url, body, headers) -> Promise.ofException(new RuntimeException("connection refused"));

            McpToolAdapter adapter = new McpToolAdapter(failingTransport);
            ToolContract c = contract("any-tool", baseUrl + "/mcp");
            ToolExecutionEnvelope e = envelope("any-tool");

            ToolExecutionResult result = runPromise(() -> adapter.handle(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED);
            assertThat(result.errorMessage()).contains("connection refused");
        }

        @Test
        @DisplayName("returns TIMEOUT status when transport promise fails with a timeout error")
        void transportTimeout_producesTimeoutResult() {
            McpHttpTransport timeoutTransport =
                    (url, body, headers) -> Promise.ofException(new RuntimeException("Read timed out"));

            McpToolAdapter adapter = new McpToolAdapter(timeoutTransport);
            ToolContract c = contract("slow-tool", baseUrl + "/mcp");

            ToolExecutionResult result = runPromise(() -> adapter.handle(envelope("slow-tool"), c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.TIMEOUT);
        }
    }

    // ─── Missing endpoint ────────────────────────────────────────────────────

    @Nested
    @DisplayName("missing remote endpoint")
    class MissingEndpoint {

        @Test
        @DisplayName("returns DENIED when ToolContract has no remoteEndpoint")
        void noEndpointReturnsDenied() {
            McpToolAdapter adapter = new McpToolAdapter(realTransport());
            ToolContract c = new ToolContractBuilder()
                    .toolId("no-endpoint-tool").name("no-endpoint-tool")
                    .actionClass(ActionClass.CALL_EXTERNAL)
                    .transport(ToolTransport.MCP)
                    // remoteEndpoint intentionally not set
                    .build();

            ToolExecutionResult result = runPromise(() -> adapter.handle(envelope("no-endpoint-tool"), c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
            assertThat(result.errorMessage()).contains("remoteEndpoint");
        }
    }

    // ─── McpToolRequest / McpToolResponse unit coverage ─────────────────────

    @Nested
    @DisplayName("McpToolRequest serialization")
    class RequestSerialization {

        @Test
        @DisplayName("toJson produces JSON-RPC 2.0 payload")
        void toJsonFormat() {
            McpToolRequest req = McpToolRequest.of("id-1", "web-search", Map.of("query", "test"));
            String json = req.toJson();

            assertThat(json).contains("\"jsonrpc\":\"2.0\"");
            assertThat(json).contains("\"id\":\"id-1\"");
            assertThat(json).contains("\"method\":\"tools/call\"");
            assertThat(json).contains("\"name\":\"web-search\"");
            assertThat(json).contains("\"query\":\"test\"");
        }

        @Test
        @DisplayName("blank toolName throws IllegalArgumentException")
        void blankToolNameThrows() {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> McpToolRequest.of("id-1", "  ", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("McpToolResponse parsing")
    class ResponseParsing {

        @Test
        @DisplayName("parse extracts text content from MCP success response")
        void parseSuccessResponse() {
            String json = "{\"jsonrpc\":\"2.0\",\"id\":\"r1\","
                    + "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"output\"}]}}";
            McpToolResponse resp = McpToolResponse.parse("r1", json);
            assertThat(resp.success()).isTrue();
            assertThat(resp.content()).isEqualTo("output");
        }

        @Test
        @DisplayName("parse extracts code and message from MCP error response")
        void parseErrorResponse() {
            String json = "{\"jsonrpc\":\"2.0\",\"id\":\"r2\","
                    + "\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
            McpToolResponse resp = McpToolResponse.parse("r2", json);
            assertThat(resp.success()).isFalse();
            assertThat(resp.errorCode()).isEqualTo(-32601);
            assertThat(resp.errorMessage()).isEqualTo("Method not found");
        }

        @Test
        @DisplayName("parse handles empty response string")
        void parseEmptyResponse() {
            McpToolResponse resp = McpToolResponse.parse("r3", "");
            assertThat(resp.success()).isFalse();
            assertThat(resp.errorCode()).isEqualTo(-32700);
        }
    }
}
