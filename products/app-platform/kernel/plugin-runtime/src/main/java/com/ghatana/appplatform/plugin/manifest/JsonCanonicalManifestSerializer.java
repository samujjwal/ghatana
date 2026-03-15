package com.ghatana.appplatform.plugin.manifest;

import com.ghatana.appplatform.plugin.domain.PluginManifest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Produces a stable, deterministic JSON serialization of a {@link PluginManifest}
 * for use in signature verification and integrity checks.
 *
 * <p>The output canon sorts all JSON object keys alphabetically and omits the
 * {@code signature} field (which is not part of the signed payload). All fields
 * are always present; {@code null} values are serialized as JSON {@code null}.
 *
 * @doc.type  class
 * @doc.purpose Deterministic JSON serialisation for manifest signing/verification
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public final class JsonCanonicalManifestSerializer implements CanonicalManifestSerializer {

    @Override
    public byte[] toCanonicalBytes(PluginManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");

        // Build canonical JSON manually for determinism — no external library dependency.
        // Keys sorted alphabetically; signature and signingKeyId omitted from payload.
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendStr(sb, "artifactChecksum", manifest.artifactChecksum()); sb.append(',');
        appendStr(sb, "author",           manifest.author());           sb.append(',');

        sb.append(jsonKey("capabilities")).append('[');
        List<String> capNames = manifest.capabilities().stream()
                .map(c -> c.parameter() != null
                        ? jsonStr(c.name()) + ":" + jsonStr(c.parameter())
                        : jsonStr(c.name()))
                .collect(Collectors.toList());
        sb.append(String.join(",", capNames));
        sb.append(']').append(',');

        appendStr(sb, "dependsOn", manifest.dependsOn() == null ? null
                : "[" + manifest.dependsOn().stream().map(this::jsonStr)
                      .collect(Collectors.joining(",")) + "]");
        sb.append(',');
        appendStr(sb, "entryPoint",            manifest.entryPoint());           sb.append(',');
        appendStr(sb, "migrationScript",        manifest.migrationScript());     sb.append(',');
        appendStr(sb, "name",                   manifest.name());                sb.append(',');
        appendStr(sb, "platformVersionRange",   manifest.platformVersionRange());sb.append(',');

        // resourceQuota sub-object
        sb.append(jsonKey("resourceQuota")).append('{');
        if (manifest.resourceQuota() != null) {
            sb.append(jsonKey("maxApiCallsPerMinute")).append(manifest.resourceQuota().maxApiCallsPerMinute()).append(',');
            sb.append(jsonKey("maxCpuMs")).append(manifest.resourceQuota().maxCpuMs()).append(',');
            sb.append(jsonKey("maxMemoryMb")).append(manifest.resourceQuota().maxMemoryMb()).append(',');
            sb.append(jsonKey("maxPayloadKb")).append(manifest.resourceQuota().maxPayloadKb());
        }
        sb.append('}').append(',');

        appendStr(sb, "tier",    manifest.tier()    == null ? null : manifest.tier().name()); sb.append(',');
        appendStr(sb, "version", manifest.version() == null ? null : manifest.version().toString());

        sb.append('}');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendStr(StringBuilder sb, String key, String value) {
        sb.append(jsonKey(key));
        if (value == null) sb.append("null");
        else sb.append(jsonStr(value));
    }

    private String jsonKey(String key) {
        return "\"" + key + "\":";
    }

    private String jsonStr(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
