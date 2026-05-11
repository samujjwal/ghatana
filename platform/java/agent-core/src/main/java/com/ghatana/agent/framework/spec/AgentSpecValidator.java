/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.spec;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.framework.runtime.AutonomyLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates an {@link AgentSpec} for canonical enum consistency.
 *
 * <p>This class is pure and stateless — it holds no mutable state and does not
 * require dependency injection. The validator checks that every field referencing
 * a canonical enum value uses the canonical spelling accepted by the shared
 * platform runtime.
 *
 * <h2>Checks performed</h2>
 * <ul>
 *   <li>{@code identity.agentType} — must resolve to a canonical {@link AgentType}</li>
 *   <li>{@code identity.autonomyLevel} — must be a canonical {@link AutonomyLevel} value</li>
 *   <li>{@code identity.stateMutability} — must be a canonical {@link StateMutability} value</li>
 *   <li>{@code identity.determinismGuarantee} — must be a canonical {@link DeterminismGuarantee} value</li>
 * </ul>
 *
 * <p>The loader's normalization path uses {@code .toUpperCase().replace('-','_')} before
 * reaching this validator, so by the time an {@link AgentSpec} is presented for
 * validation its enum fields already carry the canonical string representation.
 * This validator checks the resolved enum constants, not raw strings.
 *
 * @doc.type class
 * @doc.purpose Stateless validator that checks canonical enum consistency in a loaded AgentSpec
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class AgentSpecValidator {

    private static final Set<String> SUPPORTED_SPEC_VERSIONS = Set.of("1.0.0", "2.0.0");

    /**
     * Validates the given spec for canonical enum consistency.
     *
     * @param spec the spec to validate (must not be {@code null})
     * @return a {@link ValidationResult} — call {@link ValidationResult#isValid()} or
     *         {@link ValidationResult#throwIfInvalid()} on the result
     * @throws IllegalArgumentException if {@code spec} is {@code null}
     */
    public ValidationResult validate(AgentSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }

        List<ValidationIssue> issues = new ArrayList<>();

        validateSpecVersion(spec, issues);
        validateIdentity(spec, issues);

        return new ValidationResult(issues);
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private void validateSpecVersion(AgentSpec spec, List<ValidationIssue> issues) {
        String version = spec.getAgentSpecVersion();
        if (version != null && !SUPPORTED_SPEC_VERSIONS.contains(version)) {
            issues.add(new ValidationIssue(
                    "agentSpecVersion",
                    version,
                    "Unsupported spec version '" + version + "'. "
                            + "Supported versions: " + SUPPORTED_SPEC_VERSIONS));
        }
    }

    private void validateIdentity(AgentSpec spec, List<ValidationIssue> issues) {
        AgentSpec.SpecIdentity identity = spec.getIdentity();
        if (identity == null) {
            issues.add(new ValidationIssue("identity", null, "identity block is required"));
            return;
        }

        validateAgentType(identity, issues);
        validateAutonomyLevel(identity, issues);
        validateStateMutability(identity, issues);
        validateDeterminismGuarantee(identity, issues);
    }

    private void validateAgentType(AgentSpec.SpecIdentity identity, List<ValidationIssue> issues) {
        AgentType agentType = identity.agentType();
        if (agentType == null) {
            issues.add(new ValidationIssue(
                    "identity.agentType", null, "agentType is required"));
        }
    }

    private void validateAutonomyLevel(AgentSpec.SpecIdentity identity, List<ValidationIssue> issues) {
        String rawAutonomy = identity.autonomyLevel();
        if (rawAutonomy == null || rawAutonomy.isBlank()) {
            issues.add(new ValidationIssue(
                    "identity.autonomyLevel", null, "autonomyLevel is required"));
            return;
        }
        AutonomyLevel resolved = AutonomyLevel.fromString(rawAutonomy);
        if (resolved == null) {
            issues.add(new ValidationIssue(
                    "identity.autonomyLevel",
                    rawAutonomy,
                    "Unknown autonomyLevel '" + rawAutonomy + "'. "
                            + "Canonical values: ADVISORY, DRAFT, SUPERVISED, BOUNDED_AUTONOMOUS, AUTONOMOUS."));
        }
    }

    private void validateStateMutability(AgentSpec.SpecIdentity identity, List<ValidationIssue> issues) {
        StateMutability stateMutability = identity.stateMutability();
        if (stateMutability == null) {
            issues.add(new ValidationIssue(
                    "identity.stateMutability", null, "stateMutability is required"));
        }
        // StateMutability is already an enum; if the loader resolved it, it must be canonical.
        // No additional check needed unless the field can hold a raw string.
    }

    private void validateDeterminismGuarantee(
            AgentSpec.SpecIdentity identity, List<ValidationIssue> issues) {
        DeterminismGuarantee determinism = identity.determinismGuarantee();
        if (determinism == null) {
            issues.add(new ValidationIssue(
                    "identity.determinismGuarantee", null, "determinismGuarantee is required"));
        }
        // DeterminismGuarantee is already an enum; canonical by definition once resolved.
    }
}
