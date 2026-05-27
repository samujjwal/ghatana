package com.ghatana.phr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Standard paginated response DTO for PHR API endpoints.
 *
 * <p>Provides consistent pagination metadata across all list endpoints.
 *
 * @doc.type class
 * @doc.purpose Standard paginated response DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public class PageResponse<T> {

    private List<T> items;
    private int total;
    private int page;
    private int pageSize;
    private int totalPages;

    public PageResponse(List<T> items, int total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }

    @JsonProperty("items")
    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    @JsonProperty("total")
    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

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

    @JsonProperty("totalPages")
    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    @JsonProperty("hasNext")
    public boolean isHasNext() {
        return page < totalPages;
    }

    @JsonProperty("hasPrevious")
    public boolean isHasPrevious() {
        return page > 1;
    }
}
