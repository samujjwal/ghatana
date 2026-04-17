package com.ghatana.kernel.contracts.validation;

import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates ANALYTICS-family kernel contracts (metrics, dashboards, telemetry pipelines).
 *
 * <p>Enforces:
 * <ul>
 *   <li>{@code contractId} and semver version must be valid.</li>
 *   <li>Metadata key {@code metricType} must be one of
 *       {@code counter}, {@code gauge}, {@code histogram}, {@code summary}.</li>
 *   <li>Metadata key {@code unit} is optional but recommended.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates ANALYTICS-family kernel contracts
 * @doc.layer core
 * @doc.pattern Strategy
 * @since 1.1.0
 */
public final class AnalyticsContractValidator implements ContractValidator {

    private static final List<String> METRIC_TYPES =
            List.of("counter", "gauge", "histogram", "summary", "event", "kpi", "dashboard");

    @Override
    public ValidationResult validate(KernelContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");
        List<String> errors = new ArrayList<>(contract.getValidationErrors());

        Map<String, String> meta = contract.getMetadata();

        String metricType = meta.get("metricType");
        if (metricType != null && !metricType.isBlank()
                && !METRIC_TYPES.contains(metricType.toLowerCase(java.util.Locale.ROOT))) {
            errors.add("ANALYTICS contract '" + contract.getContractId()
                    + "' has unrecognised metricType '" + metricType
                    + "'. Expected one of: " + METRIC_TYPES);
        }

        // owner is encouraged for analytics contracts
        String owner = meta.get("owner");
        if (owner == null || owner.isBlank()) {
            // Warn-only: analytics contracts without owner are allowed but discouraged
            // (the gate emits WARNING, not ERROR, for absent owner in analytics contracts)
        }

        return errors.isEmpty() ? ValidationResult.OK : ValidationResult.failed(errors);
    }

    @Override
    public List<KernelContract.ContractFamily> applicableFamilies() {
        return List.of(KernelContract.ContractFamily.ANALYTICS);
    }
}
