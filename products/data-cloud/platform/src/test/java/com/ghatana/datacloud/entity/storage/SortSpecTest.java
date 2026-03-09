package com.ghatana.datacloud.entity.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SortSpec class.
 * 
 * @doc.type class
 * @doc.purpose Test sort specification value object
 * @doc.layer platform
 * @doc.pattern Unit Test
 */
@DisplayName("SortSpec Tests")
class SortSpecTest {
    
    @Test
    @DisplayName("asc() - creates ascending sort")
    void testAscendingSort() {
        SortSpec sort = SortSpec.asc("name");
        
        assertThat(sort.getField()).isEqualTo("name");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.ASC);
    }
    
    @Test
    @DisplayName("desc() - creates descending sort")
    void testDescendingSort() {
        SortSpec sort = SortSpec.desc("createdAt");
        
        assertThat(sort.getField()).isEqualTo("createdAt");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.DESC);
    }
    
    @Test
    @DisplayName("of(field) - creates default descending sort")
    void testDefaultSort() {
        SortSpec sort = SortSpec.of("price");
        
        assertThat(sort.getField()).isEqualTo("price");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.DESC);
    }
    
    @Test
    @DisplayName("of(field, direction) - creates sort with explicit direction")
    void testExplicitDirectionSort() {
        SortSpec ascSort = SortSpec.of("name", SortSpec.Direction.ASC);
        SortSpec descSort = SortSpec.of("price", SortSpec.Direction.DESC);
        
        assertThat(ascSort.getField()).isEqualTo("name");
        assertThat(ascSort.getDirection()).isEqualTo(SortSpec.Direction.ASC);
        
        assertThat(descSort.getField()).isEqualTo("price");
        assertThat(descSort.getDirection()).isEqualTo(SortSpec.Direction.DESC);
    }
    
    @Test
    @DisplayName("System fields - creates sorts for built-in fields")
    void testSystemFieldSorts() {
        SortSpec createdSort = SortSpec.desc("createdAt");
        SortSpec updatedSort = SortSpec.asc("updatedAt");
        SortSpec versionSort = SortSpec.desc("version");
        
        assertThat(createdSort.getField()).isEqualTo("createdAt");
        assertThat(updatedSort.getField()).isEqualTo("updatedAt");
        assertThat(versionSort.getField()).isEqualTo("version");
    }
    
    @Test
    @DisplayName("Nested field - creates sort with dot notation")
    void testNestedFieldSort() {
        SortSpec sort = SortSpec.asc("address.city");
        
        assertThat(sort.getField()).isEqualTo("address.city");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.ASC);
    }
    
    @Test
    @DisplayName("Multi-level nested field - creates sort with multiple dots")
    void testMultiLevelNestedFieldSort() {
        SortSpec sort = SortSpec.desc("location.coordinates.lat");
        
        assertThat(sort.getField()).isEqualTo("location.coordinates.lat");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.DESC);
    }
    
    @Test
    @DisplayName("equals() - returns true for identical sorts")
    void testEquality() {
        SortSpec sort1 = SortSpec.asc("name");
        SortSpec sort2 = SortSpec.asc("name");
        
        assertThat(sort1).isEqualTo(sort2);
        assertThat(sort1.hashCode()).isEqualTo(sort2.hashCode());
    }
    
    @Test
    @DisplayName("equals() - returns false for different sorts")
    void testInequality() {
        SortSpec sort1 = SortSpec.asc("name");
        SortSpec sort2 = SortSpec.desc("name");
        SortSpec sort3 = SortSpec.asc("price");
        
        assertThat(sort1).isNotEqualTo(sort2);
        assertThat(sort1).isNotEqualTo(sort3);
    }
    
    @Test
    @DisplayName("toString() - returns meaningful representation")
    void testToString() {
        SortSpec sort = SortSpec.asc("name");
        
        String str = sort.toString();
        assertThat(str).contains("name");
        assertThat(str).contains("ASC");
    }
    
    @Test
    @DisplayName("Constructor - throws NPE for null field")
    void testNullFieldThrowsException() {
        assertThatThrownBy(() -> SortSpec.asc(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("field");
    }
    
    @Test
    @DisplayName("Constructor - throws NPE for null direction")
    void testNullDirectionThrowsException() {
        assertThatThrownBy(() -> SortSpec.of("name", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("direction");
    }
    
    @Test
    @DisplayName("Direction SQL - returns correct SQL direction strings")
    void testDirectionSql() {
        assertThat(SortSpec.Direction.ASC.getSql()).isEqualTo("ASC");
        assertThat(SortSpec.Direction.DESC.getSql()).isEqualTo("DESC");
    }
    
    @Test
    @DisplayName("Multi-field sort - creates list of sort specifications")
    void testMultiFieldSort() {
        List<SortSpec> sorts = List.of(
            SortSpec.asc("category"),
            SortSpec.desc("price"),
            SortSpec.asc("name")
        );
        
        assertThat(sorts).hasSize(3);
        assertThat(sorts.get(0).getField()).isEqualTo("category");
        assertThat(sorts.get(0).getDirection()).isEqualTo(SortSpec.Direction.ASC);
        assertThat(sorts.get(1).getField()).isEqualTo("price");
        assertThat(sorts.get(1).getDirection()).isEqualTo(SortSpec.Direction.DESC);
        assertThat(sorts.get(2).getField()).isEqualTo("name");
        assertThat(sorts.get(2).getDirection()).isEqualTo(SortSpec.Direction.ASC);
    }
    
    @Test
    @DisplayName("Complex sort - combines system and JSONB fields")
    void testComplexSort() {
        List<SortSpec> sorts = List.of(
            SortSpec.desc("priority"),           // JSONB field
            SortSpec.desc("createdAt"),          // System field
            SortSpec.asc("address.city"),        // Nested JSONB field
            SortSpec.asc("name")                 // JSONB field
        );
        
        assertThat(sorts).hasSize(4);
        assertThat(sorts.get(0).getField()).isEqualTo("priority");
        assertThat(sorts.get(1).getField()).isEqualTo("createdAt");
        assertThat(sorts.get(2).getField()).isEqualTo("address.city");
        assertThat(sorts.get(3).getField()).isEqualTo("name");
    }
    
    @Test
    @DisplayName("Sort order precedence - validates multiple sorts")
    void testSortOrderPrecedence() {
        // Primary: category ASC, Secondary: price DESC, Tertiary: name ASC
        List<SortSpec> sorts = List.of(
            SortSpec.asc("category"),    // 1st priority
            SortSpec.desc("price"),       // 2nd priority
            SortSpec.asc("name")          // 3rd priority
        );
        
        assertThat(sorts).hasSize(3);
        
        // Verify order is maintained
        assertThat(sorts.get(0).getField()).isEqualTo("category");
        assertThat(sorts.get(1).getField()).isEqualTo("price");
        assertThat(sorts.get(2).getField()).isEqualTo("name");
        
        // Verify directions
        assertThat(sorts.get(0).getDirection()).isEqualTo(SortSpec.Direction.ASC);
        assertThat(sorts.get(1).getDirection()).isEqualTo(SortSpec.Direction.DESC);
        assertThat(sorts.get(2).getDirection()).isEqualTo(SortSpec.Direction.ASC);
    }
}
