/**
 * useFocusTrap Hook
 *
 * Traps keyboard focus within a container element, cycling through focusable
 * elements when Tab/Shift+Tab are pressed at the boundaries.
 *
 * WCAG 2.1 Level A requirement for modal dialogs and drawers.
 *
 * @doc.type hook
 * @doc.purpose Accessibility - Focus Trap
 * @doc.layer infrastructure
 * @doc.pattern React Hook
 *
 * @example
 * ```tsx
 * function Modal({ isOpen, children }) {
 *   const containerRef = useRef<HTMLDivElement>(null);
 *   useFocusTrap(containerRef, isOpen);
 *
 *   return (
 *     <div ref={containerRef} role="dialog" aria-modal="true">
 *       {children}
 *     </div>
 *   );
 * }
 * ```
 */

import { useEffect, useCallback, RefObject } from 'react';

/**
 * Selector for focusable elements within a container.
 * Excludes disabled elements and those with negative tabindex.
 */
const FOCUSABLE_SELECTOR = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
    'audio[controls]',
    'video[controls]',
    '[contenteditable]:not([contenteditable="false"])',
    'details > summary:first-of-type',
].join(', ');

export interface UseFocusTrapOptions {
    /** Whether focus trap is active */
    enabled?: boolean;
    /** Initial element to focus when trap activates (selector or ref) */
    initialFocus?: string | RefObject<HTMLElement>;
    /** Element to focus when trap deactivates (defaults to previously focused element) */
    returnFocus?: boolean;
    /** Prevent scroll when focusing elements */
    preventScroll?: boolean;
}

/**
 * Get all focusable elements within a container
 */
function getFocusableElements(container: HTMLElement): HTMLElement[] {
    const elements = container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
    return Array.from(elements).filter((el) => {
        // Filter out hidden elements
        const style = window.getComputedStyle(el);
        return (
            style.display !== 'none' &&
            style.visibility !== 'hidden' &&
            !el.hasAttribute('hidden') &&
            el.offsetParent !== null
        );
    });
}

/**
 * Hook to trap focus within a container element
 *
 * @param containerRef - Ref to the container element
 * @param isActive - Whether the focus trap should be active
 * @param options - Configuration options
 */
export function useFocusTrap(
    containerRef: RefObject<HTMLElement>,
    isActive: boolean,
    options: UseFocusTrapOptions = {}
): void {
    const {
        enabled = true,
        initialFocus,
        returnFocus = true,
        preventScroll = false,
    } = options;

    const handleKeyDown = useCallback(
        (event: KeyboardEvent) => {
            if (!enabled || event.key !== 'Tab') return;

            const container = containerRef.current;
            if (!container) return;

            const focusableElements = getFocusableElements(container);
            if (focusableElements.length === 0) return;

            const firstElement = focusableElements[0];
            const lastElement = focusableElements[focusableElements.length - 1];
            const activeElement = document.activeElement;

            // Shift + Tab at first element -> go to last
            if (event.shiftKey && activeElement === firstElement) {
                event.preventDefault();
                lastElement?.focus({ preventScroll });
            }
            // Tab at last element -> go to first
            else if (!event.shiftKey && activeElement === lastElement) {
                event.preventDefault();
                firstElement?.focus({ preventScroll });
            }
            // If focus is outside container, move it inside
            else if (!container.contains(activeElement)) {
                event.preventDefault();
                firstElement?.focus({ preventScroll });
            }
        },
        [containerRef, enabled, preventScroll]
    );

    useEffect(() => {
        if (!isActive || !enabled) return;

        const container = containerRef.current;
        if (!container) return;

        // Store previously focused element
        const previouslyFocused = document.activeElement as HTMLElement | null;

        // Determine initial focus target
        let initialFocusElement: HTMLElement | null = null;

        if (typeof initialFocus === 'string') {
            initialFocusElement = container.querySelector<HTMLElement>(initialFocus);
        } else if (initialFocus?.current) {
            initialFocusElement = initialFocus.current;
        }

        // If no initial focus specified, focus the first focusable element
        if (!initialFocusElement) {
            const focusableElements = getFocusableElements(container);
            initialFocusElement = focusableElements[0] || container;
        }

        // Set initial focus after a short delay to ensure DOM is ready
        const focusTimer = setTimeout(() => {
            initialFocusElement?.focus({ preventScroll });
        }, 0);

        // Add keydown listener
        document.addEventListener('keydown', handleKeyDown);

        // Cleanup
        return () => {
            clearTimeout(focusTimer);
            document.removeEventListener('keydown', handleKeyDown);

            // Return focus to previously focused element
            if (returnFocus && previouslyFocused && previouslyFocused.focus) {
                previouslyFocused.focus({ preventScroll: true });
            }
        };
    }, [
        isActive,
        enabled,
        containerRef,
        initialFocus,
        returnFocus,
        preventScroll,
        handleKeyDown,
    ]);
}

/**
 * Export focusable selector for external use
 */
export { FOCUSABLE_SELECTOR };

/**
 * Utility function to get focusable elements (for testing)
 */
export { getFocusableElements };
