package com.ghatana.datacloud.event.spi.secrets;

import java.util.Objects;

public final class SecretReference {

    public static final String PREFIX = "secret:";

    private final String provider;
    private final String locator;

    private SecretReference(String provider, String locator) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.locator = Objects.requireNonNull(locator, "locator");
    }

    public String provider() {
        return provider;
    }

    public String locator() {
        return locator;
    }

    public static boolean isSecretReference(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static SecretReference parse(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new SecretResolutionException("Secret reference is blank");
        }
        if (!reference.startsWith(PREFIX)) {
            throw new SecretResolutionException("Invalid secret reference (missing 'secret:' prefix): " + reference);
        }

        String remainder = reference.substring(PREFIX.length());
        int idx = remainder.indexOf(':');
        if (idx <= 0 || idx == remainder.length() - 1) {
            throw new SecretResolutionException(
                    "Invalid secret reference format. Expected 'secret:<provider>:<locator>', got: " + reference);
        }

        String provider = remainder.substring(0, idx).trim();
        String locator = remainder.substring(idx + 1).trim();

        if (provider.isEmpty()) {
            throw new SecretResolutionException("Secret provider is empty in reference: " + reference);
        }
        if (locator.isEmpty()) {
            throw new SecretResolutionException("Secret locator is empty in reference: " + reference);
        }

        return new SecretReference(provider, locator);
    }

    @Override
    public String toString() {
        return PREFIX + provider + ":" + locator;
    }
}
