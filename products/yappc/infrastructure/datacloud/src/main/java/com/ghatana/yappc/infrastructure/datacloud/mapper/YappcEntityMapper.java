package com.ghatana.yappc.infrastructure.datacloud.mapper;

import com.ghatana.datacloud.entity.Entity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Mapper for converting YAPPC domain models to/from data-cloud entities.
 * 
 * <p>Provides bidirectional mapping between YAPPC JPA entities and
 * data-cloud {@link Entity} instances.
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
     * Converts a YAPPC domain object to a data-cloud Entity.
     * 
     * @param source Source domain object
     * @param collectionName Collection name in data-cloud
     * @param tenantId Tenant identifier
     * @param <T> Source type
     * @return Entity instance
     */
    @NotNull
    public <T> Entity toEntity(
        @NotNull T source,
        @NotNull String collectionName,
        @NotNull String tenantId
    ) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldMap = objectMapper.convertValue(source, Map.class);
        
        return Entity.builder()
            .tenantId(tenantId)
            .collectionName(collectionName)
            .data(fieldMap)
            .build();
    }
    
    /**
     * Converts a data-cloud Entity back to YAPPC domain object.
     * 
     * @param entity Source Entity
     * @param targetClass Target class type
     * @param <T> Target type
     * @return Domain object instance
     */
    @NotNull
    public <T> T fromEntity(
        @NotNull Entity entity,
        @NotNull Class<T> targetClass
    ) {
        return objectMapper.convertValue(entity.getData(), targetClass);
    }
    
    private UUID extractId(Map<String, Object> fieldMap) {
        Object idValue = fieldMap.get("id");
        if (idValue instanceof String) {
            return UUID.fromString((String) idValue);
        } else if (idValue instanceof UUID) {
            return (UUID) idValue;
        } else {
            return UUID.randomUUID();
        }
    }
}
