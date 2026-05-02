package com.ghatana.datacloud.entity.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for StorageBackendType enum.
 *
 * @doc.type class
 * @doc.purpose Verifies StorageBackendType enum behavior, parsing, and utility methods
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("StorageBackendType Tests")
class StorageBackendTypeTest {

    @Test
    @DisplayName("should return correct label and identifier")
    void shouldReturnCorrectLabelAndIdentifier() {
        StorageBackendType relational = StorageBackendType.RELATIONAL;
        assertThat(relational.getLabel()).isEqualTo("Relational (SQL)");
        assertThat(relational.getIdentifier()).isEqualTo("sql_db");
        
        StorageBackendType timeseries = StorageBackendType.TIMESERIES;
        assertThat(timeseries.getLabel()).isEqualTo("Time-Series");
        assertThat(timeseries.getIdentifier()).isEqualTo("timeseries");
    }

    @Test
    @DisplayName("should identify windowed query optimized backends")
    void shouldIdentifyWindowedQueryOptimizedBackends() {
        assertThat(StorageBackendType.TIMESERIES.isWindowedQueryOptimized()).isTrue();
        assertThat(StorageBackendType.LAKEHOUSE.isWindowedQueryOptimized()).isTrue();
        assertThat(StorageBackendType.SEARCH.isWindowedQueryOptimized()).isTrue();
        
        assertThat(StorageBackendType.RELATIONAL.isWindowedQueryOptimized()).isFalse();
        assertThat(StorageBackendType.KEY_VALUE.isWindowedQueryOptimized()).isFalse();
        assertThat(StorageBackendType.BLOB.isWindowedQueryOptimized()).isFalse();
        assertThat(StorageBackendType.IN_MEMORY.isWindowedQueryOptimized()).isFalse();
        assertThat(StorageBackendType.GRAPH.isWindowedQueryOptimized()).isFalse();
    }

    @Test
    @DisplayName("should identify fast lookup backends")
    void shouldIdentifyFastLookupBackends() {
        assertThat(StorageBackendType.KEY_VALUE.isFastLookup()).isTrue();
        assertThat(StorageBackendType.IN_MEMORY.isFastLookup()).isTrue();
        assertThat(StorageBackendType.SEARCH.isFastLookup()).isTrue();
        
        assertThat(StorageBackendType.RELATIONAL.isFastLookup()).isFalse();
        assertThat(StorageBackendType.TIMESERIES.isFastLookup()).isFalse();
        assertThat(StorageBackendType.LAKEHOUSE.isFastLookup()).isFalse();
        assertThat(StorageBackendType.BLOB.isFastLookup()).isFalse();
        assertThat(StorageBackendType.GRAPH.isFastLookup()).isFalse();
    }

    @Test
    @DisplayName("should identify analytics optimized backends")
    void shouldIdentifyAnalyticsOptimizedBackends() {
        assertThat(StorageBackendType.LAKEHOUSE.isAnalyticsOptimized()).isTrue();
        assertThat(StorageBackendType.TIMESERIES.isAnalyticsOptimized()).isTrue();
        assertThat(StorageBackendType.SEARCH.isAnalyticsOptimized()).isTrue();
        
        assertThat(StorageBackendType.RELATIONAL.isAnalyticsOptimized()).isFalse();
        assertThat(StorageBackendType.KEY_VALUE.isAnalyticsOptimized()).isFalse();
        assertThat(StorageBackendType.BLOB.isAnalyticsOptimized()).isFalse();
        assertThat(StorageBackendType.IN_MEMORY.isAnalyticsOptimized()).isFalse();
        assertThat(StorageBackendType.GRAPH.isAnalyticsOptimized()).isFalse();
    }

    @Test
    @DisplayName("should parse identifier correctly")
    void shouldParseIdentifierCorrectly() {
        assertThat(StorageBackendType.fromIdentifier("sql_db")).isEqualTo(StorageBackendType.RELATIONAL);
        assertThat(StorageBackendType.fromIdentifier("timeseries")).isEqualTo(StorageBackendType.TIMESERIES);
        assertThat(StorageBackendType.fromIdentifier("warehouse")).isEqualTo(StorageBackendType.LAKEHOUSE);
        assertThat(StorageBackendType.fromIdentifier("kv_store")).isEqualTo(StorageBackendType.KEY_VALUE);
        assertThat(StorageBackendType.fromIdentifier("blob")).isEqualTo(StorageBackendType.BLOB);
        assertThat(StorageBackendType.fromIdentifier("memory")).isEqualTo(StorageBackendType.IN_MEMORY);
        assertThat(StorageBackendType.fromIdentifier("search")).isEqualTo(StorageBackendType.SEARCH);
        assertThat(StorageBackendType.fromIdentifier("graph")).isEqualTo(StorageBackendType.GRAPH);
    }

    @Test
    @DisplayName("should parse identifier case-insensitively")
    void shouldParseIdentifierCaseInsensitively() {
        assertThat(StorageBackendType.fromIdentifier("SQL_DB")).isEqualTo(StorageBackendType.RELATIONAL);
        assertThat(StorageBackendType.fromIdentifier("Timeseries")).isEqualTo(StorageBackendType.TIMESERIES);
        assertThat(StorageBackendType.fromIdentifier("WAREHOUSE")).isEqualTo(StorageBackendType.LAKEHOUSE);
        assertThat(StorageBackendType.fromIdentifier("KV_STORE")).isEqualTo(StorageBackendType.KEY_VALUE);
        assertThat(StorageBackendType.fromIdentifier("BLOB")).isEqualTo(StorageBackendType.BLOB);
        assertThat(StorageBackendType.fromIdentifier("MEMORY")).isEqualTo(StorageBackendType.IN_MEMORY);
        assertThat(StorageBackendType.fromIdentifier("SEARCH")).isEqualTo(StorageBackendType.SEARCH);
        assertThat(StorageBackendType.fromIdentifier("GRAPH")).isEqualTo(StorageBackendType.GRAPH);
    }

    @Test
    @DisplayName("should throw exception for null identifier")
    void shouldThrowExceptionForNullIdentifier() {
        assertThatThrownBy(() -> StorageBackendType.fromIdentifier(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("identifier must not be blank");
    }

    @Test
    @DisplayName("should throw exception for blank identifier")
    void shouldThrowExceptionForBlankIdentifier() {
        assertThatThrownBy(() -> StorageBackendType.fromIdentifier(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("identifier must not be blank");
        
        assertThatThrownBy(() -> StorageBackendType.fromIdentifier("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("identifier must not be blank");
    }

    @Test
    @DisplayName("should throw exception for unknown identifier")
    void shouldThrowExceptionForUnknownIdentifier() {
        assertThatThrownBy(() -> StorageBackendType.fromIdentifier("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown storage backend type identifier: unknown");
        
        assertThatThrownBy(() -> StorageBackendType.fromIdentifier("invalid_type"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown storage backend type identifier: invalid_type");
    }

    @Test
    @DisplayName("should return analytics backends array")
    void shouldReturnAnalyticsBackendsArray() {
        StorageBackendType[] analyticsBackends = StorageBackendType.getAnalyticsBackends();
        
        assertThat(analyticsBackends).hasSize(3);
        assertThat(analyticsBackends).contains(
            StorageBackendType.LAKEHOUSE,
            StorageBackendType.TIMESERIES,
            StorageBackendType.SEARCH
        );
    }

    @Test
    @DisplayName("should return fast access backends array")
    void shouldReturnFastAccessBackendsArray() {
        StorageBackendType[] fastAccessBackends = StorageBackendType.getFastAccessBackends();
        
        assertThat(fastAccessBackends).hasSize(2);
        assertThat(fastAccessBackends).contains(
            StorageBackendType.KEY_VALUE,
            StorageBackendType.IN_MEMORY
        );
    }

    @Test
    @DisplayName("should have correct toString representation")
    void shouldHaveCorrectToStringRepresentation() {
        assertThat(StorageBackendType.RELATIONAL.toString())
            .isEqualTo("Relational (SQL) (sql_db)");
        
        assertThat(StorageBackendType.TIMESERIES.toString())
            .isEqualTo("Time-Series (timeseries)");
        
        assertThat(StorageBackendType.KEY_VALUE.toString())
            .isEqualTo("Key-Value (kv_store)");
    }

    @Test
    @DisplayName("should contain all expected backend types")
    void shouldContainAllExpectedBackendTypes() {
        StorageBackendType[] allTypes = StorageBackendType.values();
        
        assertThat(allTypes).hasSize(8);
        assertThat(allTypes).contains(
            StorageBackendType.RELATIONAL,
            StorageBackendType.TIMESERIES,
            StorageBackendType.LAKEHOUSE,
            StorageBackendType.KEY_VALUE,
            StorageBackendType.BLOB,
            StorageBackendType.IN_MEMORY,
            StorageBackendType.SEARCH,
            StorageBackendType.GRAPH
        );
    }

    @Test
    @DisplayName("should maintain enum constants order")
    void shouldMaintainEnumConstantsOrder() {
        StorageBackendType[] types = StorageBackendType.values();
        
        assertThat(types[0]).isEqualTo(StorageBackendType.RELATIONAL);
        assertThat(types[1]).isEqualTo(StorageBackendType.TIMESERIES);
        assertThat(types[2]).isEqualTo(StorageBackendType.LAKEHOUSE);
        assertThat(types[3]).isEqualTo(StorageBackendType.KEY_VALUE);
        assertThat(types[4]).isEqualTo(StorageBackendType.BLOB);
        assertThat(types[5]).isEqualTo(StorageBackendType.IN_MEMORY);
        assertThat(types[6]).isEqualTo(StorageBackendType.SEARCH);
        assertThat(types[7]).isEqualTo(StorageBackendType.GRAPH);
    }

    @Test
    @DisplayName("should handle edge cases for utility methods")
    void shouldHandleEdgeCasesForUtilityMethods() {
        // Test that utility methods work consistently across all types
        for (StorageBackendType type : StorageBackendType.values()) {
            assertThat(type.getLabel()).isNotNull();
            assertThat(type.getLabel()).isNotEmpty();
            assertThat(type.getIdentifier()).isNotNull();
            assertThat(type.getIdentifier()).isNotEmpty();
            
            // toString should include both label and identifier
            String toString = type.toString();
            assertThat(toString).contains(type.getLabel());
            assertThat(toString).contains(type.getIdentifier());
        }
    }

    @Test
    @DisplayName("should validate identifier format consistency")
    void shouldValidateIdentifierFormatConsistency() {
        // All identifiers should be lowercase with underscores
        for (StorageBackendType type : StorageBackendType.values()) {
            String identifier = type.getIdentifier();
            assertThat(identifier).matches("[a-z_]+");
            assertThat(identifier).doesNotContain("-");
            assertThat(identifier).doesNotContain(" ");
        }
    }

    @Test
    @DisplayName("should validate label format consistency")
    void shouldValidateLabelFormatConsistency() {
        // All labels should be human-readable with proper spacing
        for (StorageBackendType type : StorageBackendType.values()) {
            String label = type.getLabel();
            assertThat(label).isNotEmpty();
            assertThat(label.charAt(0)).isEqualTo(Character.toUpperCase(label.charAt(0)));
        }
    }
}
