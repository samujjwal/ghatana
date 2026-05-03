package com.ghatana.digitalmarketing.application.recommendation;

import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendationStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for engine recommendation persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for engine recommendation storage (DMOS-F3-001)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmEngineRecommendationRepository {

    Promise<DmEngineRecommendation> save(DmEngineRecommendation recommendation);

    Promise<DmEngineRecommendation> update(DmEngineRecommendation recommendation);

    Promise<Optional<DmEngineRecommendation>> findById(String id);

    Promise<List<DmEngineRecommendation>> listByTenant(String tenantId);

    Promise<List<DmEngineRecommendation>> listByStatus(String tenantId, DmEngineRecommendationStatus status);
}
