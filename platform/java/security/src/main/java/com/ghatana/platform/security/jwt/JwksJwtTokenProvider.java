package com.ghatana.platform.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose JWT verification provider backed by a remote JWKS endpoint
 * @doc.layer platform
 * @doc.pattern Provider
 */
public final class JwksJwtTokenProvider implements com.ghatana.platform.security.port.JwtTokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(JwksJwtTokenProvider.class);
    private static final Duration JWKS_CACHE_TTL = Duration.ofMinutes(5);

    private final URI jwksUri;
    private final HttpClient httpClient;
    private volatile CachedJwkSet cachedJwkSet;

    public JwksJwtTokenProvider(String jwksUrl) {
        try {
            this.jwksUri = URI.create(jwksUrl);
            this.httpClient = HttpClient.newHttpClient();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid JWKS URL", exception);
        }
    }

    @Override
    public String createToken(String userId, List<String> roles, Map<String, Object> additionalClaims) {
        throw new UnsupportedOperationException("JWKS-backed JWT providers do not support token creation");
    }

    @Override
    public boolean validateToken(String token) {
        return parseClaims(token).isPresent();
    }

    @Override
    public Optional<String> getUserIdFromToken(String token) {
        return parseClaims(token).map(JWTClaimsSet::getSubject);
    }

    @Override
    public List<String> getRolesFromToken(String token) {
        return parseClaims(token)
            .map(claims -> {
                Object roles = claims.getClaim("roles");
                if (roles instanceof List<?> roleList) {
                    return roleList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .collect(Collectors.toList());
                }
                return List.<String>of();
            })
            .orElseGet(List::of);
    }

    @Override
    public Optional<Map<String, Object>> extractClaims(String token) {
        return parseClaims(token).map(JWTClaimsSet::getClaims);
    }

    private Optional<JWTClaimsSet> parseClaims(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!verifySignature(signedJwt)) {
                logger.warn("Invalid JWT signature from JWKS provider");
                return Optional.empty();
            }

            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                logger.warn("Expired JWT token from JWKS provider");
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (ParseException | JOSEException exception) {
            logger.warn("Failed to validate JWT via JWKS provider", exception);
            return Optional.empty();
        }
    }

    private boolean verifySignature(SignedJWT signedJwt) throws JOSEException {
        String keyId = signedJwt.getHeader().getKeyID();
        Optional<RSAKey> cachedKey = findKey(loadJwkSet(false), keyId);
        if (cachedKey.isPresent() && signedJwt.verify(new RSASSAVerifier(cachedKey.orElseThrow()))) {
            return true;
        }

        Optional<RSAKey> refreshedKey = findKey(loadJwkSet(true), keyId);
        return refreshedKey.isPresent() && signedJwt.verify(new RSASSAVerifier(refreshedKey.orElseThrow()));
    }

    private JWKSet loadJwkSet(boolean forceRefresh) {
        CachedJwkSet current = cachedJwkSet;
        if (!forceRefresh && current != null && current.expiresAt().isAfter(Instant.now())) {
            return current.jwkSet();
        }

        synchronized (this) {
            CachedJwkSet latest = cachedJwkSet;
            if (!forceRefresh && latest != null && latest.expiresAt().isAfter(Instant.now())) {
                return latest.jwkSet();
            }
            JWKSet jwkSet = fetchJwkSet();
            cachedJwkSet = new CachedJwkSet(jwkSet, Instant.now().plus(JWKS_CACHE_TTL));
            return jwkSet;
        }
    }

    private JWKSet fetchJwkSet() {
        HttpRequest request = HttpRequest.newBuilder(jwksUri)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("JWKS endpoint returned status " + response.statusCode());
            }
            return JWKSet.parse(response.body());
        } catch (IOException | InterruptedException | ParseException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to fetch JWKS from " + jwksUri, exception);
        }
    }

    private Optional<RSAKey> findKey(JWKSet jwkSet, String keyId) {
        return jwkSet.getKeys().stream()
            .filter(jwk -> keyId == null || keyId.equals(jwk.getKeyID()))
            .map(this::asRsaKey)
            .flatMap(Optional::stream)
            .findFirst();
    }

    private Optional<RSAKey> asRsaKey(JWK jwk) {
        if (jwk instanceof RSAKey rsaKey) {
            try {
                if (rsaKey.toRSAPublicKey() != null) {
                    return Optional.of(rsaKey);
                }
            } catch (JOSEException exception) {
                logger.warn("Skipping unusable RSA key from JWKS set", exception);
            }
        }
        return Optional.empty();
    }

    private record CachedJwkSet(JWKSet jwkSet, Instant expiresAt) {
    }
}