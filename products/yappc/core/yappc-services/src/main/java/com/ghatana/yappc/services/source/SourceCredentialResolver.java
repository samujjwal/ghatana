package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.SourceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @doc.type interface
 * @doc.purpose Resolves governed credential refs into provider tokens without logging secrets
 * @doc.layer service
 * @doc.pattern Strategy
 *
 * P0: Centralises credential resolution so that GitHub, GitLab, and future providers share
 * one consistent lookup chain with scope enforcement.
 * Providers must never hard-code token lookup; they delegate to this resolver.
 *
 * Security requirements:
 * - Never log credential values or secrets
 * - Enforce tenant/workspace/project ownership validation
 * - Resolve governed credential refs from vault or secret store
 *
 * The default implementation reads from environment variables only.
 * Production deployments may supply a vault-backed implementation via DI.
 */
public interface SourceCredentialResolver {

    /**
     * Resolve the credential token for the given locator with scope validation.
     *
     * P0: Validates tenant/workspace/project ownership before resolving credentials.
     * Never logs the resolved token value to prevent secret leakage.
     *
     * @param locator  the source locator whose {@code credentialRef} identifies the secret
     * @param provider the provider ID (e.g. "github", "gitlab") used to pick fallback env-vars
     * @param expectedTenantId expected tenant ID for ownership validation
     * @param expectedWorkspaceId expected workspace ID for ownership validation
     * @param expectedProjectId expected project ID for ownership validation
     * @return the resolved token, or {@code null} if no credential is available or scope mismatch
     * @throws SecurityException if scope validation fails (cross-tenant/workspace/project access attempt)
     */
    String resolve(SourceLocator locator, String provider, 
                   String expectedTenantId, String expectedWorkspaceId, String expectedProjectId);

    /**
     * Default environment-variable-backed implementation.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Validate scope (tenant/workspace/project ownership)</li>
     *   <li>The value of the env-var named exactly by {@code credentialRef} (if set).</li>
     *   <li>The value of {@code YAPPC_SOURCE_CREDENTIAL_<NORMALISED_REF>} (if set).</li>
     *   <li>Provider-specific well-known fallbacks ({@code GITHUB_TOKEN}/{@code GH_TOKEN}
     *       for GitHub; {@code GITLAB_TOKEN}/{@code CI_JOB_TOKEN} for GitLab).</li>
     * </ol>
     *
     * P0: Never logs resolved credential values to prevent secret leakage.
     *
     * @return a no-op-safe singleton instance
     */
    static SourceCredentialResolver envBacked() {
        return new EnvBackedSourceCredentialResolver();
    }

    static SourceCredentialResolver governed(SourceCredentialRepository repository) {
        return new GovernedSourceCredentialResolver(repository);
    }
}

/**
 * Package-private environment-variable implementation of {@link SourceCredentialResolver}.
 * Not part of the public API — obtain via {@link SourceCredentialResolver#envBacked()}.
 *
 * P0: Enforces tenant/workspace/project ownership validation and never logs secrets.
 */
final class EnvBackedSourceCredentialResolver implements SourceCredentialResolver {

    private static final Logger log = LoggerFactory.getLogger(EnvBackedSourceCredentialResolver.class);

    @Override
    public String resolve(SourceLocator locator, String provider,
                         String expectedTenantId, String expectedWorkspaceId, String expectedProjectId) {
        // P0: Validate scope ownership before resolving credentials
        validateScope(locator, expectedTenantId, expectedWorkspaceId, expectedProjectId);

        String credentialRef = locator.credentialRef()
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .orElse(null);

        if (credentialRef != null) {
            String direct = System.getenv(credentialRef);
            if (direct != null && !direct.isBlank()) {
                logCredentialResolved(credentialRef, provider);
                return direct;
            }

            String normalised = credentialRef
                .replace('-', '_')
                .replace('.', '_')
                .toUpperCase();
            String prefixed = System.getenv("YAPPC_SOURCE_CREDENTIAL_" + normalised);
            if (prefixed != null && !prefixed.isBlank()) {
                logCredentialResolved(credentialRef, provider);
                return prefixed;
            }
        }

        String fallback = resolveProviderFallback(provider);
        if (fallback != null) {
            logCredentialResolved("provider-fallback", provider);
        }
        return fallback;
    }

    /**
     * P0: Validate tenant/workspace/project ownership.
     * Throws SecurityException if scope mismatch is detected.
     */
    private void validateScope(SourceLocator locator, String expectedTenantId, 
                               String expectedWorkspaceId, String expectedProjectId) {
        if (!Objects.equals(locator.tenantId(), expectedTenantId)) {
            String msg = String.format("Credential scope mismatch: locator tenant=%s, expected tenant=%s",
                locator.tenantId(), expectedTenantId);
            log.error(msg);
            throw new SecurityException(msg);
        }
        if (!Objects.equals(locator.workspaceId(), expectedWorkspaceId)) {
            String msg = String.format("Credential scope mismatch: locator workspace=%s, expected workspace=%s",
                locator.workspaceId(), expectedWorkspaceId);
            log.error(msg);
            throw new SecurityException(msg);
        }
        if (!Objects.equals(locator.projectId(), expectedProjectId)) {
            String msg = String.format("Credential scope mismatch: locator project=%s, expected project=%s",
                locator.projectId(), expectedProjectId);
            log.error(msg);
            throw new SecurityException(msg);
        }
    }

    /**
     * P0: Log credential resolution without exposing the actual secret value.
     */
    private void logCredentialResolved(String credentialRef, String provider) {
        log.debug("Credential resolved for ref='{}', provider='{}' (value not logged)", 
            credentialRef, provider);
    }

    private static String resolveProviderFallback(String provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider.toLowerCase()) {
            case "github" -> firstNonBlank(System.getenv("GITHUB_TOKEN"), System.getenv("GH_TOKEN"));
            case "gitlab" -> firstNonBlank(System.getenv("GITLAB_TOKEN"), System.getenv("CI_JOB_TOKEN"));
            default -> null;
        };
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}

final class GovernedSourceCredentialResolver implements SourceCredentialResolver {

    private static final Logger log = LoggerFactory.getLogger(GovernedSourceCredentialResolver.class);
    private final SourceCredentialRepository repository;

    GovernedSourceCredentialResolver(SourceCredentialRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public String resolve(SourceLocator locator, String provider, String expectedTenantId, String expectedWorkspaceId, String expectedProjectId) {
        validateScope(locator, expectedTenantId, expectedWorkspaceId, expectedProjectId);

        String credentialRef = locator.credentialRef().map(String::trim).filter(s -> !s.isBlank()).orElse(null);
        if (credentialRef == null) {
            return null;
        }

        String effectiveProvider = provider == null || provider.isBlank() ? locator.provider() : provider;
        return repository.findBinding(expectedTenantId, expectedWorkspaceId, expectedProjectId, effectiveProvider, credentialRef)
            .map(SourceCredentialRepository.CredentialBinding::secretKey)
            .map(System::getenv)
            .filter(value -> value != null && !value.isBlank())
            .orElseGet(() -> {
                log.warn("Governed credential binding not found or secret key missing for provider='{}' credentialRef='{}'", effectiveProvider, credentialRef);
                return null;
            });
    }

    private static void validateScope(SourceLocator locator, String expectedTenantId,
                                      String expectedWorkspaceId, String expectedProjectId) {
        if (!Objects.equals(locator.tenantId(), expectedTenantId)
            || !Objects.equals(locator.workspaceId(), expectedWorkspaceId)
            || !Objects.equals(locator.projectId(), expectedProjectId)) {
            throw new SecurityException("Credential scope mismatch for source locator");
        }
    }
}
