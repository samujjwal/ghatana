package com.ghatana.appplatform.eventstore.validation;

import java.util.List;
import java.util.Objects;

/**
 * Classifies a schema change between consecutive event schema versions into a
 * semantic version bump category: PATCH, MINOR, or MAJOR.
 *
 * <h2>Classification rules</h2>
 * <table border="1">
 *   <tr><th>BACKWARD compat</th><th>FORWARD compat</th><th>Bump</th></tr>
 *   <tr><td>✔ pass</td><td>✔ pass</td><td>PATCH (documentation-only or equivalent)</td></tr>
 *   <tr><td>✔ pass</td><td>✗ fail</td><td>MINOR (backward-safe additions — producers must upgrade)</td></tr>
 *   <tr><td>✗ fail</td><td>any</td><td>MAJOR (breaking — consumers cannot read old data)</td></tr>
 * </table>
 *
 * <h2>Approval gate for MAJOR changes</h2>
 * <p>A MAJOR bump is considered {@linkplain VersionBumpResult#mayProceed() blocked} by default.
 * Callers must supply an explicit {@code approvalToken} to bypass the gate. This token is
 * validated against the schema registry's pending approval store (future sprint integration).
 *
 * @doc.type class
 * @doc.purpose Classifies schema version bumps as PATCH/MINOR/MAJOR and gates MAJOR changes (STORY-K05-029)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SchemaSemanticVersionGuard {

    private final SchemaCompatibilityChecker checker;

    /**
     * Version bump categories aligned with semantic versioning.
     */
    public enum VersionBump {
        /** No structural change. Safe to deploy with no consumer action. */
        PATCH,
        /** Backward-compatible addition (e.g. new optional field). Consumers can upgrade first. */
        MINOR,
        /** Breaking change. Requires coordinated migration. */
        MAJOR
    }

    /**
     * Result of a semantic version analysis.
     *
     * @param bump               the required version bump
     * @param backwardViolations violations from the backward compatibility check (empty = compatible)
     * @param forwardViolations  violations from the forward compatibility check (empty = compatible)
     * @param approvalRequired   true when a MAJOR bump requires an explicit approval token
     * @param approvalToken      approval token authorising a MAJOR bump (null = not yet approved)
     */
    public record VersionBumpResult(
            VersionBump bump,
            List<String> backwardViolations,
            List<String> forwardViolations,
            boolean approvalRequired,
            String approvalToken
    ) {
        /**
         * Returns true when the schema registration may proceed:
         * <ul>
         *   <li>Always true for PATCH and MINOR.</li>
         *   <li>True for MAJOR only when a non-blank {@code approvalToken} is present.</li>
         * </ul>
         */
        public boolean mayProceed() {
            if (bump != VersionBump.MAJOR) return true;
            return approvalToken != null && !approvalToken.isBlank();
        }

        /** Short description suitable for error messages. */
        public String summary() {
            return bump + " bump" + (approvalRequired && !mayProceed() ? " [BLOCKED — approval required]" : "");
        }
    }

    /**
     * Creates a guard backed by a new {@link SchemaCompatibilityChecker}.
     */
    public SchemaSemanticVersionGuard() {
        this(new SchemaCompatibilityChecker());
    }

    /**
     * Creates a guard using the provided checker (allows injection for testing).
     *
     * @param checker the compatibility checker to use
     */
    public SchemaSemanticVersionGuard(SchemaCompatibilityChecker checker) {
        this.checker = Objects.requireNonNull(checker, "checker");
    }

    /**
     * Analyses the schema change from {@code oldSchema} to {@code newSchema} and determines
     * the required semantic version bump.
     *
     * @param oldSchema      JSON Schema string of the currently active version
     * @param newSchema      JSON Schema string of the candidate new version
     * @return a {@link VersionBumpResult} with the classification and approval status
     */
    public VersionBumpResult analyse(String oldSchema, String newSchema) {
        return analyse(oldSchema, newSchema, null);
    }

    /**
     * Analyses the schema change and validates an approval token for MAJOR changes.
     *
     * @param oldSchema      JSON Schema string of the currently active version
     * @param newSchema      JSON Schema string of the candidate new version
     * @param approvalToken  approval token for MAJOR changes; null if not yet approved
     * @return a {@link VersionBumpResult} with the full classification and gate status
     */
    public VersionBumpResult analyse(String oldSchema, String newSchema, String approvalToken) {
        Objects.requireNonNull(oldSchema, "oldSchema");
        Objects.requireNonNull(newSchema, "newSchema");

        SchemaCompatibilityChecker.CompatibilityResult backward =
                checker.checkCompatibility(oldSchema, newSchema, com.ghatana.appplatform.eventstore.domain.CompatibilityType.BACKWARD);
        SchemaCompatibilityChecker.CompatibilityResult forward =
                checker.checkCompatibility(oldSchema, newSchema, com.ghatana.appplatform.eventstore.domain.CompatibilityType.FORWARD);

        VersionBump bump;
        if (!backward.compatible()) {
            bump = VersionBump.MAJOR;
        } else if (!forward.compatible()) {
            bump = VersionBump.MINOR;
        } else {
            bump = VersionBump.PATCH;
        }

        boolean approvalRequired = bump == VersionBump.MAJOR;
        return new VersionBumpResult(
                bump,
                backward.violations(),
                forward.violations(),
                approvalRequired,
                approvalToken
        );
    }

    /**
     * Convenience method: returns true when the change between old and new schema is a
     * breaking (MAJOR) change without an approval token.
     *
     * @param oldSchema currently active schema JSON
     * @param newSchema candidate new schema JSON
     * @return true if registration should be blocked
     */
    public boolean isBlocked(String oldSchema, String newSchema) {
        return !analyse(oldSchema, newSchema, null).mayProceed();
    }
}
