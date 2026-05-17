package com.ghatana.yappc.services.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Env-configured credential binding repository for governed source credential resolution
 * @doc.layer service
 * @doc.pattern Repository
 */
public final class EnvBackedSourceCredentialRepository implements SourceCredentialRepository {

    private static final TypeReference<Map<String, Map<String, String>>> REGISTRY_TYPE = new TypeReference<>() { };

    private final Map<String, Map<String, String>> bindings;

    private EnvBackedSourceCredentialRepository(Map<String, Map<String, String>> bindings) {
        this.bindings = bindings;
    }

    public static EnvBackedSourceCredentialRepository fromEnvironment(ObjectMapper objectMapper) {
        String payload = System.getenv("YAPPC_SOURCE_CREDENTIAL_REGISTRY_JSON");
        if (payload == null || payload.isBlank()) {
            return new EnvBackedSourceCredentialRepository(Map.of());
        }
        try {
            Map<String, Map<String, String>> parsed = objectMapper.readValue(payload, REGISTRY_TYPE);
            return new EnvBackedSourceCredentialRepository(parsed == null ? Map.of() : parsed);
        } catch (Exception ignored) {
            return new EnvBackedSourceCredentialRepository(Map.of());
        }
    }

    @Override
    public Optional<CredentialBinding> findBinding(
        String tenantId,
        String workspaceId,
        String projectId,
        String provider,
        String credentialRef
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(credentialRef, "credentialRef must not be null");

        String key = tenantId + "/" + workspaceId + "/" + projectId + "/" + provider + "/" + credentialRef;
        Map<String, String> binding = bindings.get(key);
        if (binding == null || binding.get("secretKey") == null || binding.get("secretKey").isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new CredentialBinding(
            tenantId,
            workspaceId,
            projectId,
            provider,
            credentialRef,
            binding.get("secretKey")
        ));
    }
}
