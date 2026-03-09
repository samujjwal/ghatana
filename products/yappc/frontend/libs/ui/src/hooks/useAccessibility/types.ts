import type { AccessibilityAuditResult } from '../../utils/accessibility';
import type { MutableRefObject } from 'react';

/**
 * Hook options for useAccessibility.
 *
 * @property componentName - Name of the component for audit identification
 * @property devOnly - Whether to run the audit in development mode only (default: true)
 * @property logResults - Whether to log audit results to console (default: true)
 * @property throwOnFailure - Whether to throw an error on audit failure (default: false)
 */
export interface UseAccessibilityOptions {
  componentName: string;
  devOnly?: boolean;
  logResults?: boolean;
  throwOnFailure?: boolean;
}

/**
 * Return type for useAccessibility hook.
 *
 * @property ref - React ref for the element to audit
 * @property auditResult - Result of the accessibility audit
 */
export interface UseAccessibilityReturn<T extends HTMLElement> {
  ref: MutableRefObject<T | null>;
  auditResult: AccessibilityAuditResult | null;
}

/**
 * Return type for useFocusTrap hook.
 *
 * @property ref - React ref for the focus trap container
 * @property activate - Function to activate the focus trap
 * @property deactivate - Function to deactivate the focus trap
 * @property active - Current active state of the focus trap
 */
export interface UseFocusTrapReturn<T extends HTMLElement> {
  ref: MutableRefObject<T | null>;
  activate: () => void;
  deactivate: () => void;
  active: boolean;
}

/**
 * Options for useKeyboardNavigation hook.
 *
 * @property vertical - Enable vertical arrow key navigation (default: true)
 * @property horizontal - Enable horizontal arrow key navigation (default: false)
 * @property loop - Enable looping navigation at boundaries (default: true)
 * @property initialIndex - Initial focused item index (default: -1)
 * @property itemSelector - CSS selector for navigable items
 */
export interface UseKeyboardNavigationOptions {
  vertical?: boolean;
  horizontal?: boolean;
  loop?: boolean;
  initialIndex?: number;
  itemSelector?: string;
}

/**
 * Return type for useKeyboardNavigation hook.
 *
 * @property ref - React ref for the navigation container
 * @property activeIndex - Index of currently focused item
 * @property setActiveIndex - Function to set active item index
 * @property focusItem - Function to focus item at specific index
 */
export interface UseKeyboardNavigationReturn<T extends HTMLElement> {
  ref: MutableRefObject<T | null>;
  activeIndex: number;
  setActiveIndex: (index: number) => void;
  focusItem: (index: number) => void;
}
