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

    private McpRequestsFactory() { // GH-90000
        // Utility class
    }

    /** Creates a sample MCP run request. */
    public static Map<String, Object> sampleRunRequest() { // GH-90000
        return Map.of( // GH-90000
                "repoRoot",
                "/tmp/test-repo",
                "languages",
                List.of("java", "typescript"), // GH-90000
                "policies",
                Map.of("java.allowTemporaryAny", "false"), // GH-90000
                "budgets",
                Map.of( // GH-90000
                        "maxPasses", 3,
                        "maxEditsPerFile", 10,
                        "timeoutSeconds", 300),
                "formatters",
                true,
                "idempotencyKey",
                "test-key-123");
    }

    /** Creates a sample MCP diagnose request. */
    public static Map<String, Object> sampleDiagnoseRequest() { // GH-90000
        return Map.of("repoRoot", "/tmp/test-repo", "languages", List.of("java", "python")); // GH-90000
    }

    /** Creates a sample MCP status request. */
    public static Map<String, Object> sampleStatusRequest() { // GH-90000
        return Map.of("jobId", "job-123"); // GH-90000
    }

    /** Creates a sample MCP report request. */
    public static Map<String, Object> sampleReportRequest() { // GH-90000
        return Map.of("jobId", "job-123"); // GH-90000
    }

    /** Builder for custom MCP run requests. */
    public static RunRequestBuilder runRequest() { // GH-90000
        return new RunRequestBuilder(); // GH-90000
    }

    /** Builder for custom MCP diagnose requests. */
    public static DiagnoseRequestBuilder diagnoseRequest() { // GH-90000
        return new DiagnoseRequestBuilder(); // GH-90000
    }

    public static class RunRequestBuilder {
        private String repoRoot = "/tmp/test-repo";
        private List<String> languages = List.of("java");
        private Map<String, Object> policies = Map.of(); // GH-90000
        private Map<String, Object> budgets = Map.of("maxPasses", 3); // GH-90000
        private boolean formatters = true;
        private String idempotencyKey = "test-key";

        public RunRequestBuilder repoRoot(String repoRoot) { // GH-90000
            this.repoRoot = repoRoot;
            return this;
        }

        public RunRequestBuilder languages(String... languages) { // GH-90000
            this.languages = List.of(languages); // GH-90000
            return this;
        }

        public RunRequestBuilder policies(Map<String, Object> policies) { // GH-90000
            this.policies = policies;
            return this;
        }

        public RunRequestBuilder budgets(Map<String, Object> budgets) { // GH-90000
            this.budgets = budgets;
            return this;
        }

        public RunRequestBuilder formatters(boolean formatters) { // GH-90000
            this.formatters = formatters;
            return this;
        }

        public RunRequestBuilder idempotencyKey(String idempotencyKey) { // GH-90000
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Map<String, Object> build() { // GH-90000
            return Map.of( // GH-90000
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

        public DiagnoseRequestBuilder repoRoot(String repoRoot) { // GH-90000
            this.repoRoot = repoRoot;
            return this;
        }

        public DiagnoseRequestBuilder languages(String... languages) { // GH-90000
            this.languages = List.of(languages); // GH-90000
            return this;
        }

        public Map<String, Object> build() { // GH-90000
            return Map.of( // GH-90000
                    "repoRoot", repoRoot,
                    "languages", languages);
        }
    }
}
