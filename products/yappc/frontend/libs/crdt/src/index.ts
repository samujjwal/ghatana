/**
 * @ghatana/yappc-crdt
 * 
 * CRDT (Conflict-free Replicated Data Types) library for YAPPC.
 * Enables real-time collaboration with automatic conflict resolution.
 * 
 * @module @ghatana/yappc-crdt
 */

// ============================================================================
// Core CRDT Implementation
// ============================================================================
// Fundamental CRDT data structures and algorithms
export * from './core/index.js';

// ============================================================================
// IDE Integration
// ============================================================================
// CRDT integration for IDE features (code editing, etc.)
export * from './ide/index.js';

// ============================================================================
// WebSocket Utilities
// ============================================================================
// Hooks and helpers for WebSocket-based collaboration
export * from './websocket/index.js';

// ============================================================================
// Conflict Resolution
// ============================================================================
// Advanced conflict resolution strategies and algorithms
export * from './conflict-resolution/index.js';
