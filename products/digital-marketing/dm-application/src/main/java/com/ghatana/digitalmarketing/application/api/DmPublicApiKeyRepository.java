package com.ghatana.digitalmarketing.application.api;

import com.ghatana.digitalmarketing.domain.api.DmPublicApiKey;
import com.ghatana.digitalmarketing.domain.api.DmPublicApiKeyStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for public API key persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for public API key storage (DMOS-F5-002)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmPublicApiKeyRepository {

    Promise<DmPublicApiKey> save(DmPublicApiKey key);

    Promise<DmPublicApiKey> update(DmPublicApiKey key);

    Promise<Optional<DmPublicApiKey>> findById(String id);

    Promise<List<DmPublicApiKey>> listByTenant(String tenantId);

    Promise<List<DmPublicApiKey>> listByTenantAndStatus(String tenantId, DmPublicApiKeyStatus status);
}
