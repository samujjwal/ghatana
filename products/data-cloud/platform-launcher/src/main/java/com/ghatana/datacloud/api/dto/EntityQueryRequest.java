package com.ghatana.datacloud.api.dto;

import java.util.Objects;

/**
 * Request DTO for executing entity queries.
 *
 * <p>
 * This is a minimal DTO aligned with QueryController usage. It captures
 * a filter expression, pagination (limit/offset), and simple sorting.
 
 *
 * @doc.type class
 * @doc.purpose Entity query request
 * @doc.layer platform
 * @doc.pattern DataTransfer
*/
public class EntityQueryRequest {

    private String filters;
    private Integer limit;
    private Integer offset;
    private String sortField;
    private String sortDirection;

    public EntityQueryRequest() {
        // Default constructor for Jackson
    }

    public EntityQueryRequest(String filters, Integer limit, Integer offset,
            String sortField, String sortDirection) {
        this.filters = filters;
        this.limit = limit;
        this.offset = offset;
        this.sortField = sortField;
        this.sortDirection = sortDirection;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
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

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }
}
