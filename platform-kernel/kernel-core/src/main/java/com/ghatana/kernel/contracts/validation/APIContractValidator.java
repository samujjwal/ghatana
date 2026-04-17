package com.ghatana.kernel.contracts.validation;

import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates API-family kernel contracts (HTTP routes, RPC services, gateway routes).
 *
 * <p>Enforces:
 * <ul>
 *   <li>{@code contractId} must be present and valid.</li>
 *   <li>Version must be semver.</li>
 *   <li>Metadata key {@code basePath} must be present and start with {@code /}.</li>
 *   <li>Metadata key {@code methods} is optional but, when present, must be a
 *       comma-separated list of valid HTTP verbs.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates API-family kernel contracts
 * @doc.layer core
 * @doc.pattern Strategy
 * @since 1.1.0
 */
public final class APIContractValidator implements ContractValidator {

    private static final List<String> HTTP_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    @Override
    public ValidationResult validate(KernelContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");
        List<String> errors = new ArrayList<>(contract.getValidationErrors());

        Map<String, String> meta = contract.getMetadata();

        String basePath = meta.get("basePath");
        if (basePath == null || basePath.isBlank()) {
            errors.add("API contract '" + contract.getContractId()
                    + "' must declare metadata.basePath");
        } else if (!basePath.startsWith("/")) {
            errors.add("API contract '" + contract.getContractId()
                    + "' metadata.basePath must begin with '/': " + basePath);
        }

        String methods = meta.get("methods");
        if (methods != null && !methods.isBlank()) {
            for (String method : methods.split(",")) {
                String m = method.trim().toUpperCase();
                if (!HTTP_METHODS.contains(m)) {
                    errors.add("API contract '" + contract.getContractId()
                            + "' has unrecognised HTTP method: " + m);
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.OK : ValidationResult.failed(errors);
    }

    @Override
    public List<KernelContract.ContractFamily> applicableFamilies() {
        return List.of(KernelContract.ContractFamily.API);
    }
}
