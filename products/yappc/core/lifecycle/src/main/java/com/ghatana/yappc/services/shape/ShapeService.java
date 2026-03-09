package com.ghatana.yappc.services.shape;

import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Transforms intent into structured design artifacts
 * @doc.layer service
 * @doc.pattern Service
 */
public interface ShapeService {
    /**
     * Derives system shape from intent using AI-assisted design.
     * 
     * @param intent The validated intent specification
     * @return Promise of ShapeSpec
     */
    Promise<ShapeSpec> derive(IntentSpec intent);
    
    /**
     * Generates detailed system model (domain, workflows, integrations).
     * 
     * @param spec The shape specification
     * @return Promise of SystemModel
     */
    Promise<SystemModel> generateModel(ShapeSpec spec);

    /**
     * Returns the total number of persisted shape entities.
     *
     * @return Promise resolving to the entity count
     */
    default Promise<Long> count() {
        return Promise.of(0L);
    }
}
