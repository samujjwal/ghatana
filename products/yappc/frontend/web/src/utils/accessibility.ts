/**
 * Accessibility Utilities
 * 
 * Shared utilities for consistent accessibility across the application.
 * 
 * @doc.type utility
 * @doc.purpose Provide consistent accessibility patterns
 * @doc.layer product
 * @doc.pattern Utility Module
 */

import React from 'react';
import type { KeyboardEvent } from 'react';

/**
 * Focus ring class for keyboard navigation
 * Use with buttons, links, and interactive elements
 */
export const focusRingClass = 'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 focus:ring-offset-bg-paper';

/**
 * Focus visible class - only shows focus ring for keyboard navigation
 */
export const focusVisibleClass = 'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2';

/**
 * Interactive element base classes with proper focus handling
 */
export const interactiveClasses = `
  transition-all duration-150 ease-in-out
  ${focusVisibleClass}
  cursor-pointer
  disabled:opacity-50 disabled:cursor-not-allowed
`.trim().replace(/\s+/g, ' ');

/**
 * Card interactive classes with hover and focus states
 */
export const cardInteractiveClasses = `
  bg-bg-paper border border-divider rounded-lg
  hover:border-primary-300 dark:hover:border-primary-700
  ${focusRingClass}
  transition-all duration-150 ease-in-out
`.trim().replace(/\s+/g, ' ');

/**
 * Skip link classes for accessibility
 * Renders a link that's only visible when focused
 */
export const skipLinkClasses = `
  absolute left-0 top-0 z-50
  -translate-y-full focus:translate-y-0
  bg-primary-600 text-white px-4 py-2
  ${focusRingClass}
  transition-transform duration-150
`.trim().replace(/\s+/g, ' ');

/**
 * Screen reader only classes (visually hidden but accessible)
 */
export const srOnlyClasses = 'sr-only';

/**
 * Not screen reader only - make visible
 */
export const notSrOnlyClasses = 'not-sr-only';

/**
 * Generates aria-label for icon-only buttons
 */
export function getIconButtonAriaLabel(action: string): string {
  return action;
}

/**
 * Live region props for dynamic content announcements
 */
export const liveRegionProps = {
  polite: {
    'aria-live': 'polite' as const,
    'aria-atomic': 'true' as const,
  },
  assertive: {
    'aria-live': 'assertive' as const,
    'aria-atomic': 'true' as const,
  },
  off: {
    'aria-live': 'off' as const,
  },
};

/**
 * Helper to create landmark region props
 */
export const landmarkProps = {
  main: { role: 'main' as const },
  navigation: { role: 'navigation' as const },
  banner: { role: 'banner' as const },
  contentinfo: { role: 'contentinfo' as const },
  complementary: { role: 'complementary' as const },
  search: { role: 'search' as const },
  region: (label: string) => ({ role: 'region' as const, 'aria-label': label }),
};

/**
 * Default keyboard handler for escape key
 */
export function handleEscapeKey(
  event: KeyboardEvent,
  onEscape: () => void
): void {
  if (event.key === 'Escape') {
    event.preventDefault();
    onEscape();
  }
}

/**
 * Creates keyboard navigation handler for arrow keys
 */
export function createArrowKeyHandler(
  items: HTMLElement[],
  currentIndex: number,
  options: { wrap?: boolean; orientation?: 'horizontal' | 'vertical' } = {}
): (event: React.KeyboardEvent) => number | null {
  const { wrap = true, orientation = 'vertical' } = options;
  
  return (event: KeyboardEvent): number | null => {
    const isVertical = orientation === 'vertical';
    const prevKey = isVertical ? 'ArrowUp' : 'ArrowLeft';
    const nextKey = isVertical ? 'ArrowDown' : 'ArrowRight';
    
    let newIndex: number | null = null;
    
    if (event.key === prevKey) {
      event.preventDefault();
      if (currentIndex > 0) {
        newIndex = currentIndex - 1;
      } else if (wrap) {
        newIndex = items.length - 1;
      }
    } else if (event.key === nextKey) {
      event.preventDefault();
      if (currentIndex < items.length - 1) {
        newIndex = currentIndex + 1;
      } else if (wrap) {
        newIndex = 0;
      }
    } else if (event.key === 'Home') {
      event.preventDefault();
      newIndex = 0;
    } else if (event.key === 'End') {
      event.preventDefault();
      newIndex = items.length - 1;
    }
    
    if (newIndex !== null && items[newIndex]) {
      items[newIndex].focus();
    }
    
    return newIndex;
  };
}

/**
 * Separates ARIA/accessibility props from the rest of an HTML attribute set.
 */
export function getA11yProps<T extends Record<string, unknown>>(
  props: T
): { a11yProps: Partial<T>; rest: Partial<T> } {
  const a11yKeys = new Set([
    'aria-label',
    'aria-labelledby',
    'aria-describedby',
    'aria-description',
    'aria-live',
    'aria-atomic',
    'aria-relevant',
    'aria-busy',
    'aria-controls',
    'aria-current',
    'aria-disabled',
    'aria-expanded',
    'aria-haspopup',
    'aria-hidden',
    'aria-invalid',
    'aria-keyshortcuts',
    'aria-modal',
    'aria-multiline',
    'aria-multiselectable',
    'aria-orientation',
    'aria-placeholder',
    'aria-pressed',
    'aria-readonly',
    'aria-required',
    'aria-roledescription',
    'aria-selected',
    'aria-sort',
    'aria-valuemax',
    'aria-valuemin',
    'aria-valuenow',
    'aria-valuetext',
    'role',
    'tabIndex',
  ]);
  const a11yProps: Partial<T> = {};
  const rest: Partial<T> = {};
  for (const key of Object.keys(props) as Array<keyof T>) {
    if (a11yKeys.has(key as string)) {
      (a11yProps as Record<string, unknown>)[key as string] = props[key];
    } else {
      (rest as Record<string, unknown>)[key as string] = props[key];
    }
  }
  return { a11yProps, rest };
}

/**
 * Computes an aria-label string from React children when one is not explicitly provided.
 * Falls back to the explicit label if given.
 */
export function computeAriaLabel(
  children: React.ReactNode,
  explicitLabel?: string
): string | undefined {
  if (explicitLabel) return explicitLabel;

  const extractText = (node: React.ReactNode): string => {
    if (typeof node === 'string') return node;
    if (typeof node === 'number') return String(node);
    if (Array.isArray(node)) return node.map(extractText).join('');
    if (React.isValidElement(node)) {
      const element = node as React.ReactElement<{ children?: React.ReactNode }>;
      return extractText(element.props.children);
    }
    return '';
  };

  const text = extractText(children).trim();
  return text.length > 0 ? text : undefined;
}

/**
 * Wraps an element with extra ARIA props for tooltip association.
 * Returns the element cloned with the additional props merged in.
 */
export function wrapForTooltip(
  element: React.ReactElement,
  extraProps: Record<string, string>
): React.ReactElement {
  return React.cloneElement(element, extraProps);
}
