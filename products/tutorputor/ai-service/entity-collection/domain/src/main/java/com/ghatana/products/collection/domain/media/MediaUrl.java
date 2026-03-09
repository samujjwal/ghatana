package com.ghatana.products.collection.domain.media;

import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing a media file access URL.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates URL access details including public vs. signed URLs,
 * expiration times, and security metadata. Enables safe URL sharing
 * with time-limited access for private content.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Public URL (permanent)
 * MediaUrl publicUrl = MediaUrl.publicUrl(
 *     "https://cdn.example.com/tenant-123/entity-456/image.jpg");
 * 
 * // Private signed URL (expires after 1 hour)
 * MediaUrl privateUrl = MediaUrl.signedUrl(
 *     "https://s3.amazonaws.com/bucket/file.jpg?signature=...",
 *     Instant.now().plus(Duration.ofHours(1)));
 * 
 * // Check if URL is still valid
 * if (privateUrl.isExpired()) {
 *     // Regenerate URL
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe by design.
 *
 * @see MediaStore#generateUrl(String, java.time.Duration)
 * @see MediaAttachment
 * @doc.type class
 * @doc.purpose Value object for media access URLs
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class MediaUrl {

    /** Full access URL (public or signed) */
    private final String url;

    /** Whether this is a public (permanent) URL */
    private final boolean isPublic;

    /** Expiration time for signed URLs (null for public URLs) */
    private final Instant expiresAt;

    /**
     * Creates a public (permanent) URL.
     *
     * <p>Public URLs do not expire and can be cached indefinitely.
     * Use for CDN-backed content with public read access.
     *
     * @param url public URL
     * @return MediaUrl instance
     * @throws IllegalArgumentException if url is null or blank
     */
    public static MediaUrl publicUrl(String url) {
        Objects.requireNonNull(url, "url cannot be null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url cannot be blank");
        }
        return new MediaUrl(url, true, null);
    }

    /**
     * Creates a signed (expiring) URL.
     *
     * <p>Signed URLs expire after a specified time. Use for private
     * content that requires temporary access grants.
     *
     * @param url signed URL with security token
     * @param expiresAt expiration timestamp
     * @return MediaUrl instance
     * @throws IllegalArgumentException if url is null/blank or expiresAt is null
     */
    public static MediaUrl signedUrl(String url, Instant expiresAt) {
        Objects.requireNonNull(url, "url cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url cannot be blank");
        }
        return new MediaUrl(url, false, expiresAt);
    }

    private MediaUrl(String url, boolean isPublic, Instant expiresAt) {
        this.url = url;
        this.isPublic = isPublic;
        this.expiresAt = expiresAt;
    }

    /**
     * Checks if this URL has expired.
     *
     * <p>Public URLs never expire. Signed URLs expire based on timestamp.
     *
     * @return true if signed URL has expired, false otherwise
     */
    public boolean isExpired() {
        if (isPublic) {
            return false;
        }
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Gets seconds until expiration.
     *
     * <p>Returns -1 for public URLs (never expire).
     * Returns 0 if already expired.
     *
     * @return seconds until expiration, or -1 if public
     */
    public long getSecondsUntilExpiry() {
        if (isPublic) {
            return -1;
        }
        if (expiresAt == null) {
            return -1;
        }
        long seconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, seconds);
    }

    public String getUrl() {
        return url;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaUrl mediaUrl = (MediaUrl) o;
        return isPublic == mediaUrl.isPublic &&
                Objects.equals(url, mediaUrl.url) &&
                Objects.equals(expiresAt, mediaUrl.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, isPublic, expiresAt);
    }

    @Override
    public String toString() {
        if (isPublic) {
            return "MediaUrl{url='" + url + "', type=PUBLIC}";
        } else {
            return "MediaUrl{url='" + url + "', type=SIGNED, expiresAt=" + expiresAt + "}";
        }
    }
}
