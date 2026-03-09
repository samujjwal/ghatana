package com.ghatana.virtualorg.security.impl;

import com.ghatana.virtualorg.security.AuthenticationResult;
import com.ghatana.virtualorg.security.AuthenticationService;
import com.ghatana.virtualorg.security.Principal;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JWT-based authentication service implementation.
 *
 * <p>Provides JWT token generation and validation for virtual organization security.
 * Uses HMAC with SHA-256 for token signing.
 *
 * @doc.type class
 * @doc.purpose JWT authentication implementation
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JWTAuthenticationService implements AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(JWTAuthenticationService.class);

    private final Eventloop eventloop;
    private final SecretKey secretKey;
    private final long defaultExpirySeconds;
    private final Set<String> revokedTokens = new HashSet<>();

    /**
     * Creates a JWT authentication service.
     *
     * @param eventloop the ActiveJ eventloop
     * @param secret the secret key for signing tokens (must be at least 256 bits)
     * @param defaultExpirySeconds default token expiry time in seconds
     */
    public JWTAuthenticationService(Eventloop eventloop, String secret, long defaultExpirySeconds) {
        this.eventloop = eventloop;
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.defaultExpirySeconds = defaultExpirySeconds;
        LOG.info("JWT authentication service initialized with {} second default expiry", defaultExpirySeconds);
    }

    @Override
    @NotNull
    public Promise<AuthenticationResult> authenticate(@NotNull String token) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                if (revokedTokens.contains(token)) {
                    LOG.debug("Token authentication failed: token is revoked");
                    return new AuthenticationResult(
                        false,
                        null,
                        "Token has been revoked",
                        Map.of()
                    );
                }

                SignedJWT signedJWT = SignedJWT.parse(token);
                MACVerifier verifier = new MACVerifier(secretKey.getEncoded());
                
                if (!signedJWT.verify(verifier)) {
                    throw new SecurityException("Invalid signature");
                }
                
                JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

                String principalId = claimsSet.getSubject();
                String principalType = claimsSet.getStringClaim("type");
                
                @SuppressWarnings("unchecked")
                Set<String> roles = new HashSet<>((java.util.Collection<String>) claimsSet.getStringListClaim("roles"));
                
                Principal principal = new Principal(
                    principalId,
                    Enum.valueOf(com.ghatana.virtualorg.security.PrincipalType.class, principalType),
                    roles,
                    null
                );

                LOG.debug("Successfully authenticated principal: {}", principalId);
                return new AuthenticationResult(true, principal, null, Map.of());
            } catch (Exception e) {
                LOG.debug("Token authentication failed: {}", e.getMessage());
                return new AuthenticationResult(
                    false,
                    null,
                    "Invalid token: " + e.getMessage(),
                    Map.of()
                );
            }
        });
    }

    @Override
    @NotNull
    public Promise<AuthenticationResult> authenticate(@NotNull String username, @NotNull String password) {
        // Not implemented for JWT service
        return Promise.of(new AuthenticationResult(
            false,
            null,
            "Username/password authentication not supported",
            Map.of()
        ));
    }

    @Override
    @NotNull
    public Promise<String> generateToken(@NotNull Principal principal, long expirySeconds) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                Instant now = Instant.now();
                Instant expirationTime = now.plusSeconds(expirySeconds);

                JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                        .subject(principal.id())
                        .claim("type", principal.type().toString())
                        .claim("roles", principal.roles())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(expirationTime))
                        .build();

                JWSSigner signer = new MACSigner(secretKey.getEncoded());
                SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
                signedJWT.sign(signer);

                String token = signedJWT.serialize();
                LOG.debug("Generated token for principal: {}", principal.id());
                return token;
            } catch (Exception e) {
                LOG.error("Failed to generate token for principal {}: {}", principal.id(), e.getMessage());
                throw new RuntimeException("Failed to generate token", e);
            }
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> validateToken(@NotNull String token) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                if (revokedTokens.contains(token)) {
                    return false;
                }

                SignedJWT signedJWT = SignedJWT.parse(token);
                MACVerifier verifier = new MACVerifier(secretKey.getEncoded());
                return signedJWT.verify(verifier);
            } catch (Exception e) {
                LOG.debug("Token validation failed: {}", e.getMessage());
                return false;
            }
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> revokeToken(@NotNull String token) {
        return Promise.ofBlocking(eventloop, () -> {
            revokedTokens.add(token);
            LOG.debug("Token revoked");
            return true;
        });
    }

    @Override
    @NotNull
    public Promise<Optional<Principal>> getPrincipal(@NotNull String token) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                if (revokedTokens.contains(token)) {
                    return Optional.empty();
                }

                SignedJWT signedJWT = SignedJWT.parse(token);
                MACVerifier verifier = new MACVerifier(secretKey.getEncoded());
                
                if (!signedJWT.verify(verifier)) {
                    return Optional.empty();
                }
                
                JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
                String principalId = claimsSet.getSubject();
                String principalType = claimsSet.getStringClaim("type");
                
                @SuppressWarnings("unchecked")
                Set<String> roles = new HashSet<>((java.util.Collection<String>) claimsSet.getStringListClaim("roles"));
                
                Principal principal = new Principal(
                    principalId,
                    Enum.valueOf(com.ghatana.virtualorg.security.PrincipalType.class, principalType),
                    roles,
                    null
                );

                return Optional.of(principal);
            } catch (Exception e) {
                LOG.debug("Failed to extract principal from token: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    @NotNull
    public Promise<String> refreshToken(@NotNull String token) {
        return getPrincipal(token)
            .then(
                principal -> {
                    if (principal.isPresent()) {
                        // Revoke the old token first
                        revokedTokens.add(token);
                        LOG.debug("Token revoked during refresh");
                        
                        // Generate new token
                        return generateToken(principal.get(), defaultExpirySeconds);
                    } else {
                        return Promise.ofException(new RuntimeException("Cannot refresh invalid token"));
                    }
                },
                error -> {
                    LOG.error("Failed to refresh token: {}", error.getMessage());
                    return Promise.ofException(new RuntimeException("Failed to refresh token", error));
                }
            );
    }
}

