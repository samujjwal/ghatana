package com.ghatana.digitalmarketing.application.narrative;

import com.ghatana.digitalmarketing.domain.narrative.DmNarrativeReview;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativeReviewStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for narrative review persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for narrative review storage (DMOS-F3-006)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmNarrativeReviewRepository {

    Promise<DmNarrativeReview> save(DmNarrativeReview review);

    Promise<DmNarrativeReview> update(DmNarrativeReview review);

    Promise<Optional<DmNarrativeReview>> findById(String id);

    Promise<List<DmNarrativeReview>> listByTenant(String tenantId);

    Promise<List<DmNarrativeReview>> listByStatus(String tenantId, DmNarrativeReviewStatus status);
}
