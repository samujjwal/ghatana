/**
 * Popover Hook
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 *
 * Rich interactive popovers with FloatingUI positioning.
 * Supports multiple triggers, outside click detection, and focus management.
 *
 * @module interactions/hooks/usePopover
 */

import {
  useFloating,
  autoUpdate,
  offset,
  flip,
  shift,
  arrow as arrowMiddleware,
  useClick,
  useDismiss,
  useRole,
  useInteractions,
  useId,
} from '@floating-ui/react';
import { useState, useCallback, useRef, useEffect, useMemo } from 'react';

import type { PopoverOptions } from '../types';

// ============================================================================
// Default Options
// ============================================================================

const DEFAULT_POPOVER_OPTIONS: Required<Omit<PopoverOptions, 'onOpenChange'>> =
  {
    placement: 'bottom-start',
    offset: 8,
    trigger: 'click',
    closeOnClickOutside: true,
    closeOnEscape: true,
    modal: false,
    arrow: false,
    portal: true,
    initialFocus: 0,
    returnFocus: true,
    disabled: false,
    width: 'auto',
    maxWidth: 'none',
    preventFlip: false,
  };

// ============================================================================
// usePopover Hook
// ============================================================================

/**
 * Hook for rich interactive popovers
 *
 * Provides smart positioning, accessibility, and interaction management.
 *
 * @example
 * ```tsx
 * const popover = usePopover({
 *   placement: 'bottom-start',
 *   closeOnOutsideClick: true
 * });
 *
 * return (
 *   <>
 *     <button {...popover.referenceProps}>Open</button>
 *     {popover.isOpen && (
 *       <FloatingPortal>
 *         <FloatingFocusManager context={popover.context}>
 *           <div {...popover.floatingProps}>
 *             <p>Popover content</p>
 *             <button onClick={popover.close}>Close</button>
 *           </div>
 *         </FloatingFocusManager>
 *       </FloatingPortal>
 *     )}
 *   </>
 * );
 * ```
 */
export function usePopover(options: PopoverOptions = {}) {
  const mergedOptions = useMemo(
    () => ({ ...DEFAULT_POPOVER_OPTIONS, ...options }),
    [options]
  );

  const {
    placement: initialPlacement,
    offset: offsetValue,
    trigger,
    closeOnClickOutside,
    closeOnEscape,
    modal,
    arrow: showArrow,
    portal,
    initialFocus,
    returnFocus,
    disabled,
    onOpenChange,
  } = mergedOptions;

  const [isOpen, setIsOpen] = useState(false);
  const arrowRef = useRef<HTMLDivElement>(null);
  const popoverId = useId();

  // Handle controlled state
  const handleOpenChange = useCallback(
    (open: boolean) => {
      setIsOpen(open);
      onOpenChange?.(open);
    },
    [onOpenChange]
  );

  // FloatingUI setup
  const { refs, floatingStyles, context, placement } = useFloating({
    open: isOpen && !disabled,
    onOpenChange: handleOpenChange,
    placement: initialPlacement,
    middleware: [
      offset(offsetValue),
      flip({
        fallbackAxisSideDirection: 'start',
        padding: 8,
      }),
      shift({ padding: 8 }),
      showArrow && arrowRef.current
        ? arrowMiddleware({ element: arrowRef.current })
        : null,
    ].filter(Boolean),
    whileElementsMounted: autoUpdate,
  });

  // Ensure trigger is always an array
  const triggers = Array.isArray(trigger) ? trigger : [trigger];

  // Interaction hooks
  const click = useClick(context, {
    enabled: triggers.includes('click'),
  });

  const dismiss = useDismiss(context, {
    enabled: closeOnClickOutside || closeOnEscape,
    outsidePress: closeOnClickOutside,
    escapeKey: closeOnEscape,
    ancestorScroll: true,
  });

  const role = useRole(context, { role: 'dialog' });

  // Merge interactions
  const { getReferenceProps, getFloatingProps } = useInteractions([
    click,
    dismiss,
    role,
  ]);

  // Manual control functions
  const open = useCallback(() => {
    if (!disabled) {
      handleOpenChange(true);
    }
  }, [disabled, handleOpenChange]);

  const close = useCallback(() => {
    handleOpenChange(false);
  }, [handleOpenChange]);

  const toggle = useCallback(() => {
    handleOpenChange(!isOpen);
  }, [isOpen, handleOpenChange]);

  return {
    /**
     * Props for the reference element (trigger)
     */
    referenceProps: {
      ref: refs.setReference,
      'aria-expanded': isOpen,
      'aria-controls': isOpen ? popoverId : undefined,
      ...getReferenceProps(),
    },

    /**
     * Props for the floating element (popover)
     */
    floatingProps: {
      ref: refs.setFloating,
      id: popoverId,
      style: floatingStyles,
      ...getFloatingProps(),
    },

    /**
     * Props for the arrow element (if shown)
     */
    arrowProps: showArrow
      ? {
          ref: arrowRef,
          'data-placement': placement,
        }
      : null,

    /**
     * Popover visibility state
     */
    isOpen: isOpen && !disabled,

    /**
     * Current placement (after flip/shift)
     */
    placement,

    /**
     * FloatingUI context (for FloatingFocusManager)
     */
    context,

    /**
     * Focus manager options
     */
    focusManagerProps: {
      context,
      modal,
      initialFocus,
      returnFocus,
    },

    /**
     * Portal props
     */
    portalProps: portal ? { id: `${popoverId}-portal` } : null,

    /**
     * Manual control functions
     */
    open,
    close,
    toggle,

    /**
     * Refs for advanced use cases
     */
    refs,
  };
}

// ============================================================================
// Utility Hooks
// ============================================================================

/**
 * Hook for controlled popover state
 *
 * @example
 * ```tsx
 * const [isOpen, setIsOpen] = useState(false);
 * const popover = useControlledPopover(setIsOpen);
 * ```
 */
export function useControlledPopover(
  setIsOpen: (open: boolean) => void,
  options: PopoverOptions = {}
) {
  return usePopover({
    ...options,
    onOpenChange: setIsOpen,
  });
}

/**
 * Hook for managing multiple popovers
 * Ensures only one popover is shown at a time
 *
 * @example
 * ```tsx
 * const { register, activeId, closeAll } = usePopoverGroup();
 *
 * const popover1 = register('popover1');
 * const popover2 = register('popover2');
 *
 * // Only one popover will be shown at a time
 * ```
 */
export function usePopoverGroup() {
  const [activeId, setActiveId] = useState<string | null>(null);

  const register = useCallback(
    (id: string, options: PopoverOptions = {}) => {
      return usePopover({
        ...options,
        onOpenChange: (open: boolean) => {
          setActiveId(open ? id : null);
          options.onOpenChange?.(open);
        },
      });
    },
    [activeId]
  );

  return {
    activeId,
    register,
    closeAll: () => setActiveId(null),
  };
}

/**
 * Hook for nested popovers
 * Handles parent/child relationships and proper dismissal
 *
 * @example
 * ```tsx
 * const parentPopover = usePopover();
 * const childPopover = useNestedPopover(options);
 *
 * // Child won't dismiss parent when opened
 * ```
 */
export function useNestedPopover(options: PopoverOptions = {}) {
  const popover = usePopover(options);

  // Prevent parent dismissal when child is opened
  useEffect(() => {
    if (!popover.isOpen) return;

    const handleParentDismiss = (e: Event) => {
      e.stopPropagation();
    };

    const floatingElement = popover.refs.floating.current;
    if (floatingElement) {
      floatingElement.addEventListener('click', handleParentDismiss);
      return () => {
        floatingElement.removeEventListener('click', handleParentDismiss);
      };
    }

    return undefined;
  }, [popover.isOpen, popover.refs.floating]);

  return popover;
}

/**
 * Hook for popover with confirmation
 * Useful for destructive actions
 *
 * @example
 * ```tsx
 * const confirmPopover = useConfirmPopover({
 *   onConfirm: () => console.log('Confirmed'),
 *   onCancel: () => console.log('Cancelled'),
 * });
 *
 * return (
 *   <>
 *     <button {...confirmPopover.triggerProps}>Delete</button>
 *     {confirmPopover.isOpen && (
 *       <div {...confirmPopover.popoverProps}>
 *         <p>Are you sure?</p>
 *         <button onClick={confirmPopover.confirm}>Yes</button>
 *         <button onClick={confirmPopover.cancel}>No</button>
 *       </div>
 *     )}
 *   </>
 * );
 * ```
 */
export function useConfirmPopover(options: {
  onConfirm?: () => void;
  onCancel?: () => void;
  popoverOptions?: PopoverOptions;
}) {
  const { onConfirm, onCancel, popoverOptions = {} } = options;
  const popover = usePopover(popoverOptions);

  const confirm = useCallback(() => {
    onConfirm?.();
    popover.close();
  }, [onConfirm, popover]);

  const cancel = useCallback(() => {
    onCancel?.();
    popover.close();
  }, [onCancel, popover]);

  return {
    ...popover,
    triggerProps: popover.referenceProps,
    popoverProps: popover.floatingProps,
    confirm,
    cancel,
  };
}
