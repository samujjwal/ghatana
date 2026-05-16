package com.ghatana.yappc.services.source;

/**
 * @doc.type record
 * @doc.purpose Canonical source locator resolved by Java source providers
 * @doc.layer service
 * @doc.pattern DataTransferObject
 */
public record SourceLocator(
    String provider,
    String repoId,
    String ref,
    String path,
    String credentialRef
) {

    public SourceLocator {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (repoId == null || repoId.isBlank()) {
            throw new IllegalArgumentException("repoId must not be blank");
        }
    }
}
