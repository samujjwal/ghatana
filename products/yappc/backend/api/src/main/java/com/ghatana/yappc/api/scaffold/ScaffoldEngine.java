package com.ghatana.yappc.api.scaffold;

import java.util.Map;
import io.activej.promise.Promise;

/**
 * ScaffoldEngine.
 *
 * @doc.type interface
 * @doc.purpose scaffold engine
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ScaffoldEngine {
    GenerationResult generate(GenerationContext context);
    Promise<Boolean> applyFeaturePack(GenerationContext context);
    Promise<Boolean> resolveConflicts(String jobId, Map<String, String> resolutions);
}
