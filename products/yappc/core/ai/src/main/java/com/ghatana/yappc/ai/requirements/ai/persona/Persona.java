package com.ghatana.yappc.ai.requirements.ai.persona;

/**
 * Enumeration of personas for requirement generation and analysis.
 *
 * <p>
 * <b>Purpose:</b> Represents different stakeholder perspectives in requirements
 * engineering. Each persona generates requirements from their unique viewpoint,
 * enabling comprehensive requirement coverage and detecting missing
 * perspectives.
 *
 * <p>
 * <b>Personas Explained:</b>
 * <ul>
 * <li><b>PRODUCT_MANAGER:</b> Business value, user needs, market fit, roadmap
 * alignment</li>
 * <li><b>DEVELOPER:</b> Implementation feasibility, technical constraints,
 * maintainability</li>
 * <li><b>ARCHITECT:</b> System design, scalability, integration, performance
 * implications</li>
 * <li><b>QA:</b> Testing strategy, edge cases, quality assurance, acceptance
 * criteria</li>
 * <li><b>UX_DESIGNER:</b> User experience, accessibility, usability, visual
 * design</li>
 * </ul>
 *
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 *   // Generate requirements from product manager perspective
 *   Persona persona = Persona.PRODUCT_MANAGER;
 *   String prompt = PersonaPromptBuilder.buildPrompt(
 *       "Add OAuth2 authentication",
 *       persona
 *   );
 *
 *   // Iterate through all personas for comprehensive coverage
 *   for (Persona p : Persona.values()) {
 *       generateRequirements(featureName, p);
 *   }
 * }</pre>
 *
 * <p>
 * <b>Thread Safety:</b> Enums are inherently thread-safe and immutable.
 *
 * @see PersonaPromptBuilder
 * @see RequirementGenerationPrompt
 * @doc.type enum
 * @doc.purpose Stakeholder personas for multi-perspective requirement
 * generation
 * @doc.layer product
 * @doc.pattern Enum Value Object
 * @since 1.0.0
 */
public enum Persona {
    /**
     * Product Manager perspective: Business value, user needs, market
     * opportunities.
     *
     * <p>
     * Focus Areas:
     * <ul>
     * <li>User problems and pain points</li>
     * <li>Business value and ROI</li>
     * <li>Market opportunities</li>
     * <li>Competitive advantage</li>
     * <li>Roadmap alignment</li>
     * </ul>
     */
    PRODUCT_MANAGER("Product Manager", "business-focused requirements"),
    /**
     * Software Developer perspective: Implementation, technical feasibility,
     * code quality.
     *
     * <p>
     * Focus Areas:
     * <ul>
     * <li>Implementation approach</li>
     * <li>Technology choices</li>
     * <li>Code maintainability</li>
     * <li>Technical debt implications</li>
     * <li>Existing codebase compatibility</li>
     * </ul>
     */
    DEVELOPER("Developer", "implementation and technical requirements"),
    /**
     * Systems Architect perspective: System design, scalability, integration,
     * performance.
     *
     * <p>
     * Focus Areas:
     * <ul>
     * <li>System design and architecture</li>
     * <li>Scalability and performance</li>
     * <li>Integration points</li>
     * <li>Operational requirements</li>
     * <li>Deployment considerations</li>
     * </ul>
     */
    ARCHITECT("Architect", "system design and scalability requirements"),
    /**
     * QA Engineer perspective: Testing, quality assurance, edge cases,
     * acceptance criteria.
     *
     * <p>
     * Focus Areas:
     * <ul>
     * <li>Test cases and scenarios</li>
     * <li>Edge cases and failure modes</li>
     * <li>Acceptance criteria</li>
     * <li>Performance testing</li>
     * <li>Security testing</li>
     * </ul>
     */
    QA("QA Engineer", "quality assurance and testing requirements"),
    /**
     * UX Designer perspective: User experience, accessibility, usability,
     * interface design.
     *
     * <p>
     * Focus Areas:
     * <ul>
     * <li>User experience and usability</li>
     * <li>Accessibility compliance (WCAG)</li>
     * <li>Visual design requirements</li>
     * <li>Interaction design</li>
     * <li>Responsive design</li>
     * </ul>
     */
    UX_DESIGNER("UX Designer", "user experience and usability requirements");

    /**
     * Default persona for general requirements analysis.
     */
    public static final Persona DEFAULT = PRODUCT_MANAGER;

    private final String displayName;
    private final String description;

    Persona(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Get human-readable display name for this persona.
     *
     * @return display name (e.g., "Product Manager")
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Get brief description of this persona's focus areas.
     *
     * @return description of what this persona cares about
     */
    public String description() {
        return description;
    }

    /**
     * Get system prompt for this persona to guide LLM behavior.
     *
     * @return system prompt text
     */
    public String getSystemPrompt() {
        return switch (this) {
            case PRODUCT_MANAGER ->
                "You are an experienced Product Manager. Focus on business value, user needs, "
                + "market opportunities, and ROI. Generate requirements that align with product strategy.";
            case DEVELOPER ->
                "You are an experienced Software Developer. Focus on implementation feasibility, "
                + "technical constraints, code maintainability, and existing codebase compatibility.";
            case ARCHITECT ->
                "You are a Systems Architect. Focus on system design, scalability, integration points, "
                + "performance, and operational requirements.";
            case QA ->
                "You are a QA Engineer. Focus on testing strategy, edge cases, acceptance criteria, "
                + "quality assurance, and validation scenarios.";
            case UX_DESIGNER ->
                "You are a UX Designer. Focus on user experience, accessibility, usability, "
                + "interface design, and responsive design principles.";
        };
    }

    /**
     * Check if this is a technical persona (Developer or Architect).
     *
     * @return true if this is a technical perspective
     */
    public boolean isTechnical() {
        return this == DEVELOPER || this == ARCHITECT;
    }

    /**
     * Check if this is a design-focused persona (UX or Product).
     *
     * @return true if this is design-oriented
     */
    public boolean isDesignFocused() {
        return this == UX_DESIGNER || this == PRODUCT_MANAGER;
    }

    /**
     * Get a short code for this persona (for identifiers, logging).
     *
     * @return uppercase code (e.g., "PM" for PRODUCT_MANAGER)
     */
    public String code() {
        return switch (this) {
            case PRODUCT_MANAGER ->
                "PM";
            case DEVELOPER ->
                "DEV";
            case ARCHITECT ->
                "ARCH";
            case QA ->
                "QA";
            case UX_DESIGNER ->
                "UX";
        };
    }
}
