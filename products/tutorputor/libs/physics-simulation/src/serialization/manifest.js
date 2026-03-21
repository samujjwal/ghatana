import { z } from "zod";
import { physicsEntitySchema, physicsConfigSchema, } from "../entities/validators";
/**
 * @doc.type constant
 * @doc.purpose Current manifest version
 * @doc.layer core
 * @doc.pattern Constant
 */
export const MANIFEST_VERSION = "1.0.0";
/**
 * @doc.type schema
 * @doc.purpose Zod schema for manifest validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export const physicsManifestSchema = z.object({
    version: z.string(),
    createdAt: z.string(),
    modifiedAt: z.string(),
    metadata: z
        .object({
        name: z.string().optional(),
        description: z.string().optional(),
        author: z.string().optional(),
        tags: z.array(z.string()).optional(),
    })
        .optional(),
    physics: physicsConfigSchema,
    entities: z.array(physicsEntitySchema),
});
/**
 * Creates an export manifest from current state
 * @doc.type function
 * @doc.purpose Serialization to manifest format
 * @doc.layer core
 * @doc.pattern Serializer
 */
export function createManifest(entities, physics, metadata) {
    const now = new Date().toISOString();
    return {
        version: MANIFEST_VERSION,
        createdAt: now,
        modifiedAt: now,
        metadata,
        physics,
        entities,
    };
}
/**
 * Exports manifest to JSON string
 * @doc.type function
 * @doc.purpose Export utility
 * @doc.layer core
 * @doc.pattern Serializer
 */
export function exportManifestToJSON(manifest) {
    return JSON.stringify(manifest, null, 2);
}
/**
 * Exports manifest as downloadable file
 * @doc.type function
 * @doc.purpose Download utility
 * @doc.layer core
 * @doc.pattern Utility
 */
export function downloadManifest(manifest, filename) {
    const json = exportManifestToJSON(manifest);
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || `simulation-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
}
/**
 * Parses and validates a manifest from JSON
 * @doc.type function
 * @doc.purpose Import utility with validation
 * @doc.layer core
 * @doc.pattern Deserializer
 */
export function parseManifest(json) {
    try {
        const parsed = JSON.parse(json);
        const result = physicsManifestSchema.safeParse(parsed);
        if (result.success) {
            return {
                success: true,
                manifest: result.data,
            };
        }
        return { success: false, errors: result.error };
    }
    catch (error) {
        return {
            success: false,
            errors: new z.ZodError([
                {
                    code: "custom",
                    path: [],
                    message: `Invalid JSON: ${error.message}`,
                },
            ]),
        };
    }
}
/**
 * Migrates old manifest format to current version
 * @doc.type function
 * @doc.purpose Version migration utility
 * @doc.layer core
 * @doc.pattern Migrator
 */
export function migrateManifest(oldManifest) {
    const now = new Date().toISOString();
    // Handle v1.0 format (from SimulationBuilderPage)
    if (oldManifest.version === "1.0" && Array.isArray(oldManifest.entities)) {
        const migratedEntities = oldManifest.entities.map((e, index) => ({
            id: `migrated-${Date.now()}-${index}`,
            type: e.type,
            x: e.position?.x ?? e.x ?? 0,
            y: e.position?.y ?? e.y ?? 0,
            width: e.dimensions?.width ?? e.width,
            height: e.dimensions?.height ?? e.height,
            radius: e.dimensions?.radius ?? e.radius,
            rotation: e.rotation ?? 0,
            appearance: {
                color: e.appearance?.color ?? e.color ?? "#3b82f6",
            },
            physics: {
                mass: e.properties?.mass ?? 1,
                friction: e.properties?.friction ?? 0.5,
                restitution: e.properties?.restitution ?? 0.3,
                isStatic: e.properties?.isStatic ?? false,
            },
        }));
        return {
            version: MANIFEST_VERSION,
            createdAt: now,
            modifiedAt: now,
            physics: oldManifest.physics ?? {
                gravity: 9.81,
                friction: 0.5,
                timeScale: 1,
                collisionEnabled: true,
            },
            entities: migratedEntities,
        };
    }
    // Already current version or unknown
    return oldManifest;
}
/**
 * Reads a manifest from file input
 * @doc.type function
 * @doc.purpose File read utility
 * @doc.layer core
 * @doc.pattern Utility
 */
export function readManifestFromFile(file) {
    return new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = (event) => {
            const content = event.target?.result;
            if (!content) {
                resolve({ success: false, error: "File is empty" });
                return;
            }
            try {
                const parsed = JSON.parse(content);
                // Check if migration is needed
                if (parsed.version !== MANIFEST_VERSION) {
                    const migrated = migrateManifest(parsed);
                    resolve({ success: true, manifest: migrated });
                    return;
                }
                const result = parseManifest(content);
                if (result.success) {
                    resolve({ success: true, manifest: result.manifest });
                }
                else {
                    resolve({
                        success: false,
                        error: result.errors?.issues
                            .map((e) => e.message)
                            .join(", "),
                    });
                }
            }
            catch (error) {
                resolve({ success: false, error: error.message });
            }
        };
        reader.onerror = () => {
            resolve({ success: false, error: "Failed to read file" });
        };
        reader.readAsText(file);
    });
}
//# sourceMappingURL=manifest.js.map