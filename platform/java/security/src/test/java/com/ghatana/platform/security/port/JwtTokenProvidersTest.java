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
@DisplayName("JwtTokenProviders Tests")
class JwtTokenProvidersTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("fromSharedSecret should return a working JwtTokenProvider port")
    void fromSharedSecretShouldReturnWorkingPort() { 
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(SECRET, 60_000L); 

        String token = provider.createToken("user-123", List.of("USER"), Map.of("tenantId", "tenant-a"));

        assertThat(provider.validateToken(token)).isTrue(); 
        assertThat(provider.getUserIdFromToken(token)).contains("user-123");
        assertThat(provider.extractClaims(token)).hasValueSatisfying( 
            claims -> assertThat(claims).containsEntry("tenantId", "tenant-a") 
        );
    }

    @Test
    @DisplayName("fromJwksUrl should validate tokens signed by the published key set")
    void fromJwksUrlShouldValidatePublishedKeys() throws Exception { 
        try (RsaJwksFixture fixture = RsaJwksFixture.create()) { 
            JwtTokenProvider provider = JwtTokenProviders.fromJwksUrl(fixture.jwksUrl()); 
            String token = fixture.createToken("user-jwks", List.of("VIEWER"), Map.of("tenant_id", "tenant-a"));

            assertThat(provider.validateToken(token)).isTrue(); 
            assertThat(provider.getUserIdFromToken(token)).contains("user-jwks");
            assertThat(provider.getRolesFromToken(token)).containsExactly("VIEWER");
            assertThat(provider.extractClaims(token)).hasValueSatisfying( 
                claims -> assertThat(claims).containsEntry("tenant_id", "tenant-a") 
            );
        }
    }

    private static final class RsaJwksFixture implements AutoCloseable {
        private final RSAKey rsaKey;
        private final HttpServer server;

        private RsaJwksFixture(RSAKey rsaKey, HttpServer server) { 
            this.rsaKey = rsaKey;
            this.server = server;
        }

        static RsaJwksFixture create() throws Exception { 
            RSAKey rsaKey = new RSAKeyGenerator(2048) 
                .keyID("kid-" + UUID.randomUUID()) 
                .generate(); 
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0); 
            String jwks = new JWKSet(rsaKey.toPublicJWK()).toString(); 
            server.createContext("/jwks", exchange -> { 
                byte[] body = jwks.getBytes(); 
                exchange.getResponseHeaders().set("Content-Type", "application/json"); 
                exchange.sendResponseHeaders(200, body.length); 
                try (OutputStream outputStream = exchange.getResponseBody()) { 
                    outputStream.write(body); 
                }
            });
            server.start(); 
            return new RsaJwksFixture(rsaKey, server); 
        }

        String jwksUrl() { 
            return "http://localhost:" + server.getAddress().getPort() + "/jwks"; 
        }

        String createToken(String userId, List<String> roles, Map<String, Object> additionalClaims) 
                throws JOSEException {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder() 
                .subject(userId) 
                .claim("roles", roles) 
                .issueTime(new java.util.Date()) 
                .expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000L)); 
            additionalClaims.forEach(claimsBuilder::claim); 

            SignedJWT signedJwt = new SignedJWT( 
                new JWSHeader.Builder(JWSAlgorithm.RS256) 
                    .keyID(rsaKey.getKeyID()) 
                    .type(JOSEObjectType.JWT) 
                    .build(), 
                claimsBuilder.build()); 
            signedJwt.sign(new RSASSASigner(rsaKey.toPrivateKey())); 
            return signedJwt.serialize(); 
        }

        @Override
        public void close() { 
            server.stop(0); 
        }
    }
}
