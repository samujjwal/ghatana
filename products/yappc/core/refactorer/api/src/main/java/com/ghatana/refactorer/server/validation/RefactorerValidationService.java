package com.ghatana.refactorer.server.validation;

import com.ghatana.platform.core.validation.ValidationResult;
import com.ghatana.platform.core.validation.ValidationResult.Violation;
import com.ghatana.platform.core.validation.ValidationService;
import com.ghatana.refactorer.api.v1.Budget;
import com.ghatana.refactorer.api.v1.DiagnoseRequest;
import com.ghatana.refactorer.api.v1.RunRequest;
import com.ghatana.refactorer.server.dto.RestModels;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ValidationService implementation for the Refactorer service.

 * Validates REST request models and provides comprehensive error reporting.

 *

 * <p>Implements core/validation ValidationService interface to ensure product module

 * (refactorer) depends on platform abstraction, not direct validation libraries.

 *

 * <p>Validation rules:

 * - DiagnoseRequest: repoRoot required, languages non-empty, budget constraints valid

 * - RunRequest: config required, idempotencyKey required

 * - Budget: maxPasses >= 1, maxEditsPerFile >= 0, timeoutSeconds > 0

 *

 * @doc.type class

 * @doc.purpose Delegate to core validation to enforce constraints on incoming payloads.

 * @doc.layer product

 * @doc.pattern Service

 */

public class RefactorerValidationService implements ValidationService {
    private static final Logger logger = LogManager.getLogger(RefactorerValidationService.class);

    @Override
    public Promise<ValidationResult> validateEvent(Object event) {
        if (event == null) {
            return Promise.of(ValidationResult.invalid("root", "Event cannot be null"));
        }

        if (event instanceof RestModels.DiagnoseRequest diagnoseReq) {
            return Promise.of(validateDiagnoseRequest(diagnoseReq));
        }

        if (event instanceof RestModels.RunRequest runReq) {
            return Promise.of(validateRunRequest(runReq));
        }

        if (event instanceof DiagnoseRequest diagnoseReq) {
            return Promise.of(validateDiagnoseRequest(diagnoseReq));
        }

        if (event instanceof RunRequest runReq) {
            return Promise.of(validateRunRequest(runReq));
        }

        // Default: validation not implemented for this event type
        logger.debug("No validation rules for event type: {}", event.getClass().getSimpleName());
        return Promise.of(ValidationResult.valid());
    }

    @Override
    public Promise<ValidationResult> validatePayload(Object eventType, String payload) {
        if (payload == null || payload.isBlank()) {
            return Promise.of(ValidationResult.invalid("payload", "Payload cannot be null or empty"));
        }
        return Promise.of(ValidationResult.valid());
    }

    @Override
    public Promise<ValidationResult> validateSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return Promise.of(ValidationResult.invalid("schema", "Schema cannot be null or empty"));
        }
        return Promise.of(ValidationResult.valid());
    }

    @Override
    public Promise<String> compileSchema(String schema) {
        return Promise.of("compiled-schema");
    }

    /**
     * Validates a DiagnoseRequest according to business rules.
     *
     * @param request the request to validate
     * @return ValidationResult with any discovered errors
     */
    private ValidationResult validateDiagnoseRequest(RestModels.DiagnoseRequest request) {
        List<Violation> violations = new ArrayList<>();

        // Validate repoRoot
        if (request.repoRoot() == null || request.repoRoot().isBlank()) {
            violations.add(new Violation("repoRoot", "Repository root path is required"));
        }

        // Validate languages
        if (request.languages() == null || request.languages().isEmpty()) {
            violations.add(new Violation("languages", "At least one language must be specified"));
        }

        // Validate budget
        if (request.budget() != null) {
            violations.addAll(validateBudget(request.budget()));
        } else {
            violations.add(new Violation("budget", "Budget configuration is required"));
        }

        // Validate tenantId if present
        if (request.tenantId() != null && request.tenantId().isBlank()) {
            violations.add(new Violation("tenantId", "Tenant ID cannot be blank"));
        }

        logger.debug("DiagnoseRequest validation complete: {} errors found", violations.size());
        return violations.isEmpty() ? ValidationResult.valid() : ValidationResult.of(violations);
    }

    /**
     * Validates a RunRequest according to business rules.
     *
     * @param request the request to validate
     * @return ValidationResult with any discovered errors
     */
    private ValidationResult validateRunRequest(RestModels.RunRequest request) {
        List<Violation> violations = new ArrayList<>();

        // Validate config
        if (request.config() == null) {
            violations.add(new Violation("config", "Diagnosis configuration is required"));
        } else {
            // Validate nested DiagnoseRequest
            ValidationResult configResult = validateDiagnoseRequest(request.config());
            if (!configResult.isValid()) {
                violations.addAll(configResult.violations());
            }
        }

        // Validate idempotencyKey
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            violations.add(new Violation("idempotencyKey", "Idempotency key is required"));
        }

        logger.debug("RunRequest validation complete: {} errors found", violations.size());
        return violations.isEmpty() ? ValidationResult.valid() : ValidationResult.of(violations);
    }

    private ValidationResult validateDiagnoseRequest(DiagnoseRequest request) {
        List<Violation> violations = new ArrayList<>();

        if (request.getRepoRoot().isBlank()) {
            violations.add(new Violation("repoRoot", "Repository root path is required"));
        }

        if (request.getLanguagesCount() == 0) {
            violations.add(new Violation("languages", "At least one language must be specified"));
        }

        if (request.hasBudget()) {
            violations.addAll(validateBudget(request.getBudget()));
        } else {
            violations.add(new Violation("budget", "Budget configuration is required"));
        }

        logger.debug("DiagnoseRequest validation complete: {} errors found", violations.size());
        return violations.isEmpty() ? ValidationResult.valid() : ValidationResult.of(violations);
    }

    private ValidationResult validateRunRequest(RunRequest request) {
        List<Violation> violations = new ArrayList<>();

        if (!request.hasConfig()) {
            violations.add(new Violation("config", "Diagnosis configuration is required"));
        } else {
            ValidationResult configResult = validateDiagnoseRequest(request.getConfig());
            if (!configResult.isValid()) {
                violations.addAll(configResult.violations());
            }
        }

        if (request.getIdempotencyKey().isBlank()) {
            violations.add(new Violation("idempotencyKey", "Idempotency key is required"));
        }

        logger.debug("RunRequest validation complete: {} errors found", violations.size());
        return violations.isEmpty() ? ValidationResult.valid() : ValidationResult.of(violations);
    }

    /**
     * Validates budget constraints.
     *
     * @param budget the budget to validate
     * @return List of validation errors (empty if valid)
     */
    private List<Violation> validateBudget(RestModels.Budget budget) {
        List<Violation> violations = new ArrayList<>();

        if (budget.maxPasses() < 1) {
            violations.add(new Violation("budget.maxPasses", "Maximum passes must be at least 1"));
        }

        if (budget.maxEditsPerFile() < 0) {
            violations.add(new Violation("budget.maxEditsPerFile", "Maximum edits per file must be non-negative"));
        }

        if (budget.timeoutSeconds() <= 0) {
            violations.add(new Violation("budget.timeoutSeconds", "Timeout must be greater than zero"));
        }

        return violations;
    }

    private List<Violation> validateBudget(Budget budget) {
        List<Violation> violations = new ArrayList<>();

        if (budget.getMaxPasses() < 1) {
            violations.add(new Violation("budget.maxPasses", "Maximum passes must be at least 1"));
        }

        if (budget.getMaxEditsPerFile() < 0) {
            violations.add(new Violation("budget.maxEditsPerFile", "Maximum edits per file must be non-negative"));
        }

        if (budget.getTimeoutSeconds() <= 0) {
            violations.add(new Violation("budget.timeoutSeconds", "Timeout must be greater than zero"));
        }

        return violations;
    }
}
