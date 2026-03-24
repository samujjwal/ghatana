/**
 * Advanced Interactions Type Definitions
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 *
 * Defines types for modal management, floating UI elements,
 * and advanced interaction patterns.
 *
 * @module interactions/types
 */

import type { Placement } from '@floating-ui/react';
import type { ReactNode, ComponentType } from 'react';

// ============================================================================
// Modal System Types
// ============================================================================

/**
 * Modal configuration options
 */
export interface ModalOptions {
  /**
   * Close modal on Escape key
   * @default true
   */
  closeOnEscape?: boolean;

  /**
   * Close modal on backdrop click
   * @default true
   */
  closeOnBackdrop?: boolean;

  /**
   * Prevent modal from closing (persistent)
   * @default false
   */
  persistent?: boolean;

  /**
   * Z-index priority for stacking
   * Higher values appear on top
   * @default 1000
   */
  priority?: number;

  /**
   * Lock body scroll when modal is open
   * @default true
   */
  lockScroll?: boolean;

  /**
   * Trap focus within modal
   * @default true
   */
  trapFocus?: boolean;

  /**
   * Modal width
   */
  width?: string | number;

  /**
   * Modal max width
   */
  maxWidth?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | false;

  /**
   * Full screen modal
   * @default false
   */
  fullScreen?: boolean;
}

/**
 * Modal configuration for registry
 */
export interface ModalConfig<P = unknown> {
  /**
   * Unique modal identifier
   */
  id: string;

  /**
   * Modal component to render
   */
  component: ComponentType<P>;

  /**
   * Default props for modal
   */
  defaultProps?: Partial<P>;

  /**
   * Modal options
   */
  options?: ModalOptions;
}

/**
 * Active modal instance
 */
export interface ActiveModal {
  /**
   * Modal ID
   */
  id: string;

  /**
   * Runtime props
   */
  props?: Record<string, unknown>;

  /**
   * Modal options (merged with defaults)
   */
  options: Required<ModalOptions>;

  /**
   * Timestamp when opened
   */
  openedAt: number;
}

// ============================================================================
// Dialog Types
// ============================================================================

/**
 * Dialog action button configuration
 */
export interface DialogAction {
  /**
   * Button label
   */
  label: string;

  /**
   * Click handler
   */
  onClick: () => void | Promise<void>;

  /**
   * Button variant
   * @default 'text'
   */
  variant?: 'text' | 'outlined' | 'contained';

  /**
   * Button color
   * @default 'primary'
   */
  color?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';

  /**
   * Show loading state
   * @default false
   */
  loading?: boolean;

  /**
   * Disable button
   * @default false
   */
  disabled?: boolean;

  /**
   * Auto-focus this button
   * @default false
   */
  autoFocus?: boolean;
}

/**
 * Dialog options
 */
export interface DialogOptions {
  /**
   * Dialog title
   */
  title?: ReactNode;

  /**
   * Dialog message/content
   */
  message?: ReactNode;

  /**
   * Custom content (overrides message)
   */
  content?: ReactNode;

  /**
   * Action buttons
   */
  actions?: DialogAction[];

  /**
   * Close dialog after action
   * @default true
   */
  closeOnAction?: boolean;

  /**
   * Close dialog on backdrop click
   * @default false
   */
  closeOnBackdrop?: boolean;

  /**
   * Dialog width
   */
  width?: string | number;

  /**
   * Dialog max width
   */
  maxWidth?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | false;
}

/**
 * Confirmation dialog result
 */
export interface ConfirmResult {
  /**
   * User confirmed
   */
  confirmed: boolean;

  /**
   * Which action was clicked (button index)
   */
  actionIndex?: number;
}

// ============================================================================
// Tooltip Types
// ============================================================================

/**
 * Tooltip placement
 */
export type TooltipPlacement = Placement;

/**
 * Tooltip trigger event
 */
export type TooltipTrigger = 'hover' | 'focus' | 'click' | 'manual';

/**
 * Tooltip options
 */
export interface TooltipOptions {
  /**
   * Tooltip placement
   * @default 'top'
   */
  placement?: TooltipPlacement;

  /**
   * Offset from target element (pixels)
   * @default 8
   */
  offset?: number;

  /**
   * Delay before showing (milliseconds)
   * @default 200
   */
  delayShow?: number;

  /**
   * Delay before hiding (milliseconds)
   * @default 0
   */
  delayHide?: number;

  /**
   * Trigger events
   * @default 'hover'
   */
  trigger?: TooltipTrigger | TooltipTrigger[];

  /**
   * Allow interaction with tooltip content
   * @default false
   */
  interactive?: boolean;

  /**
   * Show arrow pointer
   * @default true
   */
  arrow?: boolean;

  /**
   * Max width of tooltip
   */
  maxWidth?: number | string;

  /**
   * Disable tooltip
   * @default false
   */
  disabled?: boolean;
}

// ============================================================================
// Popover Types
// ============================================================================

/**
 * Popover placement
 */
export type PopoverPlacement = Placement;

/**
 * Popover trigger event
 */
export type PopoverTrigger = 'click' | 'hover' | 'focus' | 'manual';

/**
 * Popover options
 */
export interface PopoverOptions {
  /**
   * Popover placement
   * @default 'bottom'
   */
  placement?: PopoverPlacement;

  /**
   * Offset from trigger element (pixels)
   * @default 8
   */
  offset?: number;

  /**
   * Trigger event
   * @default 'click'
   */
  trigger?: PopoverTrigger | PopoverTrigger[];

  /**
   * Close on click outside
   * @default true
   */
  closeOnClickOutside?: boolean;

  /**
   * Close on Escape key
   * @default true
   */
  closeOnEscape?: boolean;

  /**
   * Show arrow pointer
   * @default false
   */
  arrow?: boolean;

  /**
   * Popover width
   */
  width?: number | string;

  /**
   * Popover max width
   */
  maxWidth?: number | string;

  /**
   * Prevent popover from flipping
   * @default false
   */
  preventFlip?: boolean;

  /**
   * Modal behavior (blocks interaction outside)
   * @default false
   */
  modal?: boolean;

  /**
   * Render in portal
   * @default true
   */
  portal?: boolean;

  /**
   * Initial focus target (element index or ref)
   * @default 0
   */
  initialFocus?: number | React.RefObject<HTMLElement>;

  /**
   * Return focus to trigger on close
   * @default true
   */
  returnFocus?: boolean;

  /**
   * Disable popover
   * @default false
   */
  disabled?: boolean;

  /**
   * Callback when popover opens/closes
   */
  onOpenChange?: (open: boolean) => void;
}

// ============================================================================
// Drawer Types
// ============================================================================

/**
 * Drawer anchor position
 */
export type DrawerAnchor = 'left' | 'right' | 'top' | 'bottom';

/**
 * Drawer variant
 */
export type DrawerVariant = 'temporary' | 'persistent' | 'permanent';

/**
 * Drawer props
 */
export interface DrawerProps {
  /**
   * Drawer is open
   */
  open: boolean;

  /**
   * Close handler
   */
  onClose: () => void;

  /**
   * Drawer anchor position
   * @default 'left'
   */
  anchor?: DrawerAnchor;

  /**
   * Drawer variant
   * @default 'temporary'
   */
  variant?: DrawerVariant;

  /**
   * Drawer width (for left/right)
   */
  width?: number | string;

  /**
   * Drawer height (for top/bottom)
   */
  height?: number | string;

  /**
   * Drawer content
   */
  children: ReactNode;

  /**
   * Close on backdrop click
   * @default true (temporary), false (persistent/permanent)
   */
  closeOnBackdrop?: boolean;

  /**
   * Close on Escape key
   * @default true
   */
  closeOnEscape?: boolean;

  /**
   * Show backdrop
   * @default true (temporary), false (persistent/permanent)
   */
  showBackdrop?: boolean;

  /**
   * Elevation level
   * @default 16
   */
  elevation?: number;

  /**
   * Custom class name
   */
  className?: string;
}

// ============================================================================
// BottomSheet Types
// ============================================================================

/**
 * Snap point definition
 * Can be a number (0-1 as fraction, >1 as pixels) or percentage string
 */
export type SnapPoint = number | string;

/**
 * BottomSheet props
 */
export interface BottomSheetProps {
  /**
   * Sheet is open
   */
  open: boolean;

  /**
   * Close handler
   */
  onClose: () => void;

  /**
   * Snap points (0-1 as fraction of viewport height)
   * @default [0.25, 0.5, 0.9]
   */
  snapPoints?: SnapPoint[];

  /**
   * Default snap point (index or value)
   * @default Middle snap point
   */
  defaultSnap?: number;

  /**
   * Current snap point (controlled)
   */
  snapPoint?: number;

  /**
   * Snap point change handler
   */
  onSnapPointChange?: (snapPoint: number) => void;

  /**
   * Enable swipe to close gesture
   * @default true
   */
  swipeToClose?: boolean;

  /**
   * Swipe velocity threshold (pixels/ms)
   * @default 0.5
   */
  swipeVelocityThreshold?: number;

  /**
   * Sheet content
   */
  children: ReactNode;

  /**
   * Close on backdrop click
   * @default true
   */
  closeOnBackdrop?: boolean;

  /**
   * Show drag handle
   * @default true
   */
  showHandle?: boolean;

  /**
   * Custom class name
   */
  className?: string;

  /**
   * Disable drag interactions
   * @default false
   */
  disableDrag?: boolean;
}

// ============================================================================
// Utility Types
// ============================================================================

/**
 * Position coordinates
 */
export interface Position {
  x: number;
  y: number;
}

/**
 * Size dimensions
 */
export interface Size {
  width: number;
  height: number;
}

/**
 * Rect/bounds information
 */
export interface Bounds extends Position, Size {}

/**
 * Focus trap options
 */
export interface FocusTrapOptions {
  /**
   * Initial focus element
   */
  initialFocus?: HTMLElement | (() => HTMLElement);

  /**
   * Return focus on unmount
   * @default true
   */
  returnFocus?: boolean;

  /**
   * Allow outside clicks
   * @default false
   */
  allowOutsideClick?: boolean;
}
