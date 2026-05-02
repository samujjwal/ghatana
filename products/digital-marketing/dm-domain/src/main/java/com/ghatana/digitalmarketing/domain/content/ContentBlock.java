package com.ghatana.digitalmarketing.domain.content;

import java.util.Objects;

/**
 * An ordered building block within a content version (headline, body copy, CTA, disclaimer, etc.).
 *
 * <p>A content version is composed of one or more ordered {@code ContentBlock} items.
 * Blocks allow fine-grained composition and selective replacement in AI generation flows.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS content block value object for compositional content versions
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ContentBlock(
        String blockId,
        String blockType,
        String bodyText,
        int ordering) {

    /**
     * Compact constructor — validates all fields.
     */
    public ContentBlock {
        Objects.requireNonNull(blockId, "blockId must not be null");
        Objects.requireNonNull(blockType, "blockType must not be null");
        Objects.requireNonNull(bodyText, "bodyText must not be null");
        if (blockId.isBlank()) throw new IllegalArgumentException("blockId must not be blank");
        if (blockType.isBlank()) throw new IllegalArgumentException("blockType must not be blank");
        if (ordering < 0) throw new IllegalArgumentException("ordering must not be negative");
    }
}
