/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
