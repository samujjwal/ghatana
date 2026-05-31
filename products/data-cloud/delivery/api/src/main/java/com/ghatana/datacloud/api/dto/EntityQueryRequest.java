package com.ghatana.datacloud.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


/**
 * Request DTO for executing entity queries.
 *
 * <p>
 * DC-10: Aligned with QuerySpec canonical model for query unification
 * across DataCloudClient.Query, EntityStore.QuerySpec, OpenAPI, UI, and SDK.
 *
 * <p>
 * This DTO captures filter expression, pagination (limit/offset), sorting,
 * projections, time window, and consistency hints.
 *
 * <p>
 * DC-11: Projections, consistencyLevel, freshnessHint, and queryType are reserved
 * fields. These fields throw UnsupportedOperationException if provided, ensuring
 * explicit rejection rather than silent ignore.
 *
 * @doc.type class
 * @doc.purpose Entity query request aligned with canonical QuerySpec
 * @doc.layer platform
 * @doc.pattern DataTransfer
*/
public class EntityQueryRequest {

    @Size(max = 4096, message = "filter expression must not exceed 4096 characters")
    private String filter;

    @Min(value = 1, message = "limit must be at least 1")
    @Max(value = 50_000, message = "limit must not exceed 50,000")
    private Integer limit;

    @Min(value = 0, message = "offset must be non-negative")
    private Integer offset;

    // DC-10: Align with QuerySpec - use sortFields instead of single sortField
    @Size(max = 10, message = "sort fields must not exceed 10 entries")
    private java.util.List<SortFieldRequest> sortFields;

    // DC-10: Add projections for field selection
    // DC-11: Reserved field - will throw if provided
    @Size(max = 100, message = "projections must not exceed 100 fields")
    private java.util.List<String> projections;

    // DC-10: Add consistency level hint
    // DC-11: Reserved field - will throw if provided
    @Pattern(regexp = "STRONG|EVENTUAL|BOUNDED_STALENESS", flags = Pattern.Flag.CASE_INSENSITIVE,
             message = "consistencyLevel must be STRONG, EVENTUAL, or BOUNDED_STALENESS")
    private String consistencyLevel;

    // DC-10: Add freshness hint for time-series queries
    // DC-11: Reserved field - will throw if provided
    @Size(max = 256, message = "freshnessHint must not exceed 256 characters")
    private String freshnessHint;

    // DC-10: Add query type for routing
    // DC-11: Reserved field - will throw if provided
    @Size(max = 64, message = "queryType must not exceed 64 characters")
    private String queryType;

    // Legacy fields for backward compatibility
    @Deprecated
    @Size(max = 128, message = "sortField must not exceed 128 characters")
    private String sortField;

    @Deprecated
    @Pattern(regexp = "ASC|DESC", flags = Pattern.Flag.CASE_INSENSITIVE,
             message = "sortDirection must be ASC or DESC")
    private String sortDirection;

    public EntityQueryRequest() {
        // Default constructor for Jackson
    }

    public EntityQueryRequest(String filter, Integer limit, Integer offset,
            String sortField, String sortDirection) {
        this.filter = filter;
        this.limit = limit;
        this.offset = offset;
        this.sortField = sortField;
        this.sortDirection = sortDirection;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public java.util.List<SortFieldRequest> getSortFields() {
        return sortFields;
    }

    public void setSortFields(java.util.List<SortFieldRequest> sortFields) {
        this.sortFields = sortFields;
    }

    public java.util.List<String> getProjections() {
        return projections;
    }

    public void setProjections(java.util.List<String> projections) {
        // DC-11: Explicitly reject reserved projections.
        if (projections != null && !projections.isEmpty()) {
            throw new UnsupportedOperationException(
                "Projections are reserved. Field selection is currently unsupported."
            );
        }
        this.projections = projections;
    }

    public String getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setConsistencyLevel(String consistencyLevel) {
        // DC-11: Explicitly reject reserved consistencyLevel.
        if (consistencyLevel != null && !consistencyLevel.trim().isEmpty()) {
            throw new UnsupportedOperationException(
                "Consistency level hints are reserved. Use default consistency behavior."
            );
        }
        this.consistencyLevel = consistencyLevel;
    }

    public String getFreshnessHint() {
        return freshnessHint;
    }

    public void setFreshnessHint(String freshnessHint) {
        // DC-11: Explicitly reject reserved freshnessHint.
        if (freshnessHint != null && !freshnessHint.trim().isEmpty()) {
            throw new UnsupportedOperationException(
                "Freshness hints are reserved. Use default query behavior."
            );
        }
        this.freshnessHint = freshnessHint;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        // DC-11: Explicitly reject reserved queryType.
        if (queryType != null && !queryType.trim().isEmpty()) {
            throw new UnsupportedOperationException(
                "Query type routing is reserved. Use default query behavior."
            );
        }
        this.queryType = queryType;
    }

    // Legacy getters/setters for backward compatibility
    @Deprecated
    public String getSortField() {
        return sortField;
    }

    @Deprecated
    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    @Deprecated
    public String getSortDirection() {
        return sortDirection;
    }

    @Deprecated
    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    /**
     * Sort field request DTO aligned with QuerySpec.SortField.
     * DC-10: Inner class for sort field specification.
     */
    public static class SortFieldRequest {
        private String fieldName;
        private String direction;

        public SortFieldRequest() {
        }

        public SortFieldRequest(String fieldName, String direction) {
            this.fieldName = fieldName;
            this.direction = direction;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }
    }
}
