package com.ghatana.products.yappc.domain.enums;

/**
 * Enumeration of supported cloud providers in the YAPPC platform.
 *
 * <p>This enum defines the major cloud providers that can be connected
 * for security monitoring, compliance checking, and cost analysis.</p>
 *
 * @doc.type enum
 * @doc.purpose Defines the supported cloud infrastructure providers
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum CloudProvider {

    /**
     * Amazon Web Services.
     */
    AWS("Amazon Web Services", "aws"),

    /**
     * Google Cloud Platform.
     */
    GCP("Google Cloud Platform", "gcp"),

    /**
     * Microsoft Azure.
     */
    AZURE("Microsoft Azure", "azure"),

    /**
     * Oracle Cloud Infrastructure.
     */
    OCI("Oracle Cloud Infrastructure", "oci"),

    /**
     * DigitalOcean.
     */
    DIGITAL_OCEAN("DigitalOcean", "do"),

    /**
     * Other/custom cloud provider.
     */
    OTHER("Other", "other");

    private final String displayName;
    private final String shortCode;

    CloudProvider(String displayName, String shortCode) {
        this.displayName = displayName;
        this.shortCode = shortCode;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the short code identifier.
     *
     * @return the short code
     */
    public String getShortCode() {
        return shortCode;
    }

    /**
     * Checks if this is a major cloud provider (AWS, GCP, Azure).
     *
     * @return true if major provider
     */
    public boolean isMajorProvider() {
        return this == AWS || this == GCP || this == AZURE;
    }

    /**
     * Finds a CloudProvider by its short code.
     *
     * @param shortCode the short code to match
     * @return the matching CloudProvider or OTHER if not found
     */
    public static CloudProvider fromShortCode(String shortCode) {
        for (CloudProvider provider : values()) {
            if (provider.shortCode.equalsIgnoreCase(shortCode)) {
                return provider;
            }
        }
        return OTHER;
    }
}
