package com.ghatana.ai.prompts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PromptTemplateManager}.
 *
 * Covers template registration, variable extraction, rendering,
 * default merging, validation, and lifecycle operations.
 */
@DisplayName("PromptTemplateManager")
class PromptTemplateManagerTest {

    private PromptTemplateManager manager;

    @BeforeEach
    void setUp() {
        manager = new PromptTemplateManager();
    }

    // ---- registerTemplate ----

    @Nested
    @DisplayName("registerTemplate")
    class RegisterTemplate {

        @Test
        @DisplayName("registers a valid template successfully")
        void validTemplate() {
            manager.registerTemplate("greeting", "Hello, {{name}}!");

            assertThat(manager.listTemplates()).contains("greeting");
        }

        @Test
        @DisplayName("overwrites existing template with same name")
        void overwriteExisting() {
            manager.registerTemplate("tmpl", "version 1: {{a}}");
            manager.registerTemplate("tmpl", "version 2: {{b}}");

            assertThat(manager.getTemplate("tmpl")).isEqualTo("version 2: {{b}}");
            assertThat(manager.getRequiredVariables("tmpl")).containsExactly("b");
        }

        @Test
        @DisplayName("throws on null name")
        void nullName() {
            assertThatThrownBy(() -> manager.registerTemplate(null, "template"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("throws on empty name")
        void emptyName() {
            assertThatThrownBy(() -> manager.registerTemplate("   ", "template"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on null template")
        void nullTemplate() {
            assertThatThrownBy(() -> manager.registerTemplate("test", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("template");
        }

        @Test
        @DisplayName("throws on empty template")
        void emptyTemplate() {
            assertThatThrownBy(() -> manager.registerTemplate("test", "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- getTemplate ----

    @Nested
    @DisplayName("getTemplate")
    class GetTemplate {

        @Test
        @DisplayName("returns registered template")
        void existingTemplate() {
            manager.registerTemplate("sys", "You are {{role}}.");
            assertThat(manager.getTemplate("sys")).isEqualTo("You are {{role}}.");
        }

        @Test
        @DisplayName("throws for non-existent template")
        void nonExistentTemplate() {
            assertThatThrownBy(() -> manager.getTemplate("missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Template not found");
        }
    }

    // ---- render ----

    @Nested
    @DisplayName("render")
    class Render {

        @Test
        @DisplayName("renders template with all variables provided")
        void allVariablesProvided() {
            manager.registerTemplate("email",
                    "Dear {{name}}, your order {{orderId}} is confirmed.");

            String result = manager.render("email", Map.of(
                    "name", "Alice",
                    "orderId", "ORD-123"
            ));

            assertThat(result).isEqualTo("Dear Alice, your order ORD-123 is confirmed.");
        }

        @Test
        @DisplayName("renders template with no variables")
        void noVariables() {
            manager.registerTemplate("static", "This is a static prompt.");

            String result = manager.render("static", Map.of());

            assertThat(result).isEqualTo("This is a static prompt.");
        }

        @Test
        @DisplayName("throws when required variable is missing")
        void missingVariable() {
            manager.registerTemplate("greet", "Hello {{firstName}} {{lastName}}!");

            assertThatThrownBy(() -> manager.render("greet", Map.of("firstName", "Bob")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing required variables")
                    .hasMessageContaining("lastName");
        }

        @Test
        @DisplayName("throws for non-existent template")
        void nonExistentTemplate() {
            assertThatThrownBy(() -> manager.render("ghost", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Template not found");
        }

        @Test
        @DisplayName("handles multiple occurrences of same variable")
        void multipleOccurrences() {
            manager.registerTemplate("repeat", "{{name}} says hi. Again, {{name}}!");

            String result = manager.render("repeat", Map.of("name", "Charlie"));

            assertThat(result).isEqualTo("Charlie says hi. Again, Charlie!");
        }
    }

    // ---- renderWithDefaults ----

    @Nested
    @DisplayName("renderWithDefaults")
    class RenderWithDefaults {

        @Test
        @DisplayName("uses default when variable not provided")
        void usesDefault() {
            manager.registerTemplate("prompt", "Role: {{role}}, Tone: {{tone}}");

            String result = manager.renderWithDefaults("prompt",
                    Map.of("role", "assistant"),
                    Map.of("tone", "friendly", "role", "system"));

            // Explicit variable overrides default
            assertThat(result).isEqualTo("Role: assistant, Tone: friendly");
        }

        @Test
        @DisplayName("falls back to defaults for all missing variables")
        void allDefaults() {
            manager.registerTemplate("cfg", "Model: {{model}}, Temp: {{temp}}");

            String result = manager.renderWithDefaults("cfg",
                    Map.of(),
                    Map.of("model", "gpt-4", "temp", "0.7"));

            assertThat(result).isEqualTo("Model: gpt-4, Temp: 0.7");
        }
    }

    // ---- getRequiredVariables ----

    @Nested
    @DisplayName("getRequiredVariables")
    class GetRequiredVariables {

        @Test
        @DisplayName("extracts all variable names from template")
        void extractsVariables() {
            manager.registerTemplate("complex",
                    "{{system}} prompt for {{user}} about {{topic}}");

            Set<String> vars = manager.getRequiredVariables("complex");

            assertThat(vars).containsExactlyInAnyOrder("system", "user", "topic");
        }

        @Test
        @DisplayName("returns empty set for template without variables")
        void noVariables() {
            manager.registerTemplate("plain", "No variables here.");

            assertThat(manager.getRequiredVariables("plain")).isEmpty();
        }

        @Test
        @DisplayName("throws for non-existent template")
        void nonExistent() {
            assertThatThrownBy(() -> manager.getRequiredVariables("nope"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- validateVariables ----

    @Nested
    @DisplayName("validateVariables")
    class ValidateVariables {

        @Test
        @DisplayName("returns true when all required variables are present")
        void allPresent() {
            manager.registerTemplate("tmpl", "{{a}} and {{b}}");

            assertThat(manager.validateVariables("tmpl", Map.of("a", "1", "b", "2"))).isTrue();
        }

        @Test
        @DisplayName("returns true when extra variables are provided")
        void extraVariables() {
            manager.registerTemplate("tmpl", "{{a}}");

            assertThat(manager.validateVariables("tmpl", Map.of("a", "1", "extra", "x"))).isTrue();
        }

        @Test
        @DisplayName("returns false when a required variable is missing")
        void missingVariable() {
            manager.registerTemplate("tmpl", "{{a}} and {{b}}");

            assertThat(manager.validateVariables("tmpl", Map.of("a", "1"))).isFalse();
        }

        @Test
        @DisplayName("returns false when variables map is null and template has variables")
        void nullVariables() {
            manager.registerTemplate("tmpl", "{{x}}");

            assertThat(manager.validateVariables("tmpl", null)).isFalse();
        }

        @Test
        @DisplayName("returns true when variables map is null and template has no variables")
        void nullVariablesNoRequired() {
            manager.registerTemplate("tmpl", "static text");

            assertThat(manager.validateVariables("tmpl", null)).isTrue();
        }
    }

    // ---- getMissingVariables ----

    @Nested
    @DisplayName("getMissingVariables")
    class GetMissingVariables {

        @Test
        @DisplayName("returns missing variable names")
        void returnsMissing() {
            manager.registerTemplate("tmpl", "{{a}} {{b}} {{c}}");

            Set<String> missing = manager.getMissingVariables("tmpl", Map.of("a", "1"));

            assertThat(missing).containsExactlyInAnyOrder("b", "c");
        }

        @Test
        @DisplayName("returns empty when all provided")
        void allProvided() {
            manager.registerTemplate("tmpl", "{{x}}");

            assertThat(manager.getMissingVariables("tmpl", Map.of("x", "val"))).isEmpty();
        }

        @Test
        @DisplayName("returns all when null variables provided")
        void nullVariables() {
            manager.registerTemplate("tmpl", "{{p}} {{q}}");

            assertThat(manager.getMissingVariables("tmpl", null))
                    .containsExactlyInAnyOrder("p", "q");
        }
    }

    // ---- listTemplates ----

    @Test
    @DisplayName("listTemplates returns all registered templates")
    void listTemplates() {
        manager.registerTemplate("alpha", "a");
        manager.registerTemplate("beta", "b");
        manager.registerTemplate("gamma", "c");

        assertThat(manager.listTemplates()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    // ---- removeTemplate ----

    @Test
    @DisplayName("removeTemplate removes specific template")
    void removeTemplate() {
        manager.registerTemplate("keep", "stay");
        manager.registerTemplate("drop", "go");

        manager.removeTemplate("drop");

        assertThat(manager.listTemplates()).containsExactly("keep");
        assertThatThrownBy(() -> manager.getTemplate("drop"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- clear ----

    @Test
    @DisplayName("clear removes all templates")
    void clear() {
        manager.registerTemplate("a", "1");
        manager.registerTemplate("b", "2");

        manager.clear();

        assertThat(manager.listTemplates()).isEmpty();
    }
}
