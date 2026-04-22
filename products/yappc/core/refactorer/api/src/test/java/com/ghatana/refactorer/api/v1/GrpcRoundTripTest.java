package com.ghatana.refactorer.api.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.api.testutils.GrpcProtoFactory;
import org.junit.jupiter.api.Test;

/** Tests for gRPC proto message round-trip serialization/deserialization.
 * @doc.type class
 * @doc.purpose Handles grpc round trip test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class GrpcRoundTripTest {

    @Test
    void testDiagnoseRequestRoundTrip() // GH-90000
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        DiagnoseRequest original = GrpcProtoFactory.sampleDiagnoseRequest(); // GH-90000

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray(); // GH-90000
        DiagnoseRequest deserialized = DiagnoseRequest.parseFrom(serialized); // GH-90000

        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getRepoRoot()).isEqualTo("/tmp/test-repo [GH-90000]");
        assertThat(deserialized.getIncludeGlobsList()).hasSize(2); // GH-90000
        assertThat(deserialized.getLanguagesList()).hasSize(2); // GH-90000
        assertThat(deserialized.getTenantId()).isEqualTo("test-tenant [GH-90000]");
    }

    @Test
    void testUnifiedDiagnosticRoundTrip() // GH-90000
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        UnifiedDiagnostic original = GrpcProtoFactory.sampleDiagnostic(); // GH-90000

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray(); // GH-90000
        UnifiedDiagnostic deserialized = UnifiedDiagnostic.parseFrom(serialized); // GH-90000

        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getTool()).isEqualTo("javac [GH-90000]");
        assertThat(deserialized.getRule()).isEqualTo("missing-import [GH-90000]");
        assertThat(deserialized.getSeverity()).isEqualTo("ERROR [GH-90000]");
        assertThat(deserialized.getSuggestion()).isEqualTo("import java.util.List [GH-90000]");
    }

    @Test
    void testRunRequestRoundTrip() // GH-90000
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        RunRequest original = GrpcProtoFactory.sampleRunRequest(); // GH-90000

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray(); // GH-90000
        RunRequest deserialized = RunRequest.parseFrom(serialized); // GH-90000

        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getIdempotencyKey()).isEqualTo("idem-key-123 [GH-90000]");
        assertThat(deserialized.getDryRun()).isFalse(); // GH-90000
        assertThat(deserialized.hasConfig()).isTrue(); // GH-90000
    }

    @Test
    void testRunStatusRoundTrip() // GH-90000
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        RunStatus original = GrpcProtoFactory.sampleRunStatus(); // GH-90000

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray(); // GH-90000
        RunStatus deserialized = RunStatus.parseFrom(serialized); // GH-90000

        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getJobId()).isEqualTo("job-123 [GH-90000]");
        assertThat(deserialized.getState()).isEqualTo("RUNNING [GH-90000]");
        assertThat(deserialized.getPass()).isEqualTo(1); // GH-90000
        assertThat(deserialized.getToolVersionsMap()).containsEntry("javac", "21.0.1"); // GH-90000
    }

    @Test
    void testProgressEventRoundTrip() // GH-90000
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        ProgressEvent original = GrpcProtoFactory.sampleProgressEvent(); // GH-90000

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray(); // GH-90000
        ProgressEvent deserialized = ProgressEvent.parseFrom(serialized); // GH-90000

        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getJobId()).isEqualTo("job-123 [GH-90000]");
        assertThat(deserialized.getEventType()).isEqualTo("diagnostic [GH-90000]");
        assertThat(deserialized.getMessage()).isNotEmpty(); // GH-90000
        assertThat(deserialized.getCurrentPass()).isEqualTo(1); // GH-90000
        assertThat(deserialized.getTotalPasses()).isEqualTo(3); // GH-90000
    }

    @Test
    void testBuilderPatterns() { // GH-90000
        DiagnoseRequest request =
                GrpcProtoFactory.diagnoseRequest() // GH-90000
                        .repoRoot("/custom/repo [GH-90000]")
                        .includeGlobs("**/*.java", "**/*.kt") // GH-90000
                        .languages("java", "kotlin") // GH-90000
                        .tenantId("custom-tenant [GH-90000]")
                        .build(); // GH-90000

        assertThat(request.getRepoRoot()).isEqualTo("/custom/repo [GH-90000]");
        assertThat(request.getIncludeGlobsList()).containsExactly("**/*.java", "**/*.kt"); // GH-90000
        assertThat(request.getLanguagesList()) // GH-90000
                .extracting(Language::getId) // GH-90000
                .containsExactly("java", "kotlin"); // GH-90000
        assertThat(request.getTenantId()).isEqualTo("custom-tenant [GH-90000]");
    }

    @Test
    void testDiagnosticBuilder() { // GH-90000
        UnifiedDiagnostic diagnostic =
                GrpcProtoFactory.diagnostic() // GH-90000
                        .tool("eslint [GH-90000]")
                        .rule("no-unused-vars [GH-90000]")
                        .message("Variable 'x' is defined but never used [GH-90000]")
                        .file("/src/main.ts [GH-90000]")
                        .location(15, 8) // GH-90000
                        .severity("WARN [GH-90000]")
                        .meta("variable", "x") // GH-90000
                        .meta("suggestion", "Remove unused variable") // GH-90000
                        .build(); // GH-90000

        assertThat(diagnostic.getTool()).isEqualTo("eslint [GH-90000]");
        assertThat(diagnostic.getRule()).isEqualTo("no-unused-vars [GH-90000]");
        assertThat(diagnostic.getLine()).isEqualTo(15); // GH-90000
        assertThat(diagnostic.getColumn()).isEqualTo(8); // GH-90000
        assertThat(diagnostic.getSeverity()).isEqualTo("WARN [GH-90000]");
        assertThat(diagnostic.getSuggestion()).isEqualTo("Remove unused variable [GH-90000]");
        assertThat(diagnostic.getTimestamp()).isPositive(); // GH-90000
    }
}
