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

@DisplayName("GeneratedTypeKey Tests [GH-90000]")
class GeneratedTypeKeyTest {

    @Test
    @DisplayName("from builds canonical cache and class name tokens [GH-90000]")
    void fromBuildsCanonicalCacheAndClassNameTokens() { // GH-90000
        EventType eventType = testEventType("tenant-a", "Order.Created", "1.2.3"); // GH-90000
        PatternSpecification specification = PatternSpecification.builder() // GH-90000
                .id(UUID.fromString("22222222-2222-2222-2222-222222222222 [GH-90000]"))
                .name("High Value [GH-90000]")
                .build(); // GH-90000

        GeneratedTypeKey key = GeneratedTypeKey.from(eventType, specification, "abc123"); // GH-90000

        assertEquals("tenant-a|Order.Created|1.2.3|22222222-2222-2222-2222-222222222222|abc123", key.asCacheKey()); // GH-90000
        assertEquals("1_2_3", key.versionToken()); // GH-90000
        assertEquals("OrderCreated__v1_2_3__p22222222222222222222222222222222", key.classNameToken()); // GH-90000
        assertEquals("tenant-a/Order.Created:1.2.3 pattern=22222222-2222-2222-2222-222222222222 hash=abc123", key.describe()); // GH-90000
    }

    @Test
    @DisplayName("constructor sanitizes blank input and from falls back when ids are absent [GH-90000]")
    void constructorSanitizesBlankInputAndFromFallsBackWhenIdsAreAbsent() { // GH-90000
        EventType eventType = testEventType(null, "9Lead.Event", "2.0"); // GH-90000
        PatternSpecification specification = PatternSpecification.builder() // GH-90000
                .name(" Anonymous Pattern  [GH-90000]")
                .build(); // GH-90000

        GeneratedTypeKey key = GeneratedTypeKey.from(eventType, specification, "hash-1"); // GH-90000

        assertEquals("default", key.tenantId()); // GH-90000
        assertEquals("9leadEvent__v2_0__pAnonymousPattern", key.classNameToken()); // GH-90000

        assertThrows(IllegalArgumentException.class, // GH-90000
                () -> new GeneratedTypeKey(" ", "event", "1", "pattern", "hash")); // GH-90000
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
