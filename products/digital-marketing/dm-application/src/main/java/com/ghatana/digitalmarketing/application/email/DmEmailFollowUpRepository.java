package com.ghatana.digitalmarketing.application.email;

import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUp;
import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUpStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for email follow-up execution records.
 *
 * @doc.type interface
 * @doc.purpose Port for email follow-up execution persistence (DMOS-F2-012)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmEmailFollowUpRepository {

    Promise<DmEmailFollowUp> save(DmEmailFollowUp followUp);

    Promise<DmEmailFollowUp> update(DmEmailFollowUp followUp);

    Promise<Optional<DmEmailFollowUp>> findById(String id);

    Promise<List<DmEmailFollowUp>> listByStatus(String tenantId, DmEmailFollowUpStatus status, int limit);
}
