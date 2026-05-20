/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DC-P1-10: Canonical runtime profile validator that collects all production requirements
 * from subsystem validators and provides unified validation.
 *
 * <p>This validator allows subsystems (security filter, entity handler, event handler, etc.)
 * to register their production requirements, which are then validated as a single unit
 * during service startup. This ensures all violations are reported together rather than
 * failing on the first check.
 *
 * <p>Usage:
 * <pre>
 * RuntimeProfileValidator validator = RuntimeProfileValidator.builder()
 *     .deploymentProfile("production")
 *     .register("SecurityFilter", securityFilter::validateRequirements)
 *     .register("EntityCrudHandler", entityHandler::validateRequirements)
 *     .register("EventHandler", eventHandler::validateRequirements)
 *     .build();
 *
 * validator.validate(); // Throws IllegalStateException with all violations
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Canonical production profile validation with requirement registration
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class RuntimeProfileValidator {

    private final String deploymentProfile;
    private final List<SubsystemValidator> validators;

    private RuntimeProfileValidator(String deploymentProfile, List<SubsystemValidator> validators) {
        this.deploymentProfile = deploymentProfile;
        this.validators = List.copyOf(validators);
    }

    /**
     * Validates all registered subsystem requirements.
     *
     * @throws IllegalStateException if any validation fails, with all violations reported
     */
    public void validate() {
        if (!isProductionLikeProfile(deploymentProfile)) {
            return;
        }

        List<String> violations = new ArrayList<>();

        for (SubsystemValidator validator : validators) {
            try {
                validator.validate(deploymentProfile);
            } catch (IllegalStateException e) {
                violations.add(String.format("[%s] %s", validator.name(), e.getMessage()));
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "DC-P1-10: Production profile validation failed for '" + deploymentProfile + "'. " +
                "Violations:\n" + String.join("\n", violations));
        }
    }

    /**
     * DC-P1-10: Determines if the deployment profile requires production-like strictness.
     */
    private static boolean isProductionLikeProfile(String profile) {
        if (profile == null) return false;
        String lower = profile.trim().toLowerCase();
        return lower.equals("production") || lower.equals("staging") || lower.equals("sovereign");
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RuntimeProfileValidator.
     */
    public static class Builder {
        private String deploymentProfile = "local";
        private final List<SubsystemValidator> validators = new ArrayList<>();

        /**
         * Sets the deployment profile.
         */
        public Builder deploymentProfile(String profile) {
            this.deploymentProfile = profile != null ? profile : "local";
            return this;
        }

        /**
         * Registers a subsystem validator.
         *
         * @param name subsystem name for error reporting
         * @param validator validation function that throws IllegalStateException on failure
         */
        public Builder register(String name, Consumer<String> validator) {
            this.validators.add(new SubsystemValidator(name, validator));
            return this;
        }

        /**
         * Builds the validator.
         */
        public RuntimeProfileValidator build() {
            return new RuntimeProfileValidator(deploymentProfile, validators);
        }
    }

    /**
     * Subsystem validator with name and validation logic.
     */
    private record SubsystemValidator(String name, Consumer<String> validator) {
        void validate(String profile) {
            validator.accept(profile);
        }
    }
}
