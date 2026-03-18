import React from 'react';

/**
 * Accessibility utilities for WCAG 2.1 AA compliance
 */

/**
 * Wrap an interactive element to preserve accessible props when used with tooltip clones.
 */
export function wrapForTooltip(element: React.ReactElement, spanProps?: Record<string, unknown>): React.ReactElement {
  return React.createElement('span', { style: { display: 'inline-block' }, ...spanProps }, element) as React.ReactElement;
}

/**
 * Check if motion should be reduced based on user preferences
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Check if high contrast mode is enabled
 */
export function prefersHighContrast(): boolean {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-contrast: more)').matches;
}

/**
 * Check if color scheme is dark
 */
export function prefersDarkColorScheme(): boolean {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

/**
 * Calculate color contrast ratio (WCAG formula)
 */
export function getContrastRatio(rgb1: [number, number, number], rgb2: [number, number, number]): number {
  const getLuminance = (rgb: [number, number, number]) => {
    const [r, g, b] = rgb.map((val) => {
      const v = val / 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };

  const l1 = getLuminance(rgb1);
  const l2 = getLuminance(rgb2);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Check if contrast ratio meets WCAG AA standard
 */
export function meetsWCAGAA(contrastRatio: number, largeText: boolean = false): boolean {
  return largeText ? contrastRatio >= 3 : contrastRatio >= 4.5;
}

/**
 * Check if contrast ratio meets WCAG AAA standard
 */
export function meetsWCAGAAA(contrastRatio: number, largeText: boolean = false): boolean {
  return largeText ? contrastRatio >= 4.5 : contrastRatio >= 7;
}

/**
 * Generate accessible label from children
 */
export function getAccessibleLabel(children: React.ReactNode): string {
  if (typeof children === 'string') return children;
  if (typeof children === 'number') return String(children);
  if (Array.isArray(children)) {
    return children.map((child) => getAccessibleLabel(child)).join(' ');
  }
  return '';
}

/**
 * Create skip link for keyboard navigation
 */
export interface SkipLinkConfig {
  href: string;
  label: string;
}

export function createSkipLinks(links: SkipLinkConfig[]): SkipLinkConfig[] {
  return links;
}

/**
 * Focus management utilities
 */
export const focusManagement = {
  /**
   * Trap focus within an element
   */
  trapFocus(element: HTMLElement, initialFocus?: HTMLElement): () => void {
    const focusableElements = element.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    ) as NodeListOf<HTMLElement>;

    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];

    if (initialFocus) {
      initialFocus.focus();
    } else {
      firstElement?.focus();
    }

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return;

      if (e.shiftKey) {
        if (document.activeElement === firstElement) {
          lastElement?.focus();
          e.preventDefault();
        }
      } else {
        if (document.activeElement === lastElement) {
          firstElement?.focus();
          e.preventDefault();
        }
      }
    };

    element.addEventListener('keydown', handleKeyDown);

    return () => {
      element.removeEventListener('keydown', handleKeyDown);
    };
  },

  /**
   * Restore focus to element
   */
  restoreFocus(element: HTMLElement): void {
    element.focus();
  },

  /**
   * Get focusable elements
   */
  getFocusableElements(container: HTMLElement): HTMLElement[] {
    const selector = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';
    return Array.from(container.querySelectorAll(selector)) as HTMLElement[];
  },
};

/**
 * ARIA utilities
 */
export const ariaUtils = {
  /**
   * Generate unique ID for ARIA attributes
   */
  generateId(prefix: string = 'aria'): string {
    return `${prefix}-${Math.random().toString(36).substr(2, 9)}`;
  },

  /**
   * Create aria-describedby from multiple IDs
   */
  createAriaDescribedBy(...ids: (string | undefined)[]): string | undefined {
    const filtered = ids.filter(Boolean);
    return filtered.length > 0 ? filtered.join(' ') : undefined;
  },

  /**
   * Create aria-labelledby from multiple IDs
   */
  createAriaLabelledBy(...ids: (string | undefined)[]): string | undefined {
    const filtered = ids.filter(Boolean);
    return filtered.length > 0 ? filtered.join(' ') : undefined;
  },
};

/**
 * Announce message to screen readers
 */
export function announceToScreenReader(message: string, priority: 'polite' | 'assertive' = 'polite'): void {
  const announcement = document.createElement('div');
  announcement.setAttribute('role', 'status');
  announcement.setAttribute('aria-live', priority);
  announcement.setAttribute('aria-atomic', 'true');
  announcement.style.position = 'absolute';
  announcement.style.left = '-10000px';
  announcement.style.width = '1px';
  announcement.style.height = '1px';
  announcement.style.overflow = 'hidden';
  announcement.textContent = message;

  document.body.appendChild(announcement);

  setTimeout(() => {
    document.body.removeChild(announcement);
  }, 1000);
}

/**
 * Check if element is visible to screen readers
 */
export function isVisibleToScreenReaders(element: HTMLElement): boolean {
  const style = window.getComputedStyle(element);
  return (
    style.display !== 'none' &&
    style.visibility !== 'hidden' &&
    style.opacity !== '0' &&
    element.getAttribute('aria-hidden') !== 'true'
  );
}
