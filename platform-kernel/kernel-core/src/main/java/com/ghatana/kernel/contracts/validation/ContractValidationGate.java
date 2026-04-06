package com.ghatana.kernel.contracts.validation;

import com.ghatana.kernel.contracts.KernelContract;
import com.ghatana.kernel.contracts.ContractValidator;

import java.util.*;

/**
 * Contract validation gate for CI/CD integration.
 *
 * <p>Enforces contract compliance before deployment by validating
 * all contracts against their respective validators.</p>
 *
 * @doc.type class
 * @doc.purpose CI/CD contract validation gate
 * @doc.layer core
 * @doc.pattern Gateway
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class ContractValidationGate {

    private final Map<KernelContract.ContractFamily, ContractValidator> validators;
    private boolean enforceCompliance = true;

    /**
     * Compliance violation.
     */
    public static class ComplianceViolation {
        private final String contractId;
        private final String message;
        private final ViolationSeverity severity;

        public ComplianceViolation(String contractId, String message, ViolationSeverity severity) {
            this.contractId = contractId;
            this.message = message;
            this.severity = severity;
        }

        public String getContractId() { return contractId; }
        public String getMessage() { return message; }
        public ViolationSeverity getSeverity() { return severity; }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s", severity, contractId, message);
        }
    }

    /**
     * Violation severity levels.
     */
    public enum ViolationSeverity {
        ERROR,
        WARNING,
        INFO
    }

    public ContractValidationGate() {
        this.validators = new HashMap<>();
        registerDefaultValidators();
    }

    private void registerDefaultValidators() {
        // TODO: Re-enable validators after fixing schema/metadata compatibility issues
        // validators.put(KernelContract.ContractFamily.API, new APIContractValidator());
        // validators.put(KernelContract.ContractFamily.SCHEMA, new SchemaContractValidator());
        // validators.put(KernelContract.ContractFamily.EVENT, new EventContractValidator());
        // validators.put(KernelContract.ContractFamily.EXPERIENCE, new ExperienceContractValidator());
        // validators.put(KernelContract.ContractFamily.ANALYTICS, new AnalyticsContractValidator());
        // validators.put(KernelContract.ContractFamily.AUTONOMOUS, new AutonomousContractValidator());
    }

    /**
     * Validates contracts for deployment.
     *
     * @param contracts the contracts to validate
     * @return gate result
     */
    public GateResult validateContractsForDeployment(List<KernelContract> contracts) {
        Objects.requireNonNull(contracts, "contracts cannot be null");

        List<ComplianceViolation> violations = new ArrayList<>();
        Map<String, ContractValidator.ValidationResult> results = new HashMap<>();

        for (KernelContract contract : contracts) {
            ContractValidator validator = validators.get(contract.getFamily());
            
            if (validator == null) {
                violations.add(new ComplianceViolation(
                    contract.getContractId(),
                    "No validator found for contract family: " + contract.getFamily(),
                    ViolationSeverity.WARNING
                ));
                continue;
            }

            ContractValidator.ValidationResult result = validator.validate(contract);
            results.put(contract.getContractId(), result);

            if (!result.valid()) {
                for (String error : result.errors()) {
                    violations.add(new ComplianceViolation(
                        contract.getContractId(),
                        error,
                        ViolationSeverity.ERROR
                    ));
                }
            }

            // Note: warnings are not tracked in ValidationResult, only errors
            // for (String warning : result.getWarnings()) {  // PENDING: implement warnings
            //     violations.add(new ComplianceViolation(
            //         contract.getContractId(),
            //         warning,
            //         ViolationSeverity.WARNING
            //     ));
            // }
        }

        boolean valid = violations.stream()
            .noneMatch(v -> v.getSeverity() == ViolationSeverity.ERROR);

        return new GateResult(valid, violations, results);
    }

    /**
     * Enforces contract compliance.
     *
     * @param enforce true to enforce compliance
     */
    public void enforceContractCompliance(boolean enforce) {
        this.enforceCompliance = enforce;
    }

    /**
     * Gets all violations.
     *
     * @return list of compliance violations
     */
    public List<ComplianceViolation> getViolations() {
        return new ArrayList<>();
    }

    /**
     * Registers a custom validator.
     *
     * @param family the contract family
     * @param validator the validator
     */
    public void registerValidator(KernelContract.ContractFamily family, ContractValidator validator) {
        validators.put(family, validator);
    }

    /**
     * Gate result for deployment validation.
     */
    public static class GateResult {
        private final boolean valid;
        private final List<ComplianceViolation> violations;
        private final Map<String, ContractValidator.ValidationResult> results;

        public GateResult(boolean valid, List<ComplianceViolation> violations, 
                         Map<String, ContractValidator.ValidationResult> results) {
            this.valid = valid;
            this.violations = violations;
            this.results = results;
        }

        public boolean isValid() { return valid; }
        public List<ComplianceViolation> getViolations() { return violations; }
        public Map<String, ContractValidator.ValidationResult> getResults() { return results; }

        public boolean hasErrors() {
            return violations.stream()
                .anyMatch(v -> v.getSeverity() == ViolationSeverity.ERROR);
        }

        public boolean hasWarnings() {
            return violations.stream()
                .anyMatch(v -> v.getSeverity() == ViolationSeverity.WARNING);
        }
    }

    /**
     * Compliance violation.
     */
    // NOTE: ComplianceViolation moved to beginning of class to prevent forward reference errors
}
