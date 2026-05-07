/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepInputValidator}.
 *
 * @doc.type class
 * @doc.purpose Validates input sanitization rules: IDs, event types, payloads, batch sizes
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("AepInputValidator")
class AepInputValidatorTest {

    // ─── validateTenantId ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateTenantId()")
    class ValidateTenantIdTests {

        @Test
        @DisplayName("accepts valid alphanumeric tenant ID")
        void acceptsValidTenantId() {
            assertThat(AepInputValidator.validateTenantId("tenant-123_abc")).isEqualTo("tenant-123_abc");
        }

        @Test
        @DisplayName("throws on null tenant ID")
        void throwsOnNullTenantId() {
            assertThatThrownBy(() -> AepInputValidator.validateTenantId(null))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("throws on blank tenant ID")
        void throwsOnBlankTenantId() {
            assertThatThrownBy(() -> AepInputValidator.validateTenantId("   "))
                    .isInstanceOf(AepInputValidator.ValidationException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"tenant@domain", "tenant/slash", "tenant<script>", "tenant;drop"})
        @DisplayName("throws on illegal characters in tenant ID")
        void throwsOnIllegalCharacters(String tenantId) {
            assertThatThrownBy(() -> AepInputValidator.validateTenantId(tenantId))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("illegal characters");
        }

        @Test
        @DisplayName("accepts tenant ID at max boundary (64 chars)")
        void acceptsMaxBoundaryTenantId() {
            String maxId = "a".repeat(64);
            assertThat(AepInputValidator.validateTenantId(maxId)).isEqualTo(maxId);
        }
    }

    // ─── validateResourceId ────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateResourceId()")
    class ValidateResourceIdTests {

        @Test
        @DisplayName("accepts valid resource ID with dots and hyphens")
        void acceptsValidResourceId() {
            String id = "my-pipeline.v1_beta";
            assertThat(AepInputValidator.validateResourceId(id, "pipelineId")).isEqualTo(id);
        }

        @Test
        @DisplayName("throws on null resource ID")
        void throwsOnNullResourceId() {
            assertThatThrownBy(() -> AepInputValidator.validateResourceId(null, "agentId"))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("throws on resource ID exceeding max length")
        void throwsOnTooLongResourceId() {
            String tooLong = "a".repeat(AepInputValidator.MAX_ID_LENGTH + 1);
            assertThatThrownBy(() -> AepInputValidator.validateResourceId(tooLong, "agentId"))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("exceeds maximum length");
        }

        @ParameterizedTest
        @ValueSource(strings = {"id with space", "id@host", "<script>", "id;drop"})
        @DisplayName("throws on illegal resource ID characters")
        void throwsOnIllegalResourceIdChars(String id) {
            assertThatThrownBy(() -> AepInputValidator.validateResourceId(id, "agentId"))
                    .isInstanceOf(AepInputValidator.ValidationException.class);
        }
    }

    // ─── validateEventType ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateEventType()")
    class ValidateEventTypeTests {

        @Test
        @DisplayName("accepts valid event type with dots")
        void acceptsValidEventType() {
            assertThat(AepInputValidator.validateEventType("transaction.created")).isEqualTo("transaction.created");
        }

        @Test
        @DisplayName("throws on null event type")
        void throwsOnNullEventType() {
            assertThatThrownBy(() -> AepInputValidator.validateEventType(null))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("event type");
        }

        @ParameterizedTest
        @ValueSource(strings = {"event type", "event@host", "<xss>", "event;drop"})
        @DisplayName("throws on illegal event type characters")
        void throwsOnIllegalEventTypeChars(String eventType) {
            assertThatThrownBy(() -> AepInputValidator.validateEventType(eventType))
                    .isInstanceOf(AepInputValidator.ValidationException.class);
        }
    }

    // ─── validatePayload ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("validatePayload()")
    class ValidatePayloadTests {

        @Test
        @DisplayName("accepts normal payload map")
        void acceptsNormalPayload() {
            Map<String, Object> payload = Map.of("id", 1, "name", "Alice", "score", 9.5);
            assertThat(AepInputValidator.validatePayload(payload)).isSameAs(payload);
        }

        @Test
        @DisplayName("throws on null payload")
        void throwsOnNullPayload() {
            assertThatThrownBy(() -> AepInputValidator.validatePayload(null))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("payload must not be null");
        }

        @Test
        @DisplayName("throws when top-level key count exceeds MAX_PAYLOAD_KEYS")
        void throwsOnTooManyKeys() {
            Map<String, Object> payload = new HashMap<>();
            for (int i = 0; i <= AepInputValidator.MAX_PAYLOAD_KEYS; i++) {
                payload.put("key" + i, "value");
            }
            assertThatThrownBy(() -> AepInputValidator.validatePayload(payload))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("exceeds maximum");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "<script>alert('xss')</script>",
            "javascript:void(0)",
            "<iframe src='evil.com'>",
            "onclick=doEvil()"
        })
        @DisplayName("throws on XSS injection patterns in payload values")
        void throwsOnXssPatterns(String injectionValue) {
            Map<String, Object> payload = Map.of("description", injectionValue);
            assertThatThrownBy(() -> AepInputValidator.validatePayload(payload))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("disallowed content");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "1=1; DROP TABLE users",
            "union select * from accounts",
            "'; DELETE FROM events --"
        })
        @DisplayName("throws on SQL injection patterns in payload values")
        void throwsOnSqlInjectionPatterns(String sqlPayload) {
            Map<String, Object> payload = Map.of("query", sqlPayload);
            assertThatThrownBy(() -> AepInputValidator.validatePayload(payload))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("disallowed");
        }

        @Test
        @DisplayName("throws on string value exceeding MAX_STRING_VALUE_LENGTH")
        void throwsOnOversizedStringValue() {
            String oversized = "x".repeat(AepInputValidator.MAX_STRING_VALUE_LENGTH + 1);
            Map<String, Object> payload = Map.of("data", oversized);
            assertThatThrownBy(() -> AepInputValidator.validatePayload(payload))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("exceeds maximum string length");
        }

        @Test
        @DisplayName("accepts nested map payload within depth limit")
        void acceptsNestedMapPayload() {
            Map<String, Object> nested = Map.of("inner", Map.of("key", "value"));
            assertThat(AepInputValidator.validatePayload(nested)).isSameAs(nested);
        }

        @Test
        @DisplayName("accepts numeric and boolean payload values without throwing")
        void acceptsNumericAndBooleanValues() {
            Map<String, Object> payload = Map.of("count", 42, "enabled", true, "score", 3.14);
            assertThat(AepInputValidator.validatePayload(payload)).isSameAs(payload);
        }
    }

    // ─── validateBatchSize ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateBatchSize()")
    class ValidateBatchSizeTests {

        @Test
        @DisplayName("accepts single event batch")
        void acceptsSingleEventBatch() {
            // No exception expected
            AepInputValidator.validateBatchSize(1);
        }

        @Test
        @DisplayName("accepts max batch size")
        void acceptsMaxBatchSize() {
            AepInputValidator.validateBatchSize(AepInputValidator.MAX_BATCH_SIZE);
        }

        @Test
        @DisplayName("throws on zero batch size")
        void throwsOnZeroBatchSize() {
            assertThatThrownBy(() -> AepInputValidator.validateBatchSize(0))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("at least one event");
        }

        @Test
        @DisplayName("throws on negative batch size")
        void throwsOnNegativeBatchSize() {
            assertThatThrownBy(() -> AepInputValidator.validateBatchSize(-5))
                    .isInstanceOf(AepInputValidator.ValidationException.class);
        }

        @Test
        @DisplayName("throws on batch exceeding MAX_BATCH_SIZE")
        void throwsOnOversizedBatch() {
            assertThatThrownBy(() -> AepInputValidator.validateBatchSize(AepInputValidator.MAX_BATCH_SIZE + 1))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("exceeds maximum");
        }
    }

    // ─── requireNonBlank ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("requireNonBlank()")
    class RequireNonBlankTests {

        @Test
        @DisplayName("returns value when non-blank")
        void returnsValueWhenNonBlank() {
            assertThat(AepInputValidator.requireNonBlank("hello", "field")).isEqualTo("hello");
        }

        @Test
        @DisplayName("throws on blank value")
        void throwsOnBlankValue() {
            assertThatThrownBy(() -> AepInputValidator.requireNonBlank("  ", "description"))
                    .isInstanceOf(AepInputValidator.ValidationException.class)
                    .hasMessageContaining("description");
        }

        @Test
        @DisplayName("throws on null value")
        void throwsOnNullValue() {
            assertThatThrownBy(() -> AepInputValidator.requireNonBlank(null, "description"))
                    .isInstanceOf(AepInputValidator.ValidationException.class);
        }
    }

    // ─── isValidTenantId / isValidPipelineId ───────────────────────────────────

    @Nested
    @DisplayName("Boolean predicate methods")
    class BooleanPredicateTests {

        @Test
        @DisplayName("isValidTenantId returns true for valid ID")
        void isValidTenantIdReturnsTrueForValid() {
            assertThat(AepInputValidator.isValidTenantId("tenant-abc")).isTrue();
        }

        @Test
        @DisplayName("isValidTenantId returns false for null")
        void isValidTenantIdReturnsFalseForNull() {
            assertThat(AepInputValidator.isValidTenantId(null)).isFalse();
        }

        @Test
        @DisplayName("isValidTenantId returns false for invalid characters")
        void isValidTenantIdReturnsFalseForInvalidChars() {
            assertThat(AepInputValidator.isValidTenantId("tenant@domain")).isFalse();
        }

        @Test
        @DisplayName("isValidPipelineId returns true for valid dotted ID")
        void isValidPipelineIdReturnsTrueForValid() {
            assertThat(AepInputValidator.isValidPipelineId("pipeline.v1.beta")).isTrue();
        }

        @Test
        @DisplayName("isValidPipelineId returns false for null")
        void isValidPipelineIdReturnsFalseForNull() {
            assertThat(AepInputValidator.isValidPipelineId(null)).isFalse();
        }

        @Test
        @DisplayName("isValidJson returns true for JSON object")
        void isValidJsonReturnsTrueForObject() {
            assertThat(AepInputValidator.isValidJson("{\"key\":\"value\"}")).isTrue();
        }

        @Test
        @DisplayName("isValidJson returns true for JSON array")
        void isValidJsonReturnsTrueForArray() {
            assertThat(AepInputValidator.isValidJson("[1,2,3]")).isTrue();
        }

        @Test
        @DisplayName("isValidJson returns false for plain string")
        void isValidJsonReturnsFalseForPlainString() {
            assertThat(AepInputValidator.isValidJson("not-json")).isFalse();
        }

        @Test
        @DisplayName("isValidJson returns false for null")
        void isValidJsonReturnsFalseForNull() {
            assertThat(AepInputValidator.isValidJson(null)).isFalse();
        }
    }
}
