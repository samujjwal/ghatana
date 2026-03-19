/**
 * CRDT primitives — absorbed from @yappc/crdt.
 *
 * Re-exports vector-clock, conflict-resolution, and IDE-integration
 * CRDT utilities. Consumers that previously imported from @yappc/crdt
 * should migrate to @yappc/collab/crdt.
 *
 * @deprecated @yappc/crdt — Use @yappc/collab/crdt instead
 */
export * from './core/index.js';
export * from './conflict-resolution/index.js';
export * from './ide/index.js';
