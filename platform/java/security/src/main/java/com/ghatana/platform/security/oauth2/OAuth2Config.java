package com.ghatana.platform.security.oauth2;

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.id.ClientID;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for OAuth2 and OIDC providers.
 
 *
 * @doc.type class
 * @doc.purpose Oauth2config
 * @doc.layer core
 * @doc.pattern Configuration
*/
public class OAuth2Config {
    private final String clientId;
    private final String clientSecret;
    private final URI tokenEndpoint;
    private final URI authorizationEndpoint;
    private final URI userInfoEndpoint;
    private final URI jwksUri;
    private final URI redirectUri;
    private final URI issuerUri;
    private final URI discoveryUri;
    private final ClientAuthenticationMethod clientAuthenticationMethod;
    private final String[] scopes;
    private final ClientID clientIdObj;

    private OAuth2Config(Builder builder) {
        this.clientId = Objects.requireNonNull(builder.clientId, "clientId is required");
        this.clientSecret = builder.clientSecret; // Can be null for public clients
        this.tokenEndpoint = builder.tokenEndpoint;
        this.authorizationEndpoint = builder.authorizationEndpoint;
        this.userInfoEndpoint = builder.userInfoEndpoint;
        this.jwksUri = builder.jwksUri;
        this.redirectUri = builder.redirectUri;
        this.issuerUri = builder.issuerUri;
        this.discoveryUri = builder.discoveryUri;
        this.clientIdObj = new ClientID(clientId);
        
        this.clientAuthenticationMethod = builder.clientAuthenticationMethod != null 
            ? builder.clientAuthenticationMethod 
            : ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
            
        this.scopes = builder.scopes != null ? builder.scopes : new String[]{"openid", "profile", "email"};
        
        // Validate that either discoveryUri or required endpoints are provided
        if (this.discoveryUri == null) {
            Objects.requireNonNull(tokenEndpoint, "tokenEndpoint is required when discoveryUri is not provided");
            Objects.requireNonNull(authorizationEndpoint, "authorizationEndpoint is required when discoveryUri is not provided");
            Objects.requireNonNull(redirectUri, "redirectUri is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create OAuth2Config from a property map. Production-grade implementation.
     * 
     * @param properties Map containing OAuth2 configuration properties
     * @return OAuth2Config instance
     */
    public static OAuth2Config fromProperties(Map<String, String> properties) {
        Builder builder = new Builder();
        
        builder.clientId(properties.get("oauth2.client-id"))
            .clientSecret(properties.get("oauth2.client-secret"));
            
        String tokenEndpoint = properties.get("oauth2.token-endpoint");
        if (tokenEndpoint != null) {
            builder.tokenEndpoint(URI.create(tokenEndpoint));
        }
        
        String authEndpoint = properties.get("oauth2.authorization-endpoint");
        if (authEndpoint != null) {
            builder.authorizationEndpoint(URI.create(authEndpoint));
        }
        
        String userInfoEndpoint = properties.get("oauth2.user-info-endpoint");
        if (userInfoEndpoint != null) {
            builder.userInfoEndpoint(URI.create(userInfoEndpoint));
        }
        
        String jwksUri = properties.get("oauth2.jwks-uri");
        if (jwksUri != null) {
            builder.jwksUri(URI.create(jwksUri));
        }
        
        String redirectUri = properties.get("oauth2.redirect-uri");
        if (redirectUri != null) {
            builder.redirectUri(URI.create(redirectUri));
        }
        
        String issuerUri = properties.get("oauth2.issuer-uri");
        if (issuerUri != null) {
            builder.issuerUri(URI.create(issuerUri));
        }
        
        String discoveryUri = properties.get("oauth2.discovery-uri");
        if (discoveryUri != null) {
            builder.discoveryUri(URI.create(discoveryUri));
        }
        
        String scopes = properties.get("oauth2.scopes");
        if (scopes != null) {
            builder.scopes(scopes.split(","));
        }
        
        String authMethod = properties.get("oauth2.client-authentication-method");
        if (authMethod != null) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.parse(authMethod));
        }
        
        return builder.build();
    }

    // Getters
    public String getClientId() {
        return clientId;
    }
    
    public ClientID getClientID() {
        return clientIdObj;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public URI getTokenEndpoint() {
        return tokenEndpoint;
    }
    
    public URI getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }
    
    public URI getUserInfoEndpoint() {
        return userInfoEndpoint;
    }
    
    public URI getJwksUri() {
        return jwksUri;
    }
    
    public URI getRedirectUri() {
        return redirectUri;
    }
    
    public URI getIssuerUri() {
        return issuerUri;
    }
    
    public URI getDiscoveryUri() {
        return discoveryUri;
    }
    
    public ClientAuthenticationMethod getClientAuthenticationMethod() {
        return clientAuthenticationMethod;
    }
    
    public String[] getScopes() {
        return scopes;
    }
    
    public String getScopeString() {
        return String.join(" ", scopes);
    }
    
    public Scope getScope() {
        return new Scope(scopes);
    }

    public static class Builder {
        private String clientId;
        private String clientSecret;
        private URI tokenEndpoint;
        private URI authorizationEndpoint;
        private URI userInfoEndpoint;
        private URI jwksUri;
        private URI redirectUri;
        private URI issuerUri;
        private URI discoveryUri;
        private ClientAuthenticationMethod clientAuthenticationMethod;
        private String[] scopes;

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder tokenEndpoint(URI tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
            return this;
        }

        public Builder authorizationEndpoint(URI authorizationEndpoint) {
            this.authorizationEndpoint = authorizationEndpoint;
            return this;
        }

        public Builder userInfoEndpoint(URI userInfoEndpoint) {
            this.userInfoEndpoint = userInfoEndpoint;
            return this;
        }

        public Builder jwksUri(URI jwksUri) {
            this.jwksUri = jwksUri;
            return this;
        }

        public Builder redirectUri(URI redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }
        
        public Builder issuerUri(URI issuerUri) {
            this.issuerUri = issuerUri;
            return this;
        }
        
        public Builder discoveryUri(URI discoveryUri) {
            this.discoveryUri = discoveryUri;
            return this;
        }

        public Builder clientAuthenticationMethod(ClientAuthenticationMethod clientAuthenticationMethod) {
            this.clientAuthenticationMethod = clientAuthenticationMethod;
            return this;
        }

        public Builder scopes(String... scopes) {
            this.scopes = scopes;
            return this;
        }

        public OAuth2Config build() {
            return new OAuth2Config(this);
        }
    }
}
