/**
 * @tutorputor/validation
 *
 * Shared Zod validation schemas for the TutorPutor platform.
 * Eliminates duplicate schema definitions across the platform service and apps.
 *
 * @doc.type module
 * @doc.purpose Barrel export for @tutorputor/validation
 * @doc.layer product
 * @doc.pattern Facade
 */

export * from "./schemas/common.js";
export * from "./schemas/auth.js";
export * from "./schemas/learning.js";
export * from "./schemas/content-studio.js";
