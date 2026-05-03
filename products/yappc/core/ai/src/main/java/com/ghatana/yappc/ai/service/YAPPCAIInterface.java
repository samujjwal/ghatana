package com.ghatana.yappc.ai.service;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Interface for YAPPC AI service operations.
 * 
 * <p>This interface extracts the core AI operations needed by analyzers and other components,
 * allowing for test doubles and alternative implementations.</p>
 *
 * @doc.type interface
 * @doc.purpose Core AI service operations interface for YAPPC
 * @doc.layer platform
 * @doc.pattern Interface
 */
public interface YAPPCAIInterface {

    /**
     * Performs reasoning or planning task.
     *
     * @param question the question to reason about
     * @return Promise containing the reasoning response
     */
    Promise<String> reason(String question);

    /**
     * Performs reasoning with additional context.
     *
     * @param question the question to reason about
     * @param context additional context for reasoning
     * @return Promise containing the reasoning response
     */
    Promise<String> reason(String question, Map<String, Object> context);

    /**
     * Generates code based on natural language description.
     *
     * @param description the code description
     * @return Promise containing generated code
     */
    Promise<String> generateCode(String description);

    /**
     * Generates code with additional context.
     *
     * @param description the code description
     * @param context additional context for code generation
     * @return Promise containing generated code
     */
    Promise<String> generateCode(String description, Map<String, Object> context);

    /**
     * Generates unit tests for given code.
     *
     * @param code the code to generate tests for
     * @return Promise containing generated tests
     */
    Promise<String> generateTests(String code);

    /**
     * Generates unit tests with additional context.
     *
     * @param code the code to generate tests for
     * @param context additional context for test generation
     * @return Promise containing generated tests
     */
    Promise<String> generateTests(String code, Map<String, Object> context);
}
