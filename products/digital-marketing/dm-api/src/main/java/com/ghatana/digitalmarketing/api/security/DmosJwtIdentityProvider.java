package com.ghatana.digitalmarketing.api.security;

import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Production JWT-backed identity provider for DMOS HTTP context derivation.
 *
 * <p>Validates Bearer tokens and resolves principal, session, role, and permission
 * claims server-side to prevent client-side identity spoofing.</p>
 *
 * @doc.type class
 * @doc.purpose Production JWT token verification and claim-to-identity mapping for DMOS APIs
 * @doc.layer product
 * @doc.pattern Adapter, Security
 */
public final class DmosJwtIdentityProvider implements DmosHttpContextFactory.IdentityProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DmosJwtIdentityProvider.class);

    private final JwtTokenProvider tokenProvider;

    public DmosJwtIdentityProvider(JwtTokenProvider tokenProvider) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
    }

    public static DmosJwtIdentityProvider fromEnvironment() {
        String jwksUrl = System.getenv("DMOS_JWT_JWKS_URL");
        JwtTokenProvider provider;
        if (jwksUrl != null && !jwksUrl.isBlank()) {
            provider = JwtTokenProviders.fromJwksUrl(jwksUrl.trim());
            LOG.info("[DMOS-SECURITY] Using JWKS-backed JWT identity provider: {}", jwksUrl);
        } else {
            String secret = System.getenv("JWT_SECRET");
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException(
                    "JWT_SECRET must be configured when DMOS_JWT_JWKS_URL is not set"
                );
            }
            long validityMs = parseValidityMs(System.getenv("DMOS_JWT_VALIDITY_MS")).orElse(3_600_000L);
            provider = JwtTokenProviders.fromSharedSecret(secret, validityMs);
            LOG.info("[DMOS-SECURITY] Using shared-secret JWT identity provider");
        }
        return new DmosJwtIdentityProvider(provider);
    }

    @Override
    public IdentityResult deriveIdentity(String token, String tenantId) {
        if (token == null || token.isBlank()) {
            return invalid();
        }
        if (!tokenProvider.validateToken(token)) {
            return invalid();
        }

        Optional<Map<String, Object>> claimsOpt = tokenProvider.extractClaims(token);
        if (claimsOpt.isEmpty()) {
            return invalid();
        }

        Map<String, Object> claims = claimsOpt.get();
        String principalId = tokenProvider.getUserIdFromToken(token).orElse(null);
        String sessionId = firstNonBlank(
            claimAsString(claims, "sid"),
            claimAsString(claims, "session_id"),
            claimAsString(claims, "jti")
        );

        if (principalId == null || principalId.isBlank() || sessionId == null || sessionId.isBlank()) {
            LOG.warn("[DMOS-SECURITY] JWT missing mandatory principal/session claims");
            return invalid();
        }

        String tokenTenant = firstNonBlank(
            claimAsString(claims, "tenantId"),
            claimAsString(claims, "tenant_id")
        );
        if (tokenTenant != null && !tokenTenant.equals(tenantId)) {
            LOG.warn("[DMOS-SECURITY] Token tenant mismatch: tokenTenant={}, requestTenant={}", tokenTenant, tenantId);
            return invalid();
        }

        Set<String> roles = new LinkedHashSet<>(tokenProvider.getRolesFromToken(token));
        Set<String> permissions = extractPermissions(claims);

        return new IdentityResult(principalId, sessionId, Set.copyOf(roles), Set.copyOf(permissions), true);
    }

    private static IdentityResult invalid() {
        return new IdentityResult(null, null, Set.of(), Set.of(), false);
    }

    private static Optional<Long> parseValidityMs(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            LOG.warn("[DMOS-SECURITY] Invalid DMOS_JWT_VALIDITY_MS '{}', falling back to default", raw);
            return Optional.empty();
        }
    }

    private static Set<String> extractPermissions(Map<String, Object> claims) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        addClaimValues(permissions, claims.get("permissions"));
        addClaimValues(permissions, claims.get("perms"));

        // OAuth2-style scope support: space-delimited scopes
        String scope = claimAsString(claims, "scope");
        if (scope != null && !scope.isBlank()) {
            for (String token : scope.split("\\s+")) {
                if (!token.isBlank()) {
                    permissions.add(token.trim());
                }
            }
        }
        return permissions;
    }

    @SuppressWarnings("unchecked")
    private static void addClaimValues(Set<String> sink, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s) {
            for (String token : s.split(",")) {
                String normalized = token.trim();
                if (!normalized.isBlank()) {
                    sink.add(normalized);
                }
            }
            return;
        }
        if (value instanceof Collection<?> c) {
            for (Object item : c) {
                if (item != null) {
                    String normalized = item.toString().trim();
                    if (!normalized.isBlank()) {
                        sink.add(normalized);
                    }
                }
            }
            return;
        }
        if (value instanceof Object[] array) {
            for (Object item : array) {
                if (item != null) {
                    String normalized = item.toString().trim();
                    if (!normalized.isBlank()) {
                        sink.add(normalized);
                    }
                }
            }
        }
    }

    private static String claimAsString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null) {
            return null;
        }
        String result = value.toString().trim();
        return result.isBlank() ? null : result;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
