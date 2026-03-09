/**
 * @ghatana/dcmaar-types
 * Shared type definitions for DCMAAR framework
 *
 * This package exports all framework types used across DCMAAR applications.
 * Includes:
 * - Common types: Agent, Event, EventPattern, Config, Storage, Plugin
 * - Error types: AppError, ValidationError classes
 * - Utility types: UUID, Timestamp, Validation helpers
 */
// Re-export all common types
export * from './common';
// Re-export all error types
export * from './errors';
// Re-export all utility types
export * from './utils';
