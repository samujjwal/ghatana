package com.ghatana.digitalmarketing.application.killswitch;

import com.ghatana.digitalmarketing.domain.killswitch.DmKillSwitch;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for kill switch persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for kill switch emergency pause entity persistence (DMOS-F2-015)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmKillSwitchRepository {

    Promise<DmKillSwitch> save(DmKillSwitch killSwitch);

    Promise<DmKillSwitch> update(DmKillSwitch killSwitch);

    Promise<Optional<DmKillSwitch>> findById(String id);

    Promise<List<DmKillSwitch>> listActive(String tenantId);

    Promise<Optional<DmKillSwitch>> findActiveByScope(String tenantId, String scope, String scopeId);
}
