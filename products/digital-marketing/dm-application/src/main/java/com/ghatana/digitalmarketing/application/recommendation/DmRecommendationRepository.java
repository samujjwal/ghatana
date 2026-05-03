package com.ghatana.digitalmarketing.application.recommendation;

import com.ghatana.digitalmarketing.domain.recommendation.DmAgentRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmRecommendationStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link DmAgentRecommendation}.
 *
 * @doc.type class
 * @doc.purpose Repository contract for agent recommendation storage (DMOS-F2-005)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmRecommendationRepository {

    Promise<DmAgentRecommendation> save(DmAgentRecommendation recommendation);

    Promise<Optional<DmAgentRecommendation>> findById(String id);

    Promise<List<DmAgentRecommendation>> findByStatus(String tenantId, DmRecommendationStatus status, int limit);

    Promise<DmAgentRecommendation> update(DmAgentRecommendation recommendation);

    Promise<Long> countByStatus(String tenantId, DmRecommendationStatus status);
}
