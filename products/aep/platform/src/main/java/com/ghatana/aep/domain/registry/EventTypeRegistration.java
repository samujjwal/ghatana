package com.ghatana.aep.domain.registry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EventTypeRegistration {

    UUID eventTypeId;
    String registrationId;
    String tenantId;
    String namespace;
    String name;
    String eventTypeName;
    String schemaJson;
    String schemaVersion;
    String createdBy;
    String sourceHint;
    String consumerHint;
    List<String> tags;
    boolean active;
    Map<String, String> agentHints;
    Map<String, String> metadata;
    Instant createdAt;
    Instant updatedAt;

    /**
     * Validates the registration has required fields.
     *
     * @return true if valid
     */
    public boolean isValid() {
        return eventTypeId != null && tenantId != null && !tenantId.isBlank();
    }
}
