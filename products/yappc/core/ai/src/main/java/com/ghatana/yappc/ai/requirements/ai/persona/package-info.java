/**
 * User persona management for personalized requirement generation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides user persona modeling and prompt customization for context-aware
 * requirement generation. Enables AI to generate requirements tailored to
 * specific user roles, expertise levels, and preferences.
 *
 * <p>
 * <b>Key Components</b><br>
 * <ul>
 * <li>{@link com.ghatana.requirements.ai.persona.Persona} - User persona value
 * object</li>
 * <li>{@link com.ghatana.requirements.ai.persona.PersonaPromptBuilder} -
 * Persona-aware prompt generation</li>
 * </ul>
 *
 * <p>
 * <b>Supported Personas</b><br>
 * <ul>
 * <li><b>Product Manager</b>: Business-focused, high-level requirements</li>
 * <li><b>Software Engineer</b>: Technical, implementation-focused
 * requirements</li>
 * <li><b>QA Engineer</b>: Test-focused, quality-oriented requirements</li>
 * <li><b>Business Analyst</b>: Process-focused, workflow requirements</li>
 * <li><b>UX Designer</b>: User experience, interface requirements</li>
 * <li><b>DevOps Engineer</b>: Infrastructure, deployment requirements</li>
 * <li><b>Security Engineer</b>: Security, compliance requirements</li>
 * </ul>
 *
 * <p>
 * <b>Basic Usage</b><br>
 * <pre>{@code
 * // Create persona
 * Persona persona = Persona.builder()
 *     .role("Product Manager")
 *     .expertiseLevel("intermediate")
 *     .preferences(Map.of(
 *         "format", "user-story",
 *         "detail-level", "high-level",
 *         "include-acceptance-criteria", "true"
 *     ))
 *     .build();
 *
 * // Build persona-aware prompt
 * PersonaPromptBuilder builder = new PersonaPromptBuilder(persona);
 * String prompt = builder.buildRequirementPrompt(
 *     "user authentication",
 *     RequirementType.FUNCTIONAL,
 *     5
 * );
 *
 * // Generate requirements with LLM
 * LLMRequest request = LLMRequest.builder()
 *     .prompt(prompt)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br> {@code Persona} is immutable and thread-safe.
 * {@code PersonaPromptBuilder} is stateless and safe for concurrent use.
 *
 * @since 1.0.0
 * @see com.ghatana.requirements.ai.persona.Persona
 * @see com.ghatana.requirements.ai.persona.PersonaPromptBuilder
 * @doc.type package
 * @doc.purpose User persona management for personalized requirement generation
 * @doc.layer product
 * @doc.pattern Value Object
 */
package com.ghatana.yappc.ai.requirements.ai.persona;
