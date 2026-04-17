package com.ghatana.kernel.contracts.validation;

import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates AUTONOMY-family kernel contracts (agent policies, model governance, learning loops).
 *
 * <p>Enforces:
 * <ul>
 *   <li>{@code contractId} and semver version must be valid.</li>
 *   <li>Metadata key {@code agentType} must be one of the canonical nine agent types.</li>
 *   <li>Metadata key {@code modelId} is required for {@code PROBABILISTIC} and
 *       {@code HYBRID} agent types.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates AUTONOMY-family kernel contracts
 * @doc.layer core
 * @doc.pattern Strategy
 * @since 1.1.0
 */
public final class AutonomousContractValidator implements ContractValidator {

    private static final List<String> AGENT_TYPES = List.of(
            "DETERMINISTIC", "PROBABILISTIC", "HYBRID", "ADAPTIVE",
            "COMPOSITE", "REACTIVE", "STREAM_PROCESSOR", "PLANNING", "CUSTOM");

    @Override
    public ValidationResult validate(KernelContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");
        List<String> errors = new ArrayList<>(contract.getValidationErrors());

        Map<String, String> meta = contract.getMetadata();

        String agentType = meta.get("agentType");
        if (agentType == null || agentType.isBlank()) {
            errors.add("AUTONOMY contract '" + contract.getContractId()
                    + "' must declare metadata.agentType");
        } else {
            String upperType = agentType.toUpperCase(java.util.Locale.ROOT);
            if (!AGENT_TYPES.contains(upperType)) {
                errors.add("AUTONOMY contract '" + contract.getContractId()
                        + "' has unrecognised agentType '" + agentType
                        + "'. Expected one of: " + AGENT_TYPES);
            }

            // PROBABILISTIC and HYBRID agents must declare a modelId
            if (("PROBABILISTIC".equals(upperType) || "HYBRID".equals(upperType))) {
                String modelId = meta.get("modelId");
                if (modelId == null || modelId.isBlank()) {
                    errors.add("AUTONOMY contract '" + contract.getContractId()
                            + "' with agentType=" + agentType
                            + " must declare metadata.modelId");
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.OK : ValidationResult.failed(errors);
    }

    @Override
    public List<KernelContract.ContractFamily> applicableFamilies() {
        return List.of(KernelContract.ContractFamily.AUTONOMY);
    }
}
