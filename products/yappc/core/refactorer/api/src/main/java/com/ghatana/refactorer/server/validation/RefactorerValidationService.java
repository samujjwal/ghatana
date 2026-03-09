package com.ghatana.refactorer.server.validation;

import com.ghatana.platform.validation.ValidationError;
import com.ghatana.platform.validation.ValidationResult;
import com.ghatana.platform.validation.ValidationService;
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
            return Promise.of(
                    ValidationResult.failure(
                            new ValidationError("NULL_EVENT", "Event cannot be null", "root", null)));
        }

        if (event instanceof RestModels.DiagnoseRequest diagnoseReq) {
            return Promise.of(validateDiagnoseRequest(diagnoseReq));
        }

        if (event instanceof RestModels.RunRequest runReq) {
            return Promise.of(validateRunRequest(runReq));
        }

        // Default: validation not implemented for this event type
        logger.debug("No validation rules for event type: {}", event.getClass().getSimpleName());
        return Promise.of(ValidationResult.success());
    }

    @Override
    public Promise<ValidationResult> validatePayload(Object eventType, String payload) {
        if (payload == null || payload.isBlank()) {
            return Promise.of(
                    ValidationResult.failure(
                            new ValidationError(
                                    "EMPTY_PAYLOAD", "Payload cannot be null or empty", "payload", null)));
        }
        return Promise.of(ValidationResult.success());
    }

    @Override
    public Promise<ValidationResult> validateSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return Promise.of(
                    ValidationResult.failure(
                            new ValidationError(
                                    "EMPTY_SCHEMA",
                                    "Schema cannot be null or empty",
                                    "schema", null)));
        }
        return Promise.of(ValidationResult.success());
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
        List<ValidationError> errors = new ArrayList<>();

        // Validate repoRoot
        if (request.repoRoot() == null || request.repoRoot().isBlank()) {
            errors.add(
                    new ValidationError(
                            "MISSING_REPO_ROOT",
                            "Repository root path is required",
                            "repoRoot",
                            request.repoRoot()));
        }

        // Validate languages
        if (request.languages() == null || request.languages().isEmpty()) {
            errors.add(
                    new ValidationError(
                            "MISSING_LANGUAGES",
                            "At least one language must be specified",
                            "languages",
                            request.languages()));
        }

        // Validate budget
        if (request.budget() != null) {
            errors.addAll(validateBudget(request.budget()));
        } else {
            errors.add(
                    new ValidationError(
                            "MISSING_BUDGET",
                            "Budget configuration is required",
                            "budget",
                            null));
        }

        // Validate tenantId if present
        if (request.tenantId() != null && request.tenantId().isBlank()) {
            errors.add(
                    new ValidationError(
                            "INVALID_TENANT_ID",
                            "Tenant ID cannot be blank",
                            "tenantId",
                            request.tenantId()));
        }

        logger.debug("DiagnoseRequest validation complete: {} errors found", errors.size());
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Validates a RunRequest according to business rules.
     *
     * @param request the request to validate
     * @return ValidationResult with any discovered errors
     */
    private ValidationResult validateRunRequest(RestModels.RunRequest request) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate config
        if (request.config() == null) {
            errors.add(
                    new ValidationError(
                            "MISSING_CONFIG",
                            "Diagnosis configuration is required",
                            "config",
                            null));
        } else {
            // Validate nested DiagnoseRequest
            ValidationResult configResult = validateDiagnoseRequest(request.config());
            if (!configResult.isValid()) {
                errors.addAll(configResult.getErrors());
            }
        }

        // Validate idempotencyKey
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            errors.add(
                    new ValidationError(
                            "MISSING_IDEMPOTENCY_KEY",
                            "Idempotency key is required",
                            "idempotencyKey",
                            request.idempotencyKey()));
        }

        logger.debug("RunRequest validation complete: {} errors found", errors.size());
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Validates budget constraints.
     *
     * @param budget the budget to validate
     * @return List of validation errors (empty if valid)
     */
    private List<ValidationError> validateBudget(RestModels.Budget budget) {
        List<ValidationError> errors = new ArrayList<>();

        if (budget.maxPasses() < 1) {
            errors.add(
                    new ValidationError(
                            "INVALID_MAX_PASSES",
                            "Maximum passes must be at least 1",
                            "budget.maxPasses",
                            budget.maxPasses()));
        }

        if (budget.maxEditsPerFile() < 0) {
            errors.add(
                    new ValidationError(
                            "INVALID_MAX_EDITS",
                            "Maximum edits per file must be non-negative",
                            "budget.maxEditsPerFile",
                            budget.maxEditsPerFile()));
        }

        if (budget.timeoutSeconds() <= 0) {
            errors.add(
                    new ValidationError(
                            "INVALID_TIMEOUT",
                            "Timeout must be greater than zero",
                            "budget.timeoutSeconds",
                            budget.timeoutSeconds()));
        }

        return errors;
    }
}
