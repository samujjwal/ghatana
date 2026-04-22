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
@DisplayName("PromptTemplateManager [GH-90000]")
class PromptTemplateManagerTest {

    private PromptTemplateManager manager;

    @BeforeEach
    void setUp() { // GH-90000
        manager = new PromptTemplateManager(); // GH-90000
    }

    // ---- registerTemplate ----

    @Nested
    @DisplayName("registerTemplate [GH-90000]")
    class RegisterTemplate {

        @Test
        @DisplayName("registers a valid template successfully [GH-90000]")
        void validTemplate() { // GH-90000
            manager.registerTemplate("greeting", "Hello, {{name}}!"); // GH-90000

            assertThat(manager.listTemplates()).contains("greeting [GH-90000]");
        }

        @Test
        @DisplayName("overwrites existing template with same name [GH-90000]")
        void overwriteExisting() { // GH-90000
            manager.registerTemplate("tmpl", "version 1: {{a}}"); // GH-90000
            manager.registerTemplate("tmpl", "version 2: {{b}}"); // GH-90000

            assertThat(manager.getTemplate("tmpl [GH-90000]")).isEqualTo("version 2: {{b}} [GH-90000]");
            assertThat(manager.getRequiredVariables("tmpl [GH-90000]")).containsExactly("b [GH-90000]");
        }

        @Test
        @DisplayName("throws on null name [GH-90000]")
        void nullName() { // GH-90000
            assertThatThrownBy(() -> manager.registerTemplate(null, "template")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("name [GH-90000]");
        }

        @Test
        @DisplayName("throws on empty name [GH-90000]")
        void emptyName() { // GH-90000
            assertThatThrownBy(() -> manager.registerTemplate("   ", "template")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("throws on null template [GH-90000]")
        void nullTemplate() { // GH-90000
            assertThatThrownBy(() -> manager.registerTemplate("test", null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("template [GH-90000]");
        }

        @Test
        @DisplayName("throws on empty template [GH-90000]")
        void emptyTemplate() { // GH-90000
            assertThatThrownBy(() -> manager.registerTemplate("test", "  ")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ---- getTemplate ----

    @Nested
    @DisplayName("getTemplate [GH-90000]")
    class GetTemplate {

        @Test
        @DisplayName("returns registered template [GH-90000]")
        void existingTemplate() { // GH-90000
            manager.registerTemplate("sys", "You are {{role}}."); // GH-90000
            assertThat(manager.getTemplate("sys [GH-90000]")).isEqualTo("You are {{role}}. [GH-90000]");
        }

        @Test
        @DisplayName("throws for non-existent template [GH-90000]")
        void nonExistentTemplate() { // GH-90000
            assertThatThrownBy(() -> manager.getTemplate("missing [GH-90000]"))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Template not found [GH-90000]");
        }
    }

    // ---- render ----

    @Nested
    @DisplayName("render [GH-90000]")
    class Render {

        @Test
        @DisplayName("renders template with all variables provided [GH-90000]")
        void allVariablesProvided() { // GH-90000
            manager.registerTemplate("email", // GH-90000
                    "Dear {{name}}, your order {{orderId}} is confirmed.");

            String result = manager.render("email", Map.of( // GH-90000
                    "name", "Alice",
                    "orderId", "ORD-123"
            ));

            assertThat(result).isEqualTo("Dear Alice, your order ORD-123 is confirmed. [GH-90000]");
        }

        @Test
        @DisplayName("renders template with no variables [GH-90000]")
        void noVariables() { // GH-90000
            manager.registerTemplate("static", "This is a static prompt."); // GH-90000

            String result = manager.render("static", Map.of()); // GH-90000

            assertThat(result).isEqualTo("This is a static prompt. [GH-90000]");
        }

        @Test
        @DisplayName("throws when required variable is missing [GH-90000]")
        void missingVariable() { // GH-90000
            manager.registerTemplate("greet", "Hello {{firstName}} {{lastName}}!"); // GH-90000

            assertThatThrownBy(() -> manager.render("greet", Map.of("firstName", "Bob"))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Missing required variables [GH-90000]")
                    .hasMessageContaining("lastName [GH-90000]");
        }

        @Test
        @DisplayName("throws for non-existent template [GH-90000]")
        void nonExistentTemplate() { // GH-90000
            assertThatThrownBy(() -> manager.render("ghost", Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Template not found [GH-90000]");
        }

        @Test
        @DisplayName("handles multiple occurrences of same variable [GH-90000]")
        void multipleOccurrences() { // GH-90000
            manager.registerTemplate("repeat", "{{name}} says hi. Again, {{name}}!"); // GH-90000

            String result = manager.render("repeat", Map.of("name", "Charlie")); // GH-90000

            assertThat(result).isEqualTo("Charlie says hi. Again, Charlie! [GH-90000]");
        }
    }

    // ---- renderWithDefaults ----

    @Nested
    @DisplayName("renderWithDefaults [GH-90000]")
    class RenderWithDefaults {

        @Test
        @DisplayName("uses default when variable not provided [GH-90000]")
        void usesDefault() { // GH-90000
            manager.registerTemplate("prompt", "Role: {{role}}, Tone: {{tone}}"); // GH-90000

            String result = manager.renderWithDefaults("prompt", // GH-90000
                    Map.of("role", "assistant"), // GH-90000
                    Map.of("tone", "friendly", "role", "system")); // GH-90000

            // Explicit variable overrides default
            assertThat(result).isEqualTo("Role: assistant, Tone: friendly [GH-90000]");
        }

        @Test
        @DisplayName("falls back to defaults for all missing variables [GH-90000]")
        void allDefaults() { // GH-90000
            manager.registerTemplate("cfg", "Model: {{model}}, Temp: {{temp}}"); // GH-90000

            String result = manager.renderWithDefaults("cfg", // GH-90000
                    Map.of(), // GH-90000
                    Map.of("model", "gpt-4", "temp", "0.7")); // GH-90000

            assertThat(result).isEqualTo("Model: gpt-4, Temp: 0.7 [GH-90000]");
        }
    }

    // ---- getRequiredVariables ----

    @Nested
    @DisplayName("getRequiredVariables [GH-90000]")
    class GetRequiredVariables {

        @Test
        @DisplayName("extracts all variable names from template [GH-90000]")
        void extractsVariables() { // GH-90000
            manager.registerTemplate("complex", // GH-90000
                    "{{system}} prompt for {{user}} about {{topic}}");

            Set<String> vars = manager.getRequiredVariables("complex [GH-90000]");

            assertThat(vars).containsExactlyInAnyOrder("system", "user", "topic"); // GH-90000
        }

        @Test
        @DisplayName("returns empty set for template without variables [GH-90000]")
        void noVariables() { // GH-90000
            manager.registerTemplate("plain", "No variables here."); // GH-90000

            assertThat(manager.getRequiredVariables("plain [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("throws for non-existent template [GH-90000]")
        void nonExistent() { // GH-90000
            assertThatThrownBy(() -> manager.getRequiredVariables("nope [GH-90000]"))
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ---- validateVariables ----

    @Nested
    @DisplayName("validateVariables [GH-90000]")
    class ValidateVariables {

        @Test
        @DisplayName("returns true when all required variables are present [GH-90000]")
        void allPresent() { // GH-90000
            manager.registerTemplate("tmpl", "{{a}} and {{b}}"); // GH-90000

            assertThat(manager.validateVariables("tmpl", Map.of("a", "1", "b", "2"))).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns true when extra variables are provided [GH-90000]")
        void extraVariables() { // GH-90000
            manager.registerTemplate("tmpl", "{{a}}"); // GH-90000

            assertThat(manager.validateVariables("tmpl", Map.of("a", "1", "extra", "x"))).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns false when a required variable is missing [GH-90000]")
        void missingVariable() { // GH-90000
            manager.registerTemplate("tmpl", "{{a}} and {{b}}"); // GH-90000

            assertThat(manager.validateVariables("tmpl", Map.of("a", "1"))).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("returns false when variables map is null and template has variables [GH-90000]")
        void nullVariables() { // GH-90000
            manager.registerTemplate("tmpl", "{{x}}"); // GH-90000

            assertThat(manager.validateVariables("tmpl", null)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("returns true when variables map is null and template has no variables [GH-90000]")
        void nullVariablesNoRequired() { // GH-90000
            manager.registerTemplate("tmpl", "static text"); // GH-90000

            assertThat(manager.validateVariables("tmpl", null)).isTrue(); // GH-90000
        }
    }

    // ---- getMissingVariables ----

    @Nested
    @DisplayName("getMissingVariables [GH-90000]")
    class GetMissingVariables {

        @Test
        @DisplayName("returns missing variable names [GH-90000]")
        void returnsMissing() { // GH-90000
            manager.registerTemplate("tmpl", "{{a}} {{b}} {{c}}"); // GH-90000

            Set<String> missing = manager.getMissingVariables("tmpl", Map.of("a", "1")); // GH-90000

            assertThat(missing).containsExactlyInAnyOrder("b", "c"); // GH-90000
        }

        @Test
        @DisplayName("returns empty when all provided [GH-90000]")
        void allProvided() { // GH-90000
            manager.registerTemplate("tmpl", "{{x}}"); // GH-90000

            assertThat(manager.getMissingVariables("tmpl", Map.of("x", "val"))).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns all when null variables provided [GH-90000]")
        void nullVariables() { // GH-90000
            manager.registerTemplate("tmpl", "{{p}} {{q}}"); // GH-90000

            assertThat(manager.getMissingVariables("tmpl", null)) // GH-90000
                    .containsExactlyInAnyOrder("p", "q"); // GH-90000
        }
    }

    // ---- listTemplates ----

    @Test
    @DisplayName("listTemplates returns all registered templates [GH-90000]")
    void listTemplates() { // GH-90000
        manager.registerTemplate("alpha", "a"); // GH-90000
        manager.registerTemplate("beta", "b"); // GH-90000
        manager.registerTemplate("gamma", "c"); // GH-90000

        assertThat(manager.listTemplates()).containsExactlyInAnyOrder("alpha", "beta", "gamma"); // GH-90000
    }

    // ---- removeTemplate ----

    @Test
    @DisplayName("removeTemplate removes specific template [GH-90000]")
    void removeTemplate() { // GH-90000
        manager.registerTemplate("keep", "stay"); // GH-90000
        manager.registerTemplate("drop", "go"); // GH-90000

        manager.removeTemplate("drop [GH-90000]");

        assertThat(manager.listTemplates()).containsExactly("keep [GH-90000]");
        assertThatThrownBy(() -> manager.getTemplate("drop [GH-90000]"))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ---- clear ----

    @Test
    @DisplayName("clear removes all templates [GH-90000]")
    void clear() { // GH-90000
        manager.registerTemplate("a", "1"); // GH-90000
        manager.registerTemplate("b", "2"); // GH-90000

        manager.clear(); // GH-90000

        assertThat(manager.listTemplates()).isEmpty(); // GH-90000
    }
}
