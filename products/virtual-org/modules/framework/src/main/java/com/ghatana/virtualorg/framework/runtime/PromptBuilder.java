package com.ghatana.virtualorg.framework.runtime;

import com.ghatana.virtualorg.framework.config.PersonaConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builder for constructing LLM prompts from agent context.
 *
 * <p>
 * <b>Purpose</b><br>
 * Constructs well-structured prompts for agent reasoning by combining: - Agent
 * persona and role information - Current task context - Available tools -
 * Conversation history - Working memory
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PromptBuilder builder = new PromptBuilder();
 *
 * String systemPrompt = builder.buildSystemPrompt(agentContext);
 * String userPrompt = builder.buildUserPrompt(perception, history);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose LLM prompt construction
 * @doc.layer product
 * @doc.pattern Builder
 */
public class PromptBuilder {

    private static final String DEFAULT_SYSTEM_TEMPLATE = """
        You are {{agentName}}, {{roleDescription}}.
        
        ## Your Identity
        - Name: {{agentName}}
        - Role: {{roleName}}
        - Department: {{departmentId}}
        - Organization: {{organizationId}}
        
        ## Your Persona
        {{personaDescription}}
        
        ## Your Capabilities
        You have access to the following tools:
        {{toolsList}}
        
        ## Guidelines
        1. Think step-by-step before taking action
        2. Use tools when you need to interact with external systems
        3. If unsure, ask for clarification rather than guessing
        4. Report back clearly on what you've done
        5. Escalate if a task is outside your authority
        
        ## Current Context
        {{workingMemoryContext}}
        """;

    private static final String DEFAULT_USER_TEMPLATE = """
        ## Current Situation
        {{eventContext}}
        
        ## Recent History
        {{conversationSummary}}
        
        Please analyze the situation and decide on the best course of action.
        Think through your reasoning step by step, then either:
        - Use a tool to take action
        - Respond with your analysis
        - Escalate if needed
        """;

    private String systemTemplate;
    private String userTemplate;

    public PromptBuilder() {
        this.systemTemplate = DEFAULT_SYSTEM_TEMPLATE;
        this.userTemplate = DEFAULT_USER_TEMPLATE;
    }

    /**
     * Sets a custom system prompt template.
     *
     * @param template The template with {{placeholder}} markers
     * @return This builder for chaining
     */
    public PromptBuilder withSystemTemplate(String template) {
        this.systemTemplate = template;
        return this;
    }

    /**
     * Sets a custom user prompt template.
     *
     * @param template The template with {{placeholder}} markers
     * @return This builder for chaining
     */
    public PromptBuilder withUserTemplate(String template) {
        this.userTemplate = template;
        return this;
    }

    /**
     * Builds the system prompt from agent context.
     *
     * @param context The agent context
     * @return The formatted system prompt
     */
    public String buildSystemPrompt(AgentContext context) {
        if (context == null) {
            return "You are a helpful AI assistant.";
        }

        String prompt = systemTemplate;

        // Agent identity
        prompt = prompt.replace("{{agentName}}", safe(context.getAgentName()));
        prompt = prompt.replace("{{organizationId}}", safe(context.getOrganizationId()));
        prompt = prompt.replace("{{departmentId}}", safe(context.getDepartmentId()));

        // Role information
        if (context.getRole() != null) {
            prompt = prompt.replace("{{roleName}}", safe(context.getRole().name()));
            prompt = prompt.replace("{{roleDescription}}", buildRoleDescription(context.getRole()));
        } else {
            prompt = prompt.replace("{{roleName}}", "Assistant");
            prompt = prompt.replace("{{roleDescription}}", "an AI assistant");
        }

        // Persona
        prompt = prompt.replace("{{personaDescription}}", buildPersonaDescription(context.getPersona()));

        // Tools list
        prompt = prompt.replace("{{toolsList}}", buildToolsList(context.getAvailableTools()));

        // Working memory context
        prompt = prompt.replace("{{workingMemoryContext}}", buildWorkingMemoryContext(context));

        return prompt.trim();
    }

    /**
     * Builds the user prompt from perception and history.
     *
     * @param perception The perception context (internal class, using Map for
     * flexibility)
     * @param history The conversation history
     * @return The formatted user prompt
     */
    public String buildUserPrompt(Object perception, List<?> history) {
        String prompt = userTemplate;

        // Event context
        prompt = prompt.replace("{{eventContext}}", buildEventContext(perception));

        // Conversation summary
        prompt = prompt.replace("{{conversationSummary}}", buildConversationSummary(history));

        return prompt.trim();
    }

    /**
     * Builds a concise summary prompt for memory storage.
     *
     * @param decision The decision to summarize
     * @param outcome The outcome of the decision
     * @return Summary text for episodic memory
     */
    public String buildMemorySummary(AgentDecision decision, String outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append("Decision: ").append(decision.getDecision()).append("\n");
        sb.append("Reasoning: ").append(truncate(decision.getReasoning(), 200)).append("\n");

        if (decision.hasAction()) {
            sb.append("Action: ").append(decision.getAction().getToolName()).append("\n");
        }

        sb.append("Outcome: ").append(outcome);
        return sb.toString();
    }

    // ========== Helper Methods ==========
    private String buildRoleDescription(com.ghatana.virtualorg.framework.hierarchy.Role role) {
        StringBuilder sb = new StringBuilder();
        sb.append("a ").append(role.name());

        // Role is a record with (name, layer), no separate description field
        if (role.layer() != null) {
            sb.append(" at ").append(role.layer().getDisplayName()).append(" level");
        }

        return sb.toString();
    }

    private String buildPersonaDescription(PersonaConfig persona) {
        if (persona == null) {
            return "A helpful, professional AI assistant.";
        }

        StringBuilder sb = new StringBuilder();

        // Display name as background
        if (persona.getDisplayName() != null) {
            sb.append("Background: ").append(persona.getDisplayName()).append("\n");
        }

        // Communication style
        if (persona.getCommunicationStyle() != null) {
            sb.append("Communication Style: ").append(persona.getCommunicationStyle()).append("\n");
        }

        // Expertise domains
        List<String> domains = persona.getExpertiseDomains();
        if (!domains.isEmpty()) {
            sb.append("Areas of Expertise: ").append(String.join(", ", domains)).append("\n");
        }

        // Specializations
        List<String> specializations = persona.getSpecializations();
        if (!specializations.isEmpty()) {
            sb.append("Specializations: ").append(String.join(", ", specializations)).append("\n");
        }

        return sb.length() > 0 ? sb.toString() : "A helpful, professional AI assistant.";
    }

    private String buildToolsList(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return "No tools currently available.";
        }

        return tools.stream()
                .map(tool -> "- " + tool)
                .collect(Collectors.joining("\n"));
    }

    private String buildWorkingMemoryContext(AgentContext context) {
        Map<String, Object> memory = context.getWorkingMemorySnapshot();
        if (memory == null || memory.isEmpty()) {
            return "No active working context.";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : memory.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ")
                    .append(truncate(String.valueOf(entry.getValue()), 100))
                    .append("\n");
        }
        return sb.toString();
    }

    private String buildEventContext(Object perception) {
        if (perception == null) {
            return "No specific event to process.";
        }

        // Use reflection or duck typing to extract event info
        try {
            Class<?> clazz = perception.getClass();

            StringBuilder sb = new StringBuilder();

            // Try to get eventType
            try {
                java.lang.reflect.Field eventTypeField = clazz.getDeclaredField("eventType");
                eventTypeField.setAccessible(true);
                Object eventType = eventTypeField.get(perception);
                if (eventType != null) {
                    sb.append("Event Type: ").append(eventType).append("\n");
                }
            } catch (NoSuchFieldException ignored) {
            }

            // Try to get eventPayload
            try {
                java.lang.reflect.Field payloadField = clazz.getDeclaredField("eventPayload");
                payloadField.setAccessible(true);
                Object payload = payloadField.get(perception);
                if (payload != null) {
                    sb.append("Event Details:\n").append(truncate(String.valueOf(payload), 500)).append("\n");
                }
            } catch (NoSuchFieldException ignored) {
            }

            return sb.length() > 0 ? sb.toString() : "Processing general request.";
        } catch (Exception e) {
            return "Processing general request.";
        }
    }

    private String buildConversationSummary(List<?> history) {
        if (history == null || history.isEmpty()) {
            return "This is the start of the conversation.";
        }

        // Summarize last few messages
        int maxMessages = Math.min(history.size(), 5);
        List<?> recentMessages = history.subList(history.size() - maxMessages, history.size());

        StringBuilder sb = new StringBuilder();
        for (Object msg : recentMessages) {
            try {
                Class<?> clazz = msg.getClass();

                // Try to get role
                java.lang.reflect.Field roleField = clazz.getDeclaredField("role");
                roleField.setAccessible(true);
                Object role = roleField.get(msg);

                // Try to get content
                java.lang.reflect.Field contentField = clazz.getDeclaredField("content");
                contentField.setAccessible(true);
                Object content = contentField.get(msg);

                if (role != null && content != null) {
                    sb.append(role).append(": ")
                            .append(truncate(String.valueOf(content), 150))
                            .append("\n\n");
                }
            } catch (Exception ignored) {
            }
        }

        return sb.length() > 0 ? sb.toString() : "This is the start of the conversation.";
    }

    private String safe(String value) {
        return value != null ? value : "unknown";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
