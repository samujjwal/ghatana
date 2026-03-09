/**
 * @fileoverview DCMAAR Configuration Presets
 *
 * Shared configuration presets for agent, desktop, and extension.
 * Provides consistent defaults and best practices across the platform.
 *
 * @module @ghatana/dcmaar-config-presets
 */

// Source presets
export * from './sources/browser-events.preset';
export * from './sources/http-polling.preset';
export * from './sources/websocket-stream.preset';
export * from './sources/ipc-bridge.preset';

// Sink presets
export * from './sinks/indexeddb-local.preset';
export * from './sinks/http-ingest.preset';
export * from './sinks/websocket-push.preset';
export * from './sinks/console-debug.preset';

// Processor presets
export * from './processors/validation.preset';
export * from './processors/transformation.preset';
export * from './processors/enrichment.preset';
export * from './processors/redaction.preset';

// Complete configuration presets
export * from './complete/development.preset';
export * from './complete/production.preset';
export * from './complete/testing.preset';

// Types
export * from './types';
