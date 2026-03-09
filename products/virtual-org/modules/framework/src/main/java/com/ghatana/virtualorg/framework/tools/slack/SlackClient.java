package com.ghatana.virtualorg.framework.tools.slack;

import com.ghatana.platform.http.client.OkHttpAdapter;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;

/**
 * Slack API client for agent communication tools.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides Slack API access for agent tools, enabling: - Sending messages to
 * channels - Replying to threads - Posting notifications - Uploading files
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * SlackClient client = new SlackClient(httpAdapter, token, metrics);
 *
 * // Send a message
 * SlackMessage msg = client.postMessage(
 *     "#engineering", "Build completed successfully!"
 * ).getResult();
 *
 * // Reply in a thread
 * client.replyToThread("#engineering", msg.ts(), "Details: ...");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Slack API client for agent integrations
 * @doc.layer product
 * @doc.pattern Client
 */
public class SlackClient {

    private static final String SLACK_API_BASE = "https://slack.com/api";

    private final OkHttpAdapter httpAdapter;
    private final String token;
    private final MetricsCollector metrics;

    public SlackClient(OkHttpAdapter httpAdapter, String token, MetricsCollector metrics) {
        this.httpAdapter = Objects.requireNonNull(httpAdapter, "httpAdapter required");
        this.token = Objects.requireNonNull(token, "token required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
    }

    // ========== Messaging ==========
    /**
     * Posts a message to a Slack channel.
     *
     * @param channel Channel ID or name (e.g., "#general" or "C1234567890")
     * @param text Message text
     * @return Promise of the sent message
     */
    public Promise<SlackMessage> postMessage(String channel, String text) {
        return postMessage(channel, text, null, null);
    }

    /**
     * Posts a formatted message to a Slack channel.
     *
     * @param channel Channel ID or name
     * @param text Message text
     * @param blocks Optional Block Kit blocks for rich formatting
     * @param threadTs Optional thread timestamp to reply in thread
     * @return Promise of the sent message
     */
    public Promise<SlackMessage> postMessage(
            String channel, String text, String blocks, String threadTs) {

        String url = SLACK_API_BASE + "/chat.postMessage";

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("channel", channel);
        params.put("text", text);

        if (blocks != null && !blocks.isEmpty()) {
            params.put("blocks", blocks);
        }
        if (threadTs != null && !threadTs.isEmpty()) {
            params.put("thread_ts", threadTs);
        }

        String body = buildJson(params);

        return httpAdapter.postJson(url, body, authHeaders())
                .map(this::parseMessage)
                .whenResult(msg -> metrics.incrementCounter("slack.message.sent", "channel", channel));
    }

    /**
     * Replies to an existing thread.
     *
     * @param channel Channel ID or name
     * @param threadTs Thread timestamp
     * @param text Reply text
     * @return Promise of the sent reply
     */
    public Promise<SlackMessage> replyToThread(String channel, String threadTs, String text) {
        return postMessage(channel, text, null, threadTs);
    }

    /**
     * Updates an existing message.
     *
     * @param channel Channel ID
     * @param ts Message timestamp
     * @param text New message text
     * @return Promise completing when updated
     */
    public Promise<Void> updateMessage(String channel, String ts, String text) {
        String url = SLACK_API_BASE + "/chat.update";

        String body = buildJson(Map.of(
                "channel", channel,
                "ts", ts,
                "text", text
        ));

        return httpAdapter.postJson(url, body, authHeaders())
                .map(r -> {
                    metrics.incrementCounter("slack.message.updated", "channel", channel);
                    return null;
                });
    }

    /**
     * Adds a reaction to a message.
     *
     * @param channel Channel ID
     * @param ts Message timestamp
     * @param emoji Emoji name without colons (e.g., "thumbsup")
     * @return Promise completing when reaction is added
     */
    public Promise<Void> addReaction(String channel, String ts, String emoji) {
        String url = SLACK_API_BASE + "/reactions.add";

        String body = buildJson(Map.of(
                "channel", channel,
                "timestamp", ts,
                "name", emoji
        ));

        return httpAdapter.postJson(url, body, authHeaders())
                .map(r -> {
                    metrics.incrementCounter("slack.reaction.added", "emoji", emoji);
                    return null;
                });
    }

    // ========== Notifications ==========
    /**
     * Sends a DM to a user.
     *
     * @param userId User ID
     * @param text Message text
     * @return Promise of the sent message
     */
    public Promise<SlackMessage> sendDirectMessage(String userId, String text) {
        // First open a DM channel
        String openUrl = SLACK_API_BASE + "/conversations.open";
        String openBody = buildJson(Map.of("users", userId));

        return httpAdapter.postJson(openUrl, openBody, authHeaders())
                .then(response -> {
                    String channelId = extractString(response, "channel", "id");
                    if (channelId.isEmpty()) {
                        return Promise.ofException(new RuntimeException("Failed to open DM channel"));
                    }
                    return postMessage(channelId, text);
                });
    }

    // ========== Helpers ==========
    private Map<String, String> authHeaders() {
        return Map.of(
                "Authorization", "Bearer " + token,
                "Content-Type", "application/json; charset=utf-8"
        );
    }

    private String buildJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private SlackMessage parseMessage(String json) {
        boolean ok = json.contains("\"ok\":true");
        if (!ok) {
            String error = extractString(json, "error");
            throw new RuntimeException("Slack API error: " + error);
        }

        return new SlackMessage(
                extractString(json, "channel"),
                extractString(json, "ts"),
                extractString(json, "message", "text")
        );
    }

    private String extractString(String json, String... keys) {
        String current = json;
        for (String key : keys) {
            int idx = current.indexOf("\"" + key + "\"");
            if (idx < 0) {
                return "";
            }
            int colonIdx = current.indexOf(":", idx);
            if (colonIdx < 0) {
                return "";
            }
            int valueStart = current.indexOf("\"", colonIdx + 1);
            if (valueStart < 0) {
                return "";
            }
            int valueEnd = current.indexOf("\"", valueStart + 1);
            if (valueEnd < 0) {
                return "";
            }
            current = current.substring(valueStart + 1, valueEnd);
        }
        return current;
    }

    // ========== Value Objects ==========
    public record SlackMessage(
            String channel,
            String ts,
            String text
    ) {
    }
}
