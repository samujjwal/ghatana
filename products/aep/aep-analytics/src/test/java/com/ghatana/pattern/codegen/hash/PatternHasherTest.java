package com.ghatana.pattern.codegen.hash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.EventParameterSpec;
import com.ghatana.platform.domain.event.EventType;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.api.model.PatternSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PatternHasher Tests")
class PatternHasherTest {

    @Test
    @DisplayName("hash is stable for identical input and changes when specification changes")
    void hashIsStableForIdenticalInputAndChangesWhenSpecificationChanges() { // GH-90000
        PatternHasher hasher = new PatternHasher(new ObjectMapper()); // GH-90000
        EventType eventType = testEventType("tenant-a", "Order.Created", "1.0"); // GH-90000
        PatternSpecification baseline = PatternSpecification.builder() // GH-90000
                .id(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .tenantId("tenant-a")
                .name("Rule A")
                .operator(OperatorSpec.builder().type("SEQ").build())
                .eventTypes(List.of("Order.Created"))
                .createdAt(null) // GH-90000
                .updatedAt(null) // GH-90000
                .build(); // GH-90000
        PatternSpecification changed = PatternSpecification.builder() // GH-90000
                .id(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .tenantId("tenant-a")
                .name("Rule A")
                .operator(OperatorSpec.builder().type("AND").build())
                .eventTypes(List.of("Order.Created"))
                .createdAt(null) // GH-90000
                .updatedAt(null) // GH-90000
                .build(); // GH-90000

        String hash1 = hasher.hash(eventType, baseline); // GH-90000
        String hash2 = hasher.hash(eventType, baseline); // GH-90000
        String hash3 = hasher.hash(eventType, changed); // GH-90000

        assertEquals(hash1, hash2); // GH-90000
        assertEquals(64, hash1.length()); // GH-90000
        assertNotEquals(hash1, hash3); // GH-90000
    }

    @Test
    @DisplayName("hash rejects null input")
    void hashRejectsNullInput() { // GH-90000
        PatternHasher hasher = new PatternHasher(new ObjectMapper()); // GH-90000
        PatternSpecification specification = PatternSpecification.builder().name("Rule A").build();

        assertThrows(NullPointerException.class, () -> hasher.hash(null, specification)); // GH-90000
        assertThrows(NullPointerException.class, () -> hasher.hash(testEventType("tenant-a", "Order.Created", "1.0"), null)); // GH-90000
    }

    private static EventType testEventType(String tenantId, String name, String version) { // GH-90000
        return new EventType() { // GH-90000
            @Override public String getVersion() { return version; } // GH-90000
            @Override public void validate(Event event) { } // GH-90000
            @Override public EventParameterSpec getHeader(String name) { return null; } // GH-90000
            @Override public EventParameterSpec getPayload(String name) { return null; } // GH-90000
            @Override public boolean hasAlias(String alias) { return false; } // GH-90000
            @Override public Event createEvent(byte[] data) { return null; } // GH-90000
            @Override public String getTenantId() { return tenantId; } // GH-90000
            @Override public String getName() { return name; } // GH-90000
            @Override public String getCategory() { return null; } // GH-90000
            @Override public String getNamespace() { return "orders"; } // GH-90000
            @Override public com.ghatana.contracts.event.v1.SemanticVersionProto getSemanticVersion() { return null; } // GH-90000
            @Override public com.ghatana.contracts.event.v1.EventContextTypeProto getContextType() { return null; } // GH-90000
            @Override public boolean isIntervalBased() { return false; } // GH-90000
            @Override public long getGranularity() { return 0; } // GH-90000
            @Override public String getDescription() { return null; } // GH-90000
            @Override public Set<String> getTags() { return Set.of(); } // GH-90000
            @Override public List<String> getExamples() { return List.of(); } // GH-90000
            @Override public Map<String, EventParameterSpec> getHeaders() { return Map.of(); } // GH-90000
            @Override public Map<String, EventParameterSpec> getPayload() { return Map.of(); } // GH-90000
            @Override public Boolean getSupportsConfidence() { return false; } // GH-90000
            @Override public Set<String> getAliases() { return Set.of(); } // GH-90000
            @Override public com.ghatana.contracts.event.v1.GovernanceProto getGovernance() { return null; } // GH-90000
            @Override public com.ghatana.contracts.event.v1.EventStorageHintsProto getStorageHints() { return null; } // GH-90000
            @Override public com.ghatana.contracts.event.v1.LifecycleStatusProto getStatus() { return null; } // GH-90000
            @Override public String getStatusMessage() { return null; } // GH-90000
            @Override public com.ghatana.contracts.common.v1.CompatibilityPolicyProto getCompatibilityPolicy() { return null; } // GH-90000
        };
    }
}
