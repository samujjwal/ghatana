import { z } from 'zod';
import { EntityType } from '../types';

/**
 * @doc.type schema
 * @doc.purpose Zod schema for physics properties validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export const physicsPropertiesSchema = z.object({
    mass: z.number().min(0).default(1),
    friction: z.number().min(0).max(1).default(0.5),
    restitution: z.number().min(0).max(1).default(0.3),
    isStatic: z.boolean().default(false),
    angularVelocity: z.number().optional(),
    velocity: z
        .object({
            x: z.number(),
            y: z.number(),
        })
        .optional(),
});

/**
 * @doc.type schema
 * @doc.purpose Zod schema for entity appearance validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export const entityAppearanceSchema = z.object({
    color: z.string().regex(/^#[0-9A-Fa-f]{6}$|^[a-zA-Z]+$/),
    strokeColor: z.string().optional(),
    strokeWidth: z.number().min(0).optional(),
    opacity: z.number().min(0).max(1).optional(),
    shadowBlur: z.number().min(0).optional(),
    shadowColor: z.string().optional(),
});

/**
 * @doc.type schema
 * @doc.purpose Zod schema for physics entity validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export const physicsEntitySchema = z.object({
    id: z.string().min(1),
    type: z.nativeEnum(EntityType),
    x: z.number(),
    y: z.number(),
    width: z.number().positive().optional(),
    height: z.number().positive().optional(),
    radius: z.number().positive().optional(),
    rotation: z.number().optional(),
    appearance: entityAppearanceSchema,
    physics: physicsPropertiesSchema,
    metadata: z.record(z.unknown()).optional(),
});

/**
 * @doc.type schema
 * @doc.purpose Zod schema for physics config validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export const physicsConfigSchema = z.object({
    gravity: z.number().min(0).max(100).default(9.81),
    friction: z.number().min(0).max(1).default(0.5),
    timeScale: z.number().min(0.1).max(10).default(1),
    collisionEnabled: z.boolean().default(true),
    airResistance: z.number().min(0).max(1).optional(),
    debugMode: z.boolean().optional(),
});

/**
 * Validates a physics entity
 * @doc.type function
 * @doc.purpose Entity validation utility
 * @doc.layer core
 * @doc.pattern Validator
 */
export function validateEntity(entity: unknown): {
    success: boolean;
    data?: z.infer<typeof physicsEntitySchema>;
    errors?: z.ZodError;
} {
    const result = physicsEntitySchema.safeParse(entity);
    if (result.success) {
        return { success: true, data: result.data };
    }
    return { success: false, errors: result.error };
}

/**
 * Validates physics configuration
 * @doc.type function
 * @doc.purpose Config validation utility
 * @doc.layer core
 * @doc.pattern Validator
 */
export function validatePhysicsConfig(config: unknown): {
    success: boolean;
    data?: z.infer<typeof physicsConfigSchema>;
    errors?: z.ZodError;
} {
    const result = physicsConfigSchema.safeParse(config);
    if (result.success) {
        return { success: true, data: result.data };
    }
    return { success: false, errors: result.error };
}

/**
 * Type guard for EntityType
 * @doc.type function
 * @doc.purpose Type guard utility
 * @doc.layer core
 * @doc.pattern Guard
 */
export function isValidEntityType(type: string): type is EntityType {
    return Object.values(EntityType).includes(type as EntityType);
}
