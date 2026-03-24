package com.ghatana.yappc.services.evolve;

import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.intent.ConstraintSpec;
import com.ghatana.yappc.domain.learn.Insights;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Proposes system improvements based on insights
 * @doc.layer service
 * @doc.pattern Service
 */
public interface EvolutionService {
    /**
     * Proposes evolution plan from insights.
     * 
     * @param insights The insights from learning phase
     * @return Promise of evolution plan
     */
    Promise<EvolutionPlan> propose(Insights insights);
    
    /**
     * Proposes evolution with business/technical constraints.
     * 
     * @param insights The insights
     * @param constraints Constraints to honor
     * @return Promise of constrained evolution plan
     */
    Promise<EvolutionPlan> proposeWithConstraints(Insights insights, ConstraintSpec constraints);
}
