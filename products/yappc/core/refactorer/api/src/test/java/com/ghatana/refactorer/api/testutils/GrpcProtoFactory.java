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

    private GrpcProtoFactory() { // GH-90000
        // Utility class
    }

    /** Creates a sample DiagnoseRequest for testing. */
    public static DiagnoseRequest sampleDiagnoseRequest() { // GH-90000
        return DiagnoseRequest.newBuilder() // GH-90000
                .setRepoRoot("/tmp/test-repo [GH-90000]")
                .addIncludeGlobs("**/*.java [GH-90000]")
                .addIncludeGlobs("**/*.ts [GH-90000]")
                .addLanguages(Language.newBuilder().setId("java [GH-90000]").build())
                .addLanguages(Language.newBuilder().setId("typescript [GH-90000]").build())
                .addPolicies( // GH-90000
                        PolicyKV.newBuilder() // GH-90000
                                .setKey("java.allowTemporaryAny [GH-90000]")
                                .setValue("false [GH-90000]")
                                .build()) // GH-90000
                .setBudget( // GH-90000
                        Budget.newBuilder() // GH-90000
                                .setMaxPasses(3) // GH-90000
                                .setMaxEditsPerFile(10) // GH-90000
                                .setTimeoutSeconds(300) // GH-90000
                                .build()) // GH-90000
                .setFormatters(true) // GH-90000
                .setTenantId("test-tenant [GH-90000]")
                .build(); // GH-90000
    }

    /** Creates a sample UnifiedDiagnostic for testing. */
    public static UnifiedDiagnostic sampleDiagnostic() { // GH-90000
        return UnifiedDiagnostic.newBuilder() // GH-90000
                .setTool("javac [GH-90000]")
                .setRule("missing-import [GH-90000]")
                .setMessage("Cannot find symbol: List [GH-90000]")
                .setFile("/tmp/test-repo/src/Main.java [GH-90000]")
                .setLine(10) // GH-90000
                .setColumn(5) // GH-90000
                .setSeverity("ERROR [GH-90000]")
                .setSuggestion("import java.util.List [GH-90000]")
                .setCategory("symbol:List [GH-90000]")
                .setTimestamp(Instant.now().toEpochMilli()) // GH-90000
                .build(); // GH-90000
    }

    /** Creates a sample DiagnoseResponse for testing. */
    public static DiagnoseResponse sampleDiagnoseResponse() { // GH-90000
        return DiagnoseResponse.newBuilder() // GH-90000
                .addDiagnostics(sampleDiagnostic()) // GH-90000
                .setExecutionId("exec-123 [GH-90000]")
                .setTimestamp(Instant.now().toEpochMilli()) // GH-90000
                .build(); // GH-90000
    }

    /** Creates a sample RunRequest for testing. */
    public static RunRequest sampleRunRequest() { // GH-90000
        return RunRequest.newBuilder() // GH-90000
                .setConfig(sampleDiagnoseRequest()) // GH-90000
                .setIdempotencyKey("idem-key-123 [GH-90000]")
                .setDryRun(false) // GH-90000
                .build(); // GH-90000
    }

    /** Creates a sample JobId for testing. */
    public static JobId sampleJobId() { // GH-90000
        return JobId.newBuilder().setId("job-123 [GH-90000]").build();
    }

    /** Creates a sample RunStatus for testing. */
    public static RunStatus sampleRunStatus() { // GH-90000
        return RunStatus.newBuilder() // GH-90000
                .setJobId("job-123 [GH-90000]")
                .setState("RUNNING [GH-90000]")
                .setPass(1) // GH-90000
                .setStartedAt(Instant.now().toString()) // GH-90000
                .setUpdatedAt(Instant.now().toString()) // GH-90000
                .putToolVersions("javac", "21.0.1") // GH-90000
                .putToolVersions("eslint", "8.45.0") // GH-90000
                .build(); // GH-90000
    }

    /** Creates a sample Report for testing. */
    public static Report sampleReport() { // GH-90000
        return Report.newBuilder() // GH-90000
                .setJobId("job-123 [GH-90000]")
                .setSummaryJson("{\"diagnostics_found\": 5, \"fixes_applied\": 3}") // GH-90000
                .setDetailsJson("{} [GH-90000]")
                .setGeneratedAt(Instant.now().toString()) // GH-90000
                .build(); // GH-90000
    }

    /** Creates a sample ProgressEvent for testing. */
    public static ProgressEvent sampleProgressEvent() { // GH-90000
        return ProgressEvent.newBuilder() // GH-90000
                .setJobId("job-123 [GH-90000]")
                .setEventType("diagnostic [GH-90000]")
                .setMessage("Found missing import [GH-90000]")
                .setCurrentPass(1) // GH-90000
                .setTotalPasses(3) // GH-90000
                .setTimestamp(Instant.now().toEpochMilli()) // GH-90000
                .build(); // GH-90000
    }

    /** Creates a sample HealthResponse for testing. */
    public static HealthResponse sampleHealthResponse() { // GH-90000
        return HealthResponse.newBuilder() // GH-90000
                .setStatus("UP [GH-90000]")
                .putDetails("version", "1.0.0") // GH-90000
                .putDetails("uptime", "3600") // GH-90000
                .setTimestamp(Instant.now().toEpochMilli()) // GH-90000
                .build(); // GH-90000
    }

    /** Builder for custom DiagnoseRequest instances. */
    public static DiagnoseRequestBuilder diagnoseRequest() { // GH-90000
        return new DiagnoseRequestBuilder(); // GH-90000
    }

    /** Builder for custom UnifiedDiagnostic instances. */
    public static UnifiedDiagnosticBuilder diagnostic() { // GH-90000
        return new UnifiedDiagnosticBuilder(); // GH-90000
    }

    public static class DiagnoseRequestBuilder {
        private final DiagnoseRequest.Builder builder = DiagnoseRequest.newBuilder(); // GH-90000

        public DiagnoseRequestBuilder repoRoot(String repoRoot) { // GH-90000
            builder.setRepoRoot(repoRoot); // GH-90000
            return this;
        }

        public DiagnoseRequestBuilder includeGlobs(String... globs) { // GH-90000
            for (String glob : globs) { // GH-90000
                builder.addIncludeGlobs(glob); // GH-90000
            }
            return this;
        }

        public DiagnoseRequestBuilder languages(String... languageIds) { // GH-90000
            for (String id : languageIds) { // GH-90000
                builder.addLanguages(Language.newBuilder().setId(id).build()); // GH-90000
            }
            return this;
        }

        public DiagnoseRequestBuilder tenantId(String tenantId) { // GH-90000
            builder.setTenantId(tenantId); // GH-90000
            return this;
        }

        public DiagnoseRequest build() { // GH-90000
            return builder.build(); // GH-90000
        }
    }

    public static class UnifiedDiagnosticBuilder {
        private final UnifiedDiagnostic.Builder builder = UnifiedDiagnostic.newBuilder(); // GH-90000

        public UnifiedDiagnosticBuilder tool(String tool) { // GH-90000
            builder.setTool(tool); // GH-90000
            return this;
        }

        public UnifiedDiagnosticBuilder rule(String rule) { // GH-90000
            builder.setRule(rule); // GH-90000
            return this;
        }

        public UnifiedDiagnosticBuilder message(String message) { // GH-90000
            builder.setMessage(message); // GH-90000
            return this;
        }

        public UnifiedDiagnosticBuilder file(String file) { // GH-90000
            builder.setFile(file); // GH-90000
            return this;
        }

        public UnifiedDiagnosticBuilder location(int line, int column) { // GH-90000
            builder.setLine(line); // GH-90000
            builder.setColumn(column); // GH-90000
            return this;
        }

        public UnifiedDiagnosticBuilder severity(String severity) { // GH-90000
            builder.setSeverity(severity); // GH-90000
            return this;
        }

        public UnifiedDiagnosticBuilder meta(String key, String value) { // GH-90000
            if ("suggestion".equals(key)) { // GH-90000
                builder.setSuggestion(value); // GH-90000
            } else {
                builder.setCategory(key + "=" + value); // GH-90000
            }
            return this;
        }

        public UnifiedDiagnostic build() { // GH-90000
            return builder.setTimestamp(Instant.now().toEpochMilli()).build(); // GH-90000
        }
    }
}
