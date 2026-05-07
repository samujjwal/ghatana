package com.ghatana.datacloud.entity.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FilterCriteria}.
 */
@DisplayName("FilterCriteria")
class FilterCriteriaTest {

    @Test
    @DisplayName("eq creates equality filter")
    void eq_createsEqualityFilter() {
        FilterCriteria filter = FilterCriteria.eq("status", "active");

        assertThat(filter.getField()).isEqualTo("status");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.EQ);
        assertThat(filter.getValue()).isEqualTo("active");
    }

    @Test
    @DisplayName("ne creates not equal filter")
    void ne_createsNotEqualFilter() {
        FilterCriteria filter = FilterCriteria.ne("status", "deleted");

        assertThat(filter.getField()).isEqualTo("status");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.NE);
        assertThat(filter.getValue()).isEqualTo("deleted");
    }

    @Test
    @DisplayName("gt creates greater than filter")
    void gt_createsGreaterThanFilter() {
        FilterCriteria filter = FilterCriteria.gt("age", 18);

        assertThat(filter.getField()).isEqualTo("age");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GT);
        assertThat(filter.getValue()).isEqualTo(18);
    }

    @Test
    @DisplayName("gte creates greater than or equal filter")
    void gte_createsGreaterThanOrEqualFilter() {
        FilterCriteria filter = FilterCriteria.gte("price", 100.0);

        assertThat(filter.getField()).isEqualTo("price");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.GTE);
        assertThat(filter.getValue()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("lt creates less than filter")
    void lt_createsLessThanFilter() {
        FilterCriteria filter = FilterCriteria.lt("quantity", 10);

        assertThat(filter.getField()).isEqualTo("quantity");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LT);
        assertThat(filter.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("lte creates less than or equal filter")
    void lte_createsLessThanOrEqualFilter() {
        FilterCriteria filter = FilterCriteria.lte("score", 95);

        assertThat(filter.getField()).isEqualTo("score");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LTE);
        assertThat(filter.getValue()).isEqualTo(95);
    }

    @Test
    @DisplayName("like creates pattern matching filter")
    void like_createsPatternMatchingFilter() {
        FilterCriteria filter = FilterCriteria.like("name", "John%");

        assertThat(filter.getField()).isEqualTo("name");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.LIKE);
        assertThat(filter.getValue()).isEqualTo("John%");
    }

    @Test
    @DisplayName("ilike creates case-insensitive pattern filter")
    void ilike_createsCaseInsensitivePatternFilter() {
        FilterCriteria filter = FilterCriteria.ilike("email", "%@example.com");

        assertThat(filter.getField()).isEqualTo("email");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.ILIKE);
        assertThat(filter.getValue()).isEqualTo("%@example.com");
    }

    @Test
    @DisplayName("in creates in list filter")
    void in_createsInListFilter() {
        java.util.List<String> values = java.util.List.of("active", "pending");
        FilterCriteria filter = FilterCriteria.in("status", values);

        assertThat(filter.getField()).isEqualTo("status");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IN);
        assertThat(filter.getValue()).isEqualTo(values);
    }

    @Test
    @DisplayName("isNull creates null check filter")
    void isNull_createsNullCheckFilter() {
        FilterCriteria filter = FilterCriteria.isNull("deletedAt");

        assertThat(filter.getField()).isEqualTo("deletedAt");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IS_NULL);
        assertThat(filter.getValue()).isNull();
    }

    @Test
    @DisplayName("isNotNull creates not null check filter")
    void isNotNull_createsNotNullCheckFilter() {
        FilterCriteria filter = FilterCriteria.isNotNull("createdAt");

        assertThat(filter.getField()).isEqualTo("createdAt");
        assertThat(filter.getOperator()).isEqualTo(FilterCriteria.Operator.IS_NOT_NULL);
        assertThat(filter.getValue()).isNull();
    }


    @Test
    @DisplayName("toString returns string representation")
    void toString_returnsStringRepresentation() {
        FilterCriteria filter = FilterCriteria.eq("status", "active");

        String str = filter.toString();
        assertThat(str).contains("field='status'");
        assertThat(str).contains("operator=EQ");
        assertThat(str).contains("value=active");
    }

    @Test
    @DisplayName("equals returns true for same filter")
    void equals_returnsTrueForSameFilter() {
        FilterCriteria f1 = FilterCriteria.eq("status", "active");
        FilterCriteria f2 = FilterCriteria.eq("status", "active");

        assertThat(f1).isEqualTo(f2);
    }

    @Test
    @DisplayName("equals returns false for different filter")
    void equals_returnsFalseForDifferentFilter() {
        FilterCriteria f1 = FilterCriteria.eq("status", "active");
        FilterCriteria f2 = FilterCriteria.eq("status", "inactive");

        assertThat(f1).isNotEqualTo(f2);
    }

    @Test
    @DisplayName("hashCode returns same for equal filters")
    void hashCode_returnsSameForEqualFilters() {
        FilterCriteria f1 = FilterCriteria.eq("status", "active");
        FilterCriteria f2 = FilterCriteria.eq("status", "active");

        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
    }

    @Test
    @DisplayName("Operator enum contains all expected operators")
    void operatorEnum_containsAllExpectedOperators() {
        FilterCriteria.Operator[] operators = FilterCriteria.Operator.values();
        assertThat(operators).contains(
                FilterCriteria.Operator.EQ,
                FilterCriteria.Operator.NE,
                FilterCriteria.Operator.GT,
                FilterCriteria.Operator.GTE,
                FilterCriteria.Operator.LT,
                FilterCriteria.Operator.LTE,
                FilterCriteria.Operator.LIKE,
                FilterCriteria.Operator.ILIKE,
                FilterCriteria.Operator.IN,
                FilterCriteria.Operator.IS_NULL,
                FilterCriteria.Operator.IS_NOT_NULL
        );
    }

    @Test
    @DisplayName("Operator getSql returns SQL representation")
    void operator_getSql_returnsSqlRepresentation() {
        assertThat(FilterCriteria.Operator.EQ.getSql()).isEqualTo("=");
        assertThat(FilterCriteria.Operator.GT.getSql()).isEqualTo(">");
        assertThat(FilterCriteria.Operator.LIKE.getSql()).isEqualTo("LIKE");
        assertThat(FilterCriteria.Operator.IS_NULL.getSql()).isEqualTo("IS NULL");
    }
}
