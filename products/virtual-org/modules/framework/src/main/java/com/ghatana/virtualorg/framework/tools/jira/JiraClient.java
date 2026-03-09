package com.ghatana.virtualorg.framework.tools.jira;

import com.ghatana.platform.http.client.OkHttpAdapter;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Jira API client for agent tools.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides Jira API access for agent tools, enabling: - Creating and updating
 * issues - Transitioning issue status - Searching issues via JQL - Adding
 * comments
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * JiraClient client = new JiraClient(httpAdapter, baseUrl, email, token, metrics);
 *
 * // Create an issue
 * JiraIssue issue = client.createIssue(
 *     "PROJ", "Bug", "Login broken", "Users cannot login"
 * ).getResult();
 *
 * // Transition issue
 * client.transitionIssue(issue.key(), "In Progress");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Jira API client for agent integrations
 * @doc.layer product
 * @doc.pattern Client
 */
public class JiraClient {

    private final OkHttpAdapter httpAdapter;
    private final String baseUrl;
    private final String authHeader;
    private final MetricsCollector metrics;

    public JiraClient(
            OkHttpAdapter httpAdapter,
            String baseUrl,
            String email,
            String apiToken,
            MetricsCollector metrics) {
        this.httpAdapter = Objects.requireNonNull(httpAdapter, "httpAdapter required");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl required").replaceAll("/$", "");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");

        // Basic auth: email:apiToken base64 encoded
        String credentials = email + ":" + apiToken;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    // ========== Issues ==========
    /**
     * Creates a new Jira issue.
     *
     * @param projectKey Project key (e.g., "PROJ")
     * @param issueType Issue type (e.g., "Bug", "Story", "Task")
     * @param summary Issue summary/title
     * @param description Issue description
     * @return Promise of the created issue
     */
    public Promise<JiraIssue> createIssue(
            String projectKey, String issueType, String summary, String description) {

        String url = baseUrl + "/rest/api/3/issue";

        String body = buildCreateIssueJson(projectKey, issueType, summary, description);

        return httpAdapter.postJson(url, body, authHeaders())
                .map(this::parseIssue)
                .whenResult(issue -> metrics.incrementCounter("jira.issue.created",
                "project", projectKey, "type", issueType));
    }

    /**
     * Gets an issue by key.
     *
     * @param issueKey Issue key (e.g., "PROJ-123")
     * @return Promise of the issue
     */
    public Promise<JiraIssue> getIssue(String issueKey) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey;

        // Using POST with empty body as workaround since adapter doesn't have GET
        return httpAdapter.postJson(url, "", authHeaders())
                .map(this::parseIssue);
    }

    /**
     * Updates an issue's fields.
     *
     * @param issueKey Issue key
     * @param fields Fields to update
     * @return Promise completing when updated
     */
    public Promise<Void> updateIssue(String issueKey, Map<String, Object> fields) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey;

        String body = buildUpdateIssueJson(fields);

        return httpAdapter.postJson(url, body, authHeaders())
                .map(r -> {
                    metrics.incrementCounter("jira.issue.updated", "issue", issueKey);
                    return null;
                });
    }

    /**
     * Transitions an issue to a new status.
     *
     * @param issueKey Issue key
     * @param statusName Target status name (e.g., "In Progress", "Done")
     * @return Promise completing when transitioned
     */
    public Promise<Void> transitionIssue(String issueKey, String statusName) {
        // First, get available transitions
        String transitionsUrl = baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions";

        return httpAdapter.postJson(transitionsUrl, "", authHeaders())
                .then(response -> {
                    String transitionId = findTransitionId(response, statusName);
                    if (transitionId == null) {
                        return Promise.ofException(new RuntimeException(
                                "Transition not found: " + statusName));
                    }

                    String body = "{\"transition\":{\"id\":\"" + transitionId + "\"}}";
                    return httpAdapter.postJson(transitionsUrl, body, authHeaders())
                            .map(r -> {
                                metrics.incrementCounter("jira.issue.transitioned",
                                        "issue", issueKey, "status", statusName);
                                return null;
                            });
                });
    }

    /**
     * Adds a comment to an issue.
     *
     * @param issueKey Issue key
     * @param comment Comment text
     * @return Promise of the created comment
     */
    public Promise<JiraComment> addComment(String issueKey, String comment) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/comment";

        // Jira Cloud uses ADF (Atlassian Document Format) for comments
        String body = buildCommentJson(comment);

        return httpAdapter.postJson(url, body, authHeaders())
                .map(this::parseComment)
                .whenResult(c -> metrics.incrementCounter("jira.comment.created", "issue", issueKey));
    }

    /**
     * Assigns an issue to a user.
     *
     * @param issueKey Issue key
     * @param accountId User's account ID
     * @return Promise completing when assigned
     */
    public Promise<Void> assignIssue(String issueKey, String accountId) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/assignee";

        String body = "{\"accountId\":\"" + escapeJson(accountId) + "\"}";

        return httpAdapter.postJson(url, body, authHeaders())
                .map(r -> {
                    metrics.incrementCounter("jira.issue.assigned", "issue", issueKey);
                    return null;
                });
    }

    // ========== Search ==========
    /**
     * Searches issues using JQL.
     *
     * @param jql JQL query
     * @param maxResults Maximum results to return
     * @return Promise of matching issues
     */
    public Promise<List<JiraIssue>> searchIssues(String jql, int maxResults) {
        String url = baseUrl + "/rest/api/3/search";

        String body = "{"
                + "\"jql\":\"" + escapeJson(jql) + "\","
                + "\"maxResults\":" + maxResults + ","
                + "\"fields\":[\"summary\",\"status\",\"assignee\",\"priority\",\"issuetype\"]"
                + "}";

        return httpAdapter.postJson(url, body, authHeaders())
                .map(this::parseSearchResults)
                .whenResult(results -> metrics.incrementCounter("jira.search",
                "results", String.valueOf(results.size())));
    }

    // ========== Helpers ==========
    private Map<String, String> authHeaders() {
        return Map.of(
                "Authorization", authHeader,
                "Content-Type", "application/json",
                "Accept", "application/json"
        );
    }

    private String buildCreateIssueJson(String projectKey, String issueType,
            String summary, String description) {
        return "{"
                + "\"fields\":{"
                + "\"project\":{\"key\":\"" + escapeJson(projectKey) + "\"},"
                + "\"issuetype\":{\"name\":\"" + escapeJson(issueType) + "\"},"
                + "\"summary\":\"" + escapeJson(summary) + "\","
                + "\"description\":{"
                + "\"type\":\"doc\","
                + "\"version\":1,"
                + "\"content\":[{"
                + "\"type\":\"paragraph\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + escapeJson(description) + "\"}]"
                + "}]"
                + "}"
                + "}"
                + "}";
    }

    private String buildUpdateIssueJson(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{\"fields\":{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else {
                sb.append(value);
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    private String buildCommentJson(String comment) {
        return "{"
                + "\"body\":{"
                + "\"type\":\"doc\","
                + "\"version\":1,"
                + "\"content\":[{"
                + "\"type\":\"paragraph\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + escapeJson(comment) + "\"}]"
                + "}]"
                + "}"
                + "}";
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

    private JiraIssue parseIssue(String json) {
        return new JiraIssue(
                extractString(json, "id"),
                extractString(json, "key"),
                extractString(json, "fields", "summary"),
                extractString(json, "fields", "status", "name"),
                extractString(json, "fields", "issuetype", "name"),
                extractString(json, "fields", "priority", "name")
        );
    }

    private JiraComment parseComment(String json) {
        return new JiraComment(
                extractString(json, "id"),
                extractString(json, "body", "content", "0", "content", "0", "text"),
                extractString(json, "author", "displayName")
        );
    }

    private List<JiraIssue> parseSearchResults(String json) {
        List<JiraIssue> results = new java.util.ArrayList<>();

        // Simple parsing - find "issues" array and extract each issue
        int issuesStart = json.indexOf("\"issues\":[");
        if (issuesStart < 0) {
            return results;
        }

        int arrayStart = json.indexOf("[", issuesStart);
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd < 0) {
            return results;
        }

        String issuesArray = json.substring(arrayStart + 1, arrayEnd);

        // Split by issue objects (simplified)
        int depth = 0;
        int issueStart = -1;
        for (int i = 0; i < issuesArray.length(); i++) {
            char c = issuesArray.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    issueStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && issueStart >= 0) {
                    String issueJson = issuesArray.substring(issueStart, i + 1);
                    results.add(parseIssue(issueJson));
                    issueStart = -1;
                }
            }
        }

        return results;
    }

    private String findTransitionId(String json, String statusName) {
        // Look for transition with matching name
        String lowerStatus = statusName.toLowerCase();
        int idx = 0;
        while (true) {
            int nameIdx = json.indexOf("\"name\"", idx);
            if (nameIdx < 0) {
                break;
            }

            int colonIdx = json.indexOf(":", nameIdx);
            int valueStart = json.indexOf("\"", colonIdx + 1);
            int valueEnd = json.indexOf("\"", valueStart + 1);
            if (valueStart < 0 || valueEnd < 0) {
                break;
            }

            String name = json.substring(valueStart + 1, valueEnd);
            if (name.toLowerCase().contains(lowerStatus)) {
                // Found it, now find the id
                int searchStart = Math.max(0, nameIdx - 200);
                int idIdx = json.lastIndexOf("\"id\"", nameIdx);
                if (idIdx >= searchStart) {
                    int idColon = json.indexOf(":", idIdx);
                    int idValueStart = json.indexOf("\"", idColon + 1);
                    int idValueEnd = json.indexOf("\"", idValueStart + 1);
                    if (idValueStart >= 0 && idValueEnd >= 0) {
                        return json.substring(idValueStart + 1, idValueEnd);
                    }
                }
            }
            idx = valueEnd;
        }
        return null;
    }

    private int findMatchingBracket(String json, int start) {
        if (start < 0 || start >= json.length()) {
            return -1;
        }
        char openChar = json.charAt(start);
        char closeChar = openChar == '[' ? ']' : '}';

        int depth = 1;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
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

            // Skip whitespace
            int valueStart = colonIdx + 1;
            while (valueStart < current.length()
                    && Character.isWhitespace(current.charAt(valueStart))) {
                valueStart++;
            }

            if (valueStart >= current.length()) {
                return "";
            }

            char startChar = current.charAt(valueStart);
            if (startChar == '"') {
                int valueEnd = current.indexOf("\"", valueStart + 1);
                if (valueEnd < 0) {
                    return "";
                }
                current = current.substring(valueStart + 1, valueEnd);
            } else if (startChar == '{' || startChar == '[') {
                int matchEnd = findMatchingBracket(current, valueStart);
                if (matchEnd < 0) {
                    return "";
                }
                current = current.substring(valueStart, matchEnd + 1);
            } else {
                return "";
            }
        }
        return current;
    }

    // ========== Value Objects ==========
    public record JiraIssue(
            String id,
            String key,
            String summary,
            String status,
            String issueType,
            String priority
    ) {
    }

    public record JiraComment(
            String id,
            String body,
            String author
    ) {
    }
}
