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
@DisplayName("ApiInputValidator [GH-90000]")
class ApiInputValidatorTest {

    // -----------------------------------------------------------------------
    // tenantId
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateTenantId [GH-90000]")
    class TenantIdTests {

        @Test
        void acceptsValidTenantId() { // GH-90000
            assertThat(ApiInputValidator.validateTenantId("acme-corp [GH-90000]")).isEmpty();
            assertThat(ApiInputValidator.validateTenantId("tenant_123 [GH-90000]")).isEmpty();
            assertThat(ApiInputValidator.validateTenantId("my.tenant:1 [GH-90000]")).isEmpty();
        }

        @Test
        void rejectsNull() { // GH-90000
            assertThat(ApiInputValidator.validateTenantId(null)).isPresent(); // GH-90000
        }

        @Test
        void rejectsBlank() { // GH-90000
            assertThat(ApiInputValidator.validateTenantId(" [GH-90000]")).isPresent();
            assertThat(ApiInputValidator.validateTenantId("    [GH-90000]")).isPresent();
        }

        @Test
        void rejectsTooLong() { // GH-90000
            String long128 = "a".repeat(ApiInputValidator.MAX_TENANT_ID_LEN + 1); // GH-90000
            assertThat(ApiInputValidator.validateTenantId(long128)).isPresent(); // GH-90000
        }

        @Test
        void acceptsMaxLength() { // GH-90000
            String exact = "a".repeat(ApiInputValidator.MAX_TENANT_ID_LEN); // GH-90000
            assertThat(ApiInputValidator.validateTenantId(exact)).isEmpty(); // GH-90000
        }

        @ParameterizedTest
        @ValueSource(strings = {"tenant/hack", "tenant\0zero", "tenant;drop", "../etc"}) // GH-90000
        void rejectsIllegalChars(String bad) { // GH-90000
            assertThat(ApiInputValidator.validateTenantId(bad)).isPresent(); // GH-90000
        }

        @Test
        void rejectsXssAttempt() { // GH-90000
            assertThat(ApiInputValidator.validateTenantId("<script>alert(1)</script> [GH-90000]")).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // collection
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateCollection [GH-90000]")
    class CollectionTests {

        @Test
        void acceptsValidCollection() { // GH-90000
            assertThat(ApiInputValidator.validateCollection("users [GH-90000]")).isEmpty();
            assertThat(ApiInputValidator.validateCollection("my-collection.v2 [GH-90000]")).isEmpty();
        }

        @Test
        void rejectsNull() { // GH-90000
            assertThat(ApiInputValidator.validateCollection(null)).isPresent(); // GH-90000
        }

        @Test
        void rejectsBlank() { // GH-90000
            assertThat(ApiInputValidator.validateCollection(" [GH-90000]")).isPresent();
        }

        @Test
        void rejectsTooLong() { // GH-90000
            String tooLong = "x".repeat(ApiInputValidator.MAX_COLLECTION_LEN + 1); // GH-90000
            assertThat(ApiInputValidator.validateCollection(tooLong)).isPresent(); // GH-90000
        }

        @ParameterizedTest
        @ValueSource(strings = {"coll/hack", "coll\ninjection", "col!@#"}) // GH-90000
        void rejectsIllegalChars(String bad) { // GH-90000
            assertThat(ApiInputValidator.validateCollection(bad)).isPresent(); // GH-90000
        }
    }

    // -----------------------------------------------------------------------
    // entity id
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateId [GH-90000]")
    class IdTests {

        @Test
        void acceptsValidId() { // GH-90000
            assertThat(ApiInputValidator.validateId("entity-123 [GH-90000]")).isEmpty();
            assertThat(ApiInputValidator.validateId("UUID-style-11111111-2 [GH-90000]")).isEmpty();
        }

        @Test
        void rejectsNull() { // GH-90000
            assertThat(ApiInputValidator.validateId(null)).isPresent(); // GH-90000
        }

        @Test
        void rejectsTooLong() { // GH-90000
            String tooLong = "a".repeat(ApiInputValidator.MAX_ID_LEN + 1); // GH-90000
            assertThat(ApiInputValidator.validateId(tooLong)).isPresent(); // GH-90000
        }

        @ParameterizedTest
        @ValueSource(strings = {"../../etc/passwd", "id\0null", "id?q=1"}) // GH-90000
        void rejectsPathTraversalAndInjection(String bad) { // GH-90000
            assertThat(ApiInputValidator.validateId(bad)).isPresent(); // GH-90000
        }
    }

    // -----------------------------------------------------------------------
    // limit
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateLimit [GH-90000]")
    class LimitTests {

        @Test
        void returnsDefaultWhenNull() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit(null, 100); // GH-90000
            assertThat(lr.isValid()).isTrue(); // GH-90000
            assertThat(lr.getValue()).isEqualTo(100); // GH-90000
        }

        @Test
        void returnsDefaultWhenBlank() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("  ", 50); // GH-90000
            assertThat(lr.isValid()).isTrue(); // GH-90000
            assertThat(lr.getValue()).isEqualTo(50); // GH-90000
        }

        @Test
        void parsesValidLimit() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("100", 10); // GH-90000
            assertThat(lr.isValid()).isTrue(); // GH-90000
            assertThat(lr.getValue()).isEqualTo(100); // GH-90000
        }

        @Test
        void acceptsMaxAllowedLimit() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit(String.valueOf(ApiInputValidator.MAX_LIMIT), 10); // GH-90000
            assertThat(lr.isValid()).isTrue(); // GH-90000
            assertThat(lr.getValue()).isEqualTo(ApiInputValidator.MAX_LIMIT); // GH-90000
        }

        @Test
        void rejectsZero() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("0", 10); // GH-90000
            assertThat(lr.isValid()).isFalse(); // GH-90000
            assertThat(lr.getError()).isPresent(); // GH-90000
        }

        @Test
        void rejectsNegative() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("-1", 10); // GH-90000
            assertThat(lr.isValid()).isFalse(); // GH-90000
        }

        @Test
        void rejectsExceedsMax() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("9999999", 10); // GH-90000
            assertThat(lr.isValid()).isFalse(); // GH-90000
        }

        @Test
        void rejectsNonNumeric() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("abc", 10); // GH-90000
            assertThat(lr.isValid()).isFalse(); // GH-90000
            assertThat(lr.getError().orElseThrow()).contains("valid integer [GH-90000]");
        }

        @Test
        void rejectsSqlInjectionAttempt() { // GH-90000
            ApiInputValidator.LimitResult lr = ApiInputValidator.validateLimit("1; DROP TABLE users--", 10); // GH-90000
            assertThat(lr.isValid()).isFalse(); // GH-90000
        }
    }

    // -----------------------------------------------------------------------
    // search query
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateSearchQuery [GH-90000]")
    class SearchQueryTests {

        @Test
        void acceptsSimpleQuery() { // GH-90000
            assertThat(ApiInputValidator.validateSearchQuery("hello world [GH-90000]")).isEmpty();
        }

        @Test
        void acceptsLuceneSyntax() { // GH-90000
            assertThat(ApiInputValidator.validateSearchQuery("name:\"John Doe\" AND age:[25 TO 35]")).isEmpty(); // GH-90000
        }

        @Test
        void rejectsNull() { // GH-90000
            assertThat(ApiInputValidator.validateSearchQuery(null)).isPresent(); // GH-90000
        }

        @Test
        void rejectsBlank() { // GH-90000
            assertThat(ApiInputValidator.validateSearchQuery(" [GH-90000]")).isPresent();
        }

        @Test
        void rejectsTooLong() { // GH-90000
            String tooLong = "a".repeat(ApiInputValidator.MAX_SEARCH_QUERY_LEN + 1); // GH-90000
            assertThat(ApiInputValidator.validateSearchQuery(tooLong)).isPresent(); // GH-90000
        }

        @Test
        void rejectsControlCharacters() { // GH-90000
            assertThat(ApiInputValidator.validateSearchQuery("ok\u0007bell [GH-90000]")).isPresent();
            assertThat(ApiInputValidator.validateSearchQuery("ok\u0000null [GH-90000]")).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // batch size
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateBatchSize [GH-90000]")
    class BatchSizeTests {

        @Test
        void acceptsValidBatch() { // GH-90000
            List<Map<String, Object>> batch = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 10; i++) batch.add(Map.of("key", i)); // GH-90000
            assertThat(ApiInputValidator.validateBatchSize(batch)).isEmpty(); // GH-90000
        }

        @Test
        void rejectsNull() { // GH-90000
            assertThat(ApiInputValidator.validateBatchSize(null)).isPresent(); // GH-90000
        }

        @Test
        void rejectsEmpty() { // GH-90000
            assertThat(ApiInputValidator.validateBatchSize(List.of())).isPresent(); // GH-90000
        }

        @Test
        void acceptsExactlyMaxBatchSize() { // GH-90000
            List<Map<String, Object>> batch = new ArrayList<>(); // GH-90000
            for (int i = 0; i < ApiInputValidator.MAX_BATCH_SIZE; i++) batch.add(Map.of()); // GH-90000
            assertThat(ApiInputValidator.validateBatchSize(batch)).isEmpty(); // GH-90000
        }

        @Test
        void rejectsExceedsMaxBatchSize() { // GH-90000
            List<Map<String, Object>> batch = new ArrayList<>(); // GH-90000
            for (int i = 0; i <= ApiInputValidator.MAX_BATCH_SIZE; i++) batch.add(Map.of()); // GH-90000
            assertThat(ApiInputValidator.validateBatchSize(batch)).isPresent(); // GH-90000
        }
    }

    // -----------------------------------------------------------------------
    // delete batch (ids) // GH-90000
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateDeleteBatch [GH-90000]")
    class DeleteBatchTests {

        @Test
        void acceptsValidIds() { // GH-90000
            assertThat(ApiInputValidator.validateDeleteBatch(List.of("id-1", "id-2", "id-3"))).isEmpty(); // GH-90000
        }

        @Test
        void rejectsNullList() { // GH-90000
            assertThat(ApiInputValidator.validateDeleteBatch(null)).isPresent(); // GH-90000
        }

        @Test
        void rejectsEmptyList() { // GH-90000
            assertThat(ApiInputValidator.validateDeleteBatch(List.of())).isPresent(); // GH-90000
        }

        @Test
        void rejectsInvalidIdInList() { // GH-90000
            List<String> ids = new ArrayList<>(); // GH-90000
            ids.add("valid-id [GH-90000]");
            ids.add("invalid/path/../traversal [GH-90000]");
            assertThat(ApiInputValidator.validateDeleteBatch(ids)).isPresent() // GH-90000
                .get().asString().contains("ids[1] [GH-90000]");
        }
    }

    // -----------------------------------------------------------------------
    // entity payload
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateEntityPayload [GH-90000]")
    class EntityPayloadTests {

        @Test
        void acceptsValidPayload() { // GH-90000
            assertThat(ApiInputValidator.validateEntityPayload(Map.of("name", "Alice", "age", 30))).isEmpty(); // GH-90000
        }

        @Test
        void rejectsNullPayload() { // GH-90000
            assertThat(ApiInputValidator.validateEntityPayload(null)).isPresent(); // GH-90000
        }

        @Test
        void rejectsEmptyPayload() { // GH-90000
            assertThat(ApiInputValidator.validateEntityPayload(Map.of())).isPresent(); // GH-90000
        }

        @Test
        void rejectsStringValueWithControlChars() { // GH-90000
            Map<String, Object> data = new HashMap<>(); // GH-90000
            data.put("field", "bad\u0000value"); // GH-90000
            assertThat(ApiInputValidator.validateEntityPayload(data)).isPresent(); // GH-90000
        }

        @Test
        void rejectsNestingTooDeep() { // GH-90000
            // Build a deeply nested map (> MAX_NESTING_DEPTH) // GH-90000
            Map<String, Object> inner = Map.of("leaf", "value"); // GH-90000
            Map<String, Object> current = new HashMap<>(inner); // GH-90000
            for (int i = 0; i < ApiInputValidator.MAX_NESTING_DEPTH + 2; i++) { // GH-90000
                Map<String, Object> next = new HashMap<>(); // GH-90000
                next.put("child", current); // GH-90000
                current = next;
            }
            assertThat(ApiInputValidator.validateEntityPayload(current)).isPresent(); // GH-90000
        }

        @Test
        void acceptsNestedMapWithinDepthLimit() { // GH-90000
            Map<String, Object> data = Map.of( // GH-90000
                "level1", Map.of( // GH-90000
                    "level2", Map.of( // GH-90000
                        "level3", "deep but ok"
                    )
                )
            );
            assertThat(ApiInputValidator.validateEntityPayload(data)).isEmpty(); // GH-90000
        }
    }

    // -----------------------------------------------------------------------
    // sanitizeForMessage — security: no log injection
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("sanitizeForMessage [GH-90000]")
    class SanitizeForMessageTests {

        @Test
        void stripsControlChars() { // GH-90000
            String result = ApiInputValidator.sanitizeForMessage("ok\u0007\r\nval [GH-90000]");
            assertThat(result).doesNotContain("\u0007 [GH-90000]").doesNotContain("\r [GH-90000]").doesNotContain("\n [GH-90000]");
        }

        @Test
        void truncatesLongStrings() { // GH-90000
            String long200 = "a".repeat(200); // GH-90000
            String result = ApiInputValidator.sanitizeForMessage(long200); // GH-90000
            assertThat(result.length()).isLessThanOrEqualTo(132); // 128 + ellipsis // GH-90000
        }

        @Test
        void handlesNull() { // GH-90000
            assertThat(ApiInputValidator.sanitizeForMessage(null)).isEqualTo("<null> [GH-90000]");
        }
    }

    // -----------------------------------------------------------------------
    // validateAll — accumulates violations
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("validateAll [GH-90000]")
    class ValidateAllTests {

        @Test
        void returnsEmptyForValidInput() { // GH-90000
            assertThat(ApiInputValidator.validateAll("tenant-1", "users", Map.of("name", "Bob"))).isEmpty(); // GH-90000
        }

        @Test
        void accumulatesMultipleViolations() { // GH-90000
            // null tenantId + null collection + null payload → all 3 violations joined
            Optional<String> result = ApiInputValidator.validateAll(null, null, null); // GH-90000
            assertThat(result).isPresent(); // GH-90000
            // error message contains separator
            assertThat(result.get()).contains("; [GH-90000]");
        }
    }
}
