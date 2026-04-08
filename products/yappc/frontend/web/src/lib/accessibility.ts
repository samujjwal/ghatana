/**
 * Accessibility Utilities
 *
 * Provides utilities for WCAG 2.1 AA compliance including:
 * - Focus management
 * - ARIA attribute helpers
 * - Keyboard navigation
 * - Screen reader optimizations
 *
 * @doc.type library
 * @doc.purpose WCAG 2.1 AA compliance utilities
 * @doc.layer product
 * @doc.pattern Utility Library
 */

import type { CSSProperties } from 'react';

// ============================================================================
// Focus Management
// ============================================================================

/**
 * Trap focus within a container element
 */
export function trapFocus(container: HTMLElement): () => void {
  const focusableElements = getFocusableElements(container);
  const firstElement = focusableElements[0];
  const lastElement = focusableElements[focusableElements.length - 1];

  const handleKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Tab') return;

    if (event.shiftKey) {
      // Shift + Tab
      if (document.activeElement === firstElement) {
        lastElement.focus();
        event.preventDefault();
      }
    } else {
      // Tab
      if (document.activeElement === lastElement) {
        firstElement.focus();
        event.preventDefault();
      }
    }
  };

  container.addEventListener('keydown', handleKeyDown);

  // Return cleanup function
  return () => {
    container.removeEventListener('keydown', handleKeyDown);
  };
}

/**
 * Get all focusable elements within a container
 */
export function getFocusableElements(container: HTMLElement): HTMLElement[] {
  const focusableSelectors = [
    'button',
    '[href]',
    'input',
    'select',
    'textarea',
    '[tabindex]:not([tabindex="-1"])',
    '[contenteditable="true"]',
  ].join(', ');

  return Array.from(container.querySelectorAll<HTMLElement>(focusableSelectors)).filter(
    element => {
      const style = window.getComputedStyle(element);
      const isDisabled = (element as HTMLButtonElement | HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).disabled;
      return style.display !== 'none' && style.visibility !== 'hidden' && !isDisabled;
    }
  );
}

/**
 * Move focus to the first focusable element in a container
 */
export function focusFirstElement(container: HTMLElement): boolean {
  const focusableElements = getFocusableElements(container);
  if (focusableElements.length > 0) {
    focusableElements[0].focus();
    return true;
  }
  return false;
}

/**
 * Move focus to the last focusable element in a container
 */
export function focusLastElement(container: HTMLElement): boolean {
  const focusableElements = getFocusableElements(container);
  if (focusableElements.length > 0) {
    focusableElements[focusableElements.length - 1].focus();
    return true;
  }
  return false;
}

/**
 * Restore focus to a previously focused element
 */
export function restoreFocus(previousElement: HTMLElement | null): void {
  if (previousElement && document.contains(previousElement)) {
    previousElement.focus();
  }
}

// ============================================================================
// ARIA Attribute Helpers
// ============================================================================

/**
 * Generate ARIA attributes for a modal/dialog
 */
export function getModalAriaProps(id: string, labelledBy: string, describedBy?: string) {
  return {
    role: 'dialog' as const,
    'aria-modal': 'true' as const,
    'aria-labelledby': labelledBy,
    'aria-describedby': describedBy,
    id,
  };
}

/**
 * Generate ARIA attributes for a live region
 */
export function getLiveRegionProps(politeness: 'polite' | 'assertive' | 'off' = 'polite') {
  return {
    role: 'status' as const,
    'aria-live': politeness,
    'aria-atomic': 'true' as const,
  };
}

/**
 * Generate ARIA attributes for a progress indicator
 */
export function getProgressProps(value: number, max: number = 100) {
  const percentage = Math.min(Math.max((value / max) * 100, 0), 100);
  return {
    role: 'progressbar' as const,
    'aria-valuenow': value,
    'aria-valuemin': 0,
    'aria-valuemax': max,
    'aria-valuetext': `${Math.round(percentage)} percent`,
  };
}

/**
 * Generate ARIA attributes for a tab panel
 */
export function getTabPanelProps(id: string, labelledBy: string, selected: boolean) {
  return {
    id,
    role: 'tabpanel' as const,
    'aria-labelledby': labelledBy,
    'aria-hidden': !selected,
    tabIndex: selected ? 0 : -1,
  };
}

/**
 * Generate ARIA attributes for a tab
 */
export function getTabProps(id: string, controls: string, selected: boolean) {
  return {
    id,
    role: 'tab' as const,
    'aria-selected': selected,
    'aria-controls': controls,
    tabIndex: selected ? 0 : -1,
  };
}

/**
 * Generate ARIA attributes for a menu item
 */
export function getMenuItemProps(id: string, hasPopup?: boolean) {
  return {
    id,
    role: 'menuitem' as const,
    'aria-haspopup': hasPopup ? 'true' : undefined,
  };
}

/**
 * Generate ARIA attributes for a toggle button
 */
export function getToggleButtonProps(pressed: boolean, label: string) {
  return {
    'aria-pressed': pressed,
    'aria-label': label,
    role: 'button' as const,
  };
}

/**
 * Generate ARIA attributes for an expandable section
 */
export function getExpandableProps(expanded: boolean, controls: string) {
  return {
    'aria-expanded': expanded,
    'aria-controls': controls,
  };
}

// ============================================================================
// Screen Reader Utilities
// ============================================================================

/**
 * Announce a message to screen readers
 */
export function announceToScreenReader(message: string, politeness: 'polite' | 'assertive' = 'polite'): void {
  const announcement = document.createElement('div');
  announcement.setAttribute('role', 'status');
  announcement.setAttribute('aria-live', politeness);
  announcement.setAttribute('aria-atomic', 'true');
  announcement.className = 'sr-only';

  announcement.textContent = message;
  document.body.appendChild(announcement);

  // Remove after announcement
  setTimeout(() => {
    document.body.removeChild(announcement);
  }, 1000);
}

/**
 * Hide content visually but keep it available to screen readers
 */
export const visuallyHiddenStyles: React.CSSProperties = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  padding: 0,
  margin: '-1px',
  overflow: 'hidden',
  clip: 'rect(0, 0, 0, 0)',
  whiteSpace: 'nowrap',
  borderWidth: 0,
};

/**
 * Check if screen reader is active
 */
export function isScreenReaderActive(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

// ============================================================================
// Keyboard Navigation Utilities
// ============================================================================

/**
 * Check if a key press is a keyboard shortcut
 */
export function isKeyboardShortcut(event: KeyboardEvent, shortcuts: string[]): boolean {
  return shortcuts.includes(event.key);
}

/**
 * Generate keyboard event handler for common patterns
 */
export function createKeyboardHandler(
  keyMap: Record<string, (event: KeyboardEvent) => void>
): (event: KeyboardEvent) => void {
  return (event: KeyboardEvent) => {
    const handler = keyMap[event.key];
    if (handler) {
      handler(event);
    }
  };
}

/**
 * Check if element is currently focused
 */
export function isFocused(element: HTMLElement): boolean {
  return document.activeElement === element;
}

// ============================================================================
// Color Contrast Utilities
// ============================================================================

/**
 * Calculate relative luminance of a color
 */
export function getRelativeLuminance(hexColor: string): number {
  const rgb = hexToRgb(hexColor);
  if (!rgb) return 0;

  const [r, g, b] = rgb.map(channel => {
    channel /= 255;
    return channel <= 0.03928
      ? channel / 12.92
      : Math.pow((channel + 0.055) / 1.055, 2.4);
  });

  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/**
 * Calculate contrast ratio between two colors
 */
export function getContrastRatio(color1: string, color2: string): number {
  const luminance1 = getRelativeLuminance(color1);
  const luminance2 = getRelativeLuminance(color2);

  const lighter = Math.max(luminance1, luminance2);
  const darker = Math.min(luminance1, luminance2);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Check if color contrast meets WCAG AA standard (4.5:1 for normal text)
 */
export function meetsWCAGAA(color1: string, color2: string, largeText: boolean = false): boolean {
  const ratio = getContrastRatio(color1, color2);
  const threshold = largeText ? 3 : 4.5;
  return ratio >= threshold;
}

/**
 * Check if color contrast meets WCAG AAA standard (7:1 for normal text)
 */
export function meetsWCAGAAA(color1: string, color2: string, largeText: boolean = false): boolean {
  const ratio = getContrastRatio(color1, color2);
  const threshold = largeText ? 4.5 : 7;
  return ratio >= threshold;
}

/**
 * Convert hex color to RGB
 */
function hexToRgb(hex: string): [number, number, number] | null {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result
    ? [
        parseInt(result[1], 16),
        parseInt(result[2], 16),
        parseInt(result[3], 16),
      ]
    : null;
}

// ============================================================================
// Reduced Motion Utilities
// ============================================================================

/**
 * Check if user prefers reduced motion
 */
export function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Get animation duration based on reduced motion preference
 */
export function getAnimationDuration(normalDuration: number): number {
  return prefersReducedMotion() ? 0 : normalDuration;
}

// ============================================================================
// Semantic HTML Helpers
// ============================================================================

/**
 * Generate semantic heading attributes
 */
export function getHeadingProps(level: 1 | 2 | 3 | 4 | 5 | 6) {
  return {
    role: 'heading' as const,
    'aria-level': level,
  };
}

/**
 * Generate semantic landmark attributes
 */
export function getLandmarkProps(role: 'banner' | 'navigation' | 'main' | 'complementary' | 'contentinfo' | 'search') {
  return {
    role,
  };
}
