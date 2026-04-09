package com.ghatana.datacloud.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


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

    @Size(max = 4096, message = "filters expression must not exceed 4096 characters")
    private String filters;

    @Min(value = 1, message = "limit must be at least 1")
    @Max(value = 10_000, message = "limit must not exceed 10,000")
    private Integer limit;

    @Min(value = 0, message = "offset must be non-negative")
    private Integer offset;

    @Size(max = 128, message = "sortField must not exceed 128 characters")
    private String sortField;

    @Pattern(regexp = "ASC|DESC", flags = Pattern.Flag.CASE_INSENSITIVE,
             message = "sortDirection must be ASC or DESC")
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
