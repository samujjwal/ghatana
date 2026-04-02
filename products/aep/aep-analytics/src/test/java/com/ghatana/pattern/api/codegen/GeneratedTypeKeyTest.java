package com.ghatana.pattern.api.codegen;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.EventParameterSpec;
import com.ghatana.platform.domain.event.EventType;
import com.ghatana.pattern.api.model.PatternSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("GeneratedTypeKey Tests")
class GeneratedTypeKeyTest {

    @Test
    @DisplayName("from builds canonical cache and class name tokens")
    void fromBuildsCanonicalCacheAndClassNameTokens() {
        EventType eventType = testEventType("tenant-a", "Order.Created", "1.2.3");
        PatternSpecification specification = PatternSpecification.builder()
                .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .name("High Value")
                .build();

        GeneratedTypeKey key = GeneratedTypeKey.from(eventType, specification, "abc123");

        assertEquals("tenant-a|Order.Created|1.2.3|22222222-2222-2222-2222-222222222222|abc123", key.asCacheKey());
        assertEquals("1_2_3", key.versionToken());
        assertEquals("OrderCreated__v1_2_3__p22222222222222222222222222222222", key.classNameToken());
        assertEquals("tenant-a/Order.Created:1.2.3 pattern=22222222-2222-2222-2222-222222222222 hash=abc123", key.describe());
    }

    @Test
    @DisplayName("constructor sanitizes blank input and from falls back when ids are absent")
    void constructorSanitizesBlankInputAndFromFallsBackWhenIdsAreAbsent() {
        EventType eventType = testEventType(null, "9Lead.Event", "2.0");
        PatternSpecification specification = PatternSpecification.builder()
                .name(" Anonymous Pattern ")
                .build();

        GeneratedTypeKey key = GeneratedTypeKey.from(eventType, specification, "hash-1");

        assertEquals("default", key.tenantId());
        assertEquals("9leadEvent__v2_0__pAnonymousPattern", key.classNameToken());

        assertThrows(IllegalArgumentException.class,
                () -> new GeneratedTypeKey(" ", "event", "1", "pattern", "hash"));
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
            @Override public com.ghatana.contracts.event.v1.SemanticVersionPojo getSemanticVersion() { return null; }
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
            @Override public com.ghatana.contracts.event.v1.GovernancePojo getGovernance() { return null; }
            @Override public com.ghatana.contracts.event.v1.EventStorageHintsPojo getStorageHints() { return null; }
            @Override public com.ghatana.contracts.event.v1.LifecycleStatusProto getStatus() { return null; }
            @Override public String getStatusMessage() { return null; }
            @Override public com.ghatana.contracts.common.v1.CompatibilityPolicyProto getCompatibilityPolicy() { return null; }
        };
    }
}