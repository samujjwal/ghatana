package com.ghatana.yappc.infrastructure.datacloud.mapper;

import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.infrastructure.datacloud.entity.ProjectEntity;
import com.ghatana.yappc.infrastructure.security.EncryptionService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Mapper for converting YAPPC domain models to/from data-cloud entities.
 *
 * <p>Provides bidirectional mapping between YAPPC domain objects and
 * data-cloud {@link DataCloudClient.Entity} instances.
 *
 * @doc.type class
 * @doc.purpose YAPPC to data-cloud entity mapping
 * @doc.layer infrastructure
 * @doc.pattern Mapper/Adapter
 */
public class YappcEntityMapper {

    private static final Logger LOG = LoggerFactory.getLogger(YappcEntityMapper.class);
    private static final String PROJECT_ENVIRONMENT_VARIABLES_FIELD = "environmentVariables";
    private static final String ENCRYPTED_VALUE_PREFIX = "enc::";

    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    public YappcEntityMapper(@NotNull ObjectMapper objectMapper) {
        this(objectMapper, resolveOptionalEncryptionService());
    }

    public YappcEntityMapper(
            @NotNull ObjectMapper objectMapper,
            EncryptionService encryptionService) {
        this.objectMapper = objectMapper;
        this.encryptionService = encryptionService;
    }

    /**
     * Converts a YAPPC domain object to a data map suitable for data-cloud persistence.
     *
     * @param source Source domain object
     * @param <T> Source type
     * @return field map for data-cloud entity data
     */
    @SuppressWarnings("unchecked")
    @NotNull
    public <T> Map<String, Object> toEntityData(@NotNull T source) {
        Map<String, Object> raw = objectMapper.convertValue(source, Map.class);
        Map<String, Object> prepared = source instanceof ProjectEntity
            ? encryptProjectEnvironmentVariables(raw)
            : raw;
        // Map.copyOf (used in DataCloudClient.Entity canonical constructor) rejects null values
        return prepared.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Converts a data-cloud Entity back to a YAPPC domain object.
     *
     * @param entity Source entity from data-cloud
     * @param targetClass Target class type
     * @param <T> Target type
     * @return Domain object instance
     */
    @NotNull
    public <T> T fromEntity(
        @NotNull DataCloudClient.Entity entity,
        @NotNull Class<T> targetClass
    ) {
        Map<String, Object> prepared = targetClass.equals(ProjectEntity.class)
                ? decryptProjectEnvironmentVariables(entity.data())
                : entity.data();
        return objectMapper.convertValue(prepared, targetClass);
    }

    private static EncryptionService resolveOptionalEncryptionService() {
        return EncryptionService.tryFromConfiguredSources().orElse(null);
    }

    private Map<String, Object> encryptProjectEnvironmentVariables(@NotNull Map<String, Object> raw) {
        Object environmentVariables = raw.get(PROJECT_ENVIRONMENT_VARIABLES_FIELD);
        if (!(environmentVariables instanceof Map<?, ?> environmentVariableMap)
                || environmentVariableMap.isEmpty()) {
            return raw;
        }
        EncryptionService service = requireEncryptionService();
        Map<String, Object> encryptedEnvironmentVariables = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : environmentVariableMap.entrySet()) {
            String key = Objects.toString(entry.getKey(), null);
            Object value = entry.getValue();
            encryptedEnvironmentVariables.put(key, encryptEnvironmentVariableValue(service, value));
        }

        Map<String, Object> updated = new LinkedHashMap<>(raw);
        updated.put(PROJECT_ENVIRONMENT_VARIABLES_FIELD, encryptedEnvironmentVariables);
        return updated;
    }

    private Map<String, Object> decryptProjectEnvironmentVariables(@NotNull Map<String, Object> raw) {
        Object environmentVariables = raw.get(PROJECT_ENVIRONMENT_VARIABLES_FIELD);
        if (!(environmentVariables instanceof Map<?, ?> environmentVariableMap)
                || environmentVariableMap.isEmpty()) {
            return raw;
        }

        Map<String, Object> decryptedEnvironmentVariables = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : environmentVariableMap.entrySet()) {
            String key = Objects.toString(entry.getKey(), null);
            Object value = entry.getValue();
            decryptedEnvironmentVariables.put(key, decryptEnvironmentVariableValue(value));
        }

        Map<String, Object> updated = new LinkedHashMap<>(raw);
        updated.put(PROJECT_ENVIRONMENT_VARIABLES_FIELD, decryptedEnvironmentVariables);
        return updated;
    }

    private Object encryptEnvironmentVariableValue(EncryptionService service, Object value) {
        if (!(value instanceof String stringValue)) {
            return value;
        }
        if (stringValue.startsWith(ENCRYPTED_VALUE_PREFIX)) {
            return stringValue;
        }
        return ENCRYPTED_VALUE_PREFIX + service.encrypt(stringValue);
    }

    private Object decryptEnvironmentVariableValue(Object value) {
        if (!(value instanceof String stringValue) || !stringValue.startsWith(ENCRYPTED_VALUE_PREFIX)) {
            return value;
        }
        EncryptionService service = requireEncryptionService();
        String ciphertext = stringValue.substring(ENCRYPTED_VALUE_PREFIX.length());
        return service.decrypt(ciphertext);
    }

    private EncryptionService requireEncryptionService() {
        if (encryptionService == null) {
            throw new IllegalStateException(
                    "Encryption key must be configured via secret manager to persist or load ProjectEntity environmentVariables");
        }
        return encryptionService;
    }
}
