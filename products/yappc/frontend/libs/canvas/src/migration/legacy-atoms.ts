/**
 * Legacy Canvas Atoms Migration Layer
 *
 * Provides backward compatibility for legacy canvas-atoms usage while
 * redirecting to the new unified state system. This allows gradual migration
 * without breaking existing code.
 *
 * NOTE: This file is intentionally lightweight and acts as a compatibility
 * shim. It re-exports atoms from the canonical `state/canvas-atoms` location
 * and provides a small `createMigrationReport` helper to assist migration
 * diagnostics.
 */

export * from '../state/canvas-atoms';

/**
 * Create a simple migration report that lists deprecated/legacy atom names and
 * their recommended replacements in the new unified system.
 */
export function createMigrationReport() {
    return {
        title: 'Canvas Legacy Atoms Migration Report',
        generatedAt: new Date().toISOString(),
        deprecatedAtoms: [
            'canvasStateAtom',
            'nodesAtom',
            'edgesAtom',
            'cameraAtom',
            'snapshotsAtom',
            'historyAtom',
            'canvasIdAtom',
        ],
        replacements: {
            canvasStateAtom: 'canvasStateAtom (same location - unified)',
            nodesAtom: 'nodesAtom (derived from canvasStateAtom)',
            edgesAtom: 'edgesAtom (derived from canvasStateAtom)',
            cameraAtom: 'cameraAtom (derived from canvasStateAtom)',
            snapshotsAtom: 'snapshotsAtom (derived from canvasStateAtom)',
            historyAtom: 'historyAtom (derived from canvasStateAtom)',
            canvasIdAtom: 'canvasIdAtom (derived from canvasStateAtom)',
        },
        guidance:
            'Replace legacy imports with `@ghatana/yappc-canvas` where possible. This file is a temporary shim to ease migration; remove after updating consumers.',
    } as const;
}

export default createMigrationReport;
