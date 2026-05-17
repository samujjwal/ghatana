package com.ghatana.yappc.domain.artifact;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Typed DTO for a residual island - a code fragment that could not be modeled as an artifact node
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 *
 * P0: Replaces ID-only List&lt;String&gt; ingestion. Every residual island from the TS worker must
 * carry its full payload so persistence can record originalSource, sourceLocation, sourceSpan, checksum,
 * rawFragmentRef, risk, and reviewRequired without synthesizing placeholder values.
 *
 * Fields mirror the canonical proto {@code ResidualIsland} message in {@code artifact_compiler.proto}.
 */
public record ResidualIslandDto(
    /**
     * Stable, deterministic island ID (SHA-256 of source span + snapshot ID).
     */
    String id,

    /**
     * Classifier for the island type (e.g. "imperative_logic", "css_module", "raw_query").
     */
    String islandType,

    /**
     * Human-readable summary of the unmodeled code.
     */
    String summary,

    /**
     * P0: Original source code fragment for complete round-trip fidelity.
     * Required for compile-back to preserve exact source when regenerating files.
     */
    String originalSource,

    /**
     * Source span as "file:startLine:startCol-endLine:endCol".
     * Required for round-trip fidelity; never synthesized.
     */
    String sourceSpan,

    /**
     * SHA-256 checksum of the raw fragment content.
     */
    String checksum,

    /**
     * Reference (path or blob hash) to the raw fragment in the content store.
     */
    String rawFragmentRef,

    /**
     * Machine-readable reason the fragment could not be modeled.
     */
    String reason,

    /**
     * Extraction confidence in [0, 1].
     */
    Double confidence,

    /**
     * Whether a human must review this island before compile-back is safe.
     */
    Boolean reviewRequired,

    /**
     * Risk score in [0, 1]; drives review prioritisation.
     */
    Double riskScore,

    /**
     * Additional key-value metadata from the extractor.
     */
    Map<String, String> metadata,

    /**
     * Number of source files contributing to this island.
     */
    Integer fileCount,

    String tenantId,
    String projectId,
    String workspaceId,
    String snapshotId
) {
}
