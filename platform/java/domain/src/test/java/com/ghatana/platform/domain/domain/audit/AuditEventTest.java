package com.ghatana.platform.domain.domain.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class AuditEventTest {
    
    private ObjectMapper yamlMapper;
    
    @BeforeEach
    void setUp() {
        yamlMapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception {
        // Given
        AuditEvent original = AuditEvent.builder()
            .tenantId("test-tenant")
            .eventType("USER_LOGIN")
            .principal("user123")
            .timestamp(Instant.parse("2025-10-07T22:00:00Z"))
            .details(Map.of("ip", "192.168.1.1", "userAgent", "Mozilla/5.0"))
            .success(true)
            .resourceId("res123")
            .resourceType("USER")
            .build();
            
        // When
        String yaml = yamlMapper.writeValueAsString(original);
        AuditEvent deserialized = yamlMapper.readValue(yaml, AuditEvent.class);
        
        // Then
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getTenantId()).isEqualTo("test-tenant");
        assertThat(deserialized.getEventType()).isEqualTo("USER_LOGIN");
        assertThat(deserialized.getPrincipal()).isEqualTo("user123");
        assertThat(deserialized.getTimestamp()).isEqualTo(Instant.parse("2025-10-07T22:00:00Z"));
        assertThat(deserialized.getDetails())
            .hasSize(2)
            .containsEntry("ip", "192.168.1.1")
            .containsEntry("userAgent", "Mozilla/5.0");
        assertThat(deserialized.getSuccess()).isTrue();
        assertThat(deserialized.getResourceId()).isEqualTo("res123");
        assertThat(deserialized.getResourceType()).isEqualTo("USER");
    }
    
    @Test
    void shouldHandleNullValues() {
        // When
        AuditEvent event = AuditEvent.builder()
            .tenantId("test-tenant")
            .eventType("TEST_EVENT")
            .timestamp(Instant.now())
            .build();
            
        // Then
        assertThat(event.getDetails()).isEmpty();
        assertThat((Object) event.getDetail("nonexistent")).isNull();
    }
    
    @ParameterizedTest
    @MethodSource("missingRequiredFieldCases")
    void shouldFailDeserializationWithMissingRequiredFields(String yaml, String missingField) {
        // When/Then — Jackson may report the error as "Missing required creator property"
        // or our custom message "Required field '...' is missing or null"
        assertThatThrownBy(() -> yamlMapper.readValue(yaml, AuditEvent.class))
            .isInstanceOf(MismatchedInputException.class)
            .hasMessageContaining(missingField);
    }

    private static Stream<Arguments> missingRequiredFieldCases() {
        return Stream.of(
            Arguments.of(
                "tenantId: test-tenant\neventType: null\ntimestamp: 2025-10-07T22:00:00Z",
                "eventType"
            ),
            Arguments.of(
                "tenantId: test-tenant\neventType: USER_LOGIN\ntimestamp: null",
                "timestamp"
            ),
            Arguments.of(
                "tenantId: null\neventType: USER_LOGIN\ntimestamp: 2025-10-07T22:00:00Z",
                "tenantId"
            )
        );
    }
    
    @Test
    void shouldHandleNestedObjectsInDetails() throws Exception {
        // Given
        AuditEvent original = AuditEvent.builder()
            .tenantId("test-tenant")
            .eventType("COMPLEX_EVENT")
            .timestamp(Instant.now())
            .detail("nested", Map.of("key1", "value1", "key2", 42))
            .build();
            
        // When
        String yaml = yamlMapper.writeValueAsString(original);
        AuditEvent deserialized = yamlMapper.readValue(yaml, AuditEvent.class);
        
        // Then
        Map<String, Object> details = deserialized.getDetails();
        assertThat(details).hasSize(1);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) details.get("nested");
        assertThat(nested)
            .containsEntry("key1", "value1")
            .containsEntry("key2", 42);
    }
    
    @Test
    void shouldHandleToBuilder() {
        // Given
        AuditEvent original = AuditEvent.builder()
            .tenantId("test-tenant")
            .eventType("ORIGINAL_EVENT")
            .timestamp(Instant.now())
            .principal("user1")
            .build();
            
        // When
        AuditEvent modified = original.toBuilder()
            .eventType("MODIFIED_EVENT")
            .build();
            
        // Then
        assertThat(modified.getEventType()).isEqualTo("MODIFIED_EVENT");
        assertThat(modified.getPrincipal()).isEqualTo("user1");
        assertThat(modified.getTimestamp()).isEqualTo(original.getTimestamp());
    }
    
    @Test
    void shouldHandleGetDetailWithType() {
        // Given
        AuditEvent event = AuditEvent.builder()
            .tenantId("test-tenant")
            .eventType("TYPED_EVENT")
            .timestamp(Instant.now())
            .detail("stringValue", "test")
            .detail("intValue", 42)
            .build();
            
        // When/Then - Use explicit type parameters for assertThat to avoid ambiguity
        assertThat(event.<String>getDetail("stringValue")).isEqualTo("test");
        assertThat(event.<Integer>getDetail("intValue")).isEqualTo(42);
        assertThat((Object) event.getDetail("nonexistent")).isNull();
    }
    
    @Test
    void shouldHandleGetDetailWithDefault() {
        // Given
        AuditEvent event = AuditEvent.builder()
            .tenantId("test-tenant")
            .eventType("DEFAULT_EVENT")
            .timestamp(Instant.now())
            .detail("exists", "value")
            .build();
            
        // When/Then
        assertThat(event.getDetail("exists", "default")).isEqualTo("value");
        assertThat(event.getDetail("nonexistent", "default")).isEqualTo("default");
    }
}
