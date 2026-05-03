package com.ghatana.digitalmarketing.application.lead;

import com.ghatana.digitalmarketing.domain.lead.DmLeadCapture;
import com.ghatana.digitalmarketing.domain.lead.DmLeadStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for DMOS F2 lead captures.
 *
 * @doc.type class
 * @doc.purpose Stores lead capture forms and CRM-lite status transitions (DMOS-F2-011)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmLeadCaptureRepository {

    Promise<DmLeadCapture> save(DmLeadCapture leadCapture);

    Promise<DmLeadCapture> update(DmLeadCapture leadCapture);

    Promise<Optional<DmLeadCapture>> findById(String leadCaptureId);

    Promise<Optional<DmLeadCapture>> findByEmailAndLandingPage(String tenantId, String landingPageId, String email);

    Promise<List<DmLeadCapture>> listByStatus(String tenantId, DmLeadStatus status, int limit);
}
