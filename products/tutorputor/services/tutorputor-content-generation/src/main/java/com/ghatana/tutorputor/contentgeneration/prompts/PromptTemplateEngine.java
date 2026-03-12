package com.ghatana.tutorputor.contentgeneration.prompts;

import java.util.Map;
import java.util.Objects;

/**
 * Unified prompt template engine combining capabilities from ai-agents and ai-service.
 *
 * @doc.type class
 * @doc.purpose Unified prompt template engine for content generation
 * @doc.layer domain
 * @doc.pattern Strategy
 */
public class PromptTemplateEngine {
    
    public PromptTemplateEngine() {}
    
    /**
     * Builds a prompt for generating educational claims.
     */
    public String buildClaimsPrompt(String topic, String gradeLevel, String domain, int maxClaims) {
        return String.format("""
            Generate %d educational claims about "%s" for %s grade level in %s domain.
            
            Requirements:
            - Claims should be factual and age-appropriate
            - Include diverse Bloom's taxonomy levels (Remember, Understand, Apply, Analyze, Evaluate, Create)
            - Each claim should be a complete sentence
            - Format as JSON array: [{"text": "claim text", "bloomLevel": "ANALYZE"}, ...]
            
            Topic: %s
            Grade Level: %s
            Domain: %s
            """, maxClaims, topic, gradeLevel, domain, topic, gradeLevel, domain);
    }
    
    /**
     * Builds a prompt for generating worked examples.
     */
    public String buildExamplePrompt(String claimText, String gradeLevel, String domain) {
        return String.format("""
            Create a worked example that demonstrates the following claim:
            
            Claim: "%s"
            
            Requirements:
            - Provide step-by-step explanation
            - Include concrete numbers or scenarios
            - Make it age-appropriate for %s
            - Use %s domain context
            - Format as structured JSON with steps
            
            Claim: %s
            Grade Level: %s
            Domain: %s
            """, claimText, gradeLevel, domain, claimText, gradeLevel, domain);
    }
    
    /**
     * Builds a prompt for generating simulation manifests.
     */
    public String buildSimulationPrompt(String claimText, String gradeLevel, String domain) {
        return String.format("""
            Design an interactive simulation to demonstrate this claim:
            
            Claim: "%s"
            
            Requirements:
            - Define simulation entities and their properties
            - Specify user interactions (sliders, buttons, inputs)
            - Include parameters students can manipulate
            - Make it appropriate for %s in %s
            - Format as JSON with entities, interactions, and parameters
            
            Claim: %s
            Grade Level: %s
            Domain: %s
            """, claimText, gradeLevel, domain, claimText, gradeLevel, domain);
    }
    
    /**
     * Builds a prompt for generating animation specifications.
     */
    public String buildAnimationPrompt(String claimText, String gradeLevel, String domain) {
        return String.format("""
            Create an animation specification to illustrate this claim:
            
            Claim: "%s"
            
            Requirements:
            - Define keyframes with timing and descriptions
            - Include visual elements and transitions
            - Specify annotations and highlights
            - Make it educational for %s in %s
            - Format as JSON with keyframes array
            
            Claim: %s
            Grade Level: %s
            Domain: %s
            """, claimText, gradeLevel, domain, claimText, gradeLevel, domain);
    }
    
    /**
     * Builds a prompt for generating assessment items.
     */
    public String buildAssessmentPrompt(String claimText, String gradeLevel, String domain) {
        return String.format("""
            Create assessment items to evaluate understanding of this claim:
            
            Claim: "%s"
            
            Requirements:
            - Include multiple choice questions with 4 options
            - Provide correct answer and explanation
            - Cover different cognitive levels
            - Make it appropriate for %s in %s
            - Format as JSON with questions, options, and correct answers
            
            Claim: %s
            Grade Level: %s
            Domain: %s
            """, claimText, gradeLevel, domain, claimText, gradeLevel, domain);
    }
    
    /**
     * Builds a custom prompt with template variables.
     */
    public String buildPrompt(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", 
                                  Objects.toString(entry.getValue(), ""));
        }
        return result;
    }
}
