package com.ghatana.refactorer.server.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.core.validation.ValidationResult;
import com.ghatana.refactorer.api.testutils.GrpcProtoFactory;
import com.ghatana.refactorer.api.v1.Budget;
import com.ghatana.refactorer.api.v1.DiagnoseRequest;
import com.ghatana.refactorer.api.v1.RunRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Verifies validation parity across REST and gRPC request models
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RefactorerValidationService")
class RefactorerValidationServiceTest extends EventloopTestBase {

    private final RefactorerValidationService service = new RefactorerValidationService();

    @Test
    @DisplayName("rejects gRPC diagnose requests without languages or a valid budget")
    void rejectsInvalidGrpcDiagnoseRequests() {
        DiagnoseRequest request = DiagnoseRequest.newBuilder()
                .setRepoRoot("/tmp/repo")
                .setBudget(Budget.newBuilder().setMaxPasses(0).setMaxEditsPerFile(-1).setTimeoutSeconds(0).build())
                .build();

        ValidationResult result = runPromise(() -> service.validateEvent(request));

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations())
                .extracting(violation -> violation.field())
                .contains("languages", "budget.maxPasses", "budget.maxEditsPerFile", "budget.timeoutSeconds");
    }

    @Test
    @DisplayName("rejects gRPC run requests without config and idempotency key")
    void rejectsInvalidGrpcRunRequests() {
        RunRequest request = RunRequest.newBuilder().build();

        ValidationResult result = runPromise(() -> service.validateEvent(request));

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations())
                .extracting(violation -> violation.field())
                .contains("config", "idempotencyKey");
    }

    @Test
    @DisplayName("accepts valid gRPC requests using the same rules as REST")
    void acceptsValidGrpcRequests() {
        ValidationResult result = runPromise(() -> service.validateEvent(GrpcProtoFactory.sampleRunRequest()));

        assertThat(result.isValid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }
}
