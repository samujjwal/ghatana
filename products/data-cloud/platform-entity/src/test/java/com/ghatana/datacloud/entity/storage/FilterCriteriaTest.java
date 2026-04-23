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
@DisplayName("FilterCriteria Tests")
class FilterCriteriaTest {

    @Test
    @DisplayName("eq() - creates equality filter")
    void testEqualityFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.eq("status", "active"); // GH-90000

        assertThat(filter.getField()).isEqualTo("status");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.EQ); // GH-90000
        assertThat(filter.getValue()).isEqualTo("active");
    }

    @Test
    @DisplayName("ne() - creates not-equal filter")
    void testNotEqualFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.ne("status", "deleted"); // GH-90000

        assertThat(filter.getField()).isEqualTo("status");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.NE); // GH-90000
        assertThat(filter.getValue()).isEqualTo("deleted");
    }

    @Test
    @DisplayName("gt() - creates greater-than filter")
    void testGreaterThanFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.gt("age", 30); // GH-90000

        assertThat(filter.getField()).isEqualTo("age");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GT); // GH-90000
        assertThat(filter.getValue()).isEqualTo(30); // GH-90000
    }

    @Test
    @DisplayName("gte() - creates greater-than-or-equal filter")
    void testGreaterThanOrEqualFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.gte("score", 75); // GH-90000

        assertThat(filter.getField()).isEqualTo("score");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GTE); // GH-90000
        assertThat(filter.getValue()).isEqualTo(75); // GH-90000
    }

    @Test
    @DisplayName("lt() - creates less-than filter")
    void testLessThanFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.lt("price", 100.0); // GH-90000

        assertThat(filter.getField()).isEqualTo("price");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LT); // GH-90000
        assertThat(filter.getValue()).isEqualTo(100.0); // GH-90000
    }

    @Test
    @DisplayName("lte() - creates less-than-or-equal filter")
    void testLessThanOrEqualFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.lte("discount", 25.5); // GH-90000

        assertThat(filter.getField()).isEqualTo("discount");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LTE); // GH-90000
        assertThat(filter.getValue()).isEqualTo(25.5); // GH-90000
    }

    @Test
    @DisplayName("like() - creates pattern matching filter")
    void testLikeFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.like("name", "John%"); // GH-90000

        assertThat(filter.getField()).isEqualTo("name");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LIKE); // GH-90000
        assertThat(filter.getValue()).isEqualTo("John%");
    }

    @Test
    @DisplayName("ilike() - creates case-insensitive pattern matching filter")
    void testIlikeFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.ilike("email", "%@example.com"); // GH-90000

        assertThat(filter.getField()).isEqualTo("email");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.ILIKE); // GH-90000
        assertThat(filter.getValue()).isEqualTo("%@example.com");
    }

    @Test
    @DisplayName("in() - creates list containment filter")
    void testInFilter() { // GH-90000
        List<String> cities = List.of("New York", "San Francisco", "Boston"); // GH-90000
        FilterCriteria filter = FilterCriteria.in("city", cities); // GH-90000

        assertThat(filter.getField()).isEqualTo("city");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IN); // GH-90000
        assertThat(filter.getValue()).isEqualTo(cities); // GH-90000
    }

    @Test
    @DisplayName("isNull() - creates null check filter")
    void testIsNullFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.isNull("deletedAt");

        assertThat(filter.getField()).isEqualTo("deletedAt");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IS_NULL); // GH-90000
        assertThat(filter.getValue()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("isNotNull() - creates not-null check filter")
    void testIsNotNullFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.isNotNull("profileImage");

        assertThat(filter.getField()).isEqualTo("profileImage");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IS_NOT_NULL); // GH-90000
        assertThat(filter.getValue()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Nested field - creates filter with dot notation")
    void testNestedFieldFilter() { // GH-90000
        FilterCriteria filter = FilterCriteria.eq("address.city", "San Francisco"); // GH-90000

        assertThat(filter.getField()).isEqualTo("address.city");
        assertThat(filter.getValue()).isEqualTo("San Francisco");
    }

    @Test
    @DisplayName("Multi-level nested field - creates filter with multiple dots")
    void testMultiLevelNestedField() { // GH-90000
        FilterCriteria filter = FilterCriteria.gte("location.coordinates.lat", 37.7); // GH-90000

        assertThat(filter.getField()).isEqualTo("location.coordinates.lat");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GTE); // GH-90000
        assertThat(filter.getValue()).isEqualTo(37.7); // GH-90000
    }

    @Test
    @DisplayName("equals() - returns true for identical filters")
    void testEquality() { // GH-90000
        FilterCriteria filter1 = FilterCriteria.eq("status", "active"); // GH-90000
        FilterCriteria filter2 = FilterCriteria.eq("status", "active"); // GH-90000

        assertThat(filter1).isEqualTo(filter2); // GH-90000
        assertThat(filter1.hashCode()).isEqualTo(filter2.hashCode()); // GH-90000
    }

    @Test
    @DisplayName("equals() - returns false for different filters")
    void testInequality() { // GH-90000
        FilterCriteria filter1 = FilterCriteria.eq("status", "active"); // GH-90000
        FilterCriteria filter2 = FilterCriteria.eq("status", "inactive"); // GH-90000
        FilterCriteria filter3 = FilterCriteria.ne("status", "active"); // GH-90000

        assertThat(filter1).isNotEqualTo(filter2); // GH-90000
        assertThat(filter1).isNotEqualTo(filter3); // GH-90000
    }

    @Test
    @DisplayName("toString() - returns meaningful representation")
    void testToString() { // GH-90000
        FilterCriteria filter = FilterCriteria.gt("age", 30); // GH-90000

        String str = filter.toString(); // GH-90000
        assertThat(str).contains("age");
        assertThat(str).contains("GT");
        assertThat(str).contains("30");
    }

    @Test
    @DisplayName("Constructor - throws NPE for null field")
    void testNullFieldThrowsException() { // GH-90000
        assertThatThrownBy(() -> FilterCriteria.eq(null, "value")) // GH-90000
            .isInstanceOf(NullPointerException.class) // GH-90000
            .hasMessageContaining("field");
    }

    @Test
    @DisplayName("Operator SQL - returns correct SQL operator strings")
    void testOperatorSql() { // GH-90000
        assertThat(FilterCriteria.Operator.EQ.getSql()).isEqualTo("=");
        assertThat(FilterCriteria.Operator.NE.getSql()).isEqualTo("!=");
        assertThat(FilterCriteria.Operator.GT.getSql()).isEqualTo(">");
        assertThat(FilterCriteria.Operator.GTE.getSql()).isEqualTo(">=");
        assertThat(FilterCriteria.Operator.LT.getSql()).isEqualTo("<");
        assertThat(FilterCriteria.Operator.LTE.getSql()).isEqualTo("<=");
        assertThat(FilterCriteria.Operator.LIKE.getSql()).isEqualTo("LIKE");
        assertThat(FilterCriteria.Operator.ILIKE.getSql()).isEqualTo("ILIKE");
        assertThat(FilterCriteria.Operator.IN.getSql()).isEqualTo("IN");
        assertThat(FilterCriteria.Operator.IS_NULL.getSql()).isEqualTo("IS NULL");
        assertThat(FilterCriteria.Operator.IS_NOT_NULL.getSql()).isEqualTo("IS NOT NULL");
    }

    @Test
    @DisplayName("Range query - combines multiple filters")
    void testRangeQuery() { // GH-90000
        FilterCriteria lowerBound = FilterCriteria.gte("age", 25); // GH-90000
        FilterCriteria upperBound = FilterCriteria.lte("age", 50); // GH-90000

        List<FilterCriteria> rangeFilter = List.of(lowerBound, upperBound); // GH-90000

        assertThat(rangeFilter).hasSize(2); // GH-90000
        assertThat(rangeFilter.get(0).getOperator()).isEqualTo(FilterCriteria.Operator.GTE); // GH-90000
        assertThat(rangeFilter.get(1).getOperator()).isEqualTo(FilterCriteria.Operator.LTE); // GH-90000
    }

    @Test
    @DisplayName("Complex filter - combines multiple criteria types")
    void testComplexFilter() { // GH-90000
        List<FilterCriteria> filters = List.of( // GH-90000
            FilterCriteria.eq("status", "active"), // GH-90000
            FilterCriteria.gt("age", 25), // GH-90000
            FilterCriteria.in("city", List.of("NY", "SF", "LA")), // GH-90000
            FilterCriteria.like("email", "%@example.com"), // GH-90000
            FilterCriteria.isNotNull("profileImage")
        );

        assertThat(filters).hasSize(5); // GH-90000
        assertThat(filters.get(0).getOperator()).isEqualTo(FilterCriteria.Operator.EQ); // GH-90000
        assertThat(filters.get(1).getOperator()).isEqualTo(FilterCriteria.Operator.GT); // GH-90000
        assertThat(filters.get(2).getOperator()).isEqualTo(FilterCriteria.Operator.IN); // GH-90000
        assertThat(filters.get(3).getOperator()).isEqualTo(FilterCriteria.Operator.LIKE); // GH-90000
        assertThat(filters.get(4).getOperator()).isEqualTo(FilterCriteria.Operator.IS_NOT_NULL); // GH-90000
    }
}
