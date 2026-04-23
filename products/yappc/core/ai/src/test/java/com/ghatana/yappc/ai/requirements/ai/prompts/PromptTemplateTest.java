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
    void testRenderSimpleTemplate() { // GH-90000
        PromptTemplate template = new PromptTemplate("Generate requirements for {{featureName}}");

        String result = template.render(Map.of( // GH-90000
                "featureName", "User Login"
        ));

        assertThat(result).isEqualTo("Generate requirements for User Login");
    }

    @Test
    @DisplayName("Should render template with multiple variables")
    void testRenderMultipleVariables() { // GH-90000
        PromptTemplate template = new PromptTemplate( // GH-90000
                "Generate {{count}} {{type}} requirements for {{featureName}} in context of {{context}}"
        );

        String result = template.render(Map.of( // GH-90000
                "count", "5",
                "type", "functional",
                "featureName", "User Authentication",
                "context", "Multi-tenant SaaS"
        ));

        assertThat(result).isEqualTo( // GH-90000
                "Generate 5 functional requirements for User Authentication in context of Multi-tenant SaaS"
        );
    }

    @Test
    @DisplayName("Should throw on undefined variable")
    void testThrowOnUndefinedVariable() { // GH-90000
        PromptTemplate template = new PromptTemplate("Generate requirements for {{featureName}}");

        assertThatThrownBy(() -> template.render(Map.of())) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessage("Undefined variable: featureName");
    }

    @Test
    @DisplayName("Should extract all variable names")
    void testGetVariableNames() { // GH-90000
        PromptTemplate template = new PromptTemplate( // GH-90000
                "Generate {{count}} {{type}} for {{feature}} in {{context}}"
        );

        Set<String> variables = template.getVariableNames(); // GH-90000

        assertThat(variables).containsExactlyInAnyOrder("count", "type", "feature", "context"); // GH-90000
    }

    @Test
    @DisplayName("Should handle duplicate variables in template")
    void testDuplicateVariables() { // GH-90000
        PromptTemplate template = new PromptTemplate( // GH-90000
                "{{feature}} is {{feature}} and {{feature}}"
        );

        Set<String> variables = template.getVariableNames(); // GH-90000
        assertThat(variables).containsExactly("feature");

        String result = template.render(Map.of("feature", "important")); // GH-90000
        assertThat(result).isEqualTo("important is important and important");
    }

    @Test
    @DisplayName("Should validate variable requirements")
    void testValidateVariables() { // GH-90000
        PromptTemplate template = new PromptTemplate("Generate for {{feature}} in {{context}}");

        assertThat(template.isValid(Map.of("feature", "auth", "context", "SaaS"))).isTrue(); // GH-90000
        assertThat(template.isValid(Map.of("feature", "auth"))).isFalse(); // GH-90000
        assertThat(template.isValid(Map.of())).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should handle special characters in values")
    void testSpecialCharactersInValues() { // GH-90000
        PromptTemplate template = new PromptTemplate("Requirement: {{description}}");

        String result = template.render(Map.of( // GH-90000
                "description", "Must support $100+ and regex [a-z]+ patterns"
        ));

        assertThat(result).contains("$100+", "[a-z]+"); // GH-90000
    }

    @Test
    @DisplayName("Should render from Object map")
    void testRenderFromObjects() { // GH-90000
        PromptTemplate template = new PromptTemplate("Count: {{count}}, Value: {{value}}");

        Map<String, Object> variables = Map.of( // GH-90000
                "count", 42,
                "value", 3.14
        );

        String result = template.renderFromObjects(variables); // GH-90000
        assertThat(result).isEqualTo("Count: 42, Value: 3.14");
    }

    @Test
    @DisplayName("Should throw on null template")
    void testNullTemplate() { // GH-90000
        assertThatThrownBy(() -> new PromptTemplate(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Should throw on empty template")
    void testEmptyTemplate() { // GH-90000
        assertThatThrownBy(() -> new PromptTemplate(""))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Should report variable count")
    void testGetVariableCount() { // GH-90000
        PromptTemplate template = new PromptTemplate("{{a}} {{b}} {{c}}");
        assertThat(template.getVariableCount()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("Should retrieve raw template")
    void testGetTemplate() { // GH-90000
        String rawTemplate = "Generate {{count}} requirements for {{feature}}";
        PromptTemplate template = new PromptTemplate(rawTemplate); // GH-90000

        assertThat(template.getTemplate()).isEqualTo(rawTemplate); // GH-90000
    }

    @Test
    @DisplayName("Should handle multi-line templates")
    void testMultilineTemplate() { // GH-90000
        PromptTemplate template = new PromptTemplate( // GH-90000
                "Feature: {{featureName}}\n" +
                "Type: {{type}}\n" +
                "Context: {{context}}"
        );

        String result = template.render(Map.of( // GH-90000
                "featureName", "Authentication",
                "type", "Security",
                "context", "OAuth 2.0"
        ));

        assertThat(result).contains("Feature: Authentication", "Type: Security", "Context: OAuth 2.0"); // GH-90000
    }
}
