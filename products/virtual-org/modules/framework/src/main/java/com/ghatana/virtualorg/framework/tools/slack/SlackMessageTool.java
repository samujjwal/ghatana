package com.ghatana.virtualorg.framework.tools.slack;

import com.ghatana.virtualorg.framework.tools.AgentTool;
import com.ghatana.virtualorg.framework.tools.ToolContext;
import com.ghatana.virtualorg.framework.tools.ToolInput;
import com.ghatana.virtualorg.framework.tools.ToolResult;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tool for sending Slack messages.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to send messages to Slack channels. Used for notifications,
 * status updates, and team communication.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new SlackMessageTool(slackClient);
 *
 * ToolInput input = ToolInput.builder()
 *     .put("channel", "#engineering")
 *     .put("message", "Build completed successfully!")
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Send messages to Slack channels
 * @doc.layer product
 * @doc.pattern Command
 */
public class SlackMessageTool implements AgentTool {

    private static final String TOOL_NAME = "slack.send_message";

    private final SlackClient slackClient;

    public SlackMessageTool(SlackClient slackClient) {
        this.slackClient = Objects.requireNonNull(slackClient, "slackClient required");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Send a message to a Slack channel. "
                + "Use this to communicate updates, ask questions, or notify the team.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "channel", Map.of(
                                "type", "string",
                                "description", "Channel to send to (e.g., '#engineering' or channel ID)"
                        ),
                        "message", Map.of(
                                "type", "string",
                                "description", "The message to send"
                        ),
                        "thread_ts", Map.of(
                                "type", "string",
                                "description", "Optional: Thread timestamp to reply in a thread"
                        )
                ),
                "required", List.of("channel", "message")
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("slack.write", "slack.channels.read");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        try {
            String channel = input.getString("channel");
            String message = input.getString("message");
            String threadTs = input.getString("thread_ts", null);

            Promise<SlackClient.SlackMessage> msgPromise;
            if (threadTs != null && !threadTs.isEmpty()) {
                msgPromise = slackClient.replyToThread(channel, threadTs, message);
            } else {
                msgPromise = slackClient.postMessage(channel, message);
            }

            return msgPromise
                    .map(msg -> ToolResult.success(Map.of(
                    "channel", msg.channel(),
                    "ts", msg.ts(),
                    "message_preview", truncate(message, 100),
                    "status", "sent"
            )))
                    .mapException(e -> new RuntimeException(
                    "Failed to send Slack message: " + e.getMessage(), e));
        } catch (IllegalArgumentException e) {
            return Promise.of(ToolResult.failure("Invalid input: " + e.getMessage()));
        }
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new java.util.ArrayList<>();

        if (!input.has("channel")) {
            errors.add("channel is required");
        } else {
            String channel = input.getString("channel", "");
            if (channel.isEmpty()) {
                errors.add("channel cannot be empty");
            }
        }

        if (!input.has("message")) {
            errors.add("message is required");
        } else {
            String message = input.getString("message", "");
            if (message.isEmpty()) {
                errors.add("message cannot be empty");
            }
        }

        return errors;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
