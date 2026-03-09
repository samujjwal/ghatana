package com.ghatana.platform.security.jwt;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Canonical JWT token provider using Nimbus JOSE+JWT.
 *
 * <p>Provides HMAC-SHA256 signed JWT token creation, validation, and claim extraction.
 * This is the canonical implementation of {@link com.ghatana.platform.security.port.JwtTokenProvider}.
 *
 * @doc.type class
 * @doc.purpose Canonical JWT token creation, validation, and parsing
 * @doc.layer platform
 * @doc.pattern Provider
 */
public class JwtTokenProvider implements com.ghatana.platform.security.port.JwtTokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    
    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final long validityInMilliseconds;
    
    /**
     * Creates a new JwtTokenProvider with the specified secret key and token validity.
     *
     * @param secretKey the secret key used for signing tokens
     * @param validityInMilliseconds the token validity period in milliseconds
     */
    public JwtTokenProvider(String secretKey, long validityInMilliseconds) {
        try {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            this.signer = new MACSigner(keyBytes);
            this.verifier = new MACVerifier(keyBytes);
            this.validityInMilliseconds = validityInMilliseconds;
        } catch (JOSEException e) {
            throw new IllegalArgumentException("Invalid secret key", e);
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public String createToken(String userId, List<String> roles, Map<String, Object> additionalClaims) {
        try {
            Date now = new Date();
            Date validity = new Date(now.getTime() + validityInMilliseconds);
            
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .claim("roles", roles)
                    .issueTime(now)
                    .expirationTime(validity);
                    
            // Add additional claims if provided
            if (additionalClaims != null) {
                for (Map.Entry<String, Object> entry : additionalClaims.entrySet()) {
                    claimsBuilder.claim(entry.getKey(), entry.getValue());
                }
            }
            
            SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claimsBuilder.build()
            );
            
            signedJWT.sign(signer);
            return signedJWT.serialize();
            
        } catch (JOSEException e) {
            logger.error("Failed to create token", e);
            throw new RuntimeException("Failed to create token", e);
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            
            // Verify the signature
            if (!signedJWT.verify(verifier)) {
                logger.warn("Invalid JWT signature");
                return false;
            }
            
            // Check expiration
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expirationTime = claims.getExpirationTime();
            
            if (expirationTime == null || expirationTime.before(new Date())) {
                logger.warn("Expired JWT token");
                return false;
            }
            
            return true;
            
        } catch (ParseException ex) {
            logger.warn("Malformed JWT token", ex);
        } catch (JOSEException ex) {
            logger.warn("JWT verification failed", ex);
        } catch (SecurityException ex) {
            logger.warn("Invalid JWT security state", ex);
        } catch (IllegalArgumentException ex) {
            logger.warn("JWT token is empty");
        }
        return false;
    }
    
    /**
     * Extracts the user ID from a JWT token.
     *
     * @param token the JWT token
     * @return the user ID, or empty if the token is invalid
     */
    @Override
    public Optional<String> getUserIdFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            
            if (!signedJWT.verify(verifier)) {
                return Optional.empty();
            }
            
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            return Optional.ofNullable(claims.getSubject());
            
        } catch (ParseException | JOSEException e) {
            logger.warn("Failed to extract user ID from token", e);
            return Optional.empty();
        }
    }
    
    /**
     * Extracts the roles from a JWT token.
     *
     * @param token the JWT token
     * @return a list of roles, or an empty list if the token is invalid or has no roles
     */
    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            
            if (!signedJWT.verify(verifier)) {
                return List.of();
            }
            
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Object rolesObj = claims.getClaim("roles");
            
            if (rolesObj instanceof List) {
                List<?> roles = (List<?>) rolesObj;
                return roles.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .collect(Collectors.toList());
            }
            
        } catch (ParseException | JOSEException e) {
            logger.warn("Failed to extract roles from token", e);
        }
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Map<String, Object>> extractClaims(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            Map<String, Object> claims = new HashMap<>(claimsSet.getClaims());
            return Optional.of(claims);
        } catch (ParseException | JOSEException e) {
            logger.warn("Failed to extract claims from token", e);
            return Optional.empty();
        }
    }

    /**
     * Creates a JWT token for the specified User model.
     *
     * @param user the user model
     * @return the signed JWT string
     */
    public String createToken(User user) {
        return createToken(user.getUsername(), user.getRoles().stream().map(Role::name).collect(Collectors.toList()), null);
    }
}
