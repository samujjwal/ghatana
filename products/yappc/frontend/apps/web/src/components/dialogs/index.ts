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
export { useDialog, useConfirmDialog } from '@ghatana/ui';
export type { UseDialogOptions, UseDialogReturn } from '@ghatana/ui';

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
