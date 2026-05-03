package com.ghatana.digitalmarketing.application.preflight;

import com.ghatana.digitalmarketing.domain.preflight.DmPreflightChecklist;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for preflight checklist persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for preflight campaign safety checklist persistence (DMOS-F2-013)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmPreflightChecklistRepository {

    Promise<DmPreflightChecklist> save(DmPreflightChecklist checklist);

    Promise<DmPreflightChecklist> update(DmPreflightChecklist checklist);

    Promise<Optional<DmPreflightChecklist>> findById(String id);

    Promise<List<DmPreflightChecklist>> listByCampaign(String tenantId, String campaignId);

    Promise<Optional<DmPreflightChecklist>> findLatestByCampaign(String tenantId, String campaignId);
}
