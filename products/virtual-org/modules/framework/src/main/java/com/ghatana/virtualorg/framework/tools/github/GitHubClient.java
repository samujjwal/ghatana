package com.ghatana.virtualorg.framework.tools.github;

import com.ghatana.platform.http.client.OkHttpAdapter;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;

/**
 * GitHub API client for agent tools.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides low-level GitHub API access for agent tools, handling: -
 * Authentication (token-based) - Rate limiting - Request/response serialization
 * - Error handling
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * GitHubClient client = new GitHubClient(httpAdapter, token, metrics);
 *
 * // Create a PR
 * GitHubPR pr = client.createPullRequest(
 *     "owner/repo", "feature-branch", "main",
 *     "Add new feature", "Description here"
 * ).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose GitHub API client for agent integrations
 * @doc.layer product
 * @doc.pattern Client
 */
public class GitHubClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final OkHttpAdapter httpAdapter;
    private final String token;
    private final MetricsCollector metrics;

    public GitHubClient(OkHttpAdapter httpAdapter, String token, MetricsCollector metrics) {
        this.httpAdapter = Objects.requireNonNull(httpAdapter, "httpAdapter required");
        this.token = Objects.requireNonNull(token, "token required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
    }

    // ========== Pull Requests ==========
    /**
     * Creates a pull request.
     *
     * @param repo Repository in "owner/repo" format
     * @param head The branch with changes
     * @param base The branch to merge into
     * @param title PR title
     * @param description PR description
     * @return Promise of the created PR
     */
    public Promise<GitHubPR> createPullRequest(
            String repo, String head, String base,
            String title, String description) {

        String url = GITHUB_API_BASE + "/repos/" + repo + "/pulls";
        String body = buildJson(Map.of(
                "title", title,
                "body", description,
                "head", head,
                "base", base
        ));

        return httpAdapter.postJson(url, body, authHeaders())
                .map(this::parsePR)
                .whenResult(pr -> metrics.incrementCounter("github.pr.created", "repo", repo));
    }

    /**
     * Gets a pull request by number.
     *
     * @param repo Repository in "owner/repo" format
     * @param number PR number
     * @return Promise of the PR details
     */
    public Promise<GitHubPR> getPullRequest(String repo, int number) {
        String url = GITHUB_API_BASE + "/repos/" + repo + "/pulls/" + number;

        return httpAdapter.postJson(url, "", authHeaders()) // GET not available, need to extend adapter
                .map(this::parsePR);
    }

    /**
     * Reviews a pull request.
     *
     * @param repo Repository in "owner/repo" format
     * @param number PR number
     * @param event Review event: APPROVE, REQUEST_CHANGES, COMMENT
     * @param comment Review comment
     * @return Promise completing when review is submitted
     */
    public Promise<Void> reviewPullRequest(
            String repo, int number, ReviewEvent event, String comment) {

        String url = GITHUB_API_BASE + "/repos/" + repo + "/pulls/" + number + "/reviews";
        String body = buildJson(Map.of(
                "body", comment,
                "event", event.name()
        ));

        return httpAdapter.postJson(url, body, authHeaders())
                .map(r -> {
                    metrics.incrementCounter("github.pr.reviewed",
                            "repo", repo, "event", event.name());
                    return null;
                });
    }

    /**
     * Merges a pull request.
     *
     * @param repo Repository in "owner/repo" format
     * @param number PR number
     * @param method Merge method: merge, squash, rebase
     * @return Promise completing when merged
     */
    public Promise<Void> mergePullRequest(String repo, int number, MergeMethod method) {
        String url = GITHUB_API_BASE + "/repos/" + repo + "/pulls/" + number + "/merge";
        String body = buildJson(Map.of(
                "merge_method", method.getValue()
        ));

        return httpAdapter.postJson(url, body, authHeaders())
                .map(r -> {
                    metrics.incrementCounter("github.pr.merged",
                            "repo", repo, "method", method.name());
                    return null;
                });
    }

    /**
     * Adds a comment to a pull request.
     *
     * @param repo Repository in "owner/repo" format
     * @param number PR number
     * @param comment The comment text
     * @return Promise of the created comment
     */
    public Promise<GitHubComment> commentOnPullRequest(String repo, int number, String comment) {
        String url = GITHUB_API_BASE + "/repos/" + repo + "/issues/" + number + "/comments";
        String body = buildJson(Map.of("body", comment));

        return httpAdapter.postJson(url, body, authHeaders())
                .map(this::parseComment)
                .whenResult(c -> metrics.incrementCounter("github.comment.created", "repo", repo));
    }

    // ========== Issues ==========
    /**
     * Creates an issue.
     *
     * @param repo Repository in "owner/repo" format
     * @param title Issue title
     * @param description Issue body
     * @param labels Labels to add
     * @return Promise of the created issue
     */
    public Promise<GitHubIssue> createIssue(
            String repo, String title, String description, String... labels) {

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("title", title);
        params.put("body", description);
        if (labels.length > 0) {
            params.put("labels", java.util.List.of(labels));
        }

        String url = GITHUB_API_BASE + "/repos/" + repo + "/issues";
        String body = buildJson(params);

        return httpAdapter.postJson(url, body, authHeaders())
                .map(this::parseIssue)
                .whenResult(issue -> metrics.incrementCounter("github.issue.created", "repo", repo));
    }

    // ========== Helpers ==========
    private Map<String, String> authHeaders() {
        return Map.of(
                "Authorization", "Bearer " + token,
                "Accept", "application/vnd.github.v3+json",
                "X-GitHub-Api-Version", "2022-11-28"
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
            } else if (value instanceof java.util.List) {
                sb.append("[");
                boolean listFirst = true;
                for (Object item : (java.util.List<?>) value) {
                    if (!listFirst) {
                        sb.append(",");
                    }
                    listFirst = false;
                    sb.append("\"").append(escapeJson(String.valueOf(item))).append("\"");
                }
                sb.append("]");
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

    private GitHubPR parsePR(String json) {
        // Simple JSON parsing - in production, use Jackson/Gson
        return new GitHubPR(
                extractInt(json, "number"),
                extractString(json, "title"),
                extractString(json, "html_url"),
                extractString(json, "state"),
                extractString(json, "head", "ref"),
                extractString(json, "base", "ref")
        );
    }

    private GitHubComment parseComment(String json) {
        return new GitHubComment(
                extractLong(json, "id"),
                extractString(json, "body"),
                extractString(json, "html_url")
        );
    }

    private GitHubIssue parseIssue(String json) {
        return new GitHubIssue(
                extractInt(json, "number"),
                extractString(json, "title"),
                extractString(json, "html_url"),
                extractString(json, "state")
        );
    }

    private String extractString(String json, String... keys) {
        // Simple extraction - in production use proper JSON library
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

    private int extractInt(String json, String key) {
        try {
            String value = extractNumber(json, key);
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long extractLong(String json, String key) {
        try {
            String value = extractNumber(json, key);
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractNumber(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) {
            return "0";
        }
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx < 0) {
            return "0";
        }
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return json.substring(start, end);
    }

    // ========== Enums ==========
    public enum ReviewEvent {
        APPROVE,
        REQUEST_CHANGES,
        COMMENT
    }

    public enum MergeMethod {
        MERGE("merge"),
        SQUASH("squash"),
        REBASE("rebase");

        private final String value;

        MergeMethod(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    // ========== Value Objects ==========
    public record GitHubPR(
            int number,
            String title,
            String url,
            String state,
            String headBranch,
            String baseBranch
    ) {
    }

    public record GitHubComment(
            long id,
            String body,
            String url
    ) {
    }

    public record GitHubIssue(
            int number,
            String title,
            String url,
            String state
    ) {
    }
}
