package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.enums.CloudProvider;
import com.ghatana.products.yappc.domain.model.CloudAccount;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CloudAccount entity operations.
 *
 * <p>Provides data access operations for connected cloud provider accounts,
 * including provider-based filtering and connection status queries.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for CloudAccount entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface CloudAccountRepository extends TenantAwareRepository<CloudAccount, UUID> {

    /**
     * Finds cloud accounts by workspace and provider.
     *
     * @param workspaceId the workspace ID
     * @param provider    the cloud provider
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of matching cloud accounts
     */
    Promise<PageResult<CloudAccount>> findByWorkspaceIdAndProvider(UUID workspaceId, CloudProvider provider, int offset, int limit);

    /**
     * Finds a cloud account by workspace and provider's account ID.
     *
     * @param workspaceId the workspace ID
     * @param accountId   the cloud provider's account identifier
     * @return promise of the cloud account if found
     */
    Promise<Optional<CloudAccount>> findByWorkspaceIdAndAccountId(UUID workspaceId, String accountId);

    /**
     * Finds all active (enabled) cloud accounts for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of active cloud accounts
     */
    Promise<PageResult<CloudAccount>> findActiveAccounts(UUID workspaceId, int offset, int limit);

    /**
     * Finds cloud accounts in a specific region.
     *
     * @param workspaceId the workspace ID
     * @param region      the cloud region
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of cloud accounts in the region
     */
    Promise<PageResult<CloudAccount>> findByWorkspaceIdAndRegion(UUID workspaceId, String region, int offset, int limit);

    /**
     * Finds all active cloud accounts for a workspace (unpaginated).
     *
     * @param workspaceId the workspace ID
     * @return promise of a list of all active cloud accounts
     */
    Promise<List<CloudAccount>> findAllActiveAccounts(UUID workspaceId);

    /**
     * Checks if a cloud account exists by workspace and provider's account ID.
     *
     * @param workspaceId the workspace ID
     * @param accountId   the cloud provider's account identifier
     * @return promise of true if the account exists
     */
    Promise<Boolean> existsByWorkspaceIdAndAccountId(UUID workspaceId, String accountId);

    /**
     * Counts active cloud accounts by provider.
     *
     * @param workspaceId the workspace ID
     * @param provider    the cloud provider
     * @return promise of the count of active accounts for the provider
     */
    Promise<Long> countActiveAccountsByProvider(UUID workspaceId, CloudProvider provider);
}
