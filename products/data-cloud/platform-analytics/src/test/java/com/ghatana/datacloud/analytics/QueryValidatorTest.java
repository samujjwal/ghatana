/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for QueryValidator.
 *
 * @doc.type class
 * @doc.purpose Tests for query validation logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("QueryValidator Tests")
class QueryValidatorTest {

    private QueryValidator validator;

    @BeforeEach
    void setUp() { // GH-90000
        validator = new QueryValidator(); // GH-90000
    }

    @Nested
    @DisplayName("Basic Input Validation")
    class BasicValidationTests {

        @Test
        @DisplayName("null tenantId fails validation")
        void nullTenantId_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate(null, "SELECT * FROM table", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("tenantId"));
        }

        @Test
        @DisplayName("blank tenantId fails validation")
        void blankTenantId_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("   ", "SELECT * FROM table", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("tenantId"));
        }

        @Test
        @DisplayName("null queryText fails validation")
        void nullQueryText_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", null, Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("queryText"));
        }

        @Test
        @DisplayName("blank queryText fails validation")
        void blankQueryText_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "   ", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("queryText"));
        }

        @Test
        @DisplayName("valid SELECT query passes validation")
        void validSelect_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.violations()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Query Length Validation")
    class LengthValidationTests {

        @Test
        @DisplayName("query exceeding maximum length fails")
        void tooLongQuery_fails() { // GH-90000
            StringBuilder longQuery = new StringBuilder("SELECT * FROM table WHERE id = '");
            for (int i = 0; i < 15000; i++) { // GH-90000
                longQuery.append("a");
            }
            longQuery.append("'");

            QueryValidator.ValidationResult result = validator.validate("tenant-1", longQuery.toString(), Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("exceeds maximum length"));
        }

        @Test
        @DisplayName("query within maximum length passes")
        void acceptableLength_passes() { // GH-90000
            StringBuilder query = new StringBuilder("SELECT * FROM table WHERE id = '");
            for (int i = 0; i < 100; i++) { // GH-90000
                query.append("a");
            }
            query.append("'");

            QueryValidator.ValidationResult result = validator.validate("tenant-1", query.toString(), Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Security Validation")
    class SecurityValidationTests {

        @Test
        @DisplayName("DROP keyword is forbidden")
        void dropKeyword_forbidden() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "DROP TABLE products", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("forbidden keyword") && v.contains("DROP"));
        }

        @Test
        @DisplayName("DELETE keyword is forbidden")
        void deleteKeyword_forbidden() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "DELETE FROM products WHERE id = 1", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("forbidden keyword") && v.contains("DELETE"));
        }

        @Test
        @DisplayName("UPDATE keyword is forbidden")
        void updateKeyword_forbidden() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "UPDATE products SET name = 'x'", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("forbidden keyword") && v.contains("UPDATE"));
        }

        @Test
        @DisplayName("INSERT keyword is forbidden")
        void insertKeyword_forbidden() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "INSERT INTO products VALUES (...)", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("forbidden keyword") && v.contains("INSERT"));
        }

        @Test
        @DisplayName("single quote triggers injection warning")
        void singleQuote_injectionWarning() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products WHERE name = 'x'; DROP TABLE", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("injection"));
        }

        @Test
        @DisplayName("comment marker triggers injection warning")
        void commentMarker_injectionWarning() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products -- DROP", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("injection"));
        }

        @Test
        @DisplayName("semicolon triggers injection warning")
        void semicolon_injectionWarning() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products; DROP TABLE", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("injection"));
        }
    }

    @Nested
    @DisplayName("SQL Syntax Validation")
    class SyntaxValidationTests {

        @Test
        @DisplayName("valid SELECT with WHERE passes")
        void validSelectWithWhere_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products WHERE price > 100", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("malformed SQL fails")
        void malformedSql_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELEC * FROM products", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("syntax error"));
        }

        @Test
        @DisplayName("SELECT with GROUP BY passes")
        void selectWithGroupBy_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT category, COUNT(*) FROM products GROUP BY category", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SELECT with JOIN passes")
        void selectWithJoin_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products JOIN categories ON products.cat_id = categories.id", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Query Structure Validation")
    class StructureValidationTests {

        @Test
        @DisplayName("LIMIT exceeding maximum fails")
        void limitExceedsMaximum_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products LIMIT 20000", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("LIMIT") && v.contains("exceeds maximum"));
        }

        @Test
        @DisplayName("LIMIT within maximum passes")
        void limitWithinMaximum_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products LIMIT 100", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("excessive JOIN depth fails")
        void excessiveJoins_fails() { // GH-90000
            String query = "SELECT * FROM t1 JOIN t2 ON t1.id = t2.id JOIN t3 ON t2.id = t3.id JOIN t4 ON t3.id = t4.id JOIN t5 ON t4.id = t5.id JOIN t6 ON t5.id = t6.id";
            QueryValidator.ValidationResult result = validator.validate("tenant-1", query, Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("JOIN depth"));
        }

        @Test
        @DisplayName("acceptable JOIN depth passes")
        void acceptableJoins_passes() { // GH-90000
            String query = "SELECT * FROM t1 JOIN t2 ON t1.id = t2.id JOIN t3 ON t2.id = t3.id";
            QueryValidator.ValidationResult result = validator.validate("tenant-1", query, Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Parameter Validation")
    class ParameterValidationTests {

        @Test
        @DisplayName("null parameter value fails")
        void nullParameter_fails() { // GH-90000
            Map<String, Object> params = new HashMap<>(); // GH-90000
            params.put("key", null); // GH-90000

            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", params); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("Parameter") && v.contains("null"));
        }

        @Test
        @DisplayName("parameter with injection pattern fails")
        void parameterWithInjection_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", Map.of("key", "value'; DROP")); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("Parameter") && v.contains("injection"));
        }

        @Test
        @DisplayName("valid parameters pass")
        void validParameters_pass() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", Map.of("key", "value")); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("empty parameters pass")
        void emptyParameters_pass() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("ValidationResult Record")
    class ValidationResultTests {

        @Test
        @DisplayName("success() returns valid result with empty violations")
        void success_returnsValidResult() { // GH-90000
            QueryValidator.ValidationResult result = QueryValidator.ValidationResult.success(); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.violations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("failure() returns invalid result with violations")
        void failure_returnsInvalidResult() { // GH-90000
            QueryValidator.ValidationResult result = QueryValidator.ValidationResult.failure(List.of("error1", "error2")); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).containsExactly("error1", "error2"); // GH-90000
        }

        @Test
        @DisplayName("violations list is immutable")
        void violationsList_isImmutable() { // GH-90000
            List<String> original = new ArrayList<>(); // GH-90000
            original.add("error");
            QueryValidator.ValidationResult result = QueryValidator.ValidationResult.failure(original); // GH-90000
            
            // Modifying original list should not affect result
            original.add("another");
            assertThat(result.violations()).hasSize(1); // GH-90000
        }
    }
}
