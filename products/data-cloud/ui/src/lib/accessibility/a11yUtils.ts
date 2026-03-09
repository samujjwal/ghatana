/**
 * Accessibility utilities for CES Workflow Platform.
 *
 * <p><b>Purpose</b><br>
 * Provides utilities for WCAG 2.1 compliance including
 * keyboard navigation, ARIA labels, and color contrast.
 *
 * <p><b>Features</b><br>
 * - Keyboard navigation helpers
 * - ARIA label generation
 * - Color contrast checking
 * - Focus management
 * - Screen reader support
 *
 * @doc.type utility
 * @doc.purpose Accessibility support
 * @doc.layer frontend
 */

/**
 * Check if color contrast meets WCAG AA standards.
 *
 * @param foreground - Foreground color (hex)
 * @param background - Background color (hex)
 * @returns Contrast ratio
 */
export function getContrastRatio(
  foreground: string,
  background: string
): number {
  const fgRGB = hexToRGB(foreground);
  const bgRGB = hexToRGB(background);

  const fgLuminance = getRelativeLuminance(fgRGB);
  const bgLuminance = getRelativeLuminance(bgRGB);

  const lighter = Math.max(fgLuminance, bgLuminance);
  const darker = Math.min(fgLuminance, bgLuminance);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Check if contrast ratio meets WCAG standards.
 *
 * @param ratio - Contrast ratio
 * @param level - WCAG level ('AA' or 'AAA')
 * @returns True if meets standard
 */
export function meetsWCAGStandard(
  ratio: number,
  level: 'AA' | 'AAA' = 'AA'
): boolean {
  const minRatio = level === 'AA' ? 4.5 : 7;
  return ratio >= minRatio;
}

/**
 * Convert hex color to RGB.
 *
 * @param hex - Hex color code
 * @returns RGB object
 */
function hexToRGB(hex: string): { r: number; g: number; b: number } {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result
    ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16),
      }
    : { r: 0, g: 0, b: 0 };
}

/**
 * Get relative luminance of RGB color.
 *
 * @param rgb - RGB object
 * @returns Relative luminance
 */
function getRelativeLuminance(rgb: {
  r: number;
  g: number;
  b: number;
}): number {
  const [r, g, b] = [rgb.r, rgb.g, rgb.b].map((c) => {
    const sRGB = c / 255;
    return sRGB <= 0.03928
      ? sRGB / 12.92
      : Math.pow((sRGB + 0.055) / 1.055, 2.4);
  });

  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/**
 * Generate ARIA label for workflow node.
 *
 * @param nodeType - Node type
 * @param label - Node label
 * @param status - Node status
 * @returns ARIA label
 */
export function generateNodeAriaLabel(
  nodeType: string,
  label: string,
  status?: string
): string {
  let ariaLabel = `${nodeType} node: ${label}`;
  if (status) {
    ariaLabel += `, status: ${status}`;
  }
  return ariaLabel;
}

/**
 * Generate ARIA label for workflow edge.
 *
 * @param sourceLabel - Source node label
 * @param targetLabel - Target node label
 * @param type - Edge type
 * @returns ARIA label
 */
export function generateEdgeAriaLabel(
  sourceLabel: string,
  targetLabel: string,
  type: string
): string {
  return `Connection from ${sourceLabel} to ${targetLabel}, type: ${type}`;
}

/**
 * Handle keyboard navigation.
 *
 * @param event - Keyboard event
 * @param onEnter - Enter key handler
 * @param onEscape - Escape key handler
 * @param onArrowUp - Arrow up handler
 * @param onArrowDown - Arrow down handler
 */
export function handleKeyboardNavigation(
  event: React.KeyboardEvent,
  handlers: {
    onEnter?: () => void;
    onEscape?: () => void;
    onArrowUp?: () => void;
    onArrowDown?: () => void;
    onArrowLeft?: () => void;
    onArrowRight?: () => void;
  }
): void {
  switch (event.key) {
    case 'Enter':
      event.preventDefault();
      handlers.onEnter?.();
      break;
    case 'Escape':
      event.preventDefault();
      handlers.onEscape?.();
      break;
    case 'ArrowUp':
      event.preventDefault();
      handlers.onArrowUp?.();
      break;
    case 'ArrowDown':
      event.preventDefault();
      handlers.onArrowDown?.();
      break;
    case 'ArrowLeft':
      event.preventDefault();
      handlers.onArrowLeft?.();
      break;
    case 'ArrowRight':
      event.preventDefault();
      handlers.onArrowRight?.();
      break;
  }
}

/**
 * Announce message to screen readers.
 *
 * @param message - Message to announce
 * @param priority - Announcement priority ('polite' or 'assertive')
 */
export function announceToScreenReader(
  message: string,
  priority: 'polite' | 'assertive' = 'polite'
): void {
  const announcement = document.createElement('div');
  announcement.setAttribute('role', 'status');
  announcement.setAttribute('aria-live', priority);
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
 * Focus management utility.
 *
 * @doc.class FocusManager
 * @doc.purpose Manage focus for keyboard navigation
 */
export class FocusManager {
  private focusableElements: HTMLElement[] = [];
  private currentFocusIndex: number = -1;

  /**
   * Initialize focus manager for container.
   *
   * @param container - Container element
   */
  initialize(container: HTMLElement): void {
    this.focusableElements = Array.from(
      container.querySelectorAll(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
      )
    ) as HTMLElement[];
    this.currentFocusIndex = -1;
  }

  /**
   * Move focus to next element.
   */
  focusNext(): void {
    if (this.focusableElements.length === 0) return;

    this.currentFocusIndex =
      (this.currentFocusIndex + 1) % this.focusableElements.length;
    this.focusableElements[this.currentFocusIndex].focus();
  }

  /**
   * Move focus to previous element.
   */
  focusPrevious(): void {
    if (this.focusableElements.length === 0) return;

    this.currentFocusIndex =
      (this.currentFocusIndex - 1 + this.focusableElements.length) %
      this.focusableElements.length;
    this.focusableElements[this.currentFocusIndex].focus();
  }

  /**
   * Focus specific element.
   *
   * @param element - Element to focus
   */
  focus(element: HTMLElement): void {
    const index = this.focusableElements.indexOf(element);
    if (index !== -1) {
      this.currentFocusIndex = index;
      element.focus();
    }
  }

  /**
   * Get current focused element.
   *
   * @returns Currently focused element
   */
  getCurrentFocus(): HTMLElement | null {
    return this.focusableElements[this.currentFocusIndex] || null;
  }
}

/**
 * Skip link utility for keyboard navigation.
 *
 * @param targetId - ID of target element
 */
export function createSkipLink(targetId: string): HTMLElement {
  const skipLink = document.createElement('a');
  skipLink.href = `#${targetId}`;
  skipLink.textContent = 'Skip to main content';
  skipLink.className = 'skip-link';
  skipLink.setAttribute('aria-label', 'Skip to main content');

  return skipLink;
}

/**
 * Check if element is visible to screen readers.
 *
 * @param element - Element to check
 * @returns True if visible to screen readers
 */
export function isVisibleToScreenReaders(element: HTMLElement): boolean {
  const style = window.getComputedStyle(element);
  const ariaHidden = element.getAttribute('aria-hidden');

  return (
    style.display !== 'none' &&
    style.visibility !== 'hidden' &&
    ariaHidden !== 'true'
  );
}
