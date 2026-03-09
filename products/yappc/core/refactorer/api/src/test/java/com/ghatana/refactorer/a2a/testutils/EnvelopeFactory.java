package com.ghatana.refactorer.a2a.testutils;

import com.ghatana.refactorer.a2a.Envelope;
import com.ghatana.refactorer.a2a.EnvelopeTypes;
import java.util.List;
import java.util.Map;

/**
 * Test data factory for A2A envelopes. Provides builders and sample data for testing A2A
 * communication.
 
 * @doc.type class
 * @doc.purpose Handles envelope factory operations
 * @doc.layer core
 * @doc.pattern Factory
*/
public final class EnvelopeFactory {

    private EnvelopeFactory() {
        // Utility class
    }

    /** Creates a sample task request envelope. */
    public static Envelope sampleTaskRequest() {
        Map<String, Object> payload =
                Map.of(
                        "operation",
                        "run",
                        "repoRoot",
                        "/tmp/test-repo",
                        "languages",
                        List.of("java", "typescript"),
                        "formatters",
                        true);

        return Envelope.request("req-123", "corr-123", payload);
    }

    /** Creates a sample task response envelope. */
    public static Envelope sampleTaskResponse() {
        Map<String, Object> payload =
                Map.of(
                        "status", "ACCEPTED",
                        "jobId", "job-123");

        return Envelope.response("resp-123", "corr-123", payload);
    }

    /** Creates a sample progress envelope. */
    public static Envelope sampleProgressEvent() {
        Map<String, Object> payload =
                Map.of(
                        "jobId", "job-123",
                        "eventType", "progress",
                        "message", "Processing step 1",
                        "currentPass", 1,
                        "totalPasses", 3);

        return Envelope.progress("prog-123", "corr-123", payload);
    }

    /** Creates a sample error envelope. */
    public static Envelope sampleErrorEvent() {
        return Envelope.error("err-123", "corr-123", "Sample error message");
    }

    /** Creates a sample capabilities envelope. */
    public static Envelope sampleCapabilities() {
        Map<String, Object> capabilities =
                Map.of(
                        "operations", List.of("run", "diagnose", "status", "report"),
                        "languages", List.of("java", "typescript", "python"),
                        "version", "1.0.0");

        return Envelope.capabilities("cap-123", capabilities);
    }

    /** Creates a sample heartbeat envelope. */
    public static Envelope sampleHeartbeat() {
        Map<String, Object> payload = Map.of("timestamp", System.currentTimeMillis());
        return Envelope.create(EnvelopeTypes.HEARTBEAT, "hb-123", null, payload);
    }

    /** Builder for custom task request envelopes. */
    public static TaskRequestBuilder taskRequest() {
        return new TaskRequestBuilder();
    }

    /** Builder for custom task response envelopes. */
    public static TaskResponseBuilder taskResponse() {
        return new TaskResponseBuilder();
    }

    public static class TaskRequestBuilder {
        private String id = "req-123";
        private String correlationId = "corr-123";
        private String operation = "run";
        private String repoRoot = "/tmp/test-repo";
        private List<String> languages = List.of("java");
        private boolean formatters = true;

        public TaskRequestBuilder id(String id) {
            this.id = id;
            return this;
        }

        public TaskRequestBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public TaskRequestBuilder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public TaskRequestBuilder repoRoot(String repoRoot) {
            this.repoRoot = repoRoot;
            return this;
        }

        public TaskRequestBuilder languages(String... languages) {
            this.languages = List.of(languages);
            return this;
        }

        public TaskRequestBuilder formatters(boolean formatters) {
            this.formatters = formatters;
            return this;
        }

        public Envelope build() {
            Map<String, Object> payload =
                    Map.of(
                            "operation", operation,
                            "repoRoot", repoRoot,
                            "languages", languages,
                            "formatters", formatters);

            return Envelope.request(id, correlationId, payload);
        }
    }

    public static class TaskResponseBuilder {
        private String id = "resp-123";
        private String correlationId = "corr-123";
        private String status = "ACCEPTED";
        private String jobId = "job-123";

        public TaskResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public TaskResponseBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public TaskResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public TaskResponseBuilder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Envelope build() {
            Map<String, Object> payload =
                    Map.of(
                            "status", status,
                            "jobId", jobId);

            return Envelope.response(id, correlationId, payload);
        }
    }
}
