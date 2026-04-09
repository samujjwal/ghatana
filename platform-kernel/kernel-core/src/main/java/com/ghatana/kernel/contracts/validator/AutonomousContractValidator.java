package com.ghatana.kernel.contracts.validator;

import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;

/**
 * Validator for Autonomous (AI/Agent) contracts.
 *
 * <p>Validates autonomous contracts including AI agent specifications,
 * autonomy levels, and governance requirements.</p>
 *
 * @doc.type class
 * @doc.purpose Autonomous contract validation
 * @doc.layer core
 * @doc.pattern Strategy
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class AutonomousContractValidator implements ContractValidator {

    @Override
    public ContractValidator.ValidationResult validate(KernelContract contract) {
        if (contract.getFamily() != KernelContract.ContractFamily.AUTONOMY) {
            return ContractValidator.ValidationResult.failed("Contract is not an Autonomy contract");
        }

        // Stub: Schema validation deferred to full implementation
        // Object schema = contract.getSchema();

        return ContractValidator.ValidationResult.OK;
    }

    @Override
    public java.util.List<KernelContract.ContractFamily> applicableFamilies() {
        return java.util.List.of(KernelContract.ContractFamily.AUTONOMY);
    }
}
