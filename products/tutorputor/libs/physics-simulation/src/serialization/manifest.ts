import { z } from "zod";
import type { PhysicsEntity, PhysicsConfig } from "../types";
import {
  physicsEntitySchema,
  physicsConfigSchema,
} from "../entities/validators";

/**
 * @doc.type interface
 * @doc.purpose Simulation manifest format for import/export
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export interface PhysicsSimulationManifest {
  /** Manifest version for compatibility */
  version: string;
  /** Manifest creation timestamp */
  createdAt: string;
  /** Last modification timestamp */
  modifiedAt: string;
  /** Optional simulation metadata */
  metadata?: {
    name?: string;
    description?: string;
    author?: string;
    tags?: string[];
  };
  /** Physics world configuration */
  physics: PhysicsConfig;
  /** All entities in the simulation */
  entities: PhysicsEntity[];
}

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
export function createManifest(
  entities: PhysicsEntity[],
  physics: PhysicsConfig,
  metadata?: PhysicsSimulationManifest["metadata"],
): PhysicsSimulationManifest {
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
export function exportManifestToJSON(
  manifest: PhysicsSimulationManifest,
): string {
  return JSON.stringify(manifest, null, 2);
}

/**
 * Exports manifest as downloadable file
 * @doc.type function
 * @doc.purpose Download utility
 * @doc.layer core
 * @doc.pattern Utility
 */
export function downloadManifest(
  manifest: PhysicsSimulationManifest,
  filename?: string,
): void {
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
export function parseManifest(json: string): {
  success: boolean;
  manifest?: PhysicsSimulationManifest;
  errors?: z.ZodError;
} {
  try {
    const parsed = JSON.parse(json);
    const result = physicsManifestSchema.safeParse(parsed);

    if (result.success) {
      return {
        success: true,
        manifest: result.data as PhysicsSimulationManifest,
      };
    }
    return { success: false, errors: result.error };
  } catch (error) {
    return {
      success: false,
      errors: new z.ZodError([
        {
          code: "custom",
          path: [],
          message: `Invalid JSON: ${(error as Error).message}`,
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
export function migrateManifest(
  oldManifest: Record<string, unknown>,
): PhysicsSimulationManifest {
  const now = new Date().toISOString();

  // Handle v1.0 format (from SimulationBuilderPage)
  if (oldManifest.version === "1.0" && Array.isArray(oldManifest.entities)) {
    const migratedEntities: PhysicsEntity[] = (
      oldManifest.entities as any[]
    ).map((e: any, index: number) => ({
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
      physics: (oldManifest.physics as PhysicsConfig) ?? {
        gravity: 9.81,
        friction: 0.5,
        timeScale: 1,
        collisionEnabled: true,
      },
      entities: migratedEntities,
    };
  }

  // Already current version or unknown
  return oldManifest as unknown as PhysicsSimulationManifest;
}

/**
 * Reads a manifest from file input
 * @doc.type function
 * @doc.purpose File read utility
 * @doc.layer core
 * @doc.pattern Utility
 */
export function readManifestFromFile(file: File): Promise<{
  success: boolean;
  manifest?: PhysicsSimulationManifest;
  error?: string;
}> {
  return new Promise((resolve) => {
    const reader = new FileReader();

    reader.onload = (event) => {
      const content = event.target?.result as string;
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
        } else {
          resolve({
            success: false,
            error: result.errors?.issues
              .map((e: z.ZodIssue) => e.message)
              .join(", "),
          });
        }
      } catch (error) {
        resolve({ success: false, error: (error as Error).message });
      }
    };

    reader.onerror = () => {
      resolve({ success: false, error: "Failed to read file" });
    };

    reader.readAsText(file);
  });
}
