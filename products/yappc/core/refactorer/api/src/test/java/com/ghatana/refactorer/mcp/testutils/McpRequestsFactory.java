package com.ghatana.refactorer.mcp.testutils;

import java.util.List;
import java.util.Map;

/**
 * Test data factory for MCP requests and responses. Provides builders and sample data for testing
 * MCP tool calls.
 
 * @doc.type class
 * @doc.purpose Handles mcp requests factory operations
 * @doc.layer core
 * @doc.pattern Factory
*/
public final class McpRequestsFactory {

    private McpRequestsFactory() {
        // Utility class
    }

    /** Creates a sample MCP run request. */
    public static Map<String, Object> sampleRunRequest() {
        return Map.of(
                "repoRoot",
                "/tmp/test-repo",
                "languages",
                List.of("java", "typescript"),
                "policies",
                Map.of("java.allowTemporaryAny", "false"),
                "budgets",
                Map.of(
                        "maxPasses", 3,
                        "maxEditsPerFile", 10,
                        "timeoutSeconds", 300),
                "formatters",
                true,
                "idempotencyKey",
                "test-key-123");
    }

    /** Creates a sample MCP diagnose request. */
    public static Map<String, Object> sampleDiagnoseRequest() {
        return Map.of("repoRoot", "/tmp/test-repo", "languages", List.of("java", "python"));
    }

    /** Creates a sample MCP status request. */
    public static Map<String, Object> sampleStatusRequest() {
        return Map.of("jobId", "job-123");
    }

    /** Creates a sample MCP report request. */
    public static Map<String, Object> sampleReportRequest() {
        return Map.of("jobId", "job-123");
    }

    /** Builder for custom MCP run requests. */
    public static RunRequestBuilder runRequest() {
        return new RunRequestBuilder();
    }

    /** Builder for custom MCP diagnose requests. */
    public static DiagnoseRequestBuilder diagnoseRequest() {
        return new DiagnoseRequestBuilder();
    }

    public static class RunRequestBuilder {
        private String repoRoot = "/tmp/test-repo";
        private List<String> languages = List.of("java");
        private Map<String, Object> policies = Map.of();
        private Map<String, Object> budgets = Map.of("maxPasses", 3);
        private boolean formatters = true;
        private String idempotencyKey = "test-key";

        public RunRequestBuilder repoRoot(String repoRoot) {
            this.repoRoot = repoRoot;
            return this;
        }

        public RunRequestBuilder languages(String... languages) {
            this.languages = List.of(languages);
            return this;
        }

        public RunRequestBuilder policies(Map<String, Object> policies) {
            this.policies = policies;
            return this;
        }

        public RunRequestBuilder budgets(Map<String, Object> budgets) {
            this.budgets = budgets;
            return this;
        }

        public RunRequestBuilder formatters(boolean formatters) {
            this.formatters = formatters;
            return this;
        }

        public RunRequestBuilder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Map<String, Object> build() {
            return Map.of(
                    "repoRoot", repoRoot,
                    "languages", languages,
                    "policies", policies,
                    "budgets", budgets,
                    "formatters", formatters,
                    "idempotencyKey", idempotencyKey);
        }
    }

    public static class DiagnoseRequestBuilder {
        private String repoRoot = "/tmp/test-repo";
        private List<String> languages = List.of("java");

        public DiagnoseRequestBuilder repoRoot(String repoRoot) {
            this.repoRoot = repoRoot;
            return this;
        }

        public DiagnoseRequestBuilder languages(String... languages) {
            this.languages = List.of(languages);
            return this;
        }

        public Map<String, Object> build() {
            return Map.of(
                    "repoRoot", repoRoot,
                    "languages", languages);
        }
    }
}
