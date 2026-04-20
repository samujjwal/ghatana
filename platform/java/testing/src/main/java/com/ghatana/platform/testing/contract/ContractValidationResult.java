package com.ghatana.platform.testing.contract;

import java.util.*;

/**
 * @doc.type class
 * @doc.purpose Result of API contract validation comparing declared vs implemented routes
 * @doc.layer platform
 * @doc.pattern Validation result
 */
public final class ContractValidationResult {
    private final boolean valid;
    private final List<String> violations;
    private final Map<String, Object> details;

    private ContractValidationResult(boolean valid, List<String> violations, Map<String, Object> details) {
        this.valid = valid;
        this.violations = Collections.unmodifiableList(violations);
        this.details = Collections.unmodifiableMap(details);
    }

    /**
     * Create a successful validation result.
     */
    public static ContractValidationResult valid() {
        return new ContractValidationResult(true, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Create a failed validation result with violations.
     */
    public static ContractValidationResult invalid(String... violations) {
        return invalid(Arrays.asList(violations), Collections.emptyMap());
    }

    /**
     * Create a failed validation result with violations and details.
     */
    public static ContractValidationResult invalid(List<String> violations, Map<String, Object> details) {
        return new ContractValidationResult(false, violations, details);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getViolations() {
        return violations;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    @Override
    public String toString() {
        if (valid) {
            return "ContractValidationResult{valid=true}";
        }
        return "ContractValidationResult{valid=false, violations=" + violations + ", details=" + details + "}";
    }
}
