/**
 * Physics Renderer - Mechanics & Dynamics
 *
 * @doc.type module
 * @doc.purpose Render physics simulation entities (bodies, springs, vectors, particles)
 * @doc.layer product
 * @doc.pattern Renderer
 */
import type { PhysicsBodyEntity, PhysicsSpringEntity, PhysicsVectorEntity, PhysicsParticleEntity } from "@ghatana/tutorputor-contracts/v1/simulation";
import type { EntityRenderer } from "../types";
/**
 * Renderer for physics rigid bodies.
 */
export declare const physicsBodyRenderer: EntityRenderer<PhysicsBodyEntity>;
/**
 * Renderer for physics springs.
 */
export declare const physicsSpringRenderer: EntityRenderer<PhysicsSpringEntity>;
/**
 * Renderer for physics vectors (force, velocity, acceleration).
 */
export declare const physicsVectorRenderer: EntityRenderer<PhysicsVectorEntity>;
/**
 * Renderer for physics particles.
 */
export declare const physicsParticleRenderer: EntityRenderer<PhysicsParticleEntity>;
export declare const physicsRenderers: EntityRenderer<PhysicsBodyEntity>[];
//# sourceMappingURL=PhysicsRenderer.d.ts.map