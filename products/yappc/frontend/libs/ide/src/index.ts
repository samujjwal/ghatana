/**
 * @ghatana/yappc-ide - Collaborative Polyglot IDE Core Package
 * 
 * ⚠️ DEPRECATION NOTICE ⚠️
 * This package is deprecated and will be removed in a future release.
 * 
 * Migration Path:
 * - All IDE components have been moved to @ghatana/yappc-canvas
 * - Import from @ghatana/yappc-canvas instead
 * 
 * Timeline:
 * - Sunset Date: 2026-06-06 (90 days from now)
 * - Phase 1 (Now): Compatibility layer with deprecation warnings
 * - Phase 2 (Week 4-6): Shared component extraction
 * - Phase 3 (Week 7-8): Complete removal
 * 
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md for full migration guide
 * 
 * @deprecated Use @ghatana/yappc-canvas instead
 * @doc.type module
 * @doc.purpose IDE core package exports (DEPRECATED)
 * @doc.layer product
 * @doc.pattern Package Entry Point
 */

// Emit deprecation warning when module is loaded
if (typeof console !== 'undefined') {
  console.warn(
    '[DEPRECATION] @ghatana/yappc-ide is deprecated and will be removed on 2026-06-06. ' +
    'Please migrate to @ghatana/yappc-canvas. ' +
    'See: /docs/LIBRARY_CONSOLIDATION_PLAN.md'
  );
}

// Types
export * from './types';

// State Management
export * from './state';

// Hooks
export * from './hooks';

// Utilities
export * from './utils';

// Components
export * from './components';

// CRDT Integration (export stable helpers only to avoid duplicate type re-exports)
export { createInitialIDEState, fileToCRDT, crdtToFile, folderToCRDT, crdtToFolder } from './crdt';

// Services
export * from './services';

// Version
export { IDE_VERSION } from './types';
