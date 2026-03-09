/**
 * Advanced Interactions Module
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 *
 * Exports for modal management, floating UI elements,
 * and advanced interaction patterns.
 *
 * @module interactions
 */

// ============================================================================
// Types
// ============================================================================

export * from './types';

// ============================================================================
// Modal Management
// ============================================================================

export { ModalManager } from './ModalManager';
export {
  modalRegistryAtom,
  activeModalsAtom,
  topModalIdAtom,
  modalStackAtom,
  modalCountAtom,
  useModal,
  useModalStack,
  useModalRegistration,
  useModalKeyboard,
  useModalZIndex,
} from './ModalManager';

// ============================================================================
// Hooks
// ============================================================================

export {
  useDialog,
  showAlert,
  showConfirm,
  showDeleteConfirm,
} from './hooks/useDialog';

export {
  useTooltip,
  useTooltipState,
  useTooltipGroup,
  useDelayedTooltip,
} from './hooks/useTooltip';

export {
  usePopover,
  useControlledPopover,
  usePopoverGroup,
  useNestedPopover,
  useConfirmPopover,
} from './hooks/usePopover';

// ============================================================================
// Components
// ============================================================================

export { InteractionDrawer } from './components/Drawer';
export { BottomSheet } from './components/BottomSheet';
