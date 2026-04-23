package com.ghatana.tutorputor.contentgeneration;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Interface for LLM providers.
 * Stub for compilation — implementation pending.
  * @doc.type interface
 * @doc.purpose Provides llm provider functionality.
 * @doc.layer product
 * @doc.pattern Interface
*/
public interface LlmProvider {
    String getModelName();
    Promise<String> generate(String prompt, Map<String, Object> params);
}
