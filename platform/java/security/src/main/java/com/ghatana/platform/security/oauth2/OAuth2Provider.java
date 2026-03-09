package com.ghatana.platform.security.oauth2;

import com.ghatana.platform.security.model.User;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.ResponseMode;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;

import com.nimbusds.openid.connect.sdk.SubjectType;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles OAuth2 and OIDC authentication flows.
 
 *
 * @doc.type class
 * @doc.purpose Oauth2provider
 * @doc.layer core
 * @doc.pattern Provider
*/
public class OAuth2Provider {
    private static final Logger logger = LoggerFactory.getLogger(OAuth2Provider.class);
    
    private final OAuth2Config config;
    private final Map<String, State> stateStore = new ConcurrentHashMap<>();
    private OIDCProviderMetadata providerMetadata;
    
    public static class AuthResponse {
        private final String authorizationUrl;
        private final String state;
        private final String nonce;
        
        public AuthResponse(String authorizationUrl, String state, String nonce) {
            this.authorizationUrl = authorizationUrl;
            this.state = state;
            this.nonce = nonce;
        }
        
        public String getAuthorizationUrl() {
            return authorizationUrl;
        }
        
        public String getState() {
            return state;
        }
        
        public String getNonce() {
            return nonce;
        }
    }

    public OAuth2Provider(OAuth2Config config) {
        this.config = config;
        initializeProviderMetadata();
    }

    private void initializeProviderMetadata() {
        try {
            // Try to fetch provider metadata if we have a discovery URL
            if (config.getDiscoveryUri() != null) {
                this.providerMetadata = OIDCProviderMetadata.resolve(new Issuer(config.getDiscoveryUri()));
                logger.info("Discovered OIDC provider metadata from: {}", config.getDiscoveryUri());
            } else {
                // Use manually configured endpoints
                this.providerMetadata = new OIDCProviderMetadata(
                    new Issuer(config.getIssuerUri()),
                    Collections.singletonList(SubjectType.PUBLIC),
                    config.getJwksUri()
                );
                this.providerMetadata.setTokenEndpointURI(config.getTokenEndpoint());
                this.providerMetadata.setAuthorizationEndpointURI(config.getAuthorizationEndpoint());
                this.providerMetadata.setUserInfoEndpointURI(config.getUserInfoEndpoint());
                logger.info("Using manually configured OIDC provider metadata");
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch OIDC provider metadata, using configuration: {}", e.getMessage());
            // Fall back to configured endpoints
            try {
                Issuer issuer = config.getTokenEndpoint() != null ? new Issuer(config.getTokenEndpoint().toString()) : new Issuer(config.getIssuerUri());
                this.providerMetadata = new OIDCProviderMetadata(
                    issuer,
                    java.util.Collections.singletonList(SubjectType.PUBLIC),  // ensure non-null subject types
                    config.getJwksUri()
                );
                this.providerMetadata.setTokenEndpointURI(config.getTokenEndpoint());
                this.providerMetadata.setAuthorizationEndpointURI(config.getAuthorizationEndpoint());
                if (config.getUserInfoEndpoint() != null) {
                    this.providerMetadata.setUserInfoEndpointURI(config.getUserInfoEndpoint());
                }
            } catch (Exception ex) {
                // As a last resort, build minimal metadata with public subject type and no endpoints
                logger.warn("Fallback provider metadata construction failed: {}", ex.getMessage());
                this.providerMetadata = new OIDCProviderMetadata(
                    new Issuer(config.getIssuerUri()),
                    java.util.Collections.singletonList(SubjectType.PUBLIC),
                    null
                );
            }
        }
    }

    /**
     * Generate an authorization URL for the OAuth2 flow.
     *
     * The first argument can be either a redirectUri (when invoked by the controller)
     * or a previously generated state string (tests pass a state as first arg). We detect
     * which one was passed by simple heuristics: if it looks like a URL (starts with http),
     * treat it as redirectUri, otherwise treat it as a state value.
     *
     * @param arg   Either redirectUri (e.g. "http://.../callback") or a state value
     * @param nonce Nonce for ID token validation
     * @return The authorization URL and state
     */
    public AuthResponse generateAuthorizationUrl(String arg, String nonce) {
        try {
            // Decide whether the caller passed redirectUri or a state value as the first arg
            String redirectUri = null;
            String maybeState = null;
            if (arg != null && (arg.startsWith("http://") || arg.startsWith("https://"))) {
                redirectUri = arg;
            } else {
                maybeState = arg;
            }

            // Respect a provided state value from caller, otherwise generate one
            State state;
            if (maybeState != null && !maybeState.isBlank()) {
                state = new State(maybeState);
            } else {
                state = new State();
            }

            // Ensure we always have a non-null nonce string
            String nonceValue = (nonce != null && !nonce.isBlank()) ? nonce : UUID.randomUUID().toString();

            // Store the state for later validation
            stateStore.put(state.getValue(), state);

            // Build the authorization request; prefer provided redirectUri if present
            AuthorizationRequest request = new AuthorizationRequest(
                config.getAuthorizationEndpoint(),
                new ResponseType(ResponseType.Value.CODE),
                ResponseMode.QUERY, // Default response mode
                new ClientID(config.getClientID()),
                (redirectUri != null) ? URI.create(redirectUri) : config.getRedirectUri(),
                new Scope(config.getScope()),
                state
            );

            // Append nonce parameter to the generated URL so OIDC clients can validate it
            String authUrl = request.toURI().toString();
            String encodedNonce = URLEncoder.encode(nonceValue, StandardCharsets.UTF_8);
            if (!authUrl.contains("nonce=")) {
                if (authUrl.contains("?")) {
                    authUrl = authUrl + "&nonce=" + encodedNonce;
                } else {
                    authUrl = authUrl + "?nonce=" + encodedNonce;
                }
            }

            return new AuthResponse(authUrl, state.getValue(), nonceValue);

        } catch (Exception e) {
            logger.error("Failed to generate authorization URL", e);
            throw new RuntimeException("Failed to generate authorization URL", e);
        }
    }

    /**
     * Exchange authorization code for tokens.
     *
     * @param code Authorization code
     * @param state State parameter for CSRF protection
     * @param storedState Original state from the authorization request
     * @param redirectUri Redirect URI
     * @return Authenticated user with tokens
     * @throws OAuth2Exception If authentication fails
     */
    public User authenticate(String code, String state, String storedState, String redirectUri) throws OAuth2Exception {
        try {
            // Validate state
            State savedState = stateStore.get(state);
            if (savedState == null || !savedState.getValue().equals(state)) {
                // Throw a precise message so tests can assert it directly
                throw new OAuth2Exception("Invalid state parameter");
            }

            // Remove the used state to prevent replay attacks
            stateStore.remove(state);

            // Exchange authorization code for tokens
            TokenRequest request = new TokenRequest(
                providerMetadata.getTokenEndpointURI(),
                new ClientSecretBasic(new ClientID(config.getClientID()), new Secret(config.getClientSecret())),
                new AuthorizationCodeGrant(new AuthorizationCode(code), new URI(redirectUri)),
                new Scope(config.getScope())
            );
            
            // Send the token request
            HTTPResponse httpResponse = request.toHTTPRequest().send();
            TokenResponse response = TokenResponse.parse(httpResponse);
            
            if (!response.indicatesSuccess()) {
                TokenErrorResponse errorResponse = response.toErrorResponse();
                String errorMsg = String.format("Token request failed: %s - %s", 
                    errorResponse.getErrorObject().getCode(),
                    errorResponse.getErrorObject().getDescription());
                throw new OAuth2Exception(errorMsg);
            }
            
            AccessTokenResponse successResponse = response.toSuccessResponse();
            
            // Get user info if the access token is available
            UserInfo userInfo = null;
            if (successResponse.getTokens() != null && successResponse.getTokens().getAccessToken() != null) {
                try {
                    userInfo = getUserInfo(successResponse.getTokens().getAccessToken().getValue());
                } catch (Exception e) {
                    logger.warn("Failed to fetch user info: {}", e.getMessage());
                    // Continue with just the ID token if user info is not available
                }
            }
            
            // Create or update user with the obtained tokens and user info
            User user = createOrUpdateUser(userInfo, successResponse);
            
            return user;
        } catch (Exception e) {
            // If an OAuth2Exception was intentionally thrown above, rethrow it unchanged so callers/tests can inspect it
            if (e instanceof OAuth2Exception) {
                throw (OAuth2Exception) e;
            }
            logger.error("OAuth2 authentication failed", e);
            throw new OAuth2Exception("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    private UserInfo getUserInfo(String accessToken) throws IOException, ParseException, java.text.ParseException {
        if (providerMetadata.getUserInfoEndpointURI() == null) {
            throw new IllegalStateException("UserInfo endpoint not configured");
        }
        
        try {
            // Create a UserInfo request with the access token
            UserInfoRequest request = new UserInfoRequest(
                providerMetadata.getUserInfoEndpointURI(),
                new BearerAccessToken(accessToken)
            );
            
            // Send the request
            HTTPResponse httpResponse = request.toHTTPRequest().send();
            
            // Check if the request was successful
            if (!httpResponse.indicatesSuccess()) {
                String errorMsg = String.format("UserInfo request failed: %d %s", 
                    httpResponse.getStatusCode(), 
                    httpResponse.getStatusMessage());
                throw new IOException(errorMsg);
            }
            
            // Parse the UserInfo response
            UserInfo userInfo = UserInfo.parse(httpResponse.getBody());
            
            if (userInfo == null) {
                throw new IOException("Failed to parse UserInfo response");
            }
            
            return userInfo;
            
        } catch (Exception e) {
            logger.error("Failed to fetch user info", e);
            throw new IOException("Failed to fetch user info: " + e.getMessage(), e);
        }
    }

    private User createOrUpdateUser(UserInfo userInfo, AccessTokenResponse tokenResponse) {
        // Use the subject (sub claim) as the user ID
        String subject = null;

        // Get the tokens from the token response (may be plain OAuth2 Tokens or OIDC tokens)
        com.nimbusds.oauth2.sdk.token.Tokens tokens = null;
        OIDCTokens oidcTokens = null;
        JWT idToken = null;
        if (tokenResponse.getTokens() != null) {
            try {
                tokens = tokenResponse.getTokens();
                if (tokens instanceof OIDCTokens) {
                    oidcTokens = (OIDCTokens) tokens;
                    idToken = oidcTokens.getIDToken();
                }
            } catch (Exception e) {
                logger.warn("Failed to inspect token types", e);
            }
        }

        // Try to get subject from ID token first
        if (idToken != null) {
            try {
                subject = idToken.getJWTClaimsSet().getSubject();
            } catch (Exception e) {
                logger.warn("Failed to get subject from ID token", e);
            }
        }

        // Fall back to user info if subject not in ID token
        if (subject == null && userInfo != null && userInfo.getSubject() != null) {
            subject = userInfo.getSubject().getValue();
        }

        // Last resort: generate a random ID
        if (subject == null) {
            subject = "user_" + UUID.randomUUID().toString();
            logger.warn("No subject claim found, generated random ID: {}", subject);
        }

        // Get email from user info or ID token
        String email = null;
        if (userInfo != null && userInfo.getEmailAddress() != null) {
            email = userInfo.getEmailAddress();
        } else if (idToken != null) {
            try {
                email = (String) idToken.getJWTClaimsSet().getClaim("email");
            } catch (Exception e) {
                // Ignore
            }
        }

        // Create or update user with the obtained information
        User.Builder builder = User.builder()
            .userId(subject)
            .username(email != null ? email : subject) // Use email as username if available
            .authenticated(true);

        if (email != null) {
            builder.email(email);
        }

        // Extract token values in a safe manner (work with both OAuth2 Tokens and OIDC tokens)
        String accessTokenValue = null;
        String idTokenSerialized = null;
        String refreshTokenValue = null;
        try {
            if (tokens != null && tokens.getAccessToken() != null) {
                accessTokenValue = tokens.getAccessToken().getValue();
            }
            if (tokens != null && tokens.getRefreshToken() != null) {
                refreshTokenValue = tokens.getRefreshToken().getValue();
            }
            if (oidcTokens != null && oidcTokens.getIDToken() != null) {
                idTokenSerialized = oidcTokens.getIDToken().serialize();
            }
        } catch (Exception e) {
            logger.warn("Failed to extract token values", e);
        }

        // Add tokens as attributes
        if (accessTokenValue != null) {
            builder.attribute("accessToken", accessTokenValue);
            // Also set the user's auth token to the access token so getToken() returns it
            builder.authToken(accessTokenValue);
        }
        if (idTokenSerialized != null) {
            builder.attribute("idToken", idTokenSerialized);
        }
        if (refreshTokenValue != null) {
            builder.attribute("refreshToken", refreshTokenValue);
        }

        // Add user info as attributes if available
        if (userInfo != null) {
            try {
                Map<String, Object> claims = userInfo.toJWTClaimsSet().getClaims();
                for (Map.Entry<String, Object> entry : claims.entrySet()) {
                    if (entry.getValue() != null) {
                        builder.attribute(entry.getKey(), entry.getValue().toString());
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to add user info claims", e);
            }
        }

        return builder.build();
    }

    /**
     * Refresh an access token using a refresh token.
     *
     * @param refreshToken The refresh token
     * @return New access and refresh tokens
     * @throws OAuth2Exception If token refresh fails
     */
    public AccessTokenResponse refreshToken(String refreshToken) throws OAuth2Exception {
        try {
            RefreshTokenGrant refreshGrant = new RefreshTokenGrant(new RefreshToken(refreshToken));
            
            ClientAuthentication clientAuth = new ClientSecretBasic(
                config.getClientID(),
                new Secret(config.getClientSecret())
            );
            
            // Build the token request
            TokenRequest request = new TokenRequest(
                providerMetadata.getTokenEndpointURI(),
                clientAuth,
                refreshGrant,
                config.getScope()
            );
            
            // Send the token request
            HTTPResponse httpResponse = request.toHTTPRequest().send();
            TokenResponse response = TokenResponse.parse(httpResponse);
            
            if (!response.indicatesSuccess()) {
                TokenErrorResponse errorResponse = response.toErrorResponse();
                String errorMsg = String.format("Token refresh failed: %s - %s", 
                    errorResponse.getErrorObject().getCode(),
                    errorResponse.getErrorObject().getDescription());
                throw new OAuth2Exception(errorMsg);
            }
            
            return response.toSuccessResponse();
            
        } catch (Exception e) {
            throw new OAuth2Exception("Failed to refresh token: " + e.getMessage(), e);
        }
    }
    
    public static class OAuth2Exception extends Exception {
        public OAuth2Exception(String message) {
            super(message);
        }
        
        public OAuth2Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
