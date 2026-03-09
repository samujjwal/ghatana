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
    void testDiagnoseRequestRoundTrip()
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        DiagnoseRequest original = GrpcProtoFactory.sampleDiagnoseRequest();

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray();
        DiagnoseRequest deserialized = DiagnoseRequest.parseFrom(serialized);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getRepoRoot()).isEqualTo("/tmp/test-repo");
        assertThat(deserialized.getIncludeGlobsList()).hasSize(2);
        assertThat(deserialized.getLanguagesList()).hasSize(2);
        assertThat(deserialized.getTenantId()).isEqualTo("test-tenant");
    }

    @Test
    void testUnifiedDiagnosticRoundTrip()
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        UnifiedDiagnostic original = GrpcProtoFactory.sampleDiagnostic();

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray();
        UnifiedDiagnostic deserialized = UnifiedDiagnostic.parseFrom(serialized);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getTool()).isEqualTo("javac");
        assertThat(deserialized.getRule()).isEqualTo("missing-import");
        assertThat(deserialized.getSeverity()).isEqualTo("ERROR");
        assertThat(deserialized.getSuggestion()).isEqualTo("import java.util.List");
    }

    @Test
    void testRunRequestRoundTrip()
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        RunRequest original = GrpcProtoFactory.sampleRunRequest();

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray();
        RunRequest deserialized = RunRequest.parseFrom(serialized);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getIdempotencyKey()).isEqualTo("idem-key-123");
        assertThat(deserialized.getDryRun()).isFalse();
        assertThat(deserialized.hasConfig()).isTrue();
    }

    @Test
    void testRunStatusRoundTrip()
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        RunStatus original = GrpcProtoFactory.sampleRunStatus();

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray();
        RunStatus deserialized = RunStatus.parseFrom(serialized);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getJobId()).isEqualTo("job-123");
        assertThat(deserialized.getState()).isEqualTo("RUNNING");
        assertThat(deserialized.getPass()).isEqualTo(1);
        assertThat(deserialized.getToolVersionsMap()).containsEntry("javac", "21.0.1");
    }

    @Test
    void testProgressEventRoundTrip()
            throws com.google.protobuf.InvalidProtocolBufferException, Exception {
        ProgressEvent original = GrpcProtoFactory.sampleProgressEvent();

        // Serialize to bytes and back
        byte[] serialized = original.toByteArray();
        ProgressEvent deserialized = ProgressEvent.parseFrom(serialized);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getJobId()).isEqualTo("job-123");
        assertThat(deserialized.getEventType()).isEqualTo("diagnostic");
        assertThat(deserialized.getMessage()).isNotEmpty();
        assertThat(deserialized.getCurrentPass()).isEqualTo(1);
        assertThat(deserialized.getTotalPasses()).isEqualTo(3);
    }

    @Test
    void testBuilderPatterns() {
        DiagnoseRequest request =
                GrpcProtoFactory.diagnoseRequest()
                        .repoRoot("/custom/repo")
                        .includeGlobs("**/*.java", "**/*.kt")
                        .languages("java", "kotlin")
                        .tenantId("custom-tenant")
                        .build();

        assertThat(request.getRepoRoot()).isEqualTo("/custom/repo");
        assertThat(request.getIncludeGlobsList()).containsExactly("**/*.java", "**/*.kt");
        assertThat(request.getLanguagesList())
                .extracting(Language::getId)
                .containsExactly("java", "kotlin");
        assertThat(request.getTenantId()).isEqualTo("custom-tenant");
    }

    @Test
    void testDiagnosticBuilder() {
        UnifiedDiagnostic diagnostic =
                GrpcProtoFactory.diagnostic()
                        .tool("eslint")
                        .rule("no-unused-vars")
                        .message("Variable 'x' is defined but never used")
                        .file("/src/main.ts")
                        .location(15, 8)
                        .severity("WARN")
                        .meta("variable", "x")
                        .meta("suggestion", "Remove unused variable")
                        .build();

        assertThat(diagnostic.getTool()).isEqualTo("eslint");
        assertThat(diagnostic.getRule()).isEqualTo("no-unused-vars");
        assertThat(diagnostic.getLine()).isEqualTo(15);
        assertThat(diagnostic.getColumn()).isEqualTo(8);
        assertThat(diagnostic.getSeverity()).isEqualTo("WARN");
        assertThat(diagnostic.getSuggestion()).isEqualTo("Remove unused variable");
        assertThat(diagnostic.getTimestamp()).isPositive();
    }
}
