package com.ghatana.yappc.infrastructure.datacloud.mapper;

import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
    private final ObjectMapper objectMapper;

    public YappcEntityMapper(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        // Map.copyOf (used in DataCloudClient.Entity canonical constructor) rejects null values
        return raw.entrySet().stream()
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
        return objectMapper.convertValue(entity.data(), targetClass);
    }
}
