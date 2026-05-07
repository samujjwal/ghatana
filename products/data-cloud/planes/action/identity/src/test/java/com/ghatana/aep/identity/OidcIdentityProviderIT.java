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
@DisplayName("OidcIdentityProviderIT")
class OidcIdentityProviderIT extends EventloopTestBase {

    @Test
    @DisplayName("resolve calls the configured introspection endpoint and returns a federated identity")
    void resolveCallsMockIntrospectionEndpoint() throws Exception { 
        AtomicReference<String> authorizationHeader = new AtomicReference<>(); 
        AtomicReference<String> requestBody = new AtomicReference<>(); 

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0); 
        server.createContext("/oauth2/introspect", new StaticJsonHandler(exchange -> { 
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readBody(exchange)); 
            return "{" +
                "\"active\":true," +
                "\"sub\":\"oidc-subject-1\"," +
                "\"scope\":\"aep:execute aep:capability:routing\"" +
                "}";
        }));
        server.start(); 

        try {
            int port = server.getAddress().getPort(); 
            OAuth2Config config = OAuth2Config.builder() 
                .clientId("aep-client")
                .clientSecret("aep-secret")
                .tokenEndpoint(URI.create("http://127.0.0.1:" + port + "/oauth2/introspect")) 
                .authorizationEndpoint(URI.create("http://127.0.0.1:" + port + "/oauth2/authorize")) 
                .redirectUri(URI.create("http://localhost/internal/aep/oidc/callback"))
                .issuerUri(URI.create("https://issuer.example.com"))
                .build(); 

            OidcIdentityProvider provider = new OidcIdentityProvider( 
                config,
                List.of(new OidcIdentityProvider.FederatedAgentRegistration( 
                    "tenant-a",
                    "agent-1",
                    "oidc-subject-1",
                    "opaque-access-token",
                    Set.of("aep:capability:routing"))));

            Optional<AgentIdentity> identity = runPromise(() -> provider.resolve("tenant-a", "agent-1")); 

            assertThat(identity).isPresent(); 
            assertThat(identity.orElseThrow().spiffeId()) 
                .isEqualTo("https://issuer.example.com/subject/oidc-subject-1");
            assertThat(identity.orElseThrow().scopes()) 
                .contains("aep:execute", "aep:capability:routing"); 
            assertThat(authorizationHeader.get()) 
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("aep-client:aep-secret".getBytes(StandardCharsets.UTF_8))); 
            assertThat(requestBody.get()).contains("token=opaque-access-token");
        } finally {
            server.stop(0); 
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException { 
        try (InputStream requestBody = exchange.getRequestBody()) { 
            return new String(requestBody.readAllBytes(), StandardCharsets.UTF_8); 
        }
    }

    private static final class StaticJsonHandler implements HttpHandler {
        private final ResponseFactory responseFactory;

        private StaticJsonHandler(ResponseFactory responseFactory) { 
            this.responseFactory = responseFactory;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException { 
            String response = responseFactory.create(exchange); 
            byte[] payload = response.getBytes(StandardCharsets.UTF_8); 
            exchange.getResponseHeaders().add("Content-Type", "application/json"); 
            exchange.sendResponseHeaders(200, payload.length); 
            try (OutputStream responseBody = exchange.getResponseBody()) { 
                responseBody.write(payload); 
            }
        }
    }

    @FunctionalInterface
    private interface ResponseFactory {
        String create(HttpExchange exchange) throws IOException; 
    }
}