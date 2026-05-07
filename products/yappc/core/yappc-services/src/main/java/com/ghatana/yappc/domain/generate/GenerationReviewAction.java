package com.ghatana.yappc.domain.generate;

/**
 * @doc.type enum
 * @doc.purpose Supported human review decisions for generated artifact runs
 * @doc.layer domain
 * @doc.pattern Enum
 */
public enum GenerationReviewAction {
    APPLY("apply", "APPLIED"),
    REJECT("reject", "REJECTED"),
    ROLLBACK("rollback", "ROLLED_BACK");

    private final String wireValue;
    private final String status;

    GenerationReviewAction(String wireValue, String status) {
        this.wireValue = wireValue;
        this.status = status;
    }

    public String wireValue() {
        return wireValue;
    }

    public String status() {
        return status;
    }
}
