package com.ghatana.digitalmarketing.application.rollback;

import com.ghatana.digitalmarketing.domain.rollback.DmRollbackAction;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for rollback action persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for rollback compensating action persistence (DMOS-F2-014)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmRollbackActionRepository {

    Promise<DmRollbackAction> save(DmRollbackAction action);

    Promise<DmRollbackAction> update(DmRollbackAction action);

    Promise<Optional<DmRollbackAction>> findById(String id);

    Promise<List<DmRollbackAction>> listByCommand(String tenantId, String commandId);

    Promise<List<DmRollbackAction>> listByStatus(String tenantId, DmRollbackStatus status, int limit);
}
