package com.ghatana.agent.memory.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canonical memory item interface shared across all memory tiers.
 * Every memory item (episode, fact, procedure, task-state, working, preference,
 * artifact) implements this interface to enable cross-tier interoperability.
 *
 * <p>This is the universal envelope: all tiers share a common set of metadata
 * (provenance, validity, embedding, links) while each tier adds typed fields
 * via its own implementation.
 *
 * @doc.type interface
 * @doc.purpose Cross-tier canonical memory envelope
 * @doc.layer agent-memory
 * @doc.pattern Sealed Interface Hierarchy
 */
public interface MemoryItem {

    /** Unique identifier for this memory item. */
    @NotNull String getId();

    /** The memory tier this item belongs to. */
    @NotNull MemoryItemType getType();

    /** When this item was first created. */
    @NotNull Instant getCreatedAt();

    /** When this item was last updated. */
    @NotNull Instant getUpdatedAt();

    /** Optional expiration time. */
    @Nullable Instant getExpiresAt();

    /** Provenance: where this item came from. */
    @NotNull Provenance getProvenance();

    /** Optional vector embedding for semantic search. */
    @Nullable float[] getEmbedding();

    /** Validity: confidence, decay, lifecycle status. */
    @NotNull Validity getValidity();

    /** Links to other memory items (supports, contradicts, derived_from, etc.). */
    @NotNull List<MemoryLink> getLinks();

    /** Free-form labels for filtering and classification. */
    @NotNull Map<String, String> getLabels();

    /** Tenant ID for multi-tenancy isolation. */
    @NotNull String getTenantId();

    /** Sphere ID for context-policy privacy boundaries. */
    @Nullable String getSphereId();

    /** Data classification (PUBLIC, INTERNAL, CONFIDENTIAL, SECRET, PII). */
    @NotNull String getClassification();
}
