import { z } from "zod";
import type { PhysicsEntity, PhysicsConfig } from "../types";
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
export declare const MANIFEST_VERSION = "1.0.0";
/**
 * @doc.type schema
 * @doc.purpose Zod schema for manifest validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export declare const physicsManifestSchema: z.ZodObject<{
    version: z.ZodString;
    createdAt: z.ZodString;
    modifiedAt: z.ZodString;
    metadata: z.ZodOptional<z.ZodObject<{
        name: z.ZodOptional<z.ZodString>;
        description: z.ZodOptional<z.ZodString>;
        author: z.ZodOptional<z.ZodString>;
        tags: z.ZodOptional<z.ZodArray<z.ZodString>>;
    }, z.core.$strip>>;
    physics: z.ZodObject<{
        gravity: z.ZodDefault<z.ZodNumber>;
        friction: z.ZodDefault<z.ZodNumber>;
        timeScale: z.ZodDefault<z.ZodNumber>;
        collisionEnabled: z.ZodDefault<z.ZodBoolean>;
        airResistance: z.ZodOptional<z.ZodNumber>;
        debugMode: z.ZodOptional<z.ZodBoolean>;
    }, z.core.$strip>;
    entities: z.ZodArray<z.ZodObject<{
        id: z.ZodString;
        type: z.ZodEnum<typeof import("..").EntityType>;
        x: z.ZodNumber;
        y: z.ZodNumber;
        width: z.ZodOptional<z.ZodNumber>;
        height: z.ZodOptional<z.ZodNumber>;
        radius: z.ZodOptional<z.ZodNumber>;
        rotation: z.ZodOptional<z.ZodNumber>;
        appearance: z.ZodObject<{
            color: z.ZodString;
            strokeColor: z.ZodOptional<z.ZodString>;
            strokeWidth: z.ZodOptional<z.ZodNumber>;
            opacity: z.ZodOptional<z.ZodNumber>;
            shadowBlur: z.ZodOptional<z.ZodNumber>;
            shadowColor: z.ZodOptional<z.ZodString>;
        }, z.core.$strip>;
        physics: z.ZodObject<{
            mass: z.ZodDefault<z.ZodNumber>;
            friction: z.ZodDefault<z.ZodNumber>;
            restitution: z.ZodDefault<z.ZodNumber>;
            isStatic: z.ZodDefault<z.ZodBoolean>;
            angularVelocity: z.ZodOptional<z.ZodNumber>;
            velocity: z.ZodOptional<z.ZodObject<{
                x: z.ZodNumber;
                y: z.ZodNumber;
            }, z.core.$strip>>;
        }, z.core.$strip>;
        metadata: z.ZodOptional<z.ZodRecord<z.ZodString, z.ZodUnknown>>;
    }, z.core.$strip>>;
}, z.core.$strip>;
/**
 * Creates an export manifest from current state
 * @doc.type function
 * @doc.purpose Serialization to manifest format
 * @doc.layer core
 * @doc.pattern Serializer
 */
export declare function createManifest(entities: PhysicsEntity[], physics: PhysicsConfig, metadata?: PhysicsSimulationManifest["metadata"]): PhysicsSimulationManifest;
/**
 * Exports manifest to JSON string
 * @doc.type function
 * @doc.purpose Export utility
 * @doc.layer core
 * @doc.pattern Serializer
 */
export declare function exportManifestToJSON(manifest: PhysicsSimulationManifest): string;
/**
 * Exports manifest as downloadable file
 * @doc.type function
 * @doc.purpose Download utility
 * @doc.layer core
 * @doc.pattern Utility
 */
export declare function downloadManifest(manifest: PhysicsSimulationManifest, filename?: string): void;
/**
 * Parses and validates a manifest from JSON
 * @doc.type function
 * @doc.purpose Import utility with validation
 * @doc.layer core
 * @doc.pattern Deserializer
 */
export declare function parseManifest(json: string): {
    success: boolean;
    manifest?: PhysicsSimulationManifest;
    errors?: z.ZodError;
};
/**
 * Migrates old manifest format to current version
 * @doc.type function
 * @doc.purpose Version migration utility
 * @doc.layer core
 * @doc.pattern Migrator
 */
export declare function migrateManifest(oldManifest: Record<string, unknown>): PhysicsSimulationManifest;
/**
 * Reads a manifest from file input
 * @doc.type function
 * @doc.purpose File read utility
 * @doc.layer core
 * @doc.pattern Utility
 */
export declare function readManifestFromFile(file: File): Promise<{
    success: boolean;
    manifest?: PhysicsSimulationManifest;
    error?: string;
}>;
//# sourceMappingURL=manifest.d.ts.map