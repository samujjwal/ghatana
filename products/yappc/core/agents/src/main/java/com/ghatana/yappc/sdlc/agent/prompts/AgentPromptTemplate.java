package com.ghatana.yappc.sdlc.agent.prompts;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Prompt template for YAPPC specialist agents.
 *
 * <p>Provides structured prompts for LLM-based agent generators, including:
 *
 * <ul>
 *   <li>System prompt (agent role and capabilities)
 *   <li>Task prompt (specific work to perform)
 *   <li>Context injection (project state, constraints)
 *   <li>Output format (structured response requirements)
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Agent prompt template system
 * @doc.layer product
 * @doc.pattern Template
 */
public class AgentPromptTemplate {

  private final String agentName;
  private final String systemPrompt;
  private final String taskTemplate;
  private final String outputFormat;
  private final Map<String, String> examples;

  private AgentPromptTemplate(Builder builder) {
    this.agentName = builder.agentName;
    this.systemPrompt = builder.systemPrompt;
    this.taskTemplate = builder.taskTemplate;
    this.outputFormat = builder.outputFormat;
    this.examples = Map.copyOf(builder.examples);
  }

  /**
   * Renders the complete prompt with injected context.
   *
   * @param context Context variables to inject
   * @return Complete rendered prompt
   */
  @NotNull
  public String render(@NotNull Map<String, Object> context) {
    StringBuilder prompt = new StringBuilder();

    // System prompt
    prompt.append("# System\n\n");
    prompt.append(systemPrompt).append("\n\n");

    // Task
    prompt.append("# Task\n\n");
    String renderedTask = renderTemplate(taskTemplate, context);
    prompt.append(renderedTask).append("\n\n");

    // Output format
    prompt.append("# Output Format\n\n");
    prompt.append(outputFormat).append("\n\n");

    // Examples (if any)
    if (!examples.isEmpty()) {
      prompt.append("# Examples\n\n");
      for (var entry : examples.entrySet()) {
        prompt.append("## ").append(entry.getKey()).append("\n\n");
        prompt.append(entry.getValue()).append("\n\n");
      }
    }

    return prompt.toString();
  }

  /**
   * Renders template with variable substitution.
   *
   * <p>Delegates to the canonical {@code {{variable}}} substitution utility
   * to avoid duplicating the replacement logic from {@code PromptTemplate}.
   */
  private String renderTemplate(String template, Map<String, Object> context) {
    // Use simple String.replace — intentionally lenient (no error on missing vars)
    // to match the agent-specific contract (partial renders allowed)
    String result = template;
    for (var entry : context.entrySet()) {
      result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
    }
    return result;
  }

  public String getAgentName() {
    return agentName;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public String getTaskTemplate() {
    return taskTemplate;
  }

  public String getOutputFormat() {
    return outputFormat;
  }

  @NotNull
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String agentName;
    private String systemPrompt;
    private String taskTemplate;
    private String outputFormat;
    private Map<String, String> examples = new HashMap<>();

    @NotNull
    public Builder agentName(@NotNull String agentName) {
      this.agentName = agentName;
      return this;
    }

    @NotNull
    public Builder systemPrompt(@NotNull String systemPrompt) {
      this.systemPrompt = systemPrompt;
      return this;
    }

    @NotNull
    public Builder taskTemplate(@NotNull String taskTemplate) {
      this.taskTemplate = taskTemplate;
      return this;
    }

    @NotNull
    public Builder outputFormat(@NotNull String outputFormat) {
      this.outputFormat = outputFormat;
      return this;
    }

    @NotNull
    public Builder addExample(@NotNull String name, @NotNull String example) {
      this.examples.put(name, example);
      return this;
    }

    @NotNull
    public AgentPromptTemplate build() {
      if (agentName == null || agentName.isEmpty()) {
        throw new IllegalStateException("agentName is required");
      }
      if (systemPrompt == null || systemPrompt.isEmpty()) {
        throw new IllegalStateException("systemPrompt is required");
      }
      if (taskTemplate == null || taskTemplate.isEmpty()) {
        throw new IllegalStateException("taskTemplate is required");
      }
      if (outputFormat == null || outputFormat.isEmpty()) {
        throw new IllegalStateException("outputFormat is required");
      }
      return new AgentPromptTemplate(this);
    }
  }
}
