package com.ghatana.datacloud.entity.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FilterCriteria class.
 *
 * @doc.type class
 * @doc.purpose Test filter criteria value object
 * @doc.layer platform
 * @doc.pattern Unit Test
 */
@DisplayName("FilterCriteria Tests [GH-90000]")
class FilterCriteriaTest {

    @Test
    @DisplayName("eq() - creates equality filter [GH-90000]")
    void testEqualityFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.eq("status", "active"); // GH-90000

        assertThat(filter.getField()).isEqualTo("status [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.EQ); // GH-90000
        assertThat(filter.getValue()).isEqualTo("active [GH-90000]");
    }

    @Test
    @DisplayName("ne() - creates not-equal filter [GH-90000]")
    void testNotEqualFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.ne("status", "deleted"); // GH-90000

        assertThat(filter.getField()).isEqualTo("status [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.NE); // GH-90000
        assertThat(filter.getValue()).isEqualTo("deleted [GH-90000]");
    }

    @Test
    @DisplayName("gt() - creates greater-than filter [GH-90000]")
    void testGreaterThanFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.gt("age", 30); // GH-90000

        assertThat(filter.getField()).isEqualTo("age [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GT); // GH-90000
        assertThat(filter.getValue()).isEqualTo(30); // GH-90000
    }

    @Test
    @DisplayName("gte() - creates greater-than-or-equal filter [GH-90000]")
    void testGreaterThanOrEqualFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.gte("score", 75); // GH-90000

        assertThat(filter.getField()).isEqualTo("score [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GTE); // GH-90000
        assertThat(filter.getValue()).isEqualTo(75); // GH-90000
    }

    @Test
    @DisplayName("lt() - creates less-than filter [GH-90000]")
    void testLessThanFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.lt("price", 100.0); // GH-90000

        assertThat(filter.getField()).isEqualTo("price [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LT); // GH-90000
        assertThat(filter.getValue()).isEqualTo(100.0); // GH-90000
    }

    @Test
    @DisplayName("lte() - creates less-than-or-equal filter [GH-90000]")
    void testLessThanOrEqualFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.lte("discount", 25.5); // GH-90000

        assertThat(filter.getField()).isEqualTo("discount [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LTE); // GH-90000
        assertThat(filter.getValue()).isEqualTo(25.5); // GH-90000
    }

    @Test
    @DisplayName("like() - creates pattern matching filter [GH-90000]")
    void testLikeFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.like("name", "John%"); // GH-90000

        assertThat(filter.getField()).isEqualTo("name [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LIKE); // GH-90000
        assertThat(filter.getValue()).isEqualTo("John% [GH-90000]");
    }

    @Test
    @DisplayName("ilike() - creates case-insensitive pattern matching filter [GH-90000]")
    void testIlikeFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.ilike("email", "%@example.com"); // GH-90000

        assertThat(filter.getField()).isEqualTo("email [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.ILIKE); // GH-90000
        assertThat(filter.getValue()).isEqualTo("%@example.com [GH-90000]");
    }

    @Test
    @DisplayName("in() - creates list containment filter [GH-90000]")
    void testInFilter() { // GH-90000
        List<String> cities = List.of("New York", "San Francisco", "Boston"); // GH-90000
        FilterCriteria filter = FilterCriteria.in("city", cities); // GH-90000

        assertThat(filter.getField()).isEqualTo("city [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IN); // GH-90000
        assertThat(filter.getValue()).isEqualTo(cities); // GH-90000
    }

    @Test
    @DisplayName("isNull() - creates null check filter [GH-90000]")
    void testIsNullFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.isNull("deletedAt [GH-90000]");

        assertThat(filter.getField()).isEqualTo("deletedAt [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IS_NULL); // GH-90000
        assertThat(filter.getValue()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("isNotNull() - creates not-null check filter [GH-90000]")
    void testIsNotNullFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.isNotNull("profileImage [GH-90000]");

        assertThat(filter.getField()).isEqualTo("profileImage [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IS_NOT_NULL); // GH-90000
        assertThat(filter.getValue()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Nested field - creates filter with dot notation [GH-90000]")
    void testNestedFieldFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.eq("address.city", "San Francisco"); // GH-90000

        assertThat(filter.getField()).isEqualTo("address.city [GH-90000]");
        assertThat(filter.getValue()).isEqualTo("San Francisco [GH-90000]");
    }

    @Test
    @DisplayName("Multi-level nested field - creates filter with multiple dots [GH-90000]")
    void testMultiLevelNestedField() { // GH-90000
        FilterCriteria filter = FilterCriteria.gte("location.coordinates.lat", 37.7); // GH-90000

        assertThat(filter.getField()).isEqualTo("location.coordinates.lat [GH-90000]");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GTE); // GH-90000
        assertThat(filter.getValue()).isEqualTo(37.7); // GH-90000
    }

    @Test
    @DisplayName("equals() - returns true for identical filters [GH-90000]")
    void testEquality() { // GH-90000
        FilterCriteria filter1 = FilterCriteria.eq("status", "active"); // GH-90000
        FilterCriteria filter2 = FilterCriteria.eq("status", "active"); // GH-90000

        assertThat(filter1).isEqualTo(filter2); // GH-90000
        assertThat(filter1.hashCode()).isEqualTo(filter2.hashCode()); // GH-90000
    }

    @Test
    @DisplayName("equals() - returns false for different filters [GH-90000]")
    void testInequality() { // GH-90000
        FilterCriteria filter1 = FilterCriteria.eq("status", "active"); // GH-90000
        FilterCriteria filter2 = FilterCriteria.eq("status", "inactive"); // GH-90000
        FilterCriteria filter3 = FilterCriteria.ne("status", "active"); // GH-90000

        assertThat(filter1).isNotEqualTo(filter2); // GH-90000
        assertThat(filter1).isNotEqualTo(filter3); // GH-90000
    }

    @Test
    @DisplayName("toString() - returns meaningful representation [GH-90000]")
    void testToString() { // GH-90000
        FilterCriteria filter = FilterCriteria.gt("age", 30); // GH-90000

        String str = filter.toString(); // GH-90000
        assertThat(str).contains("age [GH-90000]");
        assertThat(str).contains("GT [GH-90000]");
        assertThat(str).contains("30 [GH-90000]");
    }

    @Test
    @DisplayName("Constructor - throws NPE for null field [GH-90000]")
    void testNullFieldThrowsException() { // GH-90000
        assertThatThrownBy(() -> FilterCriteria.eq(null, "value")) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessageContaining("field [GH-90000]");
    }

    @Test
    @DisplayName("Operator SQL - returns correct SQL operator strings [GH-90000]")
    void testOperatorSql() { // GH-90000
        assertThat(FilterCriteria.Operator.EQ.getSql()).isEqualTo("= [GH-90000]");
        assertThat(FilterCriteria.Operator.NE.getSql()).isEqualTo("!= [GH-90000]");
        assertThat(FilterCriteria.Operator.GT.getSql()).isEqualTo("> [GH-90000]");
        assertThat(FilterCriteria.Operator.GTE.getSql()).isEqualTo(">= [GH-90000]");
        assertThat(FilterCriteria.Operator.LT.getSql()).isEqualTo("< [GH-90000]");
        assertThat(FilterCriteria.Operator.LTE.getSql()).isEqualTo("<= [GH-90000]");
        assertThat(FilterCriteria.Operator.LIKE.getSql()).isEqualTo("LIKE [GH-90000]");
        assertThat(FilterCriteria.Operator.ILIKE.getSql()).isEqualTo("ILIKE [GH-90000]");
        assertThat(FilterCriteria.Operator.IN.getSql()).isEqualTo("IN [GH-90000]");
        assertThat(FilterCriteria.Operator.IS_NULL.getSql()).isEqualTo("IS NULL [GH-90000]");
        assertThat(FilterCriteria.Operator.IS_NOT_NULL.getSql()).isEqualTo("IS NOT NULL [GH-90000]");
    }

    @Test
    @DisplayName("Range query - combines multiple filters [GH-90000]")
    void testRangeQuery() { // GH-90000
        FilterCriteria lowerBound = FilterCriteria.gte("age", 25); // GH-90000
        FilterCriteria upperBound = FilterCriteria.lte("age", 50); // GH-90000

        List<FilterCriteria> rangeFilter = List.of(lowerBound, upperBound); // GH-90000

        assertThat(rangeFilter).hasSize(2); // GH-90000
        assertThat(rangeFilter.get(0).getOperator()).isEqualTo(FilterCriteria.Operator.GTE); // GH-90000
        assertThat(rangeFilter.get(1).getOperator()).isEqualTo(FilterCriteria.Operator.LTE); // GH-90000
    }

    @Test
    @DisplayName("Complex filter - combines multiple criteria types [GH-90000]")
    void testComplexFilter() { // GH-90000
        List<FilterCriteria> filters = List.of( // GH-90000
            FilterCriteria.eq("status", "active"), // GH-90000
            FilterCriteria.gt("age", 25), // GH-90000
            FilterCriteria.in("city", List.of("NY", "SF", "LA")), // GH-90000
            FilterCriteria.like("email", "%@example.com"), // GH-90000
            FilterCriteria.isNotNull("profileImage [GH-90000]")
        );

        assertThat(filters).hasSize(5); // GH-90000
        assertThat(filters.get(0).getOperator()).isEqualTo(FilterCriteria.Operator.EQ); // GH-90000
        assertThat(filters.get(1).getOperator()).isEqualTo(FilterCriteria.Operator.GT); // GH-90000
        assertThat(filters.get(2).getOperator()).isEqualTo(FilterCriteria.Operator.IN); // GH-90000
        assertThat(filters.get(3).getOperator()).isEqualTo(FilterCriteria.Operator.LIKE); // GH-90000
        assertThat(filters.get(4).getOperator()).isEqualTo(FilterCriteria.Operator.IS_NOT_NULL); // GH-90000
    }
}
