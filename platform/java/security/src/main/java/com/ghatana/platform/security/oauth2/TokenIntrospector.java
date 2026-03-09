package com.ghatana.platform.security.oauth2;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.oauth2.exception.TokenIntrospectionException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.TokenIntrospectionRequest;
import com.nimbusds.oauth2.sdk.TokenIntrospectionResponse;
import com.nimbusds.oauth2.sdk.TokenIntrospectionSuccessResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles OAuth2 token introspection as per RFC 7662.
 */
/**
 * Handles OAuth2 token introspection as per RFC 7662 and validates JWT tokens.
 
 *
 * @doc.type class
 * @doc.purpose Token introspector
 * @doc.layer core
 * @doc.pattern Component
*/
public class TokenIntrospector {
    private static final Logger logger = LoggerFactory.getLogger(TokenIntrospector.class);
    
    private final OAuth2Config config;
    private final ExecutorService executorService;
    
    /**
     * Creates a new TokenIntrospector with the given configuration.
     * 
     * @param config The OAuth2 configuration
     */
    public TokenIntrospector(OAuth2Config config) {
        this.config = Objects.requireNonNull(config, "OAuth2Config is required");
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * Introspect an access token asynchronously.
     *
     * @param token The token to introspect
     * @return A Promise containing the user if the token is valid, or an error
     */
    public Promise<User> introspect(String token) {
        return Promise.ofBlocking(executorService, () -> {
            try {
                // First try to parse as JWT
                JWT jwt = JWTParser.parse(token);
                
                if (jwt instanceof SignedJWT) {
                    SignedJWT signedJwt = (SignedJWT) jwt;
                    
                    // Create user from JWT claims
                    Map<String, Object> claims = signedJwt.getJWTClaimsSet().getClaims();
                    return User.builder()
                        .userId((String) claims.get("sub"))
                        .email((String) claims.get("email"))
                        .username((String) claims.get("name"))
                        .build();
                }
                
                // If not a JWT, try OAuth2 introspection
                TokenIntrospectionRequest introspectionRequest = 
                    new TokenIntrospectionRequest(
                        config.getTokenEndpoint(),
                        new ClientSecretBasic(new ClientID(config.getClientId()), new Secret(config.getClientSecret())),
                        new BearerAccessToken(token)
                    );
                
                TokenIntrospectionResponse introspectionResponse =
                    TokenIntrospectionResponse.parse(introspectionRequest.toHTTPRequest().send());
                
                if (!introspectionResponse.indicatesSuccess()) {
                    throw new TokenIntrospectionException("Token introspection failed");
                }
                
                // Create user from introspection response
                TokenIntrospectionSuccessResponse successResponse = introspectionResponse.toSuccessResponse();
                return User.builder()
                    .userId(successResponse.getSubject().getValue())
                    .build();
                
            } catch (Exception e) {
                logger.error("Token introspection failed", e);
                throw new TokenIntrospectionException("Token introspection failed", e);
            }
        });
    }
    
    private boolean isJwt(String token) {
        try {
            JWTParser.parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private User createUserFromJwt(SignedJWT jwt) throws ParseException {
        // Extract claims from the JWT
        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
        
        // Create a basic user from the claims using the builder pattern
        return User.builder()
            .userId((String) claims.get("sub"))
            .build();
    }
    
    private User createUserFromUserInfo(UserInfo userInfo) {
        // Create a user from the user info using the builder pattern
        return User.builder()
            .userId(userInfo.getSubject().getValue())
            .build();
    }
    
    // This method is no longer needed as we're using UserInfo instead of introspection
    // Keeping it for backward compatibility
    private User createUserFromIntrospection(Map<String, Object> claims) {
        return User.builder()
            .userId((String) claims.get("sub"))
            .build();
    }
    
    private ClientAuthentication createClientAuthentication() {
        return new ClientSecretBasic(
            new ClientID(config.getClientId()),
            new Secret(config.getClientSecret())
        );
    }
    
    public static class IntrospectionResponse {
        private final boolean active;
        private final Map<String, Object> claims;
        
        public IntrospectionResponse(boolean active, Map<String, Object> claims) {
            this.active = active;
            this.claims = Map.copyOf(claims);
        }
        
        public boolean isActive() {
            return active;
        }
        
        public Map<String, Object> getClaims() {
            return claims;
        }
        
        public String getSubject() {
            return claims != null ? (String) claims.get("sub") : null;
        }
        
        public String getClientId() {
            return claims != null ? (String) claims.get("client_id") : null;
        }
        
        public String getScope() {
            return claims != null ? (String) claims.get("scope") : null;
        }
        
        public boolean hasScope(String scope) {
            String scopes = getScope();
            return scopes != null && scopes.contains(scope);
        }
    }
}
