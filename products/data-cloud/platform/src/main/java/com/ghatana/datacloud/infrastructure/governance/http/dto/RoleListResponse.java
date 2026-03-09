package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

/**
 * HTTP response DTO for list of roles with pagination.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates HTTP response data for role listing with pagination metadata.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoleListResponse response = RoleListResponse.builder()
 *     .addRole(roleResponse1)
 *     .addRole(roleResponse2)
 *     .totalCount(100)
 *     .pageNumber(0)
 *     .pageSize(20)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP response DTO for paginated role list
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class RoleListResponse {
    private final List<RoleResponse> roles;
    private final long totalCount;
    private final int pageNumber;
    private final int pageSize;
    private final long totalPages;

    private RoleListResponse(
            List<RoleResponse> roles,
            long totalCount,
            int pageNumber,
            int pageSize) {
        this.roles = new ArrayList<>(roles != null ? roles : List.of());
        this.totalCount = totalCount;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalPages = pageSize > 0 ? (totalCount + pageSize - 1) / pageSize : 0;

        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must be >= 0");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
    }

    /**
     * Gets list of role responses.
     */
    public List<RoleResponse> getRoles() {
        return new ArrayList<>(roles);
    }

    /**
     * Gets total number of roles matching query.
     */
    public long getTotalCount() {
        return totalCount;
    }

    /**
     * Gets current page number (0-indexed).
     */
    public int getPageNumber() {
        return pageNumber;
    }

    /**
     * Gets page size.
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Gets total number of pages.
     */
    public long getTotalPages() {
        return totalPages;
    }

    /**
     * Gets whether this is the last page.
     */
    public boolean isLastPage() {
        return pageNumber >= totalPages - 1;
    }

    /**
     * Gets whether there are more results.
     */
    public boolean hasMore() {
        return !isLastPage();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<RoleResponse> roles = new ArrayList<>();
        private long totalCount = 0;
        private int pageNumber = 0;
        private int pageSize = 20;

        public Builder addRole(RoleResponse role) {
            this.roles.add(Objects.requireNonNull(role));
            return this;
        }

        public Builder roles(List<RoleResponse> roles) {
            this.roles = new ArrayList<>(roles);
            return this;
        }

        public Builder totalCount(long totalCount) {
            this.totalCount = totalCount;
            return this;
        }

        public Builder pageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
            return this;
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public RoleListResponse build() {
            return new RoleListResponse(roles, totalCount, pageNumber, pageSize);
        }
    }

    @Override
    public String toString() {
        return "RoleListResponse{" +
                "count=" + roles.size() +
                ", totalCount=" + totalCount +
                ", page=" + pageNumber +
                ", pageSize=" + pageSize +
                ", totalPages=" + totalPages +
                '}';
    }
}
