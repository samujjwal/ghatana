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

    private final RefactorerValidationService service = new RefactorerValidationService(); // GH-90000

    @Test
    @DisplayName("rejects gRPC diagnose requests without languages or a valid budget")
    void rejectsInvalidGrpcDiagnoseRequests() { // GH-90000
        DiagnoseRequest request = DiagnoseRequest.newBuilder() // GH-90000
                .setRepoRoot("/tmp/repo")
                .setBudget(Budget.newBuilder().setMaxPasses(0).setMaxEditsPerFile(-1).setTimeoutSeconds(0).build()) // GH-90000
                .build(); // GH-90000

        ValidationResult result = runPromise(() -> service.validateEvent(request)); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.violations()) // GH-90000
                .extracting(violation -> violation.field()) // GH-90000
                .contains("languages", "budget.maxPasses", "budget.maxEditsPerFile", "budget.timeoutSeconds"); // GH-90000
    }

    @Test
    @DisplayName("rejects gRPC run requests without config and idempotency key")
    void rejectsInvalidGrpcRunRequests() { // GH-90000
        RunRequest request = RunRequest.newBuilder().build(); // GH-90000

        ValidationResult result = runPromise(() -> service.validateEvent(request)); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.violations()) // GH-90000
                .extracting(violation -> violation.field()) // GH-90000
                .contains("config", "idempotencyKey"); // GH-90000
    }

    @Test
    @DisplayName("accepts valid gRPC requests using the same rules as REST")
    void acceptsValidGrpcRequests() { // GH-90000
        ValidationResult result = runPromise(() -> service.validateEvent(GrpcProtoFactory.sampleRunRequest())); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.violations()).isEmpty(); // GH-90000
    }
}
