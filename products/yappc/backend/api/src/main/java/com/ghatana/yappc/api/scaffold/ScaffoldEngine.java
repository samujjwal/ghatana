/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.scaffold;

import io.activej.promise.Promise;
import java.util.Map;

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
