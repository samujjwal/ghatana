package com.ghatana.appplatform.config.bundle;

import com.ghatana.appplatform.config.domain.ConfigSchema;

import java.util.List;
import java.util.Objects;

/**
 * Complete config bundle for air-gap deployment.
 *
 * <p>A bundle captures a consistent snapshot of all config schemas and entries
 * for a target environment at a point in time. It can be exported, transferred
 * offline, and imported into an air-gapped system.
 *
 * @param manifest manifest with provenance, counts, hash, and optional signature
 * @param schemas  all schema definitions included in this bundle
 * @param entries  all config entries included in this bundle
 *
 * @doc.type record
 * @doc.purpose Complete air-gap config bundle (K02-012)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConfigBundle(
    ConfigBundleManifest manifest,
    List<ConfigSchema>      schemas,
    List<ConfigBundleEntry> entries
) {
    public ConfigBundle {
        Objects.requireNonNull(manifest, "manifest");
        schemas = List.copyOf(Objects.requireNonNull(schemas, "schemas"));
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /** Returns a copy of this bundle with the given manifest (e.g. to attach a signature). */
    public ConfigBundle withManifest(ConfigBundleManifest updatedManifest) {
        return new ConfigBundle(updatedManifest, schemas, entries);
    }
}
