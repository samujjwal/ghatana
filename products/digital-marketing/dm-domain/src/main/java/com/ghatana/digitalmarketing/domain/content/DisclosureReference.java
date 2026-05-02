package com.ghatana.digitalmarketing.domain.content;

import java.util.Objects;

/**
 * A reference to a legal or regulatory disclosure attached to a content version.
 *
 * <p>Disclosures are mandatory compliance elements that must appear in specific
 * content types (e.g., financial promotions, pharmaceutical ads).</p>
 *
 * @doc.type class
 * @doc.purpose DMOS disclosure reference value object for content compliance
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DisclosureReference(
        String disclosureId,
        String disclosureText,
        String disclosureType) {

    /**
     * Compact constructor — validates all fields.
     */
    public DisclosureReference {
        Objects.requireNonNull(disclosureId, "disclosureId must not be null");
        Objects.requireNonNull(disclosureText, "disclosureText must not be null");
        Objects.requireNonNull(disclosureType, "disclosureType must not be null");
        if (disclosureId.isBlank()) throw new IllegalArgumentException("disclosureId must not be blank");
        if (disclosureText.isBlank()) throw new IllegalArgumentException("disclosureText must not be blank");
        if (disclosureType.isBlank()) throw new IllegalArgumentException("disclosureType must not be blank");
    }
}
