package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.identity.spi.IdentityResolver;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.oauth2.OAuth2Config;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * OIDC-backed {@link IdentityResolver} for federated AEP agent identities.
 *
 * <p>Each federated agent is registered with an expected provider subject and an
 * access token that can be introspected against the enterprise IdP. Resolution is
 * considered successful only when the provider confirms the configured subject.
 *
 * @doc.type class
 * @doc.purpose Resolve AEP agent identities via OIDC-backed federated service principals
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class OidcIdentityProvider implements IdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(OidcIdentityProvider.class);

    private final TokenIntrospector tokenIntrospector;
    private final String issuer;
    private final Map<String, FederatedAgentRegistration> registrations;

    public OidcIdentityProvider(
            @NotNull OAuth2Config config,
            @NotNull List<FederatedAgentRegistration> registrations) {
        this(new TokenIntrospector(Objects.requireNonNull(config, "config")),
            issuerString(config.getIssuerUri()),
            registrations);
    }

    OidcIdentityProvider(
            @NotNull TokenIntrospector tokenIntrospector,
            @NotNull String issuer,
            @NotNull List<FederatedAgentRegistration> registrations) {
        this.tokenIntrospector = Objects.requireNonNull(tokenIntrospector, "tokenIntrospector");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(registrations, "registrations");

        LinkedHashMap<String, FederatedAgentRegistration> byKey = new LinkedHashMap<>();
        for (FederatedAgentRegistration registration : registrations) {
            Objects.requireNonNull(registration, "registration");
            byKey.put(key(registration.tenantId(), registration.agentId()), registration);
        }
        this.registrations = Map.copyOf(byKey);
    }

    @Override
    public boolean supports(String tenantId, String agentId) {
        return registrations.containsKey(key(tenantId, agentId));
    }

    @Override
    public Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId) {
        FederatedAgentRegistration registration = registrations.get(key(tenantId, agentId));
        if (registration == null) {
            return Promise.of(Optional.empty());
        }

        return tokenIntrospector.introspect(registration.accessToken())
            .map(user -> resolveIdentity(registration, user))
            .whenException(e -> log.error(
                "Failed to resolve OIDC identity for tenant={} agent={}", tenantId, agentId, e));
    }

    private Optional<AgentIdentity> resolveIdentity(
            FederatedAgentRegistration registration,
            User user) {
        if (!registration.subject().equals(user.getUserId())) {
            log.warn(
                "OIDC subject mismatch for tenant={} agent={}: expected={}, actual={}",
                registration.tenantId(),
                registration.agentId(),
                registration.subject(),
                user.getUserId());
            return Optional.empty();
        }

        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        scopes.add("aep:execute");
        scopes.addAll(registration.scopes());

        return Optional.of(new AgentIdentity(
            registration.tenantId(),
            registration.agentId(),
            issuer + "/subject/" + registration.subject(),
            Set.copyOf(scopes),
            Instant.now()));
    }

    private static String issuerString(URI issuerUri) {
        return issuerUri != null ? issuerUri.toString() : "oidc://unknown-issuer";
    }

    private static String key(String tenantId, String agentId) {
        return tenantId + ":" + agentId;
    }

    /**
     * OIDC federation registration for a tenant-scoped AEP agent.
     */
    public record FederatedAgentRegistration(
            String tenantId,
            String agentId,
            String subject,
            String accessToken,
            Set<String> scopes) {

        public FederatedAgentRegistration {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(accessToken, "accessToken");
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        }
    }
}