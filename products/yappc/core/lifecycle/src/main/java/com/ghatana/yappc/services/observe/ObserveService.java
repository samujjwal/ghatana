package com.ghatana.yappc.services.observe;

import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.RunResult;
import io.activej.promise.Promise;
import java.util.function.Consumer;

/**
 * @doc.type interface
 * @doc.purpose Collects runtime observations and telemetry
 * @doc.layer service
 * @doc.pattern Service
 */
public interface ObserveService {
    /**
     * Collects observations from a run.
     * 
     * @param run The run result to observe
     * @return Promise of observation data
     */
    Promise<Observation> collect(RunResult run);
    
    /**
     * Streams observations in real-time.
     * 
     * @param run The run result
     * @param consumer Observation consumer
     * @return Promise when streaming completes
     */
    Promise<Void> streamObservations(RunResult run, Consumer<Observation> consumer);
}
