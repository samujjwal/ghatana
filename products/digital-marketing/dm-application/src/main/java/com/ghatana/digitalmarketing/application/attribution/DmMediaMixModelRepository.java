package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModelStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for media mix model persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for media mix model storage (DMOS-F5-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmMediaMixModelRepository {

    Promise<DmMediaMixModel> save(DmMediaMixModel model);

    Promise<DmMediaMixModel> update(DmMediaMixModel model);

    Promise<Optional<DmMediaMixModel>> findById(String id);

    Promise<List<DmMediaMixModel>> listByTenant(String tenantId);

    Promise<List<DmMediaMixModel>> listByTenantAndStatus(String tenantId, DmMediaMixModelStatus status);
}
