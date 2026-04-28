/**
 * Dialogs Index
 *
 * Centralized exports for standardized dialog components and hooks.
 *
 * @doc.type module
 * @doc.purpose Dialog components barrel export
 * @doc.layer infrastructure
 * @doc.pattern Barrel Export
 */

// Hooks — imported directly from @ghatana/ui (no longer via deprecated local re-export)
export { useDialog, useConfirmDialog } from '@ghatana/design-system';
export type { UseDialogOptions, UseDialogReturn } from '@ghatana/design-system';

// Modal Components
export { StandardModal, ConfirmDialog } from './StandardModal';
export type {
    StandardModalProps,
    ConfirmDialogProps,
} from './StandardModal';

// Drawer Components
export { StandardDrawer, FormDrawer } from './StandardDrawer';
export type {
    StandardDrawerProps,
    FormDrawerProps,
} from './StandardDrawer';

// Phase Gate Dialog (F-Y042 / AI-Y3)
export { PhaseGateDialog } from './PhaseGateDialog';
export type { PhaseGateDialogProps } from './PhaseGateDialog';
