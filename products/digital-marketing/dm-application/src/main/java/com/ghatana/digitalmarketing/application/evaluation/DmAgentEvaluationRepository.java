package com.ghatana.digitalmarketing.application.evaluation;

import com.ghatana.digitalmarketing.domain.evaluation.DmAgentEvaluation;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for agent evaluation persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for agent evaluation storage (DMOS-F3-005)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmAgentEvaluationRepository {

    Promise<DmAgentEvaluation> save(DmAgentEvaluation evaluation);

    Promise<Optional<DmAgentEvaluation>> findById(String id);

    Promise<List<DmAgentEvaluation>> listByAgent(String tenantId, String agentId);

    Promise<List<DmAgentEvaluation>> listByTenant(String tenantId);
}
