package com.ghatana.tutorputor.contentstudio.prompt;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Production-ready prompt templates for TutorPutor content generation.
 * 
 * <p>These prompts are carefully designed to generate high-quality educational
 * content that is:
 * <ul>
 *   <li>Pedagogically sound (follows Bloom's taxonomy)</li>
 *   <li>Age-appropriate (matched to grade level)</li>
 *   <li>Factually accurate (with built-in self-checking)</li>
 *   <li>Structured for easy parsing</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Prompt templates for educational content generation
 * @doc.layer product
 * @doc.pattern Template
 */
public final class PromptTemplates {

    private PromptTemplates() {
        // Utility class
    }

    // =========================================================================
    // Claims Generation
    // =========================================================================

    public static String buildClaimsPrompt(String topic, String domain, String gradeLevel, int maxClaims) {
      String gradeLevelDescription = getGradeLevelDescription(gradeLevel);
      String domainDescription = getDomainDescription(domain);
        
        return String.format("""
            You are an expert educational content designer creating learning claims for a %s curriculum.
            
            ## Task
            Generate %d distinct, testable learning claims for the topic: "%s"
            
            ## Requirements
            1. Each claim must be a single, verifiable statement of what a learner will know or be able to do
            2. Claims must progress through Bloom's Taxonomy levels (Remember → Understand → Apply → Analyze → Evaluate → Create)
            3. Language must be appropriate for %s students
            4. Claims must be specific to %s domain
            5. Each claim must be independently assessable
            
            ## Output Format (JSON)
            ```json
            {
              "claims": [
                {
                  "claim_ref": "C1",
                  "text": "The learner can [specific observable action]",
                  "bloom_level": "UNDERSTAND",
                  "rationale": "Brief explanation of why this claim matters"
                }
              ]
            }
            ```
            
            ## Bloom's Taxonomy Levels
            - REMEMBER: Recall facts and basic concepts
            - UNDERSTAND: Explain ideas or concepts
            - APPLY: Use information in new situations
            - ANALYZE: Draw connections among ideas
            - EVALUATE: Justify a decision or course of action
            - CREATE: Produce new or original work
            
            Generate exactly %d claims, ensuring coverage across multiple Bloom's levels.
            """,
            domainDescription,
            maxClaims,
            topic,
            gradeLevelDescription,
            domainDescription,
            maxClaims
        );
    }

    // =========================================================================
    // Content Needs Analysis
    // =========================================================================

    public static String buildContentNeedsPrompt(String claimText, String bloomLevel,
                            String gradeLevel, String domain) {
        return String.format("""
            You are an instructional design expert analyzing what types of supporting content a learning claim requires.
            
            ## Learning Claim
            "%s"
            
            ## Claim Characteristics
            - Bloom's Level: %s
            - Grade Level: %s
            - Domain: %s
            
            ## Task
            Analyze what types of supporting content would best help learners master this claim.
            
            ## Content Types to Consider
            
            ### Examples
            - REAL_WORLD: Concrete, relatable scenarios from everyday life
            - PROBLEM_SOLVING: Worked examples with step-by-step solutions
            - ANALOGY: Comparisons to familiar concepts
            - CASE_STUDY: In-depth exploration of a specific instance
            
            ### Simulations
            - PARAMETER_EXPLORATION: Interactive adjustment of variables
            - PREDICTION: Guess-then-verify experiments
            - CONSTRUCTION: Building/assembling virtual models
            
            ### Animations
            - TWO_D: 2D visual explanations
            - THREE_D: 3D spatial visualizations
            - TIMELINE: Sequential process illustrations
            
            ## Output Format (JSON)
            ```json
            {
              "examples": {
                "required": true,
                "types": ["REAL_WORLD", "PROBLEM_SOLVING"],
                "count": 2,
                "necessity": 0.9,
                "rationale": "Why examples are needed"
              },
              "simulation": {
                "required": true,
                "interaction_type": "PARAMETER_EXPLORATION",
                "complexity": "MEDIUM",
                "necessity": 0.8,
                "rationale": "Why simulation is needed"
              },
              "animation": {
                "required": false,
                "animation_type": "TWO_D",
                "duration_seconds": 30,
                "necessity": 0.3,
                "rationale": "Why animation is/isn't needed"
              }
            }
            ```
            
            Consider:
            - Higher Bloom's levels (APPLY+) typically need more examples and simulations
            - Abstract concepts benefit from animations
            - Younger students need more concrete examples
            - Science/Engineering domains often benefit from simulations
            """,
            claimText,
            normalizeEnumLikeValue(bloomLevel),
            getGradeLevelDescription(gradeLevel),
            getDomainDescription(domain)
        );
    }

    // =========================================================================
    // Examples Generation
    // =========================================================================

    public static String buildExamplesPrompt(String topic, String domain, String gradeLevel,
                         String exampleType, int count) {
      String typesList = List.of(exampleType).stream()
        .map(PromptTemplates::getExampleTypeDescription)
            .collect(Collectors.joining(", "));
        
        return String.format("""
            You are an expert educational content creator generating examples for a learning claim.
            
            ## Learning Claim
            Reference: %s
            Claim: "%s"
            
            ## Target Audience
            - Grade Level: %s
            - Domain: %s
            
            ## Requirements
            Generate %d examples of the following types: %s
            
            Each example must:
            1. Directly support understanding the claim
            2. Be age-appropriate and culturally sensitive
            3. Include clear learning points
            4. Connect to real-world applications where possible
            5. Be factually accurate and verifiable
            
            ## Output Format (JSON)
            ```json
            {
              "examples": [
                {
                  "example_id": "EX1",
                  "type": "REAL_WORLD",
                  "title": "Descriptive title",
                  "description": "Brief overview",
                  "problem_statement": "Optional: Problem to solve",
                  "solution_content": "The main example content with explanation",
                  "key_learning_points": ["Point 1", "Point 2"],
                  "real_world_connection": "How this applies to everyday life"
                }
              ]
            }
            ```
            
            Ensure examples are diverse, engaging, and pedagogically effective.
            """,
            "C1",
            topic,
            getGradeLevelDescription(gradeLevel),
            getDomainDescription(domain),
            count,
            typesList
        );
    }

    // =========================================================================
    // Simulation Generation
    // =========================================================================

    public static String buildSimulationPrompt(String topic, String domain, String gradeLevel) {
        return String.format("""
            You are an expert simulation designer creating an interactive physics/science simulation.
            
            ## Learning Claim
            Reference: %s
            Claim: "%s"
            
            ## Simulation Requirements
            - Grade Level: %s
            - Domain: %s
            - Interaction Type: %s
            - Complexity: %s
            
            ## Available Entity Types
            - BALL: Spherical object with mass and velocity
            - BOX: Rectangular rigid body
            - PLATFORM: Static horizontal surface
            - RAMP: Inclined surface
            - SPRING: Elastic connector
            - PENDULUM: Hanging oscillating mass
            - PULLEY: Rotating wheel for ropes
            - WALL: Static vertical barrier
            - LEVER: Rotatable bar with fulcrum
            - WHEEL: Rotating circular object
            
            ## Task
            Design a simulation manifest that helps learners explore and understand the claim.
            
            ## Output Format (JSON)
            ```json
            {
              "manifest": {
                "name": "Simulation title",
                "description": "What the simulation demonstrates",
                "entities": [
                  {
                    "entity_id": "ball1",
                    "type": "BALL",
                    "properties": {
                      "x": "100",
                      "y": "50",
                      "mass": "1.0",
                      "radius": "20",
                      "color": "#3B82F6"
                    }
                  }
                ],
                "config": {
                  "physics_settings": {
                    "gravity": "9.8",
                    "friction": "0.1"
                  },
                  "visual_settings": {
                    "grid": "true",
                    "vectors": "true"
                  }
                },
                "goals": [
                  {
                    "goal_id": "G1",
                    "description": "What the learner should achieve",
                    "success_criteria": {
                      "condition": "ball reaches target"
                    }
                  }
                ],
                "controllable_params": [
                  {
                    "param_id": "mass",
                    "name": "Ball Mass",
                    "type": "slider",
                    "min_value": 0.1,
                    "max_value": 10.0,
                    "default_value": 1.0,
                    "unit": "kg"
                  }
                ]
              }
            }
            ```
            
            Design an engaging, educational simulation appropriate for the grade level.
            """,
            "C1",
            topic,
            getGradeLevelDescription(gradeLevel),
            getDomainDescription(domain),
            "PARAMETER_EXPLORATION",
            "MEDIUM"
        );
    }

    // =========================================================================
    // Animation Generation
    // =========================================================================

    public static String buildAnimationPrompt(String topic, String domain, String gradeLevel) {
        return String.format("""
            You are an expert educational animator creating keyframe-based animations.
            
            ## Learning Claim
            Reference: %s
            Claim: "%s"
            
            ## Animation Requirements
            - Type: %s
            - Duration: %d seconds
            
            ## Task
            Create a detailed animation specification with keyframes that visually explains the claim.
            
            ## Output Format (JSON)
            ```json
            {
              "animation": {
                "title": "Animation title",
                "description": "What the animation shows",
                "type": "%s",
                "duration_seconds": %d,
                "keyframes": [
                  {
                    "time_ms": 0,
                    "description": "Initial state",
                    "properties": {
                      "element1_x": "100",
                      "element1_y": "100",
                      "element1_opacity": "1.0",
                      "text": "Step 1 narration"
                    }
                  },
                  {
                    "time_ms": 2000,
                    "description": "Transition state",
                    "properties": {
                      "element1_x": "200",
                      "element1_y": "150",
                      "element1_opacity": "0.8",
                      "text": "Step 2 narration"
                    }
                  }
                ],
                "config": {
                  "background_color": "#FFFFFF",
                  "easing": "easeInOut"
                }
              }
            }
            ```
            
            Create smooth, educational keyframe transitions that clearly illustrate the concept.
            Include at least 5 keyframes for a good animation flow.
            """,
            "C1",
            topic,
            "2D Animation",
            30,
            "TWO_D",
            30
        );
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static String getGradeLevelDescription(String level) {
      String normalized = normalizeEnumLikeValue(level);
      return switch (normalized) {
        case "K_2", "K-2" -> "Kindergarten to 2nd grade (ages 5-8)";
        case "GRADE_3_5", "3-5", "GRADE_3-5" -> "3rd to 5th grade (ages 8-11)";
        case "GRADE_6_8", "6-8", "GRADE_6-8" -> "6th to 8th grade (ages 11-14)";
        case "GRADE_9_12", "9-12", "GRADE_9-12" -> "9th to 12th grade (ages 14-18)";
        case "UNDERGRADUATE" -> "Undergraduate (ages 18-22)";
        case "GRADUATE" -> "Graduate level (ages 22+)";
        default -> level == null || level.isBlank() ? "General audience" : level;
      };
    }

    private static String getDomainDescription(String domain) {
      String normalized = normalizeEnumLikeValue(domain);
      return switch (normalized) {
        case "MATH", "MATHEMATICS" -> "Mathematics";
        case "SCIENCE" -> "Science (Physics, Chemistry, Biology)";
        case "TECH", "TECHNOLOGY", "COMPUTER_SCIENCE" -> "Technology & Computer Science";
        case "ENGINEERING" -> "Engineering";
        case "ARTS", "HUMANITIES" -> "Arts & Humanities";
        case "LANGUAGE", "LANGUAGE_ARTS" -> "Language Arts & Literature";
        default -> domain == null || domain.isBlank() ? "General Education" : domain;
      };
    }

    private static String getExampleTypeDescription(String type) {
      String normalized = normalizeEnumLikeValue(type);
      return switch (normalized) {
        case "REAL_WORLD" -> "Real-world application";
        case "PROBLEM_SOLVING" -> "Problem-solving exercise";
        case "ANALOGY" -> "Analogy/comparison";
        case "CASE_STUDY" -> "Case study";
        default -> type == null || type.isBlank() ? "General example" : type;
      };
    }

    private static String normalizeEnumLikeValue(String value) {
      if (value == null) {
        return "";
      }
      return value.trim().toUpperCase();
    }
}
