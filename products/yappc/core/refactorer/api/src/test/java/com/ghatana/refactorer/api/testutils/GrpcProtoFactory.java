package com.ghatana.refactorer.api.testutils;

import com.ghatana.refactorer.api.v1.Budget;
import com.ghatana.refactorer.api.v1.DiagnoseRequest;
import com.ghatana.refactorer.api.v1.DiagnoseResponse;
import com.ghatana.refactorer.api.v1.HealthResponse;
import com.ghatana.refactorer.api.v1.JobId;
import com.ghatana.refactorer.api.v1.Language;
import com.ghatana.refactorer.api.v1.PolicyKV;
import com.ghatana.refactorer.api.v1.ProgressEvent;
import com.ghatana.refactorer.api.v1.Report;
import com.ghatana.refactorer.api.v1.RunRequest;
import com.ghatana.refactorer.api.v1.RunStatus;
import com.ghatana.refactorer.api.v1.UnifiedDiagnostic;
import java.time.Instant;

/**
 * Test data factory for gRPC proto messages. Provides builders and sample data for testing proto
 * round-trips.
 
 * @doc.type class
 * @doc.purpose Handles grpc proto factory operations
 * @doc.layer core
 * @doc.pattern Factory
*/
public final class GrpcProtoFactory {

    private GrpcProtoFactory() {
        // Utility class
    }

    /** Creates a sample DiagnoseRequest for testing. */
    public static DiagnoseRequest sampleDiagnoseRequest() {
        return DiagnoseRequest.newBuilder()
                .setRepoRoot("/tmp/test-repo")
                .addIncludeGlobs("**/*.java")
                .addIncludeGlobs("**/*.ts")
                .addLanguages(Language.newBuilder().setId("java").build())
                .addLanguages(Language.newBuilder().setId("typescript").build())
                .addPolicies(
                        PolicyKV.newBuilder()
                                .setKey("java.allowTemporaryAny")
                                .setValue("false")
                                .build())
                .setBudget(
                        Budget.newBuilder()
                                .setMaxPasses(3)
                                .setMaxEditsPerFile(10)
                                .setTimeoutSeconds(300)
                                .build())
                .setFormatters(true)
                .setTenantId("test-tenant")
                .build();
    }

    /** Creates a sample UnifiedDiagnostic for testing. */
    public static UnifiedDiagnostic sampleDiagnostic() {
        return UnifiedDiagnostic.newBuilder()
                .setTool("javac")
                .setRule("missing-import")
                .setMessage("Cannot find symbol: List")
                .setFile("/tmp/test-repo/src/Main.java")
                .setLine(10)
                .setColumn(5)
                .setSeverity("ERROR")
                .setSuggestion("import java.util.List")
                .setCategory("symbol:List")
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /** Creates a sample DiagnoseResponse for testing. */
    public static DiagnoseResponse sampleDiagnoseResponse() {
        return DiagnoseResponse.newBuilder()
                .addDiagnostics(sampleDiagnostic())
                .setExecutionId("exec-123")
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /** Creates a sample RunRequest for testing. */
    public static RunRequest sampleRunRequest() {
        return RunRequest.newBuilder()
                .setConfig(sampleDiagnoseRequest())
                .setIdempotencyKey("idem-key-123")
                .setDryRun(false)
                .build();
    }

    /** Creates a sample JobId for testing. */
    public static JobId sampleJobId() {
        return JobId.newBuilder().setId("job-123").build();
    }

    /** Creates a sample RunStatus for testing. */
    public static RunStatus sampleRunStatus() {
        return RunStatus.newBuilder()
                .setJobId("job-123")
                .setState("RUNNING")
                .setPass(1)
                .setStartedAt(Instant.now().toString())
                .setUpdatedAt(Instant.now().toString())
                .putToolVersions("javac", "21.0.1")
                .putToolVersions("eslint", "8.45.0")
                .build();
    }

    /** Creates a sample Report for testing. */
    public static Report sampleReport() {
        return Report.newBuilder()
                .setJobId("job-123")
                .setSummaryJson("{\"diagnostics_found\": 5, \"fixes_applied\": 3}")
                .setDetailsJson("{}")
                .setGeneratedAt(Instant.now().toString())
                .build();
    }

    /** Creates a sample ProgressEvent for testing. */
    public static ProgressEvent sampleProgressEvent() {
        return ProgressEvent.newBuilder()
                .setJobId("job-123")
                .setEventType("diagnostic")
                .setMessage("Found missing import")
                .setCurrentPass(1)
                .setTotalPasses(3)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /** Creates a sample HealthResponse for testing. */
    public static HealthResponse sampleHealthResponse() {
        return HealthResponse.newBuilder()
                .setStatus("UP")
                .putDetails("version", "1.0.0")
                .putDetails("uptime", "3600")
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    /** Builder for custom DiagnoseRequest instances. */
    public static DiagnoseRequestBuilder diagnoseRequest() {
        return new DiagnoseRequestBuilder();
    }

    /** Builder for custom UnifiedDiagnostic instances. */
    public static UnifiedDiagnosticBuilder diagnostic() {
        return new UnifiedDiagnosticBuilder();
    }

    public static class DiagnoseRequestBuilder {
        private final DiagnoseRequest.Builder builder = DiagnoseRequest.newBuilder();

        public DiagnoseRequestBuilder repoRoot(String repoRoot) {
            builder.setRepoRoot(repoRoot);
            return this;
        }

        public DiagnoseRequestBuilder includeGlobs(String... globs) {
            for (String glob : globs) {
                builder.addIncludeGlobs(glob);
            }
            return this;
        }

        public DiagnoseRequestBuilder languages(String... languageIds) {
            for (String id : languageIds) {
                builder.addLanguages(Language.newBuilder().setId(id).build());
            }
            return this;
        }

        public DiagnoseRequestBuilder tenantId(String tenantId) {
            builder.setTenantId(tenantId);
            return this;
        }

        public DiagnoseRequest build() {
            return builder.build();
        }
    }

    public static class UnifiedDiagnosticBuilder {
        private final UnifiedDiagnostic.Builder builder = UnifiedDiagnostic.newBuilder();

        public UnifiedDiagnosticBuilder tool(String tool) {
            builder.setTool(tool);
            return this;
        }

        public UnifiedDiagnosticBuilder rule(String rule) {
            builder.setRule(rule);
            return this;
        }

        public UnifiedDiagnosticBuilder message(String message) {
            builder.setMessage(message);
            return this;
        }

        public UnifiedDiagnosticBuilder file(String file) {
            builder.setFile(file);
            return this;
        }

        public UnifiedDiagnosticBuilder location(int line, int column) {
            builder.setLine(line);
            builder.setColumn(column);
            return this;
        }

        public UnifiedDiagnosticBuilder severity(String severity) {
            builder.setSeverity(severity);
            return this;
        }

        public UnifiedDiagnosticBuilder meta(String key, String value) {
            if ("suggestion".equals(key)) {
                builder.setSuggestion(value);
            } else {
                builder.setCategory(key + "=" + value);
            }
            return this;
        }

        public UnifiedDiagnostic build() {
            return builder.setTimestamp(Instant.now().toEpochMilli()).build();
        }
    }
}
