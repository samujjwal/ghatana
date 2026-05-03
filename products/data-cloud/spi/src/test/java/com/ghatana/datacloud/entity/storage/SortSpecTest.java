package com.ghatana.datacloud.entity.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SortSpec}.
 */
@DisplayName("SortSpec")
class SortSpecTest {

    @Test
    @DisplayName("asc creates ascending sort")
    void asc_createsAscendingSort() {
        SortSpec sort = SortSpec.asc("name");

        assertThat(sort.getField()).isEqualTo("name");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.ASC);
    }

    @Test
    @DisplayName("desc creates descending sort")
    void desc_createsDescendingSort() {
        SortSpec sort = SortSpec.desc("createdAt");

        assertThat(sort.getField()).isEqualTo("createdAt");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.DESC);
    }

    @Test
    @DisplayName("of creates descending sort by default")
    void of_createsDescendingSortByDefault() {
        SortSpec sort = SortSpec.of("age");

        assertThat(sort.getField()).isEqualTo("age");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.DESC);
    }

    @Test
    @DisplayName("of with direction creates sort with specified direction")
    void of_withDirection_createsSortWithSpecifiedDirection() {
        SortSpec sort = SortSpec.of("price", SortSpec.Direction.ASC);

        assertThat(sort.getField()).isEqualTo("price");
        assertThat(sort.getDirection()).isEqualTo(SortSpec.Direction.ASC);
    }


    @Test
    @DisplayName("toString returns string representation")
    void toString_returnsStringRepresentation() {
        SortSpec sort = SortSpec.asc("name");

        String str = sort.toString();
        assertThat(str).contains("field='name'");
        assertThat(str).contains("direction=ASC");
    }

    @Test
    @DisplayName("equals returns true for same sort")
    void equals_returnsTrueForSameSort() {
        SortSpec s1 = SortSpec.asc("name");
        SortSpec s2 = SortSpec.asc("name");

        assertThat(s1).isEqualTo(s2);
    }

    @Test
    @DisplayName("equals returns false for different sort")
    void equals_returnsFalseForDifferentSort() {
        SortSpec s1 = SortSpec.asc("name");
        SortSpec s2 = SortSpec.desc("name");

        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    @DisplayName("hashCode returns same for equal sorts")
    void hashCode_returnsSameForEqualSorts() {
        SortSpec s1 = SortSpec.asc("name");
        SortSpec s2 = SortSpec.asc("name");

        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    @DisplayName("Direction enum contains ASC and DESC")
    void directionEnum_containsAscAndDesc() {
        SortSpec.Direction[] directions = SortSpec.Direction.values();
        assertThat(directions).containsExactlyInAnyOrder(
                SortSpec.Direction.ASC,
                SortSpec.Direction.DESC
        );
    }

    @Test
    @DisplayName("Direction getSql returns SQL representation")
    void direction_getSql_returnsSqlRepresentation() {
        assertThat(SortSpec.Direction.ASC.getSql()).isEqualTo("ASC");
        assertThat(SortSpec.Direction.DESC.getSql()).isEqualTo("DESC");
    }
}
