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
    void hashIsStableForIdenticalInputAndChangesWhenSpecificationChanges() {
        PatternHasher hasher = new PatternHasher(new ObjectMapper());
        EventType eventType = testEventType("tenant-a", "Order.Created", "1.0");
        PatternSpecification baseline = PatternSpecification.builder()
                .id(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .tenantId("tenant-a")
                .name("Rule A")
                .operator(OperatorSpec.builder().type("SEQ").build())
                .eventTypes(List.of("Order.Created"))
                .createdAt(null)
                .updatedAt(null)
                .build();
        PatternSpecification changed = PatternSpecification.builder()
                .id(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .tenantId("tenant-a")
                .name("Rule A")
                .operator(OperatorSpec.builder().type("AND").build())
                .eventTypes(List.of("Order.Created"))
                .createdAt(null)
                .updatedAt(null)
                .build();

        String hash1 = hasher.hash(eventType, baseline);
        String hash2 = hasher.hash(eventType, baseline);
        String hash3 = hasher.hash(eventType, changed);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
        assertNotEquals(hash1, hash3);
    }

    @Test
    @DisplayName("hash rejects null input")
    void hashRejectsNullInput() {
        PatternHasher hasher = new PatternHasher(new ObjectMapper());
        PatternSpecification specification = PatternSpecification.builder().name("Rule A").build();

        assertThrows(NullPointerException.class, () -> hasher.hash(null, specification));
        assertThrows(NullPointerException.class, () -> hasher.hash(testEventType("tenant-a", "Order.Created", "1.0"), null));
    }

    private static EventType testEventType(String tenantId, String name, String version) {
        return new EventType() {
            @Override public String getVersion() { return version; }
            @Override public void validate(Event event) { }
            @Override public EventParameterSpec getHeader(String name) { return null; }
            @Override public EventParameterSpec getPayload(String name) { return null; }
            @Override public boolean hasAlias(String alias) { return false; }
            @Override public Event createEvent(byte[] data) { return null; }
            @Override public String getTenantId() { return tenantId; }
            @Override public String getName() { return name; }
            @Override public String getCategory() { return null; }
            @Override public String getNamespace() { return "orders"; }
            @Override public com.ghatana.contracts.event.v1.SemanticVersionProto getSemanticVersion() { return null; }
            @Override public com.ghatana.contracts.event.v1.EventContextTypeProto getContextType() { return null; }
            @Override public boolean isIntervalBased() { return false; }
            @Override public long getGranularity() { return 0; }
            @Override public String getDescription() { return null; }
            @Override public Set<String> getTags() { return Set.of(); }
            @Override public List<String> getExamples() { return List.of(); }
            @Override public Map<String, EventParameterSpec> getHeaders() { return Map.of(); }
            @Override public Map<String, EventParameterSpec> getPayload() { return Map.of(); }
            @Override public Boolean getSupportsConfidence() { return false; }
            @Override public Set<String> getAliases() { return Set.of(); }
            @Override public com.ghatana.contracts.event.v1.GovernanceProto getGovernance() { return null; }
            @Override public com.ghatana.contracts.event.v1.EventStorageHintsProto getStorageHints() { return null; }
            @Override public com.ghatana.contracts.event.v1.LifecycleStatusProto getStatus() { return null; }
            @Override public String getStatusMessage() { return null; }
            @Override public com.ghatana.contracts.common.v1.CompatibilityPolicyProto getCompatibilityPolicy() { return null; }
        };
    }
}
