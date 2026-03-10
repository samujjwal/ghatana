package com.ghatana.yappc.ai.requirements.ai.prompts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PromptTemplate variable substitution engine.
 */
@DisplayName("PromptTemplate Tests")
/**
 * @doc.type class
 * @doc.purpose Handles prompt template test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PromptTemplateTest {

    @Test
    @DisplayName("Should render simple template with single variable")
    void testRenderSimpleTemplate() {
        PromptTemplate template = new PromptTemplate("Generate requirements for {{featureName}}");
        
        String result = template.render(Map.of(
                "featureName", "User Login"
        ));
        
        assertThat(result).isEqualTo("Generate requirements for User Login");
    }

    @Test
    @DisplayName("Should render template with multiple variables")
    void testRenderMultipleVariables() {
        PromptTemplate template = new PromptTemplate(
                "Generate {{count}} {{type}} requirements for {{featureName}} in context of {{context}}"
        );
        
        String result = template.render(Map.of(
                "count", "5",
                "type", "functional",
                "featureName", "User Authentication",
                "context", "Multi-tenant SaaS"
        ));
        
        assertThat(result).isEqualTo(
                "Generate 5 functional requirements for User Authentication in context of Multi-tenant SaaS"
        );
    }

    @Test
    @DisplayName("Should throw on undefined variable")
    void testThrowOnUndefinedVariable() {
        PromptTemplate template = new PromptTemplate("Generate requirements for {{featureName}}");
        
        assertThatThrownBy(() -> template.render(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Undefined variable: featureName");
    }

    @Test
    @DisplayName("Should extract all variable names")
    void testGetVariableNames() {
        PromptTemplate template = new PromptTemplate(
                "Generate {{count}} {{type}} for {{feature}} in {{context}}"
        );
        
        Set<String> variables = template.getVariableNames();
        
        assertThat(variables).containsExactlyInAnyOrder("count", "type", "feature", "context");
    }

    @Test
    @DisplayName("Should handle duplicate variables in template")
    void testDuplicateVariables() {
        PromptTemplate template = new PromptTemplate(
                "{{feature}} is {{feature}} and {{feature}}"
        );
        
        Set<String> variables = template.getVariableNames();
        assertThat(variables).containsExactly("feature");
        
        String result = template.render(Map.of("feature", "important"));
        assertThat(result).isEqualTo("important is important and important");
    }

    @Test
    @DisplayName("Should validate variable requirements")
    void testValidateVariables() {
        PromptTemplate template = new PromptTemplate("Generate for {{feature}} in {{context}}");
        
        assertThat(template.isValid(Map.of("feature", "auth", "context", "SaaS"))).isTrue();
        assertThat(template.isValid(Map.of("feature", "auth"))).isFalse();
        assertThat(template.isValid(Map.of())).isFalse();
    }

    @Test
    @DisplayName("Should handle special characters in values")
    void testSpecialCharactersInValues() {
        PromptTemplate template = new PromptTemplate("Requirement: {{description}}");
        
        String result = template.render(Map.of(
                "description", "Must support $100+ and regex [a-z]+ patterns"
        ));
        
        assertThat(result).contains("$100+", "[a-z]+");
    }

    @Test
    @DisplayName("Should render from Object map")
    void testRenderFromObjects() {
        PromptTemplate template = new PromptTemplate("Count: {{count}}, Value: {{value}}");
        
        Map<String, Object> variables = Map.of(
                "count", 42,
                "value", 3.14
        );
        
        String result = template.renderFromObjects(variables);
        assertThat(result).isEqualTo("Count: 42, Value: 3.14");
    }

    @Test
    @DisplayName("Should throw on null template")
    void testNullTemplate() {
        assertThatThrownBy(() -> new PromptTemplate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw on empty template")
    void testEmptyTemplate() {
        assertThatThrownBy(() -> new PromptTemplate(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should report variable count")
    void testGetVariableCount() {
        PromptTemplate template = new PromptTemplate("{{a}} {{b}} {{c}}");
        assertThat(template.getVariableCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should retrieve raw template")
    void testGetTemplate() {
        String rawTemplate = "Generate {{count}} requirements for {{feature}}";
        PromptTemplate template = new PromptTemplate(rawTemplate);
        
        assertThat(template.getTemplate()).isEqualTo(rawTemplate);
    }

    @Test
    @DisplayName("Should handle multi-line templates")
    void testMultilineTemplate() {
        PromptTemplate template = new PromptTemplate(
                "Feature: {{featureName}}\n" +
                "Type: {{type}}\n" +
                "Context: {{context}}"
        );
        
        String result = template.render(Map.of(
                "featureName", "Authentication",
                "type", "Security",
                "context", "OAuth 2.0"
        ));
        
        assertThat(result).contains("Feature: Authentication", "Type: Security", "Context: OAuth 2.0");
    }
}