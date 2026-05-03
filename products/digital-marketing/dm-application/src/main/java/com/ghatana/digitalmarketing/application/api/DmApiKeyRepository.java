package com.ghatana.digitalmarketing.application.api;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.api.DmApiKey;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository port for {@link DmApiKey} (DMOS-P1-016).
 *
 * @doc.type interface
 * @doc.purpose Repository for API key storage with hashing (DMOS-P1-016)
 * @doc.layer application
 * @doc.pattern Repository
 */
public interface DmApiKeyRepository {

    Promise<DmApiKey> save(DmApiKey apiKey);

    Promise<Optional<DmApiKey>> findById(String id);

    Promise<Optional<DmApiKey>> findByKeyPrefix(String keyPrefix, DmTenantId tenantId, DmWorkspaceId workspaceId);

    Promise<DmApiKey> update(DmApiKey apiKey);

    Promise<Void> delete(String id);
}
