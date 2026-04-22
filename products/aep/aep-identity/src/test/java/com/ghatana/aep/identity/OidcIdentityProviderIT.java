package com.ghatana.aep.identity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.ghatana.identity.AgentIdentity;
import com.ghatana.platform.security.oauth2.OAuth2Config;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OidcIdentityProvider} against a mock OAuth2 IdP.
 *
 * @doc.type class
 * @doc.purpose Verify OIDC federation against a mock token-introspection endpoint
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("OidcIdentityProviderIT [GH-90000]")
class OidcIdentityProviderIT extends EventloopTestBase {

    @Test
    @DisplayName("resolve calls the configured introspection endpoint and returns a federated identity [GH-90000]")
    void resolveCallsMockIntrospectionEndpoint() throws Exception { // GH-90000
        AtomicReference<String> authorizationHeader = new AtomicReference<>(); // GH-90000
        AtomicReference<String> requestBody = new AtomicReference<>(); // GH-90000

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0); // GH-90000
        server.createContext("/oauth2/introspect", new StaticJsonHandler(exchange -> { // GH-90000
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization [GH-90000]"));
            requestBody.set(readBody(exchange)); // GH-90000
            return "{" +
                "\"active\":true," +
                "\"sub\":\"oidc-subject-1\"," +
                "\"scope\":\"aep:execute aep:capability:routing\"" +
                "}";
        }));
        server.start(); // GH-90000

        try {
            int port = server.getAddress().getPort(); // GH-90000
            OAuth2Config config = OAuth2Config.builder() // GH-90000
                .clientId("aep-client [GH-90000]")
                .clientSecret("aep-secret [GH-90000]")
                .tokenEndpoint(URI.create("http://127.0.0.1:" + port + "/oauth2/introspect")) // GH-90000
                .authorizationEndpoint(URI.create("http://127.0.0.1:" + port + "/oauth2/authorize")) // GH-90000
                .redirectUri(URI.create("http://localhost/internal/aep/oidc/callback [GH-90000]"))
                .issuerUri(URI.create("https://issuer.example.com [GH-90000]"))
                .build(); // GH-90000

            OidcIdentityProvider provider = new OidcIdentityProvider( // GH-90000
                config,
                List.of(new OidcIdentityProvider.FederatedAgentRegistration( // GH-90000
                    "tenant-a",
                    "agent-1",
                    "oidc-subject-1",
                    "opaque-access-token",
                    Set.of("aep:capability:routing [GH-90000]"))));

            Optional<AgentIdentity> identity = runPromise(() -> provider.resolve("tenant-a", "agent-1")); // GH-90000

            assertThat(identity).isPresent(); // GH-90000
            assertThat(identity.orElseThrow().spiffeId()) // GH-90000
                .isEqualTo("https://issuer.example.com/subject/oidc-subject-1 [GH-90000]");
            assertThat(identity.orElseThrow().scopes()) // GH-90000
                .contains("aep:execute", "aep:capability:routing"); // GH-90000
            assertThat(authorizationHeader.get()) // GH-90000
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("aep-client:aep-secret".getBytes(StandardCharsets.UTF_8))); // GH-90000
            assertThat(requestBody.get()).contains("token=opaque-access-token [GH-90000]");
        } finally {
            server.stop(0); // GH-90000
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException { // GH-90000
        try (InputStream requestBody = exchange.getRequestBody()) { // GH-90000
            return new String(requestBody.readAllBytes(), StandardCharsets.UTF_8); // GH-90000
        }
    }

    private static final class StaticJsonHandler implements HttpHandler {
        private final ResponseFactory responseFactory;

        private StaticJsonHandler(ResponseFactory responseFactory) { // GH-90000
            this.responseFactory = responseFactory;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException { // GH-90000
            String response = responseFactory.create(exchange); // GH-90000
            byte[] payload = response.getBytes(StandardCharsets.UTF_8); // GH-90000
            exchange.getResponseHeaders().add("Content-Type", "application/json"); // GH-90000
            exchange.sendResponseHeaders(200, payload.length); // GH-90000
            try (OutputStream responseBody = exchange.getResponseBody()) { // GH-90000
                responseBody.write(payload); // GH-90000
            }
        }
    }

    @FunctionalInterface
    private interface ResponseFactory {
        String create(HttpExchange exchange) throws IOException; // GH-90000
    }
}