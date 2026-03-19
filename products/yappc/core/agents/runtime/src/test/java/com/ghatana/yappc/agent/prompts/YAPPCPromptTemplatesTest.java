package com.ghatana.yappc.agent.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link YAPPCPromptTemplates} lookup and template rendering.
 *
 * @doc.type class
 * @doc.purpose Verify prompt template registry aliases and rendering behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("YAPPCPromptTemplates Tests")
class YAPPCPromptTemplatesTest {

  @Test
  @DisplayName("should resolve implementation alias to a valid template")
  void shouldResolveImplementationAlias() {
    AgentPromptTemplate template = YAPPCPromptTemplates.get("implementation.implement");

    assertThat(template.getAgentName()).isEqualTo("DetailedImplementSpecialistAgent");
    assertThat(template.getTaskTemplate()).contains("{{unitName}}", "{{specification}}");
  }

  @Test
  @DisplayName("should render context values into prompt output")
  void shouldRenderContextValuesIntoPrompt() {
    AgentPromptTemplate template = YAPPCPromptTemplates.get("architecture.intake");

    String rendered =
        template.render(
            Map.of(
                "requirements", "Build a secure audit service",
                "functionalRequirements", "record events",
                "nonFunctionalRequirements", "durability",
                "constraints", "Java 21"));

    assertThat(rendered).contains("Build a secure audit service");
    assertThat(rendered).contains("# Output Format");
    assertThat(rendered).contains("requirements intake and validation");
  }

  @Test
  @DisplayName("should reject unknown step ids")
  void shouldRejectUnknownStepIds() {
    assertThatThrownBy(() -> YAPPCPromptTemplates.get("unknown.step"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No prompt template");
  }
}