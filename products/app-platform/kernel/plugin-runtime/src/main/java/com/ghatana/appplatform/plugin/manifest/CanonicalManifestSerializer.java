package com.ghatana.appplatform.plugin.manifest;

import com.ghatana.appplatform.plugin.domain.PluginManifest;

/**
 * Converts a {@link PluginManifest} to its canonical byte representation for
 * signature verification and checksum computation.
 *
 * <p>The canonical form is a deterministic JSON serialization where object keys are
 * sorted alphabetically and string values are UTF-8 encoded. Implementations must
 * produce bit-identical output for equal manifests.
 *
 * @doc.type  interface
 * @doc.purpose Canonical serialisation contract used by signature and checksum components
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public interface CanonicalManifestSerializer {

    /**
     * Returns the canonical bytes for the given manifest.
     *
     * @param manifest manifest to serialise
     * @return deterministic UTF-8 bytes
     */
    byte[] toCanonicalBytes(PluginManifest manifest);
}
