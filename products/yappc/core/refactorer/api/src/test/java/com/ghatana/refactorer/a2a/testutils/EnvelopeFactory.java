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

    private EnvelopeFactory() { // GH-90000
        // Utility class
    }

    /** Creates a sample task request envelope. */
    public static Envelope sampleTaskRequest() { // GH-90000
        Map<String, Object> payload =
                Map.of( // GH-90000
                        "operation",
                        "run",
                        "repoRoot",
                        "/tmp/test-repo",
                        "languages",
                        List.of("java", "typescript"), // GH-90000
                        "formatters",
                        true);

        return Envelope.request("req-123", "corr-123", payload); // GH-90000
    }

    /** Creates a sample task response envelope. */
    public static Envelope sampleTaskResponse() { // GH-90000
        Map<String, Object> payload =
                Map.of( // GH-90000
                        "status", "ACCEPTED",
                        "jobId", "job-123");

        return Envelope.response("resp-123", "corr-123", payload); // GH-90000
    }

    /** Creates a sample progress envelope. */
    public static Envelope sampleProgressEvent() { // GH-90000
        Map<String, Object> payload =
                Map.of( // GH-90000
                        "jobId", "job-123",
                        "eventType", "progress",
                        "message", "Processing step 1",
                        "currentPass", 1,
                        "totalPasses", 3);

        return Envelope.progress("prog-123", "corr-123", payload); // GH-90000
    }

    /** Creates a sample error envelope. */
    public static Envelope sampleErrorEvent() { // GH-90000
        return Envelope.error("err-123", "corr-123", "Sample error message"); // GH-90000
    }

    /** Creates a sample capabilities envelope. */
    public static Envelope sampleCapabilities() { // GH-90000
        Map<String, Object> capabilities =
                Map.of( // GH-90000
                        "operations", List.of("run", "diagnose", "status", "report"), // GH-90000
                        "languages", List.of("java", "typescript", "python"), // GH-90000
                        "version", "1.0.0");

        return Envelope.capabilities("cap-123", capabilities); // GH-90000
    }

    /** Creates a sample heartbeat envelope. */
    public static Envelope sampleHeartbeat() { // GH-90000
        Map<String, Object> payload = Map.of("timestamp", System.currentTimeMillis()); // GH-90000
        return Envelope.create(EnvelopeTypes.HEARTBEAT, "hb-123", null, payload); // GH-90000
    }

    /** Builder for custom task request envelopes. */
    public static TaskRequestBuilder taskRequest() { // GH-90000
        return new TaskRequestBuilder(); // GH-90000
    }

    /** Builder for custom task response envelopes. */
    public static TaskResponseBuilder taskResponse() { // GH-90000
        return new TaskResponseBuilder(); // GH-90000
    }

    public static class TaskRequestBuilder {
        private String id = "req-123";
        private String correlationId = "corr-123";
        private String operation = "run";
        private String repoRoot = "/tmp/test-repo";
        private List<String> languages = List.of("java [GH-90000]");
        private boolean formatters = true;

        public TaskRequestBuilder id(String id) { // GH-90000
            this.id = id;
            return this;
        }

        public TaskRequestBuilder correlationId(String correlationId) { // GH-90000
            this.correlationId = correlationId;
            return this;
        }

        public TaskRequestBuilder operation(String operation) { // GH-90000
            this.operation = operation;
            return this;
        }

        public TaskRequestBuilder repoRoot(String repoRoot) { // GH-90000
            this.repoRoot = repoRoot;
            return this;
        }

        public TaskRequestBuilder languages(String... languages) { // GH-90000
            this.languages = List.of(languages); // GH-90000
            return this;
        }

        public TaskRequestBuilder formatters(boolean formatters) { // GH-90000
            this.formatters = formatters;
            return this;
        }

        public Envelope build() { // GH-90000
            Map<String, Object> payload =
                    Map.of( // GH-90000
                            "operation", operation,
                            "repoRoot", repoRoot,
                            "languages", languages,
                            "formatters", formatters);

            return Envelope.request(id, correlationId, payload); // GH-90000
        }
    }

    public static class TaskResponseBuilder {
        private String id = "resp-123";
        private String correlationId = "corr-123";
        private String status = "ACCEPTED";
        private String jobId = "job-123";

        public TaskResponseBuilder id(String id) { // GH-90000
            this.id = id;
            return this;
        }

        public TaskResponseBuilder correlationId(String correlationId) { // GH-90000
            this.correlationId = correlationId;
            return this;
        }

        public TaskResponseBuilder status(String status) { // GH-90000
            this.status = status;
            return this;
        }

        public TaskResponseBuilder jobId(String jobId) { // GH-90000
            this.jobId = jobId;
            return this;
        }

        public Envelope build() { // GH-90000
            Map<String, Object> payload =
                    Map.of( // GH-90000
                            "status", status,
                            "jobId", jobId);

            return Envelope.response(id, correlationId, payload); // GH-90000
        }
    }
}
