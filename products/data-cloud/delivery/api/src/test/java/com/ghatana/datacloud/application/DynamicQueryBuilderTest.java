package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DC-9: Test suite for DynamicQueryBuilder with JSONB path validation.
 * Ensures strict allowlist/regex validation prevents SQL injection.
 */
@DisplayName("DynamicQueryBuilder Tests")
class DynamicQueryBuilderTest {

    private static MetaCollection createTestCollection() {
        return MetaCollection.builder()
                .name("test_collection")
                .label("Test Collection")
                .build();
    }

    private static List<MetaField> createTestFields() {
        return List.of(
            MetaField.builder().name("id").type(DataType.STRING).required(false).build(),
            MetaField.builder().name("name").type(DataType.STRING).required(false).build(),
            MetaField.builder().name("status").type(DataType.STRING).required(false).build()
        );
    }

    @Test
    @DisplayName("filterJsonb accepts valid JSONB paths")
    void filterJsonb_acceptsValidPaths() {
        DynamicQueryBuilder builder = new DynamicQueryBuilder(
            createTestCollection(),
            createTestFields()
        );

        // Valid paths should not throw
        assertDoesNotThrow(() -> builder
            .select()
            .filterJsonb("data->'status'", "=", "active")
            .build());

        assertDoesNotThrow(() -> builder
            .select()
            .filterJsonb("data->>'name'", "=", "test")
            .build());

        assertDoesNotThrow(() -> builder
            .select()
            .filterJsonb("metadata#>{path}", "=", "value")
            .build());

        assertDoesNotThrow(() -> builder
            .select()
            .filterJsonb("config#>>'key'", "=", "value")
            .build());
    }

    @Test
    @DisplayName("filterJsonb rejects SQL injection attempts")
    void filterJsonb_rejectsSqlInjection() {
        DynamicQueryBuilder builder = new DynamicQueryBuilder(
            createTestCollection(),
            createTestFields()
        );

        // SQL injection attempts should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->'status' OR 1=1", "=", "active")
            .build());

        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->'status'; DROP TABLE entities--", "=", "active")
            .build());

        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->'status' UNION SELECT * FROM users", "=", "active")
            .build());
    }

    @Test
    @DisplayName("filterJsonb rejects invalid JSONB operators")
    void filterJsonb_rejectsInvalidOperators() {
        DynamicQueryBuilder builder = new DynamicQueryBuilder(
            createTestCollection(),
            createTestFields()
        );

        // Invalid operators should throw
        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->'status'", "<script>", "active")
            .build());

        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->'status'", "||", "active")
            .build());
    }

    @Test
    @DisplayName("filterJsonb rejects paths with special characters")
    void filterJsonb_rejectsSpecialCharacters() {
        DynamicQueryBuilder builder = new DynamicQueryBuilder(
            createTestCollection(),
            createTestFields()
        );

        // Paths with special characters should be rejected
        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->'status';", "=", "active")
            .build());

        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->'status'--", "=", "active")
            .build());

        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->'status'/*", "=", "active")
            .build());
    }

    @Test
    @DisplayName("filterJsonb rejects paths starting with numbers")
    void filterJsonb_rejectsNumericFieldStart() {
        DynamicQueryBuilder builder = new DynamicQueryBuilder(
            createTestCollection(),
            createTestFields()
        );

        // Field names starting with numbers should be rejected
        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("1data->'status'", "=", "active")
            .build());

        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("123->'status'", "=", "active")
            .build());
    }

    @Test
    @DisplayName("filterJsonb rejects paths without proper operators")
    void filterJsonb_rejectsMissingOperators() {
        DynamicQueryBuilder builder = new DynamicQueryBuilder(
            createTestCollection(),
            createTestFields()
        );

        // Paths without proper JSONB operators should be rejected
        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data.status", "=", "active")
            .build());

        assertThrows(IllegalArgumentException.class, () -> builder
            .select()
            .filterJsonb("data->status", "=", "active") // Missing quotes
            .build());
    }

    @Test
    @DisplayName("filterJsonb rejects null path")
    void filterJsonb_rejectsNullPath() {
        DynamicQueryBuilder builder = new DynamicQueryBuilder(
            createTestCollection(),
            createTestFields()
        );

        assertThrows(NullPointerException.class, () -> builder
            .select()
            .filterJsonb(null, "=", "active")
            .build());
    }
}
