package com.ghatana.platform.security.port;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verify canonical JWT provider factories expose working port instances
 * @doc.layer platform
 * @doc.pattern Unit Test
 */
@DisplayName("JwtTokenProviders Tests [GH-90000]")
class JwtTokenProvidersTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("fromSharedSecret should return a working JwtTokenProvider port [GH-90000]")
    void fromSharedSecretShouldReturnWorkingPort() { // GH-90000
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(SECRET, 60_000L); // GH-90000

        String token = provider.createToken("user-123", List.of("USER [GH-90000]"), Map.of("tenantId", "tenant-a"));

        assertThat(provider.validateToken(token)).isTrue(); // GH-90000
        assertThat(provider.getUserIdFromToken(token)).contains("user-123 [GH-90000]");
        assertThat(provider.extractClaims(token)).hasValueSatisfying( // GH-90000
            claims -> assertThat(claims).containsEntry("tenantId", "tenant-a") // GH-90000
        );
    }

    @Test
    @DisplayName("fromJwksUrl should validate tokens signed by the published key set [GH-90000]")
    void fromJwksUrlShouldValidatePublishedKeys() throws Exception { // GH-90000
        try (RsaJwksFixture fixture = RsaJwksFixture.create()) { // GH-90000
            JwtTokenProvider provider = JwtTokenProviders.fromJwksUrl(fixture.jwksUrl()); // GH-90000
            String token = fixture.createToken("user-jwks", List.of("VIEWER [GH-90000]"), Map.of("tenant_id", "tenant-a"));

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(token)).contains("user-jwks [GH-90000]");
            assertThat(provider.getRolesFromToken(token)).containsExactly("VIEWER [GH-90000]");
            assertThat(provider.extractClaims(token)).hasValueSatisfying( // GH-90000
                claims -> assertThat(claims).containsEntry("tenant_id", "tenant-a") // GH-90000
            );
        }
    }

    private static final class RsaJwksFixture implements AutoCloseable {
        private final RSAKey rsaKey;
        private final HttpServer server;

        private RsaJwksFixture(RSAKey rsaKey, HttpServer server) { // GH-90000
            this.rsaKey = rsaKey;
            this.server = server;
        }

        static RsaJwksFixture create() throws Exception { // GH-90000
            RSAKey rsaKey = new RSAKeyGenerator(2048) // GH-90000
                .keyID("kid-" + UUID.randomUUID()) // GH-90000
                .generate(); // GH-90000
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0); // GH-90000
            String jwks = new JWKSet(rsaKey.toPublicJWK()).toString(); // GH-90000
            server.createContext("/jwks", exchange -> { // GH-90000
                byte[] body = jwks.getBytes(); // GH-90000
                exchange.getResponseHeaders().set("Content-Type", "application/json"); // GH-90000
                exchange.sendResponseHeaders(200, body.length); // GH-90000
                try (OutputStream outputStream = exchange.getResponseBody()) { // GH-90000
                    outputStream.write(body); // GH-90000
                }
            });
            server.start(); // GH-90000
            return new RsaJwksFixture(rsaKey, server); // GH-90000
        }

        String jwksUrl() { // GH-90000
            return "http://localhost:" + server.getAddress().getPort() + "/jwks"; // GH-90000
        }

        String createToken(String userId, List<String> roles, Map<String, Object> additionalClaims) // GH-90000
                throws JOSEException {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder() // GH-90000
                .subject(userId) // GH-90000
                .claim("roles", roles) // GH-90000
                .issueTime(new java.util.Date()) // GH-90000
                .expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000L)); // GH-90000
            additionalClaims.forEach(claimsBuilder::claim); // GH-90000

            SignedJWT signedJwt = new SignedJWT( // GH-90000
                new JWSHeader.Builder(JWSAlgorithm.RS256) // GH-90000
                    .keyID(rsaKey.getKeyID()) // GH-90000
                    .type(JOSEObjectType.JWT) // GH-90000
                    .build(), // GH-90000
                claimsBuilder.build()); // GH-90000
            signedJwt.sign(new RSASSASigner(rsaKey.toPrivateKey())); // GH-90000
            return signedJwt.serialize(); // GH-90000
        }

        @Override
        public void close() { // GH-90000
            server.stop(0); // GH-90000
        }
    }
}
