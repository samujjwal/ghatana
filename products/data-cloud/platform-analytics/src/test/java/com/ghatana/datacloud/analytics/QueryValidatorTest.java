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
@DisplayName("QueryValidator Tests [GH-90000]")
class QueryValidatorTest {

    private QueryValidator validator;

    @BeforeEach
    void setUp() { // GH-90000
        validator = new QueryValidator(); // GH-90000
    }

    @Nested
    @DisplayName("Basic Input Validation [GH-90000]")
    class BasicValidationTests {

        @Test
        @DisplayName("null tenantId fails validation [GH-90000]")
        void nullTenantId_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate(null, "SELECT * FROM table", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("tenantId [GH-90000]"));
        }

        @Test
        @DisplayName("blank tenantId fails validation [GH-90000]")
        void blankTenantId_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("   ", "SELECT * FROM table", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("tenantId [GH-90000]"));
        }

        @Test
        @DisplayName("null queryText fails validation [GH-90000]")
        void nullQueryText_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", null, Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("queryText [GH-90000]"));
        }

        @Test
        @DisplayName("blank queryText fails validation [GH-90000]")
        void blankQueryText_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "   ", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("queryText [GH-90000]"));
        }

        @Test
        @DisplayName("valid SELECT query passes validation [GH-90000]")
        void validSelect_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.violations()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Query Length Validation [GH-90000]")
    class LengthValidationTests {

        @Test
        @DisplayName("query exceeding maximum length fails [GH-90000]")
        void tooLongQuery_fails() { // GH-90000
            StringBuilder longQuery = new StringBuilder("SELECT * FROM table WHERE id = ' [GH-90000]");
            for (int i = 0; i < 15000; i++) { // GH-90000
                longQuery.append("a [GH-90000]");
            }
            longQuery.append("' [GH-90000]");

            QueryValidator.ValidationResult result = validator.validate("tenant-1", longQuery.toString(), Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("exceeds maximum length [GH-90000]"));
        }

        @Test
        @DisplayName("query within maximum length passes [GH-90000]")
        void acceptableLength_passes() { // GH-90000
            StringBuilder query = new StringBuilder("SELECT * FROM table WHERE id = ' [GH-90000]");
            for (int i = 0; i < 100; i++) { // GH-90000
                query.append("a [GH-90000]");
            }
            query.append("' [GH-90000]");

            QueryValidator.ValidationResult result = validator.validate("tenant-1", query.toString(), Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Security Validation [GH-90000]")
    class SecurityValidationTests {

        @Test
        @DisplayName("DROP keyword is forbidden [GH-90000]")
        void dropKeyword_forbidden() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "DROP TABLE products", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("forbidden keyword [GH-90000]") && v.contains("DROP [GH-90000]"));
        }

        @Test
        @DisplayName("DELETE keyword is forbidden [GH-90000]")
        void deleteKeyword_forbidden() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "DELETE FROM products WHERE id = 1", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("forbidden keyword [GH-90000]") && v.contains("DELETE [GH-90000]"));
        }

        @Test
        @DisplayName("UPDATE keyword is forbidden [GH-90000]")
        void updateKeyword_forbidden() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "UPDATE products SET name = 'x'", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("forbidden keyword [GH-90000]") && v.contains("UPDATE [GH-90000]"));
        }

        @Test
        @DisplayName("INSERT keyword is forbidden [GH-90000]")
        void insertKeyword_forbidden() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "INSERT INTO products VALUES (...)", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("forbidden keyword [GH-90000]") && v.contains("INSERT [GH-90000]"));
        }

        @Test
        @DisplayName("single quote triggers injection warning [GH-90000]")
        void singleQuote_injectionWarning() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products WHERE name = 'x'; DROP TABLE", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("injection [GH-90000]"));
        }

        @Test
        @DisplayName("comment marker triggers injection warning [GH-90000]")
        void commentMarker_injectionWarning() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products -- DROP", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("injection [GH-90000]"));
        }

        @Test
        @DisplayName("semicolon triggers injection warning [GH-90000]")
        void semicolon_injectionWarning() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products; DROP TABLE", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("injection [GH-90000]"));
        }
    }

    @Nested
    @DisplayName("SQL Syntax Validation [GH-90000]")
    class SyntaxValidationTests {

        @Test
        @DisplayName("valid SELECT with WHERE passes [GH-90000]")
        void validSelectWithWhere_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products WHERE price > 100", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("malformed SQL fails [GH-90000]")
        void malformedSql_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELEC * FROM products", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("syntax error [GH-90000]"));
        }

        @Test
        @DisplayName("SELECT with GROUP BY passes [GH-90000]")
        void selectWithGroupBy_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT category, COUNT(*) FROM products GROUP BY category", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SELECT with JOIN passes [GH-90000]")
        void selectWithJoin_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products JOIN categories ON products.cat_id = categories.id", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Query Structure Validation [GH-90000]")
    class StructureValidationTests {

        @Test
        @DisplayName("LIMIT exceeding maximum fails [GH-90000]")
        void limitExceedsMaximum_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products LIMIT 20000", Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("LIMIT [GH-90000]") && v.contains("exceeds maximum [GH-90000]"));
        }

        @Test
        @DisplayName("LIMIT within maximum passes [GH-90000]")
        void limitWithinMaximum_passes() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products LIMIT 100", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("excessive JOIN depth fails [GH-90000]")
        void excessiveJoins_fails() { // GH-90000
            String query = "SELECT * FROM t1 JOIN t2 ON t1.id = t2.id JOIN t3 ON t2.id = t3.id JOIN t4 ON t3.id = t4.id JOIN t5 ON t4.id = t5.id JOIN t6 ON t5.id = t6.id";
            QueryValidator.ValidationResult result = validator.validate("tenant-1", query, Map.of()); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("JOIN depth [GH-90000]"));
        }

        @Test
        @DisplayName("acceptable JOIN depth passes [GH-90000]")
        void acceptableJoins_passes() { // GH-90000
            String query = "SELECT * FROM t1 JOIN t2 ON t1.id = t2.id JOIN t3 ON t2.id = t3.id";
            QueryValidator.ValidationResult result = validator.validate("tenant-1", query, Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Parameter Validation [GH-90000]")
    class ParameterValidationTests {

        @Test
        @DisplayName("null parameter value fails [GH-90000]")
        void nullParameter_fails() { // GH-90000
            Map<String, Object> params = new HashMap<>(); // GH-90000
            params.put("key", null); // GH-90000

            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", params); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("Parameter [GH-90000]") && v.contains("null [GH-90000]"));
        }

        @Test
        @DisplayName("parameter with injection pattern fails [GH-90000]")
        void parameterWithInjection_fails() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", Map.of("key", "value'; DROP")); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("Parameter [GH-90000]") && v.contains("injection [GH-90000]"));
        }

        @Test
        @DisplayName("valid parameters pass [GH-90000]")
        void validParameters_pass() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", Map.of("key", "value")); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("empty parameters pass [GH-90000]")
        void emptyParameters_pass() { // GH-90000
            QueryValidator.ValidationResult result = validator.validate("tenant-1", "SELECT * FROM products", Map.of()); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("ValidationResult Record [GH-90000]")
    class ValidationResultTests {

        @Test
        @DisplayName("success() returns valid result with empty violations [GH-90000]")
        void success_returnsValidResult() { // GH-90000
            QueryValidator.ValidationResult result = QueryValidator.ValidationResult.success(); // GH-90000
            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.violations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("failure() returns invalid result with violations [GH-90000]")
        void failure_returnsInvalidResult() { // GH-90000
            QueryValidator.ValidationResult result = QueryValidator.ValidationResult.failure(List.of("error1", "error2")); // GH-90000
            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).containsExactly("error1", "error2"); // GH-90000
        }

        @Test
        @DisplayName("violations list is immutable [GH-90000]")
        void violationsList_isImmutable() { // GH-90000
            List<String> original = new ArrayList<>(); // GH-90000
            original.add("error [GH-90000]");
            QueryValidator.ValidationResult result = QueryValidator.ValidationResult.failure(original); // GH-90000
            
            // Modifying original list should not affect result
            original.add("another [GH-90000]");
            assertThat(result.violations()).hasSize(1); // GH-90000
        }
    }
}
