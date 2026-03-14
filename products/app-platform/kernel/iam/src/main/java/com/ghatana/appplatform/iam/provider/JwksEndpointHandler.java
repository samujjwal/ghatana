/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.provider;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * HTTP handler for the JWKS (JSON Web Key Set) discovery endpoint (STORY-K14-006).
 *
 * <p>Handles {@code GET /.well-known/jwks.json} by returning all currently valid public
 * keys: the active signing key plus any grace-period keys still held in Redis.
 *
 * <p>Clients (e.g., the API gateway JWT validation filter) call this endpoint to discover
 * the public keys needed to verify RS256 tokens. The response includes all keys currently
 * valid so that tokens signed with a recently rotated key can still be verified during
 * the 24-hour grace window.
 *
 * <h3>Response format</h3>
 * <pre>{@code
 * {
 *   "keys": [
 *     { "kty": "RSA", "kid": "uuid-1", "n": "...", "e": "AQAB", "use": "sig", "alg": "RS256" },
 *     { "kty": "RSA", "kid": "uuid-2", "n": "...", "e": "AQAB", "use": "sig", "alg": "RS256" }
 *   ]
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JWKS endpoint handler — serves active + grace-period public keys (K14-006)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class JwksEndpointHandler implements AsyncServlet {

    private static final Logger log = LoggerFactory.getLogger(JwksEndpointHandler.class);

    private static final String CONTENT_TYPE_JWKS = "application/json";

    private final SigningKeyRotator keyRotator;

    /**
     * @param keyRotator rotating key provider used to list all valid public keys
     */
    public JwksEndpointHandler(SigningKeyRotator keyRotator) {
        this.keyRotator = keyRotator;
    }

    // ──────────────────────────────────────────────────────────────────────
    // AsyncServlet implementation
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        return keyRotator.listValidPublicKeys()
            .map(this::buildJwksResponse)
            .whenException(e -> log.error("[JWKS] Failed to build JWKS response: {}", e.getMessage(), e));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────────────────────────────

    private HttpResponse buildJwksResponse(List<RSAKey> publicKeys) {
        JWKSet jwkSet = new JWKSet(List.copyOf(publicKeys));
        String json = jwkSet.toPublicJWKSet().toString();
        log.debug("[JWKS] Serving {} public key(s)", publicKeys.size());
        return HttpResponse.ok200()
            .withHeader(io.activej.http.HttpHeaders.of("Content-Type"), CONTENT_TYPE_JWKS)
            .withHeader(io.activej.http.HttpHeaders.of("Cache-Control"), "public, max-age=3600")
            .withBody(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
