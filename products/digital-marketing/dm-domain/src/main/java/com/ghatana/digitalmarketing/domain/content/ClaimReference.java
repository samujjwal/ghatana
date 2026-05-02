package com.ghatana.digitalmarketing.domain.content;

import java.util.Objects;

/**
 * A reference to a marketing or regulatory claim attached to a content version.
 *
 * <p>Claims must be verified before a content version can be approved for campaign launch.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS claim reference value object for content compliance
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ClaimReference(
        String claimId,
        String claimText,
        String claimSource) {

    /**
     * Compact constructor — validates all fields.
     */
    public ClaimReference {
        Objects.requireNonNull(claimId, "claimId must not be null");
        Objects.requireNonNull(claimText, "claimText must not be null");
        Objects.requireNonNull(claimSource, "claimSource must not be null");
        if (claimId.isBlank()) throw new IllegalArgumentException("claimId must not be blank");
        if (claimText.isBlank()) throw new IllegalArgumentException("claimText must not be blank");
        if (claimSource.isBlank()) throw new IllegalArgumentException("claimSource must not be blank");
    }
}
