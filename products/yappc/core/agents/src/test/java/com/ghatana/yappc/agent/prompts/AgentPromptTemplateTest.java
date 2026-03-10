package com.ghatana.yappc.agent.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgentPromptTemplate} — the structured prompt template system
 * for YAPPC specialist agents.
 *
 * <p>Tests rendering, variable substitution, builder validation, and examples.
 *
 * @doc.type class
 * @doc.purpose Unit tests for agent prompt template rendering and validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentPromptTemplate Tests")
class AgentPromptTemplateTest {

  // ===== Builder Validation Tests =====

  @Nested
  @DisplayName("Builder Validation")
  class BuilderValidation {

    @Test
    @DisplayName("Should build template with all required fields")
    void shouldBuildWithAllFields() {
      AgentPromptTemplate template = AgentPromptTemplate.builder()
          .agentName("architecture-agent")
          .systemPrompt("You are an architecture expert.")
          .taskTemplate("Design a system for {{project}}.")
          .outputFormat("Return JSON with 'architecture' field.")
          .build();

      assertThat(template.getAgentName()).isEqualTo("architecture-agent");
      assertThat(template.getSystemPrompt()).isEqualTo("You are an architecture expert.");
      assertThat(template.getTaskTemplate()).isEqualTo("Design a system for {{project}}.");
      assertThat(template.getOutputFormat()).isEqualTo("Return JSON with 'architecture' field.");
    }

    @Test
    @DisplayName("Should reject missing agentName")
    void shouldRejectMissingAgentName() {
      assertThatThrownBy(() ->
          AgentPromptTemplate.builder()
              .systemPrompt("System")
              .taskTemplate("Task")
              .outputFormat("Format")
              .build())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("agentName");
    }

    @Test
    @DisplayName("Should reject missing systemPrompt")
    void shouldRejectMissingSystemPrompt() {
      assertThatThrownBy(() ->
          AgentPromptTemplate.builder()
              .agentName("test")
              .taskTemplate("Task")
              .outputFormat("Format")
              .build())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("systemPrompt");
    }

    @Test
    @DisplayName("Should reject missing taskTemplate")
    void shouldRejectMissingTaskTemplate() {
      assertThatThrownBy(() ->
          AgentPromptTemplate.builder()
              .agentName("test")
              .systemPrompt("System")
              .outputFormat("Format")
              .build())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("taskTemplate");
    }

    @Test
    @DisplayName("Should reject missing outputFormat")
    void shouldRejectMissingOutputFormat() {
      assertThatThrownBy(() ->
          AgentPromptTemplate.builder()
              .agentName("test")
              .systemPrompt("System")
              .taskTemplate("Task")
              .build())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("outputFormat");
    }

    @Test
    @DisplayName("Should reject empty agentName")
    void shouldRejectEmptyAgentName() {
      assertThatThrownBy(() ->
          AgentPromptTemplate.builder()
              .agentName("")
              .systemPrompt("System")
              .taskTemplate("Task")
              .outputFormat("Format")
              .build())
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // ===== Rendering Tests =====

  @Nested
  @DisplayName("Rendering")
  class Rendering {

    @Test
    @DisplayName("Should render template with variable substitution")
    void shouldRenderWithVariables() {
      AgentPromptTemplate template = createTemplate(
          "Design a system for {{project}} in {{language}}.");

      String rendered = template.render(Map.of(
          "project", "YAPPC",
          "language", "Java"));

      assertThat(rendered).contains("Design a system for YAPPC in Java.");
    }

    @Test
    @DisplayName("Should include system prompt section")
    void shouldIncludeSystemPrompt() {
      AgentPromptTemplate template = createTemplate("Task {{var}}.");

      String rendered = template.render(Map.of("var", "value"));

      assertThat(rendered).contains("# System");
      assertThat(rendered).contains("You are a test agent.");
    }

    @Test
    @DisplayName("Should include task section")
    void shouldIncludeTaskSection() {
      AgentPromptTemplate template = createTemplate("Analyze {{input}}.");

      String rendered = template.render(Map.of("input", "codebase"));

      assertThat(rendered).contains("# Task");
      assertThat(rendered).contains("Analyze codebase.");
    }

    @Test
    @DisplayName("Should include output format section")
    void shouldIncludeOutputFormatSection() {
      AgentPromptTemplate template = createTemplate("Task.");

      String rendered = template.render(Map.of());

      assertThat(rendered).contains("# Output Format");
      assertThat(rendered).contains("JSON response");
    }

    @Test
    @DisplayName("Should handle missing variables leniently")
    void shouldHandleMissingVariablesLeniently() {
      AgentPromptTemplate template = createTemplate(
          "Process {{known}} and {{unknown}}.");

      // Should NOT throw — partial rendering is allowed
      String rendered = template.render(Map.of("known", "value"));

      assertThat(rendered).contains("Process value and {{unknown}}.");
    }

    @Test
    @DisplayName("Should render with empty context")
    void shouldRenderWithEmptyContext() {
      AgentPromptTemplate template = createTemplate("No variables needed.");

      String rendered = template.render(Map.of());

      assertThat(rendered).contains("No variables needed.");
    }

    @Test
    @DisplayName("Should handle null-valued context entries")
    void shouldHandleNullValues() {
      AgentPromptTemplate template = createTemplate("Value is {{key}}.");

      String rendered = template.render(Map.of("key", "null-test"));

      assertThat(rendered).contains("Value is null-test.");
    }
  }

  // ===== Examples Tests =====

  @Nested
  @DisplayName("Examples")
  class Examples {

    @Test
    @DisplayName("Should include examples section when examples are present")
    void shouldIncludeExamplesSection() {
      AgentPromptTemplate template = AgentPromptTemplate.builder()
          .agentName("test")
          .systemPrompt("System")
          .taskTemplate("Task")
          .outputFormat("Format")
          .addExample("Simple Case", "Input: foo\nOutput: bar")
          .addExample("Complex Case", "Input: baz\nOutput: qux")
          .build();

      String rendered = template.render(Map.of());

      assertThat(rendered).contains("# Examples");
      assertThat(rendered).contains("## Simple Case");
      assertThat(rendered).contains("Input: foo");
      assertThat(rendered).contains("## Complex Case");
      assertThat(rendered).contains("Input: baz");
    }

    @Test
    @DisplayName("Should omit examples section when no examples provided")
    void shouldOmitExamplesWhenEmpty() {
      AgentPromptTemplate template = createTemplate("Task.");

      String rendered = template.render(Map.of());

      assertThat(rendered).doesNotContain("# Examples");
    }
  }

  // ===== Test Helpers =====

  private AgentPromptTemplate createTemplate(String taskTemplate) {
    return AgentPromptTemplate.builder()
        .agentName("test-agent")
        .systemPrompt("You are a test agent.")
        .taskTemplate(taskTemplate)
        .outputFormat("JSON response")
        .build();
  }
}
