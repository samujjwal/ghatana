package com.ghatana.yappc.ai.requirements.ai.prompts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for prompt templates used in AI requirement generation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Central registry for prompt templates, providing template lookup, caching,
 * and variable substitution. Templates are loaded once and cached for
 * performance.
 *
 * <p>
 * <b>Template Storage</b><br>
 * Templates are currently hardcoded for simplicity. Future versions may load
 * from external configuration or database.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap for template storage.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PromptTemplateManager manager = new PromptTemplateManager();
 *
 * PromptTemplate template = manager.getTemplate("generate-requirements");
 * String rendered = template.render(Map.of(
 *     "persona", "Product Manager",
 *     "feature", "user authentication"
 * ));
 * }</pre>
 *
 * @see PromptTemplate
 * @doc.type class
 * @doc.purpose Prompt template management and caching
 * @doc.layer application
 * @doc.pattern Registry / Factory
 */
public class PromptTemplateManager {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateManager.class);

    private final Map<String, PromptTemplate> templates;

    /**
     * Creates a new PromptTemplateManager with default templates.
     */
    public PromptTemplateManager() {
        this.templates = new ConcurrentHashMap<>();
        initializeDefaultTemplates();
    }

    /**
     * Gets template by name.
     *
     * @param name template name
     * @return prompt template
     * @throws IllegalArgumentException if template not found
     */
    public PromptTemplate getTemplate(String name) {
        PromptTemplate template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException(
                    "Template not found: " + name + ". Available templates: " + templates.keySet()
            );
        }
        return template;
    }

    /**
     * Checks if template exists.
     *
     * @param name template name
     * @return true if template exists
     */
    public boolean hasTemplate(String name) {
        return templates.containsKey(name);
    }

    /**
     * Gets all template names.
     *
     * @return set of template names
     */
    public Set<String> getTemplateNames() {
        return Collections.unmodifiableSet(templates.keySet());
    }

    /**
     * Registers a new template or replaces existing one.
     *
     * @param name template name
     * @param template template to register
     */
    public void registerTemplate(String name, PromptTemplate template) {
        templates.put(name, template);
        logger.info("Registered template: {}", name);
    }

    /**
     * Initializes default templates for requirement generation.
     */
    private void initializeDefaultTemplates() {
        // Generate Requirements Template
        registerTemplate("generate-requirements", new PromptTemplate(
                """
            {{personaPrompt}}
            
            Feature Description: {{featureDescription}}
            {{contextPrompt}}
            
            Generate {{count}} {{typeDescription}} requirements for this feature.
            
            For each requirement:
            1. Write a clear, specific, and testable requirement statement
            2. Ensure it follows SMART criteria (Specific, Measurable, Achievable, Relevant, Time-bound)
            {{acceptanceCriteriaPrompt}}
            
            Return your response as a JSON array with this structure:
            [
              {
                "description": "requirement text",
                "type": "FUNCTIONAL|NON_FUNCTIONAL|CONSTRAINT",
                "priority": "high|medium|low",
                "acceptanceCriteria": ["criteria 1", "criteria 2", ...]
              }
            ]
            
            Focus on quality over quantity. Each requirement should be production-ready.
            """
        ));

        // Improve Requirement Template
        registerTemplate("improve-requirement", new PromptTemplate(
                """
            You are an expert requirements analyst. Review the following requirement and suggest improvements.
            
            Requirement: {{requirement}}
            
            Analyze the requirement for:
            1. Clarity - Is it clear and unambiguous?
            2. Completeness - Does it include all necessary information?
            3. Testability - Can it be verified?
            4. Specificity - Is it specific enough?
            5. Consistency - Does it follow standard patterns?
            
            Provide 3-5 specific, actionable improvement suggestions as a JSON array:
            [
              {
                "category": "clarity|completeness|testability|specificity|consistency",
                "issue": "description of the issue",
                "suggestion": "specific improvement suggestion",
                "priority": "high|medium|low"
              }
            ]
            """
        ));

        // Extract Acceptance Criteria Template
        registerTemplate("extract-acceptance-criteria", new PromptTemplate(
                """
            Extract or generate acceptance criteria for the following requirement in Given-When-Then format.
            
            Requirement: {{requirement}}
            
            Generate 3-5 acceptance criteria that:
            1. Cover the main scenarios
            2. Include edge cases
            3. Are testable and specific
            4. Follow Given-When-Then format
            
            Return as a JSON array of strings:
            [
              "GIVEN [context] WHEN [action] THEN [outcome]",
              "GIVEN [context] WHEN [action] THEN [outcome]",
              ...
            ]
            """
        ));

        // Classify Requirement Template
        registerTemplate("classify-requirement", new PromptTemplate(
                """
            Classify the following requirement into one of these types:
            - FUNCTIONAL: Describes what the system should do (features, behaviors)
            - NON_FUNCTIONAL: Describes quality attributes (performance, security, usability)
            - CONSTRAINT: Describes limitations or boundaries (compliance, technology constraints)
            
            Requirement: {{requirement}}
            
            Return ONLY the classification as one word: FUNCTIONAL, NON_FUNCTIONAL, or CONSTRAINT
            """
        ));

        // Validate Quality Template
        registerTemplate("validate-quality", new PromptTemplate(
                """
            Analyze the quality of the following requirement across multiple dimensions.
            
            Requirement: {{requirement}}
            
            Score each dimension from 0.0 to 1.0:
            
            1. **Clarity** (0.0-1.0): Is it clear, unambiguous, and easy to understand?
            2. **Completeness** (0.0-1.0): Does it include all necessary information?
            3. **Testability** (0.0-1.0): Can it be verified objectively?
            4. **Consistency** (0.0-1.0): Does it align with standard patterns and practices?
            
            Also identify specific issues and recommendations.
            
            Return as JSON:
            {
              "clarityScore": 0.0-1.0,
              "completenessScore": 0.0-1.0,
              "testabilityScore": 0.0-1.0,
              "consistencyScore": 0.0-1.0,
              "overallScore": 0.0-1.0,
              "issues": [
                {"category": "clarity|completeness|testability|consistency", "description": "issue description", "critical": true|false}
              ],
              "recommendations": ["recommendation 1", "recommendation 2", ...]
            }
            """
        ));

        logger.info("Initialized {} default templates", templates.size());
    }

    /**
     * Renders template with variables.
     *
     * <p>
     * Convenience method that looks up template and renders in one call.
     *
     * @param templateName template name
     * @param variables variable map
     * @return rendered template
     * @throws IllegalArgumentException if template not found or variables
     * missing
     */
    public String render(String templateName, Map<String, Object> variables) {
        return getTemplate(templateName).renderFromObjects(variables);
    }

    /**
     * Gets count of registered templates.
     *
     * @return template count
     */
    public int getTemplateCount() {
        return templates.size();
    }

    @Override
    public String toString() {
        return "PromptTemplateManager{templates=" + templates.keySet() + '}';
    }
}
