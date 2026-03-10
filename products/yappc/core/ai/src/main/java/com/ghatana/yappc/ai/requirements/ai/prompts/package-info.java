/**
 * Prompt templates and generation for LLM interactions.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides reusable, parameterized prompt templates for consistent LLM
 * interactions. Ensures high-quality prompt engineering best practices are
 * applied consistently across requirement generation use cases.
 *
 * <p>
 * <b>Key Components</b><br>
 * <ul>
 * <li>{@link com.ghatana.requirements.ai.prompts.PromptTemplate} -
 * Parameterized prompt template</li>
 * <li>{@link com.ghatana.requirements.ai.prompts.RequirementGenerationPrompt} -
 * Requirements-specific prompts</li>
 * </ul>
 *
 * <p>
 * <b>Prompt Engineering Principles</b><br>
 * All templates follow best practices:
 * <ul>
 * <li>Clear instructions and context</li>
 * <li>Explicit output format specification</li>
 * <li>Few-shot examples when appropriate</li>
 * <li>Constraint specification (length, style, etc.)</li>
 * <li>Role definition (persona-based prompting)</li>
 * </ul>
 *
 * <p>
 * <b>Basic Usage</b><br>
 * <pre>{@code
 * // Use predefined template
 * PromptTemplate template = RequirementGenerationPrompt.FUNCTIONAL_REQUIREMENTS;
 * String prompt = template.format(Map.of(
 *     "context", "User authentication system",
 *     "count", "5",
 *     "format", "user-story"
 * ));
 *
 * // Create custom template
 * PromptTemplate custom = PromptTemplate.builder()
 *     .name("security-requirements")
 *     .template("""
 *         Generate {{count}} security requirements for: {{context}}
 *
 *         Focus on:
 *         - Authentication and authorization
 *         - Data encryption
 *         - Secure communication
 *         - Vulnerability protection
 *
 *         Format: {{format}}
 *         """)
 *     .requiredParameters("context", "count", "format")
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br> {@code PromptTemplate} is immutable and thread-safe.
 * Template formatting is stateless and safe for concurrent use.
 *
 * @since 1.0.0
 * @see com.ghatana.requirements.ai.prompts.PromptTemplate
 * @see com.ghatana.requirements.ai.prompts.RequirementGenerationPrompt
 * @doc.type package
 * @doc.purpose Prompt templates and generation for LLM interactions
 * @doc.layer product
 * @doc.pattern Template Method
 */
package com.ghatana.yappc.ai.requirements.ai.prompts;
