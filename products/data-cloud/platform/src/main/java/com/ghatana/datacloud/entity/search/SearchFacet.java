package com.ghatana.datacloud.entity.search;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Search facet value object with field counts.
 *
 * <p><b>Purpose</b><br>
 * Immutable representation of facet aggregation results, showing unique values
 * and their document counts for a specific field.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SearchFacet categoryFacet = SearchFacet.builder()
 *     .fieldName("category")
 *     .values(List.of(
 *         SearchFacet.FacetValue.of("electronics", 45),
 *         SearchFacet.FacetValue.of("clothing", 32),
 *         SearchFacet.FacetValue.of("books", 18)
 *     ))
 *     .build();
 * 
 * // Access facet results
 * String field = categoryFacet.getFieldName(); // "category"
 * List<FacetValue> values = categoryFacet.getValues();
 * int electronicsCount = values.get(0).getCount(); // 45
 * }</pre>
 *
 * <p><b>Field Requirements</b><br>
 * - fieldName: Non-blank, the field being aggregated
 * - values: Non-null list of FacetValue entries (value + count)
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - All fields are private final, list is unmodifiable.
 *
 * @doc.type record
 * @doc.purpose Immutable search facet value object
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class SearchFacet {

    private final String fieldName;
    private final List<FacetValue> values;

    private SearchFacet(String fieldName, List<FacetValue> values) {
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.values = Objects.requireNonNull(values, "values cannot be null");

        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
    }

    public String getFieldName() {
        return fieldName;
    }

    public List<FacetValue> getValues() {
        return Collections.unmodifiableList(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int getTotalValues() {
        return values.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String fieldName;
        private List<FacetValue> values;

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder values(List<FacetValue> values) {
            this.values = values;
            return this;
        }

        public SearchFacet build() {
            return new SearchFacet(fieldName, values);
        }
    }

    @Override
    public String toString() {
        return "SearchFacet{" +
                "fieldName='" + fieldName + '\'' +
                ", valueCount=" + values.size() +
                '}';
    }

    /**
     * Single facet value with document count.
     *
     * <p><b>Purpose</b><br>
     * Represents a unique value for a facet field and the number of documents
     * containing that value.
     *
     * <p><b>Usage</b><br>
     * <pre>{@code
     * FacetValue electronics = FacetValue.of("electronics", 45);
     * String value = electronics.getValue(); // "electronics"
     * int count = electronics.getCount(); // 45
     * }</pre>
     */
    public static final class FacetValue {
        private final String value;
        private final int count;

        private FacetValue(String value, int count) {
            this.value = Objects.requireNonNull(value, "value cannot be null");
            this.count = count;

            if (count < 0) {
                throw new IllegalArgumentException("count cannot be negative");
            }
        }

        public static FacetValue of(String value, int count) {
            return new FacetValue(value, count);
        }

        public String getValue() {
            return value;
        }

        public int getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FacetValue that = (FacetValue) o;
            return count == that.count && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, count);
        }

        @Override
        public String toString() {
            return "FacetValue{" +
                    "value='" + value + '\'' +
                    ", count=" + count +
                    '}';
        }
    }
}
