package com.ghatana.yappc.services.learn;

import com.ghatana.yappc.domain.learn.HistoricalContext;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Transforms observations into actionable insights
 * @doc.layer service
 * @doc.pattern Service
 */
public interface LearningService {
    /**
     * Analyzes observations to extract insights.
     * 
     * @param observation The observation data
     * @return Promise of insights
     */
    Promise<Insights> analyze(Observation observation);
    
    /**
     * Analyzes with historical context for deeper insights.
     * 
     * @param observation Current observation
     * @param context Historical context
     * @return Promise of insights
     */
    Promise<Insights> analyzeWithContext(Observation observation, HistoricalContext context);
}
