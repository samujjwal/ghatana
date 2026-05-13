/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Typed reference to evidence supporting an obsolescence detection.
 *
 * <p>Evidence can reference procedures, evaluations, episodes, or other entities
 * that provide context for why a mastery item was detected as obsolete.
 *
 * @doc.type class
 * @doc.purpose Typed evidence reference for obsolescence detection
 * @doc.layer agent-core
 * @doc.pattern Value Object
 */
public final class ObsolescenceEvidenceRef {

    private final @NotNull String refType;
    private final @NotNull String refId;
    private final @Nullable String description;

    /**
     * Evidence reference types.
     */
    public enum RefType {
        PROCEDURE,
        EVALUATION,
        EPISODE,
        POLICY,
        VERSION_SCOPE,
        ENVIRONMENT_FINGERPRINT,
        SECURITY_REPORT,
        DOCUMENTATION,
        DEPENDENCY_CHANGE,
        API_DIFF,
        TEST_FAILURE,
        SECURITY_ADVISORY,
        CUSTOM
    }

    /**
     * Creates an evidence reference.
     *
     * @param refType type of the reference (e.g., "procedure", "evaluation")
     * @param refId identifier of the referenced entity
     * @param description optional description of the evidence
     */
    public ObsolescenceEvidenceRef(
            @NotNull String refType,
            @NotNull String refId,
            @Nullable String description) {
        this.refType = Objects.requireNonNull(refType, "refType must not be null");
        this.refId = Objects.requireNonNull(refId, "refId must not be null");
        this.description = description;
    }

    /**
     * Creates an evidence reference without description.
     *
     * @param refType type of the reference
     * @param refId identifier of the referenced entity
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef of(@NotNull String refType, @NotNull String refId) {
        return new ObsolescenceEvidenceRef(refType, refId, null);
    }

    /**
     * Creates a procedure evidence reference.
     *
     * @param procedureId procedure identifier
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef procedure(@NotNull String procedureId) {
        return new ObsolescenceEvidenceRef(RefType.PROCEDURE.name(), procedureId, null);
    }

    /**
     * Creates an evaluation evidence reference.
     *
     * @param evaluationId evaluation identifier
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef evaluation(@NotNull String evaluationId) {
        return new ObsolescenceEvidenceRef(RefType.EVALUATION.name(), evaluationId, null);
    }

    /**
     * Creates an episode evidence reference.
     *
     * @param episodeId episode identifier
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef episode(@NotNull String episodeId) {
        return new ObsolescenceEvidenceRef(RefType.EPISODE.name(), episodeId, null);
    }

    /**
     * Creates a version scope evidence reference.
     *
     * @param versionScope version scope identifier
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef versionScope(@NotNull String versionScope) {
        return new ObsolescenceEvidenceRef(RefType.VERSION_SCOPE.name(), versionScope, null);
    }

    /**
     * Creates a security report evidence reference.
     *
     * @param reportId security report identifier
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef securityReport(@NotNull String reportId) {
        return new ObsolescenceEvidenceRef(RefType.SECURITY_REPORT.name(), reportId, null);
    }

    /**
     * Creates a documentation evidence reference.
     *
     * @param docId documentation identifier
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef documentation(@NotNull String docId) {
        return new ObsolescenceEvidenceRef(RefType.DOCUMENTATION.name(), docId, null);
    }

    /**
     * Creates a dependency changes evidence reference.
     *
     * @param changes list of dependency changes
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef dependencyChanges(@NotNull List<String> changes) {
        return new ObsolescenceEvidenceRef(RefType.DEPENDENCY_CHANGE.name(), "dependency-changes", String.join("; ", changes));
    }

    /**
     * Creates an API diff evidence reference.
     *
     * @param changes list of API changes
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef apiDiffs(@NotNull List<String> changes) {
        return new ObsolescenceEvidenceRef(RefType.API_DIFF.name(), "api-diffs", String.join("; ", changes));
    }

    /**
     * Creates a test failures evidence reference.
     *
     * @param failures list of test failures
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef testFailures(@NotNull List<String> failures) {
        return new ObsolescenceEvidenceRef(RefType.TEST_FAILURE.name(), "test-failures", String.join("; ", failures));
    }

    /**
     * Creates a security advisories evidence reference.
     *
     * @param advisories list of security advisories
     * @return evidence reference
     */
    @NotNull
    public static ObsolescenceEvidenceRef securityAdvisories(@NotNull List<String> advisories) {
        return new ObsolescenceEvidenceRef(RefType.SECURITY_ADVISORY.name(), "security-advisories", String.join("; ", advisories));
    }

    @NotNull
    public String refType() {
        return refType;
    }

    @NotNull
    public String refId() {
        return refId;
    }

    @Nullable
    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObsolescenceEvidenceRef that = (ObsolescenceEvidenceRef) o;
        return refType.equals(that.refType) && refId.equals(that.refId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refType, refId);
    }

    @Override
    public String toString() {
        return "ObsolescenceEvidenceRef{" +
                "refType='" + refType + '\'' +
                ", refId='" + refId + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
