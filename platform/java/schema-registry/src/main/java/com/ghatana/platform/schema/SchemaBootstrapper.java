package com.ghatana.platform.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bootstraps a {@link SchemaRegistry} with well-known platform schemas
 * from a pre-built bundle JSON file.
 *
 * <h2>Bundle Format</h2>
 * <p>The bundle is a JSON object produced by the contracts build, at
 * {@code platform/contracts/build/generated/schemas/bundle.schema.json}:
 * <pre>{@code
 * {
 *   "schemas": [
 *     {
 *       "name":    "OrderCreated",
 *       "version": "1.0.0",
 *       "schema":  { "$schema": "http://json-schema.org/draft-07/schema#", ... }
 *     },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * <h2>Idempotency</h2>
 * <p>Seeds that are already registered (same name + version) are silently skipped.
 * It is safe to call {@link #seedFromBundle} on every startup.
 *
 * <h2>Compatibility Mode</h2>
 * <p>All schemas seeded from the bundle use {@link CompatibilityMode#BACKWARD} by
 * default (the platform default). Per-schema overrides can be specified via the
 * optional {@code "compatibilityMode"} field in each bundle entry.
 *
 * @doc.type class
 * @doc.purpose Seeds a SchemaRegistry from the platform contracts bundle
 * @doc.layer platform
 * @doc.pattern Bootstrap, Factory Method
 */
public final class SchemaBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(SchemaBootstrapper.class);

    /** Default path to the generated schema bundle, relative to workspace root. */
    public static final String DEFAULT_BUNDLE_PATH =
            "platform/contracts/build/generated/schemas/bundle.schema.json";

    /** Classpath fallback for the bundle (e.g. packaged in a JAR). */
    public static final String CLASSPATH_BUNDLE_RESOURCE = "schemas/bundle.schema.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Seeds the registry from the default bundle location
     * ({@value DEFAULT_BUNDLE_PATH}), falling back to the classpath resource.
     *
     * @param registry the registry to seed
     * @return promise that completes when seeding is done (or nothing was seeded)
     */
    @NotNull
    public Promise<Void> seedFromDefaultBundle(@NotNull SchemaRegistry registry) {
        return seedFromBundle(registry, DEFAULT_BUNDLE_PATH);
    }

    /**
     * Seeds the registry from a bundle JSON file located at {@code bundlePath}.
     *
     * <p>If the path does not exist on the filesystem, the method falls back to
     * {@link Class#getResourceAsStream(String)} using the same path. If neither
     * exists, the call is a silent no-op (the registry is considered empty by design).
     *
     * @param registry   the registry to seed
     * @param bundlePath filesystem path (relative or absolute) to the bundle JSON
     * @return promise that completes when all schemas have been registered
     */
    @NotNull
    public Promise<Void> seedFromBundle(
            @NotNull SchemaRegistry registry, @NotNull String bundlePath) {
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(bundlePath, "bundlePath must not be null");

        JsonNode bundleRoot = loadBundle(bundlePath);
        if (bundleRoot == null) {
            log.debug("Schema bundle not found at '{}' — skipping seed", bundlePath);
            return Promise.complete();
        }

        List<Promise<RegisteredSchema>> registrations = new ArrayList<>();
        JsonNode schemas = bundleRoot.get("schemas");
        if (schemas == null || !schemas.isArray()) {
            log.warn("Bundle '{}' has no 'schemas' array — nothing to seed", bundlePath);
            return Promise.complete();
        }

        for (JsonNode schemaNode : schemas) {
            String name = textField(schemaNode, "name");
            String version = textField(schemaNode, "version");
            JsonNode schemaDef = schemaNode.get("schema");

            if (name == null || version == null || schemaDef == null) {
                log.warn("Skipping malformed schema bundle entry: {}", schemaNode);
                continue;
            }

            String jsonSchemaStr;
            try {
                jsonSchemaStr = objectMapper.writeValueAsString(schemaDef);
            } catch (Exception e) {
                log.warn("Failed to serialise schema '{}' from bundle: {}", name, e.getMessage());
                continue;
            }

            CompatibilityMode mode = CompatibilityMode.BACKWARD;
            String modeStr = textField(schemaNode, "compatibilityMode");
            if (modeStr != null) {
                try {
                    mode = CompatibilityMode.valueOf(modeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown compatibilityMode '{}' for schema '{}' — using BACKWARD", modeStr, name);
                }
            }

            final CompatibilityMode finalMode = mode;
            final String fnName = name;
            final String fnVersion = version;
            final String fnSchema = jsonSchemaStr;

            Promise<RegisteredSchema> reg = registry
                    .registerSchema(fnName, fnVersion, fnSchema, finalMode)
                    .whenException(ex -> {
                        if (ex instanceof SchemaCompatibilityException) {
                            log.warn("Schema '{}' v{} cannot be seeded due to compatibility violation: {}",
                                    fnName, fnVersion, ex.getMessage());
                        } else {
                            log.error("Failed to seed schema '{}' v{}: {}", fnName, fnVersion, ex.getMessage());
                        }
                    });
            registrations.add(reg);
        }

        if (registrations.isEmpty()) {
            return Promise.complete();
        }

        return Promises.all(registrations)
                .whenResult(() -> log.info("Schema bundle seed complete: {} schema(s) processed from '{}'",
                        registrations.size(), bundlePath));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Loads a bundle JSON file from the filesystem or classpath.
     *
     * @param bundlePath path to try (filesystem first, then classpath)
     * @return parsed {@link JsonNode} root, or {@code null} if not found
     */
    private JsonNode loadBundle(@NotNull String bundlePath) {
        // 1. Filesystem
        Path fsPath = Path.of(bundlePath);
        if (Files.exists(fsPath)) {
            try {
                return objectMapper.readTree(fsPath.toFile());
            } catch (IOException e) {
                log.warn("Failed to read bundle from '{}': {}", bundlePath, e.getMessage());
            }
        }

        // 2. Classpath fallback
        String resource = bundlePath.startsWith("/") ? bundlePath : "/" + bundlePath;
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is != null) {
                return objectMapper.readTree(is);
            }
        } catch (IOException e) {
            log.warn("Failed to read bundle from classpath '{}': {}", resource, e.getMessage());
        }

        // 3. Standard classpath resource name
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(CLASSPATH_BUNDLE_RESOURCE)) {
            if (is != null) {
                return objectMapper.readTree(is);
            }
        } catch (IOException e) {
            log.warn("Failed to read bundle from classpath '{}': {}", CLASSPATH_BUNDLE_RESOURCE, e.getMessage());
        }

        return null;
    }

    private static String textField(@NotNull JsonNode node, @NotNull String field) {
        JsonNode n = node.get(field);
        return (n != null && n.isTextual()) ? n.asText() : null;
    }
}
