package com.ghatana.tutorputor.contentgeneration;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Interface for LLM providers.
 * Stub for compilation — implementation pending.
 */
public interface LlmProvider {
    String getModelName();
    Promise<String> generate(String prompt, Map<String, Object> params);
}
