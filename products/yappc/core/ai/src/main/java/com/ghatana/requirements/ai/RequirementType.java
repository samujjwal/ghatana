package com.ghatana.requirements.ai;

/**
 * Enumeration of requirement types for classification.
 *
 * <p>
 * <b>Purpose</b><br>
 * Categorizes requirements into standard types to enable filtering, validation,
 * and specialized processing.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RequirementType type = RequirementType.FUNCTIONAL;
 * if (type.isTechnical()) {
 *     // Process as technical requirement
 * }
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Requirement type classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum RequirementType {
    /**
     * Functional requirements describe system behavior and features. Examples:
     * "User can login with email", "System generates reports"
     */
    FUNCTIONAL("Functional", "Describes what the system should do", false),
    /**
     * Non-functional requirements describe quality attributes and constraints.
     * Examples: "Response time under 500ms", "Support 10,000 concurrent users"
     */
    NON_FUNCTIONAL("Non-Functional", "Describes how well the system should perform", true),
    /**
     * Constraint requirements define boundaries and limitations. Examples:
     * "Must comply with GDPR", "Must integrate with SAP"
     */
    CONSTRAINT("Constraint", "Describes limitations and boundaries", false);

    private final String displayName;
    private final String description;
    private final boolean technical;

    RequirementType(String displayName, String description, boolean technical) {
        this.displayName = displayName;
        this.description = description;
        this.technical = technical;
    }

    /**
     * Gets human-readable display name.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets detailed description of this requirement type.
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this is a technical requirement type (typically
     * non-functional).
     *
     * @return true if technical, false otherwise
     */
    public boolean isTechnical() {
        return technical;
    }

    /**
     * Parses requirement type from string, case-insensitive.
     *
     * @param value string value to parse
     * @return corresponding RequirementType
     * @throws IllegalArgumentException if value is invalid
     */
    public static RequirementType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Requirement type cannot be null or empty");
        }

        String normalized = value.trim().toUpperCase().replace("-", "_");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid requirement type: " + value + ". Valid values: FUNCTIONAL, NON_FUNCTIONAL, CONSTRAINT"
            );
        }
    }
}
