/**
 * Agent sub-module — shared types for DCMAAR agent applications.
 *
 * @doc.type module
 * @doc.purpose Agent application type definitions (API, config, and JSON utilities)
 * @doc.layer product
 * @doc.pattern TypeModule
 */

export * from './config';
export * from './api';

// Re-export common JSON utility types from type-fest
export type { JsonValue, JsonObject, JsonArray } from 'type-fest';
