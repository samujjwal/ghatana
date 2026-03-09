/**
 * OIDC Discovery and Token Verification
 *
 * Implements OIDC discovery and JWT verification.
 *
 * @doc.type module
 * @doc.purpose OIDC token verification for enterprise SSO
 * @doc.layer product
 * @doc.pattern Strategy
 */

import * as jose from "jose";

/**
 * OIDC Discovery Document (from .well-known/openid-configuration)
 */
export interface OidcDiscoveryDocument {
    issuer: string;
    authorization_endpoint: string;
    token_endpoint: string;
    userinfo_endpoint?: string;
    jwks_uri: string;
    scopes_supported?: string[];
    response_types_supported: string[];
    claims_supported?: string[];
    end_session_endpoint?: string;
}

/**
 * OIDC Token Response from token endpoint.
 */
export interface OidcTokenResponse {
    access_token: string;
    id_token: string;
    token_type: string;
    expires_in?: number;
    refresh_token?: string;
    scope?: string;
}

/**
 * Verified OIDC token claims.
 */
export interface OidcVerifiedClaims {
    /** Subject identifier (unique user ID from IdP) */
    sub: string;
    /** User email */
    email?: string;
    /** Whether email is verified */
    email_verified?: boolean;
    /** Display name */
    name?: string;
    /** Given name */
    given_name?: string;
    /** Family name */
    family_name?: string;
    /** Profile picture URL */
    picture?: string;
    /** Token issuer */
    iss: string;
    /** Audience (client ID) */
    aud: string | string[];
    /** Expiration time (Unix timestamp) */
    exp: number;
    /** Issued at time (Unix timestamp) */
    iat: number;
    /** Nonce for replay protection */
    nonce?: string;
    /** Google Workspace domain (hd claim) */
    hd?: string;
    /** Custom claims */
    [key: string]: unknown;
}

/**
 * Configuration for OIDC client.
 */
export interface OidcClientConfig {
    /** OIDC discovery endpoint URL */
    discoveryEndpoint: string;
    /** OAuth2 client ID */
    clientId: string;
    /** OAuth2 client secret */
    clientSecret: string;
    /** Expected audience (usually same as clientId) */
    expectedAudience?: string;
    /** Redirect URI for authorization code flow */
    redirectUri: string;
    /** Requested scopes */
    scopes?: string[];
    /** JWKS cache TTL in milliseconds (default: 5 minutes) */
    jwksCacheTtl?: number;
}

/**
 * OIDC Client for handling authentication flows.
 */
export class OidcClient {
    private config: OidcClientConfig;
    private discoveryDocs: OidcDiscoveryDocument | null = null;
    private jwks: ReturnType<typeof jose.createRemoteJWKSet> | null = null;

    constructor(config: OidcClientConfig) {
        this.config = config;
    }

    /**
     * Initializes OIDC client by fetching discovery document.
     */
    async initialize(): Promise<void> {
        if (this.discoveryDocs) return;

        const response = await fetch(this.config.discoveryEndpoint);
        if (!response.ok) {
            throw new Error(
                `Failed to fetch discovery document from ${this.config.discoveryEndpoint}: ${response.statusText}`,
            );
        }

        this.discoveryDocs = (await response.json()) as OidcDiscoveryDocument;

        // Create JWKS function with caching
        this.jwks = jose.createRemoteJWKSet(new URL(this.discoveryDocs.jwks_uri), {
            cacheMaxAge: this.config.jwksCacheTtl || 300000,
            cooldownDuration: 30000,
        });
    }

    /**
     * Generates authorization URL.
     */
    async generateAuthUrl(
        state: string,
        nonce?: string,
    ): Promise<{ url: string; codeVerifier?: string }> {
        await this.initialize();
        if (!this.discoveryDocs) throw new Error("OIDC not initialized");

        const endpoint = new URL(this.discoveryDocs.authorization_endpoint);
        endpoint.searchParams.set("client_id", this.config.clientId);
        endpoint.searchParams.set("response_type", "code");
        endpoint.searchParams.set("redirect_uri", this.config.redirectUri);
        endpoint.searchParams.set("scope", (this.config.scopes || ["openid", "email", "profile"]).join(" "));
        endpoint.searchParams.set("state", state);

        if (nonce) {
            endpoint.searchParams.set("nonce", nonce);
        }

        return { url: endpoint.toString() };
    }

    /**
     * Exchanges authorization code for tokens.
     */
    async exchangeCode(
        code: string,
        codeVerifier?: string,
    ): Promise<OidcTokenResponse> {
        await this.initialize();
        if (!this.discoveryDocs) throw new Error("OIDC not initialized");

        const params = new URLSearchParams();
        params.set("grant_type", "authorization_code");
        params.set("code", code);
        params.set("redirect_uri", this.config.redirectUri);
        params.set("client_id", this.config.clientId);
        params.set("client_secret", this.config.clientSecret);

        if (codeVerifier) {
            params.set("code_verifier", codeVerifier);
        }

        const response = await fetch(this.discoveryDocs.token_endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                Accept: "application/json",
            },
            body: params.toString(),
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Token exchange failed: ${response.status} ${errorText}`);
        }

        return (await response.json()) as OidcTokenResponse;
    }

    /**
     * Verifies ID Token.
     */
    async verifyIdToken(
        idToken: string,
        nonce?: string,
    ): Promise<OidcVerifiedClaims> {
        await this.initialize();
        if (!this.discoveryDocs || !this.jwks)
            throw new Error("OIDC not initialized");

        const { payload } = await jose.jwtVerify(idToken, this.jwks, {
            issuer: this.discoveryDocs.issuer,
            audience: this.config.expectedAudience || this.config.clientId,
        });

        if (nonce && payload.nonce !== nonce) {
            throw new Error("Invalid nonce in ID token");
        }

        return payload as unknown as OidcVerifiedClaims;
    }
}
