package com.ghatana.datacloud;

import com.ghatana.datacloud.entity.storage.FilterCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract/type drift test for DC-DRY-001.
 *
 * <p>Verifies that {@link FilterCriteria.Operator} is the single canonical filter
 * operator type used across {@link RecordQuery} and {@link DataCloudClient.Filter}.
 * Any divergence — a new operator enum or a string-typed operator field — must
 * break this test before reaching main.
 *
 * @doc.type class
 * @doc.purpose Prevent operator enum drift between RecordQuery and DataCloudClient.Filter
 * @doc.layer product
 * @doc.pattern Contract Test
 */
@DisplayName("Filter Operator Consolidation — DC-DRY-001")
class FilterOperatorConsolidationTest {

    @Test
    @DisplayName("RecordQuery.FilterCondition.operator field uses FilterCriteria.Operator (not a local enum)")
    void filterConditionOperatorType_isFilterCriteriaOperator() throws Exception {
        var operatorField = RecordQuery.FilterCondition.class.getDeclaredField("operator");
        assertThat(operatorField.getType())
                .as("FilterCondition.operator must use the canonical FilterCriteria.Operator type")
                .isEqualTo(FilterCriteria.Operator.class);
    }

    @Test
    @DisplayName("DataCloudClient.Filter.operator field uses FilterCriteria.Operator (not String)")
    void dataCloudClientFilterOperatorType_isFilterCriteriaOperator() throws Exception {
        var operatorField = DataCloudClient.Filter.class.getDeclaredField("operator");
        assertThat(operatorField.getType())
                .as("DataCloudClient.Filter.operator must use the canonical FilterCriteria.Operator type, not String")
                .isEqualTo(FilterCriteria.Operator.class);
    }

    @Test
    @DisplayName("FilterCriteria.Operator covers all operators needed by RecordQuery and DataCloudClient")
    void filterCriteriaOperator_coversAllRequiredOperators() {
        Set<String> enumNames = new java.util.HashSet<>();
        for (FilterCriteria.Operator op : FilterCriteria.Operator.values()) {
            enumNames.add(op.name());
        }
        assertThat(enumNames).containsAll(Set.of(
                "EQ", "NE", "GT", "GTE", "LT", "LTE",
                "LIKE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL",
                "NOT_LIKE", "STARTS_WITH", "ENDS_WITH", "CONTAINS",
                "BETWEEN", "REGEX"
        ));
    }

    @Test
    @DisplayName("FilterCriteria.Operator every value has non-null SQL representation")
    void filterCriteriaOperator_allValuesHaveSqlRepresentation() {
        for (FilterCriteria.Operator op : FilterCriteria.Operator.values()) {
            assertThat(op.getSql())
                    .as("Operator %s must have a SQL representation", op)
                    .isNotNull()
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("DataCloudClient.Filter factory methods return typed operator instances")
    void dataCloudClientFilterFactories_returnTypedOperator() {
        assertThat(DataCloudClient.Filter.eq("f", "v").operator()).isEqualTo(FilterCriteria.Operator.EQ);
        assertThat(DataCloudClient.Filter.ne("f", "v").operator()).isEqualTo(FilterCriteria.Operator.NE);
        assertThat(DataCloudClient.Filter.gt("f", 1).operator()).isEqualTo(FilterCriteria.Operator.GT);
        assertThat(DataCloudClient.Filter.gte("f", 1).operator()).isEqualTo(FilterCriteria.Operator.GTE);
        assertThat(DataCloudClient.Filter.lt("f", 1).operator()).isEqualTo(FilterCriteria.Operator.LT);
        assertThat(DataCloudClient.Filter.lte("f", 1).operator()).isEqualTo(FilterCriteria.Operator.LTE);
        assertThat(DataCloudClient.Filter.like("f", "%v%").operator()).isEqualTo(FilterCriteria.Operator.LIKE);
    }
}
