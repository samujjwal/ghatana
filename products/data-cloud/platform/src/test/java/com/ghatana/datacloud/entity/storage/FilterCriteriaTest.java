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
    void testEqualityFilter() {
        FilterCriteria filter = FilterCriteria.eq("status", "active");
        
        assertThat(filter.getField()).isEqualTo("status");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.EQ);
        assertThat(filter.getValue()).isEqualTo("active");
    }
    
    @Test
    @DisplayName("ne() - creates not-equal filter")
    void testNotEqualFilter() {
        FilterCriteria filter = FilterCriteria.ne("status", "deleted");
        
        assertThat(filter.getField()).isEqualTo("status");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.NE);
        assertThat(filter.getValue()).isEqualTo("deleted");
    }
    
    @Test
    @DisplayName("gt() - creates greater-than filter")
    void testGreaterThanFilter() {
        FilterCriteria filter = FilterCriteria.gt("age", 30);
        
        assertThat(filter.getField()).isEqualTo("age");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GT);
        assertThat(filter.getValue()).isEqualTo(30);
    }
    
    @Test
    @DisplayName("gte() - creates greater-than-or-equal filter")
    void testGreaterThanOrEqualFilter() {
        FilterCriteria filter = FilterCriteria.gte("score", 75);
        
        assertThat(filter.getField()).isEqualTo("score");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GTE);
        assertThat(filter.getValue()).isEqualTo(75);
    }
    
    @Test
    @DisplayName("lt() - creates less-than filter")
    void testLessThanFilter() {
        FilterCriteria filter = FilterCriteria.lt("price", 100.0);
        
        assertThat(filter.getField()).isEqualTo("price");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LT);
        assertThat(filter.getValue()).isEqualTo(100.0);
    }
    
    @Test
    @DisplayName("lte() - creates less-than-or-equal filter")
    void testLessThanOrEqualFilter() {
        FilterCriteria filter = FilterCriteria.lte("discount", 25.5);
        
        assertThat(filter.getField()).isEqualTo("discount");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LTE);
        assertThat(filter.getValue()).isEqualTo(25.5);
    }
    
    @Test
    @DisplayName("like() - creates pattern matching filter")
    void testLikeFilter() {
        FilterCriteria filter = FilterCriteria.like("name", "John%");
        
        assertThat(filter.getField()).isEqualTo("name");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LIKE);
        assertThat(filter.getValue()).isEqualTo("John%");
    }
    
    @Test
    @DisplayName("ilike() - creates case-insensitive pattern matching filter")
    void testIlikeFilter() {
        FilterCriteria filter = FilterCriteria.ilike("email", "%@example.com");
        
        assertThat(filter.getField()).isEqualTo("email");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.ILIKE);
        assertThat(filter.getValue()).isEqualTo("%@example.com");
    }
    
    @Test
    @DisplayName("in() - creates list containment filter")
    void testInFilter() {
        List<String> cities = List.of("New York", "San Francisco", "Boston");
        FilterCriteria filter = FilterCriteria.in("city", cities);
        
        assertThat(filter.getField()).isEqualTo("city");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IN);
        assertThat(filter.getValue()).isEqualTo(cities);
    }
    
    @Test
    @DisplayName("isNull() - creates null check filter")
    void testIsNullFilter() {
        FilterCriteria filter = FilterCriteria.isNull("deletedAt");
        
        assertThat(filter.getField()).isEqualTo("deletedAt");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IS_NULL);
        assertThat(filter.getValue()).isNull();
    }
    
    @Test
    @DisplayName("isNotNull() - creates not-null check filter")
    void testIsNotNullFilter() {
        FilterCriteria filter = FilterCriteria.isNotNull("profileImage");
        
        assertThat(filter.getField()).isEqualTo("profileImage");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IS_NOT_NULL);
        assertThat(filter.getValue()).isNull();
    }
    
    @Test
    @DisplayName("Nested field - creates filter with dot notation")
    void testNestedFieldFilter() {
        FilterCriteria filter = FilterCriteria.eq("address.city", "San Francisco");
        
        assertThat(filter.getField()).isEqualTo("address.city");
        assertThat(filter.getValue()).isEqualTo("San Francisco");
    }
    
    @Test
    @DisplayName("Multi-level nested field - creates filter with multiple dots")
    void testMultiLevelNestedField() {
        FilterCriteria filter = FilterCriteria.gte("location.coordinates.lat", 37.7);
        
        assertThat(filter.getField()).isEqualTo("location.coordinates.lat");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GTE);
        assertThat(filter.getValue()).isEqualTo(37.7);
    }
    
    @Test
    @DisplayName("equals() - returns true for identical filters")
    void testEquality() {
        FilterCriteria filter1 = FilterCriteria.eq("status", "active");
        FilterCriteria filter2 = FilterCriteria.eq("status", "active");
        
        assertThat(filter1).isEqualTo(filter2);
        assertThat(filter1.hashCode()).isEqualTo(filter2.hashCode());
    }
    
    @Test
    @DisplayName("equals() - returns false for different filters")
    void testInequality() {
        FilterCriteria filter1 = FilterCriteria.eq("status", "active");
        FilterCriteria filter2 = FilterCriteria.eq("status", "inactive");
        FilterCriteria filter3 = FilterCriteria.ne("status", "active");
        
        assertThat(filter1).isNotEqualTo(filter2);
        assertThat(filter1).isNotEqualTo(filter3);
    }
    
    @Test
    @DisplayName("toString() - returns meaningful representation")
    void testToString() {
        FilterCriteria filter = FilterCriteria.gt("age", 30);
        
        String str = filter.toString();
        assertThat(str).contains("age");
        assertThat(str).contains("GT");
        assertThat(str).contains("30");
    }
    
    @Test
    @DisplayName("Constructor - throws NPE for null field")
    void testNullFieldThrowsException() {
        assertThatThrownBy(() -> FilterCriteria.eq(null, "value"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("field");
    }
    
    @Test
    @DisplayName("Operator SQL - returns correct SQL operator strings")
    void testOperatorSql() {
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
    void testRangeQuery() {
        FilterCriteria lowerBound = FilterCriteria.gte("age", 25);
        FilterCriteria upperBound = FilterCriteria.lte("age", 50);
        
        List<FilterCriteria> rangeFilter = List.of(lowerBound, upperBound);
        
        assertThat(rangeFilter).hasSize(2);
        assertThat(rangeFilter.get(0).getOperator()).isEqualTo(FilterCriteria.Operator.GTE);
        assertThat(rangeFilter.get(1).getOperator()).isEqualTo(FilterCriteria.Operator.LTE);
    }
    
    @Test
    @DisplayName("Complex filter - combines multiple criteria types")
    void testComplexFilter() {
        List<FilterCriteria> filters = List.of(
            FilterCriteria.eq("status", "active"),
            FilterCriteria.gt("age", 25),
            FilterCriteria.in("city", List.of("NY", "SF", "LA")),
            FilterCriteria.like("email", "%@example.com"),
            FilterCriteria.isNotNull("profileImage")
        );
        
        assertThat(filters).hasSize(5);
        assertThat(filters.get(0).getOperator()).isEqualTo(FilterCriteria.Operator.EQ);
        assertThat(filters.get(1).getOperator()).isEqualTo(FilterCriteria.Operator.GT);
        assertThat(filters.get(2).getOperator()).isEqualTo(FilterCriteria.Operator.IN);
        assertThat(filters.get(3).getOperator()).isEqualTo(FilterCriteria.Operator.LIKE);
        assertThat(filters.get(4).getOperator()).isEqualTo(FilterCriteria.Operator.IS_NOT_NULL);
    }
}
