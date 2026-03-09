package com.ghatana.datacloud.entity.version;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadata for an entity version change.
 *
 * <p><b>Purpose</b><br>
 * Captures author, timestamp, and change reason for version tracking and audit trails.
 * Enables understanding why changes were made and by whom.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VersionMetadata metadata = new VersionMetadata(
 *     "user-456",
 *     Instant.now(),
 *     "Added missing email field for contact validation"
 * );
 * }</pre>
 *
 * <p><b>Immutability</b><br>
 * This record is immutable and thread-safe.
 *
 * @param author the user ID who made the change
 * @param timestamp the exact time of the change
 * @param reason the human-readable change description
 *
 * @doc.type record
 * @doc.purpose Version metadata (author, timestamp, reason)
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record VersionMetadata(
        String author,
        Instant timestamp,
        String reason
) {
    /**
     * Creates VersionMetadata with validation.
     *
     * @param author the user ID who made the change (required)
     * @param timestamp the change timestamp (required)
     * @param reason the change reason (optional, can be null/empty)
     */
    public VersionMetadata {
        Objects.requireNonNull(author, "Author must not be null");
        Objects.requireNonNull(timestamp, "Timestamp must not be null");
    }

    /**
     * Gets a human-readable summary of the version change.
     *
     * @return summary string
     */
    public String getSummary() {
        if (reason == null || reason.isEmpty()) {
            return String.format("Modified by %s at %s", author, timestamp);
        }
        return String.format("%s (by %s at %s)", reason, author, timestamp);
    }
}
