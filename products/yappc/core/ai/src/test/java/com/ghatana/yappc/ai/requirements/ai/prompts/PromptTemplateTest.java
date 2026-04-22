package com.ghatana.yappc.ai.requirements.ai.prompts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PromptTemplate variable substitution engine.
 */
@DisplayName("PromptTemplate Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles prompt template test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PromptTemplateTest {

    @Test
    @DisplayName("Should render simple template with single variable [GH-90000]")
    void testRenderSimpleTemplate() { // GH-90000
        PromptTemplate template = new PromptTemplate("Generate requirements for {{featureName}} [GH-90000]");

        String result = template.render(Map.of( // GH-90000
                "featureName", "User Login"
        ));

        assertThat(result).isEqualTo("Generate requirements for User Login [GH-90000]");
    }

    @Test
    @DisplayName("Should render template with multiple variables [GH-90000]")
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
    @DisplayName("Should throw on undefined variable [GH-90000]")
    void testThrowOnUndefinedVariable() { // GH-90000
        PromptTemplate template = new PromptTemplate("Generate requirements for {{featureName}} [GH-90000]");

        assertThatThrownBy(() -> template.render(Map.of())) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessage("Undefined variable: featureName [GH-90000]");
    }

    @Test
    @DisplayName("Should extract all variable names [GH-90000]")
    void testGetVariableNames() { // GH-90000
        PromptTemplate template = new PromptTemplate( // GH-90000
                "Generate {{count}} {{type}} for {{feature}} in {{context}}"
        );

        Set<String> variables = template.getVariableNames(); // GH-90000

        assertThat(variables).containsExactlyInAnyOrder("count", "type", "feature", "context"); // GH-90000
    }

    @Test
    @DisplayName("Should handle duplicate variables in template [GH-90000]")
    void testDuplicateVariables() { // GH-90000
        PromptTemplate template = new PromptTemplate( // GH-90000
                "{{feature}} is {{feature}} and {{feature}}"
        );

        Set<String> variables = template.getVariableNames(); // GH-90000
        assertThat(variables).containsExactly("feature [GH-90000]");

        String result = template.render(Map.of("feature", "important")); // GH-90000
        assertThat(result).isEqualTo("important is important and important [GH-90000]");
    }

    @Test
    @DisplayName("Should validate variable requirements [GH-90000]")
    void testValidateVariables() { // GH-90000
        PromptTemplate template = new PromptTemplate("Generate for {{feature}} in {{context}} [GH-90000]");

        assertThat(template.isValid(Map.of("feature", "auth", "context", "SaaS"))).isTrue(); // GH-90000
        assertThat(template.isValid(Map.of("feature", "auth"))).isFalse(); // GH-90000
        assertThat(template.isValid(Map.of())).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should handle special characters in values [GH-90000]")
    void testSpecialCharactersInValues() { // GH-90000
        PromptTemplate template = new PromptTemplate("Requirement: {{description}} [GH-90000]");

        String result = template.render(Map.of( // GH-90000
                "description", "Must support $100+ and regex [a-z]+ patterns"
        ));

        assertThat(result).contains("$100+", "[a-z]+"); // GH-90000
    }

    @Test
    @DisplayName("Should render from Object map [GH-90000]")
    void testRenderFromObjects() { // GH-90000
        PromptTemplate template = new PromptTemplate("Count: {{count}}, Value: {{value}} [GH-90000]");

        Map<String, Object> variables = Map.of( // GH-90000
                "count", 42,
                "value", 3.14
        );

        String result = template.renderFromObjects(variables); // GH-90000
        assertThat(result).isEqualTo("Count: 42, Value: 3.14 [GH-90000]");
    }

    @Test
    @DisplayName("Should throw on null template [GH-90000]")
    void testNullTemplate() { // GH-90000
        assertThatThrownBy(() -> new PromptTemplate(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Should throw on empty template [GH-90000]")
    void testEmptyTemplate() { // GH-90000
        assertThatThrownBy(() -> new PromptTemplate(" [GH-90000]"))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Should report variable count [GH-90000]")
    void testGetVariableCount() { // GH-90000
        PromptTemplate template = new PromptTemplate("{{a}} {{b}} {{c}} [GH-90000]");
        assertThat(template.getVariableCount()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("Should retrieve raw template [GH-90000]")
    void testGetTemplate() { // GH-90000
        String rawTemplate = "Generate {{count}} requirements for {{feature}}";
        PromptTemplate template = new PromptTemplate(rawTemplate); // GH-90000

        assertThat(template.getTemplate()).isEqualTo(rawTemplate); // GH-90000
    }

    @Test
    @DisplayName("Should handle multi-line templates [GH-90000]")
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
