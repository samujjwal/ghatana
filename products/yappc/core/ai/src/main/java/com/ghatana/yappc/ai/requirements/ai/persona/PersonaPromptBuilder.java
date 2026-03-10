package com.ghatana.yappc.ai.requirements.ai.persona;

import com.ghatana.yappc.ai.requirements.ai.prompts.PromptTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builder for persona-specific requirement generation prompts.
 *
 * <p><b>Purpose:</b> Constructs LLM prompts tailored to each persona's perspective.
 * Each persona gets a unique system prompt and variable substitution to generate
 * requirements from their unique viewpoint.
 *
 * <p><b>Thread Safety:</b> Stateless utility class. All methods are static and
 * thread-safe. Returns immutable PromptTemplate instances.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   // Generate prompt for Product Manager
 *   PromptTemplate pmTemplate = PersonaPromptBuilder.buildPrompt(
 *       "Add two-factor authentication",
 *       Persona.PRODUCT_MANAGER
 *   );
 *
 *   // The template is ready to render with variables
 *   String renderedPrompt = pmTemplate.render(Map.of(
 *       "FEATURE", "Add two-factor authentication"
 *   ));
 *
 *   // Generate requirements from all personas
 *   List<String> allRequirements = new ArrayList<>();
 *   for (Persona persona : Persona.values()) {
 *       PromptTemplate template = PersonaPromptBuilder.buildPrompt(feature, persona);
 *       // Use with LLMService.complete()
 *   }
 * }</pre>
 *
 * @see Persona
 * @see PromptTemplate
 * @doc.type class
 * @doc.purpose Factory for persona-specific prompt templates
 * @doc.layer product
 * @doc.pattern Factory
 * @since 1.0.0
 */
public final class PersonaPromptBuilder {
  private PersonaPromptBuilder() {
    // Utility class
  }

  /**
   * Build a prompt template for the given persona and feature.
   *
   * @param feature the feature description (non-null, non-empty)
   * @param persona the persona to generate requirements for (non-null)
   * @return persona-specific prompt template ready for variable substitution
   * @throws IllegalArgumentException if feature is empty
   * @throws NullPointerException if persona is null
   */
  public static PromptTemplate buildPrompt(String feature, Persona persona) {
    Objects.requireNonNull(feature, "feature cannot be null");
    if (feature.trim().isEmpty()) {
      throw new IllegalArgumentException("feature cannot be empty");
    }
    Objects.requireNonNull(persona, "persona cannot be null");

    String systemPrompt = buildSystemPrompt(persona);
    String userPrompt = buildUserPrompt(feature, persona);

    // Combine system and user prompts into a single template
    String combinedTemplate = systemPrompt + "\n\n" + userPrompt;
    return new PromptTemplate(combinedTemplate);
  }

  /**
   * Build system-level prompt that defines the persona's role and constraints.
   *
   * @param persona the persona to build prompt for
   * @return system prompt with persona instructions
   */
  private static String buildSystemPrompt(Persona persona) {
    return switch (persona) {
      case PRODUCT_MANAGER -> buildProductManagerSystem();
      case DEVELOPER -> buildDeveloperSystem();
      case ARCHITECT -> buildArchitectSystem();
      case QA -> buildQASystem();
      case UX_DESIGNER -> buildUXDesignerSystem();
    };
  }

  /**
   * Build user-level prompt with the feature context.
   *
   * @param feature the feature description
   * @param persona the persona for context
   * @return user prompt with persona-specific focus
   */
  private static String buildUserPrompt(String feature, Persona persona) {
    return switch (persona) {
      case PRODUCT_MANAGER -> buildProductManagerUser(feature);
      case DEVELOPER -> buildDeveloperUser(feature);
      case ARCHITECT -> buildArchitectUser(feature);
      case QA -> buildQAUser(feature);
      case UX_DESIGNER -> buildUXDesignerUser(feature);
    };
  }

  // ============================================================================
  // PRODUCT MANAGER PROMPTS
  // ============================================================================

  private static String buildProductManagerSystem() {
    return """
        You are a Product Manager evaluating feature requirements.
        
        Focus on:
        - User problems and pain points being solved
        - Business value and return on investment
        - Market opportunities and competitive advantages
        - Alignment with product roadmap and strategy
        - Priority and sequencing implications
        
        Generate requirements that emphasize business value, user benefits,
        and strategic alignment. Use business-friendly language.
        """;
  }

  private static String buildProductManagerUser(String feature) {
    return String.format(
        """
        As a Product Manager, generate 3-5 high-level requirements for:
        "%s"
        
        Requirements should focus on:
        1. User problems being solved
        2. Business value and metrics
        3. Market fit and competitive advantage
        4. Acceptance criteria from business perspective
        
        Return as JSON with fields: requirement_text, business_value, priority (1-5)
        """,
        feature);
  }

  // ============================================================================
  // DEVELOPER PROMPTS
  // ============================================================================

  private static String buildDeveloperSystem() {
    return """
        You are a Software Developer evaluating feature requirements.
        
        Focus on:
        - Implementation feasibility and approach
        - Technology choices and frameworks
        - Code quality and maintainability
        - Integration with existing codebase
        - Performance and optimization concerns
        
        Generate requirements that emphasize technical correctness,
        code quality, and implementation patterns.
        """;
  }

  private static String buildDeveloperUser(String feature) {
    return String.format(
        """
        As a Developer, generate 3-5 implementation requirements for:
        "%s"
        
        Requirements should cover:
        1. Implementation approach and patterns
        2. API design and interfaces
        3. Integration points with existing code
        4. Performance considerations
        5. Code quality and testing requirements
        
        Return as JSON with fields: requirement_text, implementation_notes, complexity (low/medium/high)
        """,
        feature);
  }

  // ============================================================================
  // ARCHITECT PROMPTS
  // ============================================================================

  private static String buildArchitectSystem() {
    return """
        You are a Systems Architect evaluating feature requirements.
        
        Focus on:
        - System design and architecture patterns
        - Scalability and performance implications
        - Integration and data flow
        - Operational requirements
        - Deployment and infrastructure considerations
        
        Generate requirements emphasizing system-level concerns,
        scalability, and architectural patterns.
        """;
  }

  private static String buildArchitectUser(String feature) {
    return String.format(
        """
        As a Systems Architect, generate 3-5 architectural requirements for:
        "%s"
        
        Requirements should address:
        1. System design and patterns
        2. Scalability and performance requirements
        3. Integration architecture
        4. Data flow and storage considerations
        5. Deployment and operational requirements
        
        Return as JSON with fields: requirement_text, architectural_concern, scale_impact (low/medium/high)
        """,
        feature);
  }

  // ============================================================================
  // QA PROMPTS
  // ============================================================================

  private static String buildQASystem() {
    return """
        You are a QA Engineer evaluating feature requirements.
        
        Focus on:
        - Testing strategy and test cases
        - Edge cases and failure modes
        - Acceptance criteria definition
        - Performance testing requirements
        - Security and compliance testing
        
        Generate requirements emphasizing quality assurance,
        testability, and edge case coverage.
        """;
  }

  private static String buildQAUser(String feature) {
    return String.format(
        """
        As a QA Engineer, generate 3-5 quality assurance requirements for:
        "%s"
        
        Requirements should include:
        1. Test cases and scenarios
        2. Edge cases and failure modes
        3. Acceptance criteria (GWEN format)
        4. Performance and load testing needs
        5. Security testing requirements
        
        Return as JSON with fields: requirement_text, test_scenario, risk_level (low/medium/high)
        """,
        feature);
  }

  // ============================================================================
  // UX DESIGNER PROMPTS
  // ============================================================================

  private static String buildUXDesignerSystem() {
    return """
        You are a UX Designer evaluating feature requirements.
        
        Focus on:
        - User experience and usability
        - Accessibility and inclusive design
        - Visual design and interaction patterns
        - Responsive design considerations
        - User journey and flow
        
        Generate requirements emphasizing user experience,
        accessibility, and intuitive interaction design.
        """;
  }

  private static String buildUXDesignerUser(String feature) {
    return String.format(
        """
        As a UX Designer, generate 3-5 user experience requirements for:
        "%s"
        
        Requirements should focus on:
        1. User experience and usability
        2. Accessibility compliance (WCAG)
        3. Visual design and interaction patterns
        4. Responsive design and mobile considerations
        5. User feedback and validation
        
        Return as JSON with fields: requirement_text, ux_concern, accessibility_impact (yes/no)
        """,
        feature);
  }

  /**
   * Get all system prompts as a map for reference/analysis.
   *
   * @return map of persona to system prompt
   */
  public static Map<Persona, String> allSystemPrompts() {
    Map<Persona, String> prompts = new HashMap<>();
    for (Persona p : Persona.values()) {
      prompts.put(p, buildSystemPrompt(p));
    }
    return prompts;
  }
}