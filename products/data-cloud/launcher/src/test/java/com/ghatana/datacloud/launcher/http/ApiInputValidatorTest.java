package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ApiInputValidator}.
 *
 * <p>Covers every validation method and critical boundary conditions to ensure
 * comprehensive API input safety at all HTTP entry points.
 *
 * @doc.type class
 * @doc.purpose Verify all ApiInputValidator rules: size limits, safe chars, null checks
 * @doc.layer product
 * @doc.pattern Validator
 */
@DisplayName("ApiInputValidator")
class ApiInputValidatorTest {

    // -----------------------------------------------------------------------
    // tenantId
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateTenantId")
    class TenantIdTests {

        @Test
        void acceptsValidTenantId() {
            assertThat(ApiInputValidator.validateTenantId("acme-corp")).isEmpty();
            assertThat(ApiInputValidator.validateTenantId("tenant_123")).isEmpty();
            assertThat(ApiInputValidator.validateTenantId("my.tenant:1")).isEmpty();
        }

        @Test
        void rejectsNull() {
            assertThat(ApiInputValidator.validateTenantId(null)).isPresent();
        }

        @Test
        void rejectsBlank() {
            assertThat(ApiInputValidator.validateTenantId("")).isPresent();
            assertThat(ApiInputValidator.validateTenantId("   ")).isPresent();
        }

        @Test
        void rejectsTooLong() {
            String long128 = "a".repeat(ApiInputValidator.MAX_TENANT_ID_LEN + 1);
            assertThat(ApiInputValidator.validateTenantId(long128)).isPresent();
        }

        @Test
        void acceptsMaxLength() {
            String exact = "a".repeat(ApiInputValidator.MAX_TENANT_ID_LEN);
            assertThat(ApiInputValidator.validateTenantId(exact)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"tenant/hack", "tenant\0zero", "tenant;drop", "../etc"})
        void rejectsIllegalChars(String bad) {
            assertThat(ApiInputValidator.validateTenantId(bad)).isPresent();
        }

        @Test
        void rejectsXssAttempt() {
            assertThat(ApiInputValidator.validateTenantId("<script>alert(1)</script>")).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // collection
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateCollection")
    class CollectionTests {

        @Test
        void acceptsValidCollection() {
            assertThat(ApiInputValidator.validateCollection("users")).isEmpty();
            assertThat(ApiInputValidator.validateCollection("my-collection.v2")).isEmpty();
        }

        @Test
        void rejectsNull() {
            assertThat(ApiInputValidator.validateCollection(null)).isPresent();
        }

        @Test
        void rejectsBlank() {
            assertThat(ApiInputValidator.validateCollection("")).isPresent();
        }

        @Test
        void rejectsTooLong() {
            String tooLong = "x".repeat(ApiInputValidator.MAX_COLLECTION_LEN + 1);
            assertThat(ApiInputValidator.validateCollection(tooLong)).isPresent();
        }

        @ParameterizedTest
        @ValueSource(strings = {"coll/hack", "coll\ninjection", "col!@#"})
        void rejectsIllegalChars(String bad) {
            assertThat(ApiInputValidator.validateCollection(bad)).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // entity id
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateId")
    class IdTests {

        @Test
        void acceptsValidId() {
            assertThat(ApiInputValidator.validateId("entity-123")).isEmpty();
            assertThat(ApiInputValidator.validateId("UUID-style-11111111-2")).isEmpty();
        }

        @Test
        void rejectsNull() {
            assertThat(ApiInputValidator.validateId(null)).isPresent();
        }

        @Test
        void rejectsTooLong() {
            String tooLong = "a".repeat(ApiInputValidator.MAX_ID_LEN + 1);
            assertThat(ApiInputValidator.validateId(tooLong)).isPresent();
        }

        @ParameterizedTest
        @ValueSource(strings = {"../../etc/passwd", "id\0null", "id?q=1"})
        void rejectsPathTraversalAndInjection(String bad) {
            assertThat(ApiInputValidator.validateId(bad)).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // limit
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateLimit")
    class LimitTests {

        @Test
        void returnsDefaultWhenNull() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit(null, 100);
            assertThat(lr.isValid()).isTrue();
            assertThat(lr.getValue()).isEqualTo(100);
        }

        @Test
        void returnsDefaultWhenBlank() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("  ", 50);
            assertThat(lr.isValid()).isTrue();
            assertThat(lr.getValue()).isEqualTo(50);
        }

        @Test
        void parsesValidLimit() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("100", 10);
            assertThat(lr.isValid()).isTrue();
            assertThat(lr.getValue()).isEqualTo(100);
        }

        @Test
        void acceptsMaxAllowedLimit() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit(String.valueOf(ApiInputValidator.MAX_LIMIT), 10);
            assertThat(lr.isValid()).isTrue();
            assertThat(lr.getValue()).isEqualTo(ApiInputValidator.MAX_LIMIT);
        }

        @Test
        void rejectsZero() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("0", 10);
            assertThat(lr.isValid()).isFalse();
            assertThat(lr.getError()).isPresent();
        }

        @Test
        void rejectsNegative() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("-1", 10);
            assertThat(lr.isValid()).isFalse();
        }

        @Test
        void rejectsExceedsMax() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("9999999", 10);
            assertThat(lr.isValid()).isFalse();
        }

        @Test
        void rejectsNonNumeric() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("abc", 10);
            assertThat(lr.isValid()).isFalse();
            assertThat(lr.getError().orElseThrow()).contains("valid integer");
        }

        @Test
        void rejectsSqlInjectionAttempt() {
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("1; DROP TABLE users--", 10);
            assertThat(lr.isValid()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // search query
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateSearchQuery")
    class SearchQueryTests {

        @Test
        void acceptsSimpleQuery() {
            assertThat(ApiInputValidator.validateSearchQuery("hello world")).isEmpty();
        }

        @Test
        void acceptsLuceneSyntax() {
            assertThat(ApiInputValidator.validateSearchQuery("name:\"John Doe\" AND age:[25 TO 35]")).isEmpty();
        }

        @Test
        void rejectsNull() {
            assertThat(ApiInputValidator.validateSearchQuery(null)).isPresent();
        }

        @Test
        void rejectsBlank() {
            assertThat(ApiInputValidator.validateSearchQuery("")).isPresent();
        }

        @Test
        void rejectsTooLong() {
            String tooLong = "a".repeat(ApiInputValidator.MAX_SEARCH_QUERY_LEN + 1);
            assertThat(ApiInputValidator.validateSearchQuery(tooLong)).isPresent();
        }

        @Test
        void rejectsControlCharacters() {
            assertThat(ApiInputValidator.validateSearchQuery("ok\u0007bell")).isPresent();
            assertThat(ApiInputValidator.validateSearchQuery("ok\u0000null")).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // batch size
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateBatchSize")
    class BatchSizeTests {

        @Test
        void acceptsValidBatch() {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (int i = 0; i < 10; i++) batch.add(Map.of("key", i));
            assertThat(ApiInputValidator.validateBatchSize(batch)).isEmpty();
        }

        @Test
        void rejectsNull() {
            assertThat(ApiInputValidator.validateBatchSize(null)).isPresent();
        }

        @Test
        void rejectsEmpty() {
            assertThat(ApiInputValidator.validateBatchSize(List.of())).isPresent();
        }

        @Test
        void acceptsExactlyMaxBatchSize() {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (int i = 0; i < ApiInputValidator.MAX_BATCH_SIZE; i++) batch.add(Map.of());
            assertThat(ApiInputValidator.validateBatchSize(batch)).isEmpty();
        }

        @Test
        void rejectsExceedsMaxBatchSize() {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (int i = 0; i <= ApiInputValidator.MAX_BATCH_SIZE; i++) batch.add(Map.of());
            assertThat(ApiInputValidator.validateBatchSize(batch)).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // delete batch (ids)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateDeleteBatch")
    class DeleteBatchTests {

        @Test
        void acceptsValidIds() {
            assertThat(ApiInputValidator.validateDeleteBatch(List.of("id-1", "id-2", "id-3"))).isEmpty();
        }

        @Test
        void rejectsNullList() {
            assertThat(ApiInputValidator.validateDeleteBatch(null)).isPresent();
        }

        @Test
        void rejectsEmptyList() {
            assertThat(ApiInputValidator.validateDeleteBatch(List.of())).isPresent();
        }

        @Test
        void rejectsInvalidIdInList() {
            List<String> ids = new ArrayList<>();
            ids.add("valid-id");
            ids.add("invalid/path/../traversal");
            assertThat(ApiInputValidator.validateDeleteBatch(ids)).isPresent()
                .get().asString().contains("ids[1]");
        }
    }

    // -----------------------------------------------------------------------
    // entity payload
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateEntityPayload")
    class EntityPayloadTests {

        @Test
        void acceptsValidPayload() {
            assertThat(ApiInputValidator.validateEntityPayload(Map.of("name", "Alice", "age", 30))).isEmpty();
        }

        @Test
        void rejectsNullPayload() {
            assertThat(ApiInputValidator.validateEntityPayload(null)).isPresent();
        }

        @Test
        void rejectsEmptyPayload() {
            assertThat(ApiInputValidator.validateEntityPayload(Map.of())).isPresent();
        }

        @Test
        void rejectsStringValueWithControlChars() {
            Map<String, Object> data = new HashMap<>();
            data.put("field", "bad\u0000value");
            assertThat(ApiInputValidator.validateEntityPayload(data)).isPresent();
        }

        @Test
        void rejectsNestingTooDeep() {
            // Build a deeply nested map (> MAX_NESTING_DEPTH)
            Map<String, Object> inner = Map.of("leaf", "value");
            Map<String, Object> current = new HashMap<>(inner);
            for (int i = 0; i < ApiInputValidator.MAX_NESTING_DEPTH + 2; i++) {
                Map<String, Object> next = new HashMap<>();
                next.put("child", current);
                current = next;
            }
            assertThat(ApiInputValidator.validateEntityPayload(current)).isPresent();
        }

        @Test
        void acceptsNestedMapWithinDepthLimit() {
            Map<String, Object> data = Map.of(
                "level1", Map.of(
                    "level2", Map.of(
                        "level3", "deep but ok"
                    )
                )
            );
            assertThat(ApiInputValidator.validateEntityPayload(data)).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // sanitizeForMessage — security: no log injection
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("sanitizeForMessage")
    class SanitizeForMessageTests {

        @Test
        void stripsControlChars() {
            String result = ApiInputValidator.sanitizeForMessage("ok\u0007\r\nval");
            assertThat(result).doesNotContain("\u0007").doesNotContain("\r").doesNotContain("\n");
        }

        @Test
        void truncatesLongStrings() {
            String long200 = "a".repeat(200);
            String result = ApiInputValidator.sanitizeForMessage(long200);
            assertThat(result.length()).isLessThanOrEqualTo(132); // 128 + ellipsis
        }

        @Test
        void handlesNull() {
            assertThat(ApiInputValidator.sanitizeForMessage(null)).isEqualTo("<null>");
        }
    }

    // -----------------------------------------------------------------------
    // validateAll — accumulates violations
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateAll")
    class ValidateAllTests {

        @Test
        void returnsEmptyForValidInput() {
            assertThat(ApiInputValidator.validateAll("tenant-1", "users", Map.of("name", "Bob"))).isEmpty();
        }

        @Test
        void accumulatesMultipleViolations() {
            // null tenantId + null collection + null payload → all 3 violations joined
            Optional<String> result = ApiInputValidator.validateAll(null, null, null);
            assertThat(result).isPresent();
            // error message contains separator
            assertThat(result.get()).contains(";");
        }
    }
}
