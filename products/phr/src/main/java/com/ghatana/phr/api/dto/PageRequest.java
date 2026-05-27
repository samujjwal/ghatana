package com.ghatana.phr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Standard pagination request DTO for PHR API endpoints.
 *
 * <p>Provides consistent pagination parameters across all list endpoints.
 * Default page size is 50, maximum is 200.
 *
 * @doc.type class
 * @doc.purpose Standard pagination request DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public class PageRequest {

    @Min(value = 1, message = "page must be at least 1")
    private int page = 1;

    @Min(value = 1, message = "pageSize must be at least 1")
    @Max(value = 200, message = "pageSize must not exceed 200")
    private int pageSize = 50;

    private String sortBy;
    private String sortOrder = "asc";

    @JsonProperty("page")
    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    @JsonProperty("pageSize")
    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    @JsonProperty("sortBy")
    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    @JsonProperty("sortOrder")
    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * Returns the offset for database queries.
     */
    public int getOffset() {
        return (page - 1) * pageSize;
    }

    /**
     * Validates the sort order.
     *
     * @throws IllegalArgumentException if sort order is invalid
     */
    public void validateSortOrder() {
        if (sortOrder != null && !sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("sortOrder must be 'asc' or 'desc'");
        }
    }
}
