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
@DisplayName("YAPPCPromptTemplates Tests [GH-90000]")
class YAPPCPromptTemplatesTest {

  @Test
  @DisplayName("should resolve implementation alias to a valid template [GH-90000]")
  void shouldResolveImplementationAlias() { // GH-90000
    AgentPromptTemplate template = YAPPCPromptTemplates.get("implementation.implement [GH-90000]");

    assertThat(template.getAgentName()).isEqualTo("DetailedImplementSpecialistAgent [GH-90000]");
    assertThat(template.getTaskTemplate()).contains("{{unitName}}", "{{specification}}"); // GH-90000
  }

  @Test
  @DisplayName("should render context values into prompt output [GH-90000]")
  void shouldRenderContextValuesIntoPrompt() { // GH-90000
    AgentPromptTemplate template = YAPPCPromptTemplates.get("architecture.intake [GH-90000]");

    String rendered =
        template.render( // GH-90000
            Map.of( // GH-90000
                "requirements", "Build a secure audit service",
                "functionalRequirements", "record events",
                "nonFunctionalRequirements", "durability",
                "constraints", "Java 21"));

    assertThat(rendered).contains("Build a secure audit service [GH-90000]");
    assertThat(rendered).contains("# Output Format [GH-90000]");
    assertThat(rendered).contains("requirements intake and validation [GH-90000]");
  }

  @Test
  @DisplayName("should reject unknown step ids [GH-90000]")
  void shouldRejectUnknownStepIds() { // GH-90000
    assertThatThrownBy(() -> YAPPCPromptTemplates.get("unknown.step [GH-90000]"))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("No prompt template [GH-90000]");
  }
}
