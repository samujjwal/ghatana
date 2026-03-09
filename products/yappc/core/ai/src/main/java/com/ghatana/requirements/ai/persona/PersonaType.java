package com.ghatana.requirements.ai.persona;

import java.util.Arrays;

/**
 * Enumeration of persona types for requirements generation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Categorizes personas by their area of expertise and perspective, enabling
 * filtering and selection of appropriate context for requirement generation.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PersonaType type = PersonaType.SECURITY;
 * Persona persona = Persona.builder()
 *     .type(type)
 *     .name("Security Analyst")
 *     .build();
 * }</pre>
 *
 * @see Persona
 * @doc.type enum
 * @doc.purpose Persona type classification
 * @doc.layer application
 * @doc.pattern Value Object
 */
public enum PersonaType {
    /**
     * General-purpose requirements analyst. Balanced perspective suitable for
     * most scenarios.
     */
    GENERAL("General Requirements Analyst", "Balanced perspective for general requirements"),
    /**
     * Security-focused analyst. Emphasizes security, privacy, and compliance
     * requirements.
     */
    SECURITY("Security Analyst", "Focus on security, privacy, and compliance"),
    /**
     * Business analyst perspective. Emphasizes business value, ROI, and
     * stakeholder needs.
     */
    BUSINESS("Business Analyst", "Focus on business value and stakeholder needs"),
    /**
     * Technical architect perspective. Emphasizes technical feasibility,
     * architecture, and performance.
     */
    TECHNICAL("Technical Architect", "Focus on technical feasibility and architecture"),
    /**
     * User experience designer perspective. Emphasizes usability,
     * accessibility, and user satisfaction.
     */
    UX("UX Designer", "Focus on usability and user experience"),
    /**
     * Compliance specialist perspective. Emphasizes regulatory compliance,
     * legal requirements, and standards.
     */
    COMPLIANCE("Compliance Specialist", "Focus on regulatory compliance and standards"),
    /**
     * Quality assurance engineer perspective. Emphasizes testability, quality
     * attributes, and validation criteria.
     */
    QA("QA Engineer", "Focus on testability and quality assurance");

    private final String displayName;
    private final String description;

    PersonaType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
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
     * Gets detailed description of this persona type.
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Parses persona type from string, case-insensitive.
     *
     * @param value string value to parse
     * @return corresponding PersonaType
     * @throws IllegalArgumentException if value is invalid
     */
    public static PersonaType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Persona type cannot be null or empty");
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid persona type: " + value + ". Valid values: "
                    + String.join(", ", Arrays.stream(values())
                            .map(Enum::name)
                            .toArray(String[]::new))
            );
        }
    }
}
