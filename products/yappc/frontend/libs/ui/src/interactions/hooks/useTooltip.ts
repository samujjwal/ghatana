/**
 * Tooltip Hook
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 *
 * Smart tooltip positioning with FloatingUI.
 * Provides accessible, interactive tooltips with proper focus management.
 *
 * @module interactions/hooks/useTooltip
 */

import {
  useFloating,
  autoUpdate,
  offset,
  flip,
  shift,
  arrow as arrowMiddleware,
  useHover,
  useFocus,
  useClick,
  useDismiss,
  useRole,
  useInteractions,
} from '@floating-ui/react';
import { useState, useRef, useEffect, useCallback } from 'react';

import type { CSSProperties, ReactNode, RefObject } from 'react';

import type { TooltipOptions, TooltipPlacement } from '../types';

// ============================================================================
// Default Options
// ============================================================================

const DEFAULT_TOOLTIP_OPTIONS: Required<TooltipOptions> = {
  placement: 'top',
  offset: 8,
  delayShow: 200,
  delayHide: 0,
  trigger: 'hover',
  interactive: false,
  arrow: true,
  maxWidth: 300,
  disabled: false,
};

// ============================================================================
// useTooltip Hook
// ============================================================================

/**
 * Hook for tooltip management with FloatingUI
 *
 * Provides smart positioning, accessibility, and interaction handling.
 *
 * @example
 * ```tsx
 * const {
 *   referenceProps,
 *   floatingProps,
 *   arrowProps,
 *   isOpen,
 *   content
 * } = useTooltip('This is a tooltip', {
 *   placement: 'top',
 *   trigger: 'hover'
 * });
 *
 * return (
 *   <>
 *     <button {...referenceProps}>Hover me</button>
 *     {isOpen && (
 *       <div {...floatingProps}>
 *         {content}
 *         <div {...arrowProps} />
 *       </div>
 *     )}
 *   </>
 * );
 * ```
 */
export function useTooltip(
  content: ReactNode,
  options: TooltipOptions = {}
): UseTooltipResult {
  const mergedOptions = { ...DEFAULT_TOOLTIP_OPTIONS, ...options };
  const {
    placement: initialPlacement,
    offset: offsetValue,
    delayShow,
    delayHide,
    trigger,
    interactive,
    arrow: showArrow,
    maxWidth,
    disabled,
  } = mergedOptions;

  const [isOpen, setIsOpen] = useState(false);
  const arrowRef = useRef<SVGSVGElement>(null);

  // Ensure trigger is always an array
  const triggers = Array.isArray(trigger) ? trigger : [trigger];

  // FloatingUI setup
  const { refs, floatingStyles, context, placement } = useFloating({
    open: isOpen && !disabled,
    onOpenChange: setIsOpen,
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

  // Interaction hooks
  const hover = useHover(context, {
    enabled: triggers.includes('hover'),
    delay: { open: delayShow, close: delayHide },
    move: false,
  });

  const focus = useFocus(context, {
    enabled: triggers.includes('focus'),
  });

  const click = useClick(context, {
    enabled: triggers.includes('click'),
  });

  const dismiss = useDismiss(context, {
    ancestorScroll: true,
  });

  const role = useRole(context, { role: 'tooltip' });

  // Merge interactions
  const { getReferenceProps, getFloatingProps } = useInteractions([
    hover,
    focus,
    click,
    dismiss,
    role,
  ]);

  // Close on Escape key
  useEffect(() => {
    if (!isOpen) return;

    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setIsOpen(false);
      }
    };

    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [isOpen]);

  return {
    /**
     * Props for the reference element (trigger)
     */
    referenceProps: {
      ref: refs.setReference,
      ...getReferenceProps(),
    },

    /**
     * Props for the floating element (tooltip)
     */
    floatingProps: {
      ref: refs.setFloating,
      style: {
        ...(floatingStyles as CSSProperties),
        maxWidth: typeof maxWidth === 'number' ? `${maxWidth}px` : maxWidth,
        pointerEvents: interactive ? 'auto' : 'none',
      },
      ...getFloatingProps(),
    },

    /**
     * Props for the arrow element (if shown)
     */
    arrowProps: showArrow
      ? {
        ref: arrowRef,
        'data-placement': placement as TooltipPlacement,
      }
      : null,

    /**
     * Tooltip visibility state
     */
    isOpen: isOpen && !disabled,

    /**
     * Tooltip content
     */
    content,

    /**
     * Current placement (after flip/shift)
     */
    placement: placement as TooltipPlacement,

    /**
     * Manual control functions
     */
    show: () => setIsOpen(true),
    hide: () => setIsOpen(false),
    toggle: () => setIsOpen((prev) => !prev),
  };
}

export interface UseTooltipReferenceProps extends Record<string, unknown> {
  ref: (node: Element | null) => void;
}

export interface UseTooltipFloatingProps extends Record<string, unknown> {
  ref: (node: HTMLElement | null) => void;
  style: CSSProperties;
}

export interface UseTooltipArrowProps {
  ref: RefObject<SVGSVGElement | null>;
  'data-placement': TooltipPlacement;
}

export interface UseTooltipResult {
  referenceProps: UseTooltipReferenceProps;
  floatingProps: UseTooltipFloatingProps;
  arrowProps: UseTooltipArrowProps | null;
  isOpen: boolean;
  content: ReactNode;
  placement: TooltipPlacement;
  show: () => void;
  hide: () => void;
  toggle: () => void;
}

// ============================================================================
// Utility Hooks
// ============================================================================

/**
 * Hook for simple tooltip with manual control
 *
 * @example
 * ```tsx
 * const tooltip = useTooltipState();
 *
 * return (
 *   <>
 *     <button
 *       onMouseEnter={tooltip.show}
 *       onMouseLeave={tooltip.hide}
 *     >
 *       Hover me
 *     </button>
 *     {tooltip.isOpen && <div>Tooltip</div>}
 *   </>
 * );
 * ```
 */
export function useTooltipState(defaultOpen = false) {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  const show = useCallback(() => setIsOpen(true), []);
  const hide = useCallback(() => setIsOpen(false), []);
  const toggle = useCallback(() => setIsOpen((prev) => !prev), []);

  return {
    isOpen,
    show,
    hide,
    toggle,
    setIsOpen,
  };
}

/**
 * Hook for managing multiple tooltips
 * Ensures only one tooltip is shown at a time
 *
 * @example
 * ```tsx
 * const { register, activeId } = useTooltipGroup();
 *
 * const tooltip1 = register('tooltip1');
 * const tooltip2 = register('tooltip2');
 *
 * // Only one tooltip will be shown at a time
 * ```
 */
export function useTooltipGroup() {
  const [activeId, setActiveId] = useState<string | null>(null);

  const register = useCallback(
    (id: string) => ({
      isOpen: activeId === id,
      show: () => setActiveId(id),
      hide: () => setActiveId(null),
    }),
    [activeId]
  );

  return {
    activeId,
    register,
    clear: () => setActiveId(null),
  };
}

/**
 * Hook for delayed tooltip
 * Useful for progressive disclosure
 *
 * @example
 * ```tsx
 * const tooltip = useDelayedTooltip(2000); // Show after 2 seconds
 *
 * return (
 *   <div {...tooltip.triggerProps}>
 *     {tooltip.isVisible && <Tooltip {...tooltip.tooltipProps} />}
 *   </div>
 * );
 * ```
 */
export function useDelayedTooltip(delay: number = 1000) {
  const [isHovering, setIsHovering] = useState(false);
  const [isVisible, setIsVisible] = useState(false);
  const timeoutRef = useRef<number | null>(null);

  useEffect(() => {
    if (isHovering) {
      timeoutRef.current = window.setTimeout(() => {
        setIsVisible(true);
      }, delay);
    } else {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
      setIsVisible(false);
    }

    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
    };
  }, [isHovering, delay]);

  return {
    isVisible,
    isHovering,
    triggerProps: {
      onMouseEnter: () => setIsHovering(true),
      onMouseLeave: () => setIsHovering(false),
    },
    tooltipProps: {
      'aria-hidden': !isVisible,
    },
  };
}
