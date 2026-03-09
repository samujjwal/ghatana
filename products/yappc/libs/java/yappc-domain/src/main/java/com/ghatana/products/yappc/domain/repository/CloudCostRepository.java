package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.model.CloudCost;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for CloudCost entity operations.
 *
 * <p>Provides data access operations for cloud spending data, including
 * date-range queries, aggregations, and service-level cost breakdowns.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for CloudCost entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface CloudCostRepository extends TenantAwareRepository<CloudCost, UUID> {

    /**
     * Finds costs for a specific cloud account.
     *
     * @param workspaceId    the workspace ID
     * @param cloudAccountId the cloud account ID
     * @param offset         the zero-based offset for pagination
     * @param limit          the maximum number of results to return
     * @return promise of a paginated result of costs for the account
     */
    Promise<PageResult<CloudCost>> findByWorkspaceIdAndCloudAccountId(UUID workspaceId, UUID cloudAccountId, int offset, int limit);

    /**
     * Finds costs within a date range.
     *
     * @param workspaceId the workspace ID
     * @param startDate   the start date
     * @param endDate     the end date
     * @return promise of a list of costs in the date range
     */
    Promise<List<CloudCost>> findByWorkspaceIdAndCostDateBetween(UUID workspaceId, LocalDate startDate, LocalDate endDate);

    /**
     * Calculates total cost for a workspace within a date range.
     *
     * @param workspaceId the workspace ID
     * @param startDate   the start date
     * @param endDate     the end date
     * @return promise of the total cost amount
     */
    Promise<BigDecimal> sumCostByWorkspaceIdAndDateRange(UUID workspaceId, LocalDate startDate, LocalDate endDate);

    /**
     * Finds costs by service name.
     *
     * @param workspaceId the workspace ID
     * @param serviceName the cloud service name
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of costs for the service
     */
    Promise<PageResult<CloudCost>> findByWorkspaceIdAndServiceName(UUID workspaceId, String serviceName, int offset, int limit);
}
