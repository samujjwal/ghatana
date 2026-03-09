/**
 * Helper utilities for accessibility auditing.
 *
 * Provides color contrast calculations, element analysis, and DOM utilities.
 */

/**
 * Utility class for accessibility audit helpers.
 *
 * Static methods provide color contrast calculations, element property detection,
 * ARIA validation, and other DOM-related utilities used by the auditor.
 */
export class AccessibilityUtils {
  /**
   * Parse RGB color string to tuple of RGB values.
   *
   * @param color - Color string in format 'rgb(r, g, b)'
   * @returns Tuple of [red, green, blue] values or null if not valid RGB
   *
   * @example
   * ```ts
   * AccessibilityUtils.parseColor('rgb(255, 0, 0)'); // [255, 0, 0]
   * ```
   */
  static parseColor(color: string): [number, number, number] | null {
    const rgb = color.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
    return rgb
      ? [parseInt(rgb[1], 10), parseInt(rgb[2], 10), parseInt(rgb[3], 10)]
      : null;
  }

  /**
   * Get effective background color of an element by traversing parent elements.
   *
   * Walks up the DOM tree to find the first element with a non-transparent background.
   * Returns white as default if no background found.
   *
   * @param element - Element to check
   * @returns Tuple of [red, green, blue] values for effective background
   */
  static getEffectiveBackgroundColor(
    element: Element
  ): [number, number, number] {
    let current: Element | null = element;

    while (current && current !== document.body) {
      const style = window.getComputedStyle(current);
      const bg = this.parseColor(style.backgroundColor);
      if (bg && bg.some((channel) => channel > 0)) {
        return bg;
      }
      current = current.parentElement;
    }

    return [255, 255, 255]; // Default white background
  }

  /**
   * Calculate contrast ratio between two colors using WCAG formula.
   *
   * @param color1 - First color as [red, green, blue]
   * @param color2 - Second color as [red, green, blue]
   * @returns Contrast ratio (1.0 to 21.0)
   *
   * @example
   * ```ts
   * const ratio = AccessibilityUtils.calculateContrast([0, 0, 0], [255, 255, 255]);
   * // ratio = 21.0
   * ```
   */
  static calculateContrast(
    color1: [number, number, number],
    color2: [number, number, number]
  ): number {
    const luminance1 = this.getLuminance(color1);
    const luminance2 = this.getLuminance(color2);
    const brighter = Math.max(luminance1, luminance2);
    const darker = Math.min(luminance1, luminance2);
    return (brighter + 0.05) / (darker + 0.05);
  }

  /**
   * Calculate relative luminance of a color for WCAG contrast calculation.
   *
   * Uses the WCAG formula for calculating relative luminance.
   *
   * @param color - Color as [red, green, blue]
   * @returns Luminance value (0.0 to 1.0)
   */
  static getLuminance([r, g, b]: [number, number, number]): number {
    const [rs, gs, bs] = [r, g, b].map((c: number) => {
      const channel = c / 255;
      return channel <= 0.03928
        ? channel / 12.92
        : Math.pow((channel + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
  }

  /**
   * Determine if text is considered "large" under WCAG AA standards.
   *
   * Large text has lower contrast requirements:
   * - 18pt or larger, or
   * - 14pt or larger and bold (weight 700+)
   *
   * @param style - Computed CSS style of element
   * @returns True if text qualifies as large
   */
  static isLargeText(style: CSSStyleDeclaration): boolean {
    const fontSize = parseFloat(style.fontSize);
    const fontWeight = style.fontWeight;

    return (
      fontSize >= 18 ||
      (fontSize >= 14 &&
        (fontWeight === 'bold' || parseInt(fontWeight, 10) >= 700))
    );
  }

  /**
   * Check if element has custom focus styles in className.
   *
   * Simple heuristic check for Tailwind-style focus classes.
   *
   * @param element - Element to check
   * @returns True if custom focus styles detected
   */
  static hasCustomFocusStyles(element: Element): boolean {
    const className = element.className;
    return (
      typeof className === 'string' &&
      (className.includes('focus:') || className.includes('focus-visible:'))
    );
  }

  /**
   * Check if input has associated label or ARIA labeling.
   *
   * @param input - Input element to check
   * @returns True if properly labeled
   */
  static hasAssociatedLabel(input: HTMLInputElement): boolean {
    return !!(
      input.labels?.length ||
      input.getAttribute('aria-label') ||
      input.getAttribute('aria-labelledby') ||
      input.getAttribute('title')
    );
  }

  /**
   * Validate ARIA attribute name against known valid attributes.
   *
   * @param attrName - Attribute name to validate
   * @returns True if attribute is valid ARIA
   *
   * @example
   * ```ts
   * AccessibilityUtils.isValidAriaAttribute('aria-label'); // true
   * AccessibilityUtils.isValidAriaAttribute('aria-invalid'); // true
   * AccessibilityUtils.isValidAriaAttribute('aria-fake'); // false
   * ```
   */
  static isValidAriaAttribute(attrName: string): boolean {
    const validAriaAttributes = [
      'aria-label',
      'aria-labelledby',
      'aria-describedby',
      'aria-hidden',
      'aria-expanded',
      'aria-selected',
      'aria-checked',
      'aria-disabled',
      'aria-readonly',
      'aria-required',
      'aria-invalid',
      'aria-live',
      'aria-atomic',
      'aria-relevant',
      'aria-busy',
      'aria-controls',
      'aria-owns',
      'aria-flowto',
      'aria-haspopup',
      'aria-level',
      'aria-multiline',
      'aria-multiselectable',
      'aria-orientation',
      'aria-pressed',
      'aria-sort',
      'aria-valuemax',
      'aria-valuemin',
      'aria-valuenow',
      'aria-valuetext',
      'aria-current',
      'aria-details',
      'aria-keyshortcuts',
      'aria-roledescription',
    ];

    return validAriaAttributes.includes(attrName);
  }

  /**
   * Check if page respects prefers-reduced-motion preference.
   *
   * Looks for @media (prefers-reduced-motion: reduce) in stylesheets.
   *
   * @returns True if reduced motion support found
   */
  static respectsReducedMotion(): boolean {
    // Simplified check - would need more sophisticated CSS parsing
    const styles = document.querySelectorAll('style');
    for (const style of styles) {
      if (
        style.textContent?.includes('@media (prefers-reduced-motion: reduce)')
      ) {
        return true;
      }
    }
    return false;
  }

  /**
   * Generate unique ID for issue tracking.
   *
   * @param category - Issue category name
   * @returns Unique ID string
   */
  static generateIssueId(category: string): string {
    return `${category}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}
