import { useRef, useEffect, useState } from 'react';

import { AccessibilityUtils } from './utils';
import {
  runAccessibilityAudit,
  type AccessibilityAuditResult,
} from '../../utils/accessibility';

import type {
  UseAccessibilityOptions,
  UseAccessibilityReturn,
  UseFocusTrapReturn,
  UseKeyboardNavigationOptions,
  UseKeyboardNavigationReturn,
} from './types';

/**
 * Hook for adding accessibility audit features to components.
 *
 * Runs accessibility audit on mounted component and logs/throws errors based on configuration.
 * Can be configured to run in development mode only.
 *
 * @param options - Configuration options for the accessibility audit
 * @returns Object containing ref and audit result
 *
 * @example
 * const { ref, auditResult } = useAccessibility({
 *   componentName: 'MyComponent',
 *   devOnly: true,
 *   logResults: true,
 * });
 *
 * return <div ref={ref}>Component content</div>;
 */
export function useAccessibility<T extends HTMLElement = HTMLDivElement>(
  options: UseAccessibilityOptions
): UseAccessibilityReturn<T> {
  const {
    componentName,
    devOnly = true,
    logResults = true,
    throwOnFailure = false,
  } = options;

  const ref = useRef<T>(null);
  const [auditResult, setAuditResult] =
    useState<AccessibilityAuditResult | null>(null);

  useEffect(() => {
    // Skip in production if devOnly is true
    if (devOnly && AccessibilityUtils.isProduction()) {
      return;
    }

    // Run audit when component mounts
    const element = ref.current;
    if (element) {
      const result = runAccessibilityAudit(element, componentName);
      setAuditResult(result);

      // Log results if enabled
      if (logResults && !result.passed) {
        console.warn(
          `Accessibility audit for ${componentName}: found ${result.issues.length} issues`
        );
        result.issues.forEach((issue) => {
          if (issue.type === 'error') {
            console.error(
              `${issue.code}: ${issue.message}${issue.selector ? ` (${issue.selector})` : ''}`
            );
          } else {
            console.warn(
              `${issue.code}: ${issue.message}${issue.selector ? ` (${issue.selector})` : ''}`
            );
          }
          if (issue.fix) {
            console.warn(`Suggested fix: ${issue.fix}`);
          }
        });
      }

      // Throw error if enabled and audit failed
      if (throwOnFailure && !result.passed) {
        throw new Error(`Accessibility audit failed for ${componentName}`);
      }
    }
  }, [componentName, devOnly, logResults, throwOnFailure]);

  return { ref, auditResult };
}

/**
 * Hook for managing focus trap in modal dialogs and overlays.
 *
 * Traps keyboard focus within a container, preventing users from tabbing outside.
 * Restores focus to previously active element when deactivated.
 *
 * @returns Object containing ref, activation methods, and active state
 *
 * @example
 * const { ref, activate, deactivate, active } = useFocusTrap();
 *
 * useEffect(() => {
 *   if (isOpen) {
 *     activate();
 *   } else {
 *     deactivate();
 *   }
 * }, [isOpen]);
 *
 * return <div ref={ref} role="dialog">Modal content</div>;
 */
export function useFocusTrap<
  T extends HTMLElement = HTMLDivElement,
>(): UseFocusTrapReturn<T> {
  const ref = useRef<T>(null);
  const [active, setActive] = useState(false);

  // Store previously focused element
  const previousFocus = useRef<HTMLElement | null>(null);

  // Activate focus trap
  const activate = () => {
    // Store current active element
    previousFocus.current = document.activeElement as HTMLElement;
    setActive(true);

    // Focus first focusable element
    setTimeout(() => {
      if (ref.current) {
        const focusSelector =
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';
        const focusedFirst = AccessibilityUtils.focusFirstElement(
          ref.current,
          focusSelector
        );

        if (!focusedFirst) {
          // If no focusable elements, focus the container
          AccessibilityUtils.focusContainer(ref.current);
        }
      }
    }, 0);
  };

  // Deactivate focus trap
  const deactivate = () => {
    setActive(false);

    // Restore previous focus
    if (previousFocus.current) {
      AccessibilityUtils.restoreFocus(previousFocus.current);
      previousFocus.current = null;
    }
  };

  // Handle tab key to trap focus
  useEffect(() => {
    if (!active || !ref.current) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Tab' || !ref.current) return;

      const focusSelector =
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';
      const focusableElements = AccessibilityUtils.getFocusableElements(
        ref.current,
        focusSelector
      );

      if (focusableElements.length === 0) return;

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];

      // Shift + Tab: focus last element when tabbing backward from first
      if (event.shiftKey && document.activeElement === firstElement) {
        lastElement.focus();
        event.preventDefault();
      }
      // Tab: focus first element when tabbing forward from last
      else if (!event.shiftKey && document.activeElement === lastElement) {
        firstElement.focus();
        event.preventDefault();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [active]);

  return { ref, activate, deactivate, active };
}

/**
 * Hook for managing keyboard navigation in components like menus and lists.
 *
 * Handles arrow key navigation, Home/End keys, and looping behavior.
 * Automatically manages focus and active item tracking.
 *
 * @param options - Configuration options for keyboard navigation
 * @returns Object containing ref, active index, and navigation methods
 *
 * @example
 * const { ref, activeIndex, focusItem } = useKeyboardNavigation({
 *   vertical: true,
 *   loop: true,
 * });
 *
 * return (
 *   <div ref={ref} role="menu">
 *     {items.map((item, idx) => (
 *       <button key={idx} role="menuitem">
 *         {item}
 *       </button>
 *     ))}
 *   </div>
 * );
 */
export function useKeyboardNavigation<T extends HTMLElement = HTMLDivElement>(
  options: UseKeyboardNavigationOptions = {}
): UseKeyboardNavigationReturn<T> {
  const {
    vertical = true,
    horizontal = false,
    loop = true,
    initialIndex = -1,
    itemSelector = '[role="option"], [role="menuitem"], [role="tab"], li, button, a',
  } = options;

  const ref = useRef<T>(null);
  const [activeIndex, setActiveIndex] = useState(initialIndex);

  // Get all navigable items
  const getItems = (): HTMLElement[] => {
    return AccessibilityUtils.getFocusableElements(ref.current, itemSelector);
  };

  // Set focus to item at index
  const focusItem = (index: number) => {
    const items = getItems();
    if (items.length === 0) return;

    // Calculate target index
    let targetIndex = index;
    if (vertical || horizontal) {
      if (loop) {
        if (index < 0) targetIndex = items.length - 1;
        if (index >= items.length) targetIndex = 0;
      } else {
        if (index < 0) targetIndex = 0;
        if (index >= items.length) targetIndex = items.length - 1;
      }
    }

    // Set active index and focus item
    setActiveIndex(targetIndex);
    const targetItem = AccessibilityUtils.getItemAtIndex(items, targetIndex);
    if (targetItem) {
      targetItem.focus();
    }
  };

  // Handle keyboard navigation
  useEffect(() => {
    if (!ref.current) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      const items = getItems();
      if (items.length === 0) return;

      switch (event.key) {
        case 'ArrowDown':
          if (vertical) {
            event.preventDefault();
            focusItem(activeIndex + 1);
          }
          break;
        case 'ArrowUp':
          if (vertical) {
            event.preventDefault();
            focusItem(activeIndex - 1);
          }
          break;
        case 'ArrowRight':
          if (horizontal) {
            event.preventDefault();
            focusItem(activeIndex + 1);
          }
          break;
        case 'ArrowLeft':
          if (horizontal) {
            event.preventDefault();
            focusItem(activeIndex - 1);
          }
          break;
        case 'Home':
          event.preventDefault();
          focusItem(0);
          break;
        case 'End':
          event.preventDefault();
          focusItem(items.length - 1);
          break;
      }
    };

    ref.current.addEventListener('keydown', handleKeyDown);
    return () => {
      ref.current?.removeEventListener('keydown', handleKeyDown);
    };
  }, [activeIndex, vertical, horizontal, loop]);

  return { ref, activeIndex, setActiveIndex, focusItem };
}
