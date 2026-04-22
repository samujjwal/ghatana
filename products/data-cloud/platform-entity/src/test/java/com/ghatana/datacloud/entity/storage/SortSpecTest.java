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
@DisplayName("SortSpec Tests [GH-90000]")
class SortSpecTest {

    @Test
    @DisplayName("asc() - creates ascending sort [GH-90000]")
    void testAscendingSort() { // GH-90000
        SortSpec sort = SortSpec.asc("name [GH-90000]");

        assertThat(sort.getField()).isEqualTo("name [GH-90000]");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.ASC); // GH-90000
    }

    @Test
    @DisplayName("desc() - creates descending sort [GH-90000]")
    void testDescendingSort() { // GH-90000
        SortSpec sort = SortSpec.desc("createdAt [GH-90000]");

        assertThat(sort.getField()).isEqualTo("createdAt [GH-90000]");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.DESC); // GH-90000
    }

    @Test
    @DisplayName("of(field) - creates default descending sort [GH-90000]")
    void testDefaultSort() { // GH-90000
        SortSpec sort = SortSpec.of("price [GH-90000]");

        assertThat(sort.getField()).isEqualTo("price [GH-90000]");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.DESC); // GH-90000
    }

    @Test
    @DisplayName("of(field, direction) - creates sort with explicit direction [GH-90000]")
    void testExplicitDirectionSort() { // GH-90000
        SortSpec ascSort = SortSpec.of("name", SortSpec.Direction.ASC); // GH-90000
        SortSpec descSort = SortSpec.of("price", SortSpec.Direction.DESC); // GH-90000

        assertThat(ascSort.getField()).isEqualTo("name [GH-90000]");
        assertThat(ascSort.getDirection()).isEqualTo(SortSpec.Direction.ASC); // GH-90000

        assertThat(descSort.getField()).isEqualTo("price [GH-90000]");
        assertThat(descSort.getDirection()).isEqualTo(SortSpec.Direction.DESC); // GH-90000
    }

    @Test
    @DisplayName("System fields - creates sorts for built-in fields [GH-90000]")
    void testSystemFieldSorts() { // GH-90000
        SortSpec createdSort = SortSpec.desc("createdAt [GH-90000]");
        SortSpec updatedSort = SortSpec.asc("updatedAt [GH-90000]");
        SortSpec versionSort = SortSpec.desc("version [GH-90000]");

        assertThat(createdSort.getField()).isEqualTo("createdAt [GH-90000]");
        assertThat(updatedSort.getField()).isEqualTo("updatedAt [GH-90000]");
        assertThat(versionSort.getField()).isEqualTo("version [GH-90000]");
    }

    @Test
    @DisplayName("Nested field - creates sort with dot notation [GH-90000]")
    void testNestedFieldSort() { // GH-90000
        SortSpec sort = SortSpec.asc("address.city [GH-90000]");

        assertThat(sort.getField()).isEqualTo("address.city [GH-90000]");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.ASC); // GH-90000
    }

    @Test
    @DisplayName("Multi-level nested field - creates sort with multiple dots [GH-90000]")
    void testMultiLevelNestedFieldSort() { // GH-90000
        SortSpec sort = SortSpec.desc("location.coordinates.lat [GH-90000]");

        assertThat(sort.getField()).isEqualTo("location.coordinates.lat [GH-90000]");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.DESC); // GH-90000
    }

    @Test
    @DisplayName("equals() - returns true for identical sorts [GH-90000]")
    void testEquality() { // GH-90000
        SortSpec sort1 = SortSpec.asc("name [GH-90000]");
        SortSpec sort2 = SortSpec.asc("name [GH-90000]");

        assertThat(sort1).isEqualTo(sort2); // GH-90000
        assertThat(sort1.hashCode()).isEqualTo(sort2.hashCode()); // GH-90000
    }

    @Test
    @DisplayName("equals() - returns false for different sorts [GH-90000]")
    void testInequality() { // GH-90000
        SortSpec sort1 = SortSpec.asc("name [GH-90000]");
        SortSpec sort2 = SortSpec.desc("name [GH-90000]");
        SortSpec sort3 = SortSpec.asc("price [GH-90000]");

        assertThat(sort1).isNotEqualTo(sort2); // GH-90000
        assertThat(sort1).isNotEqualTo(sort3); // GH-90000
    }

    @Test
    @DisplayName("toString() - returns meaningful representation [GH-90000]")
    void testToString() { // GH-90000
        SortSpec sort = SortSpec.asc("name [GH-90000]");

        String str = sort.toString(); // GH-90000
        assertThat(str).contains("name [GH-90000]");
        assertThat(str).contains("ASC [GH-90000]");
    }

    @Test
    @DisplayName("Constructor - throws NPE for null field [GH-90000]")
    void testNullFieldThrowsException() { // GH-90000
        assertThatThrownBy(() -> SortSpec.asc(null)) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessageContaining("field [GH-90000]");
    }

    @Test
    @DisplayName("Constructor - throws NPE for null direction [GH-90000]")
    void testNullDirectionThrowsException() { // GH-90000
        assertThatThrownBy(() -> SortSpec.of("name", null)) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessageContaining("direction [GH-90000]");
    }

    @Test
    @DisplayName("Direction SQL - returns correct SQL direction strings [GH-90000]")
    void testDirectionSql() { // GH-90000
        assertThat(SortSpec.Direction.ASC.getSql()).isEqualTo("ASC [GH-90000]");
        assertThat(SortSpec.Direction.DESC.getSql()).isEqualTo("DESC [GH-90000]");
    }

    @Test
    @DisplayName("Multi-field sort - creates list of sort specifications [GH-90000]")
    void testMultiFieldSort() { // GH-90000
        List<SortSpec> sorts = List.of( // GH-90000
            SortSpec.asc("category [GH-90000]"),
            SortSpec.desc("price [GH-90000]"),
            SortSpec.asc("name [GH-90000]")
        );

        assertThat(sorts).hasSize(3); // GH-90000
        assertThat(sorts.get(0).getField()).isEqualTo("category [GH-90000]");
        assertThat(sorts.get(0).getDirection()).isEqualTo(SortSpec.Direction.ASC); // GH-90000
        assertThat(sorts.get(1).getField()).isEqualTo("price [GH-90000]");
        assertThat(sorts.get(1).getDirection()).isEqualTo(SortSpec.Direction.DESC); // GH-90000
        assertThat(sorts.get(2).getField()).isEqualTo("name [GH-90000]");
        assertThat(sorts.get(2).getDirection()).isEqualTo(SortSpec.Direction.ASC); // GH-90000
    }

    @Test
    @DisplayName("Complex sort - combines system and JSONB fields [GH-90000]")
    void testComplexSort() { // GH-90000
        List<SortSpec> sorts = List.of( // GH-90000
            SortSpec.desc("priority [GH-90000]"),           // JSONB field
            SortSpec.desc("createdAt [GH-90000]"),          // System field
            SortSpec.asc("address.city [GH-90000]"),        // Nested JSONB field
            SortSpec.asc("name [GH-90000]")                 // JSONB field
        );

        assertThat(sorts).hasSize(4); // GH-90000
        assertThat(sorts.get(0).getField()).isEqualTo("priority [GH-90000]");
        assertThat(sorts.get(1).getField()).isEqualTo("createdAt [GH-90000]");
        assertThat(sorts.get(2).getField()).isEqualTo("address.city [GH-90000]");
        assertThat(sorts.get(3).getField()).isEqualTo("name [GH-90000]");
    }

    @Test
    @DisplayName("Sort order precedence - validates multiple sorts [GH-90000]")
    void testSortOrderPrecedence() { // GH-90000
        // Primary: category ASC, Secondary: price DESC, Tertiary: name ASC
        List<SortSpec> sorts = List.of( // GH-90000
            SortSpec.asc("category [GH-90000]"),    // 1st priority
            SortSpec.desc("price [GH-90000]"),       // 2nd priority
            SortSpec.asc("name [GH-90000]")          // 3rd priority
        );

        assertThat(sorts).hasSize(3); // GH-90000

        // Verify order is maintained
        assertThat(sorts.get(0).getField()).isEqualTo("category [GH-90000]");
        assertThat(sorts.get(1).getField()).isEqualTo("price [GH-90000]");
        assertThat(sorts.get(2).getField()).isEqualTo("name [GH-90000]");

        // Verify directions
        assertThat(sorts.get(0).getDirection()).isEqualTo(SortSpec.Direction.ASC); // GH-90000
        assertThat(sorts.get(1).getDirection()).isEqualTo(SortSpec.Direction.DESC); // GH-90000
        assertThat(sorts.get(2).getDirection()).isEqualTo(SortSpec.Direction.ASC); // GH-90000
    }
}
