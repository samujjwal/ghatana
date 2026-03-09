package com.ghatana.refactorer.server.auth;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JWT-based auth filter that delegates token verification to the platform's
 * canonical {@link JwtTokenProvider} — eliminating inline Nimbus parsing.
 *
 * <p>Validates bearer tokens, extracts tenant context, and attaches
 * {@link TenantContext} to the request for downstream handlers.
 *
 * @doc.type class
 * @doc.purpose Apply JWT authentication checks via platform token provider
 * @doc.layer product
 * @doc.pattern Filter
 */
public final class JwtAuthFilter implements AsyncServlet {

    private static final Logger logger = LogManager.getLogger(JwtAuthFilter.class);

    private final AsyncServlet delegate;
    private final AccessPolicy accessPolicy;
    private final JwtTokenProvider tokenProvider;

    /**
     * Creates a JWT auth filter backed by the platform token provider.
     *
     * @param delegate     the downstream servlet to delegate to after auth
     * @param accessPolicy the access policy for authentication requirements
     * @param tokenProvider the platform JWT token provider
     */
    public JwtAuthFilter(
            AsyncServlet delegate, AccessPolicy accessPolicy, JwtTokenProvider tokenProvider) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy must not be null");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        if (!shouldAuthenticate(request)) {
            return delegate.serve(request);
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized("Missing bearer token");
        }

        String token = header.substring("Bearer ".length()).trim();
        try {
            TenantContext context = verifyAndResolve(token);
            TenantResolver.attach(request, context);
            accessPolicy.ensureAuthenticated(context);
            return delegate.serve(request);
        } catch (SecurityException e) {
            logger.debug("JWT rejected: {}", e.getMessage());
            return unauthorized(e.getMessage());
        } catch (Exception e) {
            logger.warn("Failed to process JWT", e);
            return unauthorized("Invalid token");
        }
    }

    private boolean shouldAuthenticate(HttpRequest request) {
        if (accessPolicy.isAuthRequired()) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith("Bearer ");
    }

    private TenantContext verifyAndResolve(String token) {
        // Delegate validation to the platform provider
        if (!tokenProvider.validateToken(token)) {
            throw new SecurityException("Token validation failed (invalid or expired)");
        }

        // Extract claims via the platform provider
        Optional<Map<String, Object>> claimsOpt = tokenProvider.extractClaims(token);
        if (claimsOpt.isEmpty()) {
            throw new SecurityException("Unable to extract claims from token");
        }

        Map<String, Object> claims = claimsOpt.get();

        // Resolve tenant from multiple possible claim names
        String tenantId = firstNonEmptyString(
                getStringClaim(claims, "tenantId"),
                getStringClaim(claims, "tenant_id"),
                getStringClaim(claims, "tenant"));
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Missing tenant identifier in token");
        }

        // Use platform provider for userId and roles
        String subject = tokenProvider.getUserIdFromToken(token).orElse("unknown");
        List<String> rolesList = tokenProvider.getRolesFromToken(token);
        Set<String> roles = rolesList == null
                ? Collections.emptySet()
                : new HashSet<>(rolesList);

        Map<String, String> flatClaims = flattenClaims(claims);
        return TenantContext.of(tenantId, subject, roles, flatClaims);
    }

    private static String getStringClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value instanceof String s ? s : null;
    }

    private Map<String, String> flattenClaims(Map<String, Object> claims) {
        Map<String, String> result = new HashMap<>();
        claims.forEach((key, value) -> result.put(key, value == null ? "" : value.toString()));
        return Collections.unmodifiableMap(result);
    }

    private Promise<HttpResponse> unauthorized(String message) {
        HttpResponse response
                = ResponseBuilder.unauthorized()
                        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                        .text(message)
                        .build();
        return Promise.of(response);
    }

    private static String firstNonEmptyString(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
