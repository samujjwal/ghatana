package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.enums.CloudProvider;
import com.ghatana.products.yappc.domain.model.CloudResource;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CloudResource entity operations.
 *
 * <p>Provides data access operations for cloud infrastructure resources,
 * including type-based filtering, risk scoring queries, and sync tracking.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for CloudResource entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface CloudResourceRepository extends TenantAwareRepository<CloudResource, UUID> {

    /**
     * Finds resources by cloud account.
     *
     * @param workspaceId    the workspace ID
     * @param cloudAccountId the cloud account ID
     * @param offset         the zero-based offset for pagination
     * @param limit          the maximum number of results to return
     * @return promise of a paginated result of resources for the account
     */
    Promise<PageResult<CloudResource>> findByWorkspaceIdAndCloudAccountId(UUID workspaceId, UUID cloudAccountId, int offset, int limit);

    /**
     * Finds a resource by its cloud provider identifier.
     *
     * @param workspaceId the workspace ID
     * @param identifier  the cloud provider's resource identifier
     * @return promise of the resource if found
     */
    Promise<Optional<CloudResource>> findByWorkspaceIdAndIdentifier(UUID workspaceId, String identifier);

    /**
     * Finds resources by type.
     *
     * @param workspaceId  the workspace ID
     * @param resourceType the resource type (e.g., ec2:instance)
     * @param offset       the zero-based offset for pagination
     * @param limit        the maximum number of results to return
     * @return promise of a paginated result of resources of the specified type
     */
    Promise<PageResult<CloudResource>> findByWorkspaceIdAndResourceType(UUID workspaceId, String resourceType, int offset, int limit);

    /**
     * Finds publicly accessible resources.
     *
     * @param workspaceId the workspace ID
     * @return promise of a list of public resources
     */
    Promise<List<CloudResource>> findPublicResources(UUID workspaceId);

    /**
     * Finds resources with high risk scores.
     *
     * @param workspaceId   the workspace ID
     * @param minRiskScore  the minimum risk score threshold
     * @param offset        the zero-based offset for pagination
     * @param limit         the maximum number of results to return
     * @return promise of a paginated result of high-risk resources
     */
    Promise<PageResult<CloudResource>> findByWorkspaceIdAndRiskScoreGreaterThan(UUID workspaceId, int minRiskScore, int offset, int limit);

    /**
     * Counts resources by provider.
     *
     * @param workspaceId the workspace ID
     * @param provider    the cloud provider
     * @return promise of the count of resources for the provider
     */
    Promise<Long> countByWorkspaceIdAndProvider(UUID workspaceId, CloudProvider provider);
}
