package com.ghatana.pipeline.registry.publisher;

import com.ghatana.pipeline.registry.model.Pattern;
import com.ghatana.pipeline.registry.model.Pipeline;
import io.activej.promise.Promise;

/**
 * Publisher interface for emitting registry events to EventCloud.
 *
 * <p>
 * <b>Purpose</b><br>
 * Abstraction for publishing pattern and pipeline registration events to
 * EventCloud for consumption by other components (learning, runtime, UI).
 *
 * <p>
 * <b>Events Emitted</b><br>
 * - `pattern.registered` - When a pattern is registered with compilation
 * results - `pattern.activated` - When a pattern is activated for execution -
 * `pattern.deactivated` - When a pattern is deactivated - `pipeline.registered`
 * - When a pipeline is registered - `pipeline.activated` - When a pipeline is
 * deployed - `pipeline.deactivated` - When a pipeline is undeployed
 *
 * <p>
 * <b>Implementation</b><br>
 * Events contain metadata (tenant, version, timestamps) and are appended to
 * EventCloud for durable storage and multi-subscriber fan-out.
 *
 * @doc.type interface
 * @doc.purpose Registry event publisher abstraction
 * @doc.layer product
 * @doc.pattern Publisher
 */
public interface RegistryEventPublisher {

    /**
     * Publish pattern.registered event when pattern is registered.
     *
     * @param pattern the registered pattern
     * @param userId the user registering
     * @return Promise that completes when event is published
     */
    Promise<Void> publishPatternRegistered(Pattern pattern, String userId);

    /**
     * Publish pattern.activated event.
     *
     * @param pattern the activated pattern
     * @param userId the user activating
     * @return Promise that completes when event is published
     */
    Promise<Void> publishPatternActivated(Pattern pattern, String userId);

    /**
     * Publish pattern.deactivated event.
     *
     * @param pattern the deactivated pattern
     * @param userId the user deactivating
     * @return Promise that completes when event is published
     */
    Promise<Void> publishPatternDeactivated(Pattern pattern, String userId);

    /**
     * Publish pipeline.registered event when pipeline is registered.
     *
     * @param pipeline the registered pipeline
     * @param userId the user registering
     * @return Promise that completes when event is published
     */
    Promise<Void> publishPipelineRegistered(Pipeline pipeline, String userId);

    /**
     * Publish pipeline.activated event.
     *
     * @param pipeline the activated pipeline
     * @param userId the user activating
     * @return Promise that completes when event is published
     */
    Promise<Void> publishPipelineActivated(Pipeline pipeline, String userId);

    /**
     * Publish pipeline.deactivated event.
     *
     * @param pipeline the deactivated pipeline
     * @param userId the user deactivating
     * @return Promise that completes when event is published
     */
    Promise<Void> publishPipelineDeactivated(Pipeline pipeline, String userId);
}
