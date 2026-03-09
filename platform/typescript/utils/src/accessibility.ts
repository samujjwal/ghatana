/**
 * Accessibility Utilities
 *
 * WCAG 2.1 AA compliant accessibility helpers
 *
 * @migrated-from @ghatana/yappc-ui/utils/accessibility
 */

/**
 * Calculate relative luminance of a color
 * Used for WCAG contrast ratio calculation
 *
 * @param r - Red component (0-255)
 * @param g - Green component (0-255)
 * @param b - Blue component (0-255)
 * @returns Relative luminance (0-1)
 */
function getRelativeLuminance(r: number, g: number, b: number): number {
  const [rs, gs, bs] = [r, g, b].map((c) => {
    const sRGB = c / 255;
    return sRGB <= 0.03928 ? sRGB / 12.92 : Math.pow((sRGB + 0.055) / 1.055, 2.4);
  });

  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

/**
 * Parse hex color to RGB
 *
 * @param hex - Hex color string (#RGB or #RRGGBB)
 * @returns RGB object or null if invalid
 */
function parseHexColor(hex: string): { r: number; g: number; b: number } | null {
  const cleaned = hex.replace('#', '');

  if (cleaned.length === 3) {
    return {
      r: parseInt(cleaned[0] + cleaned[0], 16),
      g: parseInt(cleaned[1] + cleaned[1], 16),
      b: parseInt(cleaned[2] + cleaned[2], 16),
    };
  }

  if (cleaned.length === 6) {
    return {
      r: parseInt(cleaned.substring(0, 2), 16),
      g: parseInt(cleaned.substring(2, 4), 16),
      b: parseInt(cleaned.substring(4, 6), 16),
    };
  }

  return null;
}

/**
 * Calculate contrast ratio between two colors
 *
 * @param color1 - First color (hex format)
 * @param color2 - Second color (hex format)
 * @returns Contrast ratio (1-21)
 *
 * @example
 * ```typescript
 * const ratio = getContrastRatio('#000000', '#ffffff');
 * // Returns 21 (maximum contrast)
 * ```
 */
export function getContrastRatio(color1: string, color2: string): number {
  const rgb1 = parseHexColor(color1);
  const rgb2 = parseHexColor(color2);

  if (!rgb1 || !rgb2) {
    console.warn('Invalid hex color format');
    return 1;
  }

  const l1 = getRelativeLuminance(rgb1.r, rgb1.g, rgb1.b);
  const l2 = getRelativeLuminance(rgb2.r, rgb2.g, rgb2.b);

  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Check if contrast ratio meets WCAG AA standard
 *
 * @param color1 - Foreground color
 * @param color2 - Background color
 * @param fontSize - Font size in pixels (default: 16)
 * @param fontWeight - Font weight (default: 400)
 * @returns True if contrast is sufficient
 *
 * @example
 * ```typescript
 * meetsWCAGAA('#333333', '#ffffff') // true
 * meetsWCAGAA('#777777', '#888888') // false
 * ```
 */
export function meetsWCAGAA(
  color1: string,
  color2: string,
  fontSize: number = 16,
  fontWeight: number = 400
): boolean {
  const ratio = getContrastRatio(color1, color2);

  // Large text (18pt+ or 14pt+ bold) requires 3:1
  const isLargeText = fontSize >= 18 || (fontSize >= 14 && fontWeight >= 700);
  const requiredRatio = isLargeText ? 3 : 4.5;

  return ratio >= requiredRatio;
}

/**
 * Check if contrast ratio meets WCAG AAA standard
 *
 * @param color1 - Foreground color
 * @param color2 - Background color
 * @param fontSize - Font size in pixels (default: 16)
 * @param fontWeight - Font weight (default: 400)
 * @returns True if contrast is sufficient
 */
export function meetsWCAGAAA(
  color1: string,
  color2: string,
  fontSize: number = 16,
  fontWeight: number = 400
): boolean {
  const ratio = getContrastRatio(color1, color2);

  // Large text (18pt+ or 14pt+ bold) requires 4.5:1
  const isLargeText = fontSize >= 18 || (fontSize >= 14 && fontWeight >= 700);
  const requiredRatio = isLargeText ? 4.5 : 7;

  return ratio >= requiredRatio;
}

/**
 * Generate ARIA label from text content
 *
 * @param text - Text content
 * @returns Cleaned ARIA label
 */
export function generateAriaLabel(text: string): string {
  return text
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/[^\w\s-]/g, '');
}

/**
 * Check if element is keyboard accessible
 *
 * @param element - DOM element
 * @returns True if keyboard accessible
 */
export function isKeyboardAccessible(element: HTMLElement): boolean {
  const tabIndex = element.getAttribute('tabindex');
  const isInteractive =
    element.tagName === 'BUTTON' ||
    element.tagName === 'A' ||
    element.tagName === 'INPUT' ||
    element.tagName === 'SELECT' ||
    element.tagName === 'TEXTAREA';

  return isInteractive || (tabIndex !== null && parseInt(tabIndex) >= 0);
}

/**
 * Get recommended text color (black or white) for background
 *
 * @param backgroundColor - Background color (hex)
 * @returns '#000000' or '#ffffff'
 *
 * @example
 * ```typescript
 * getTextColorForBackground('#3B82F6') // '#ffffff'
 * getTextColorForBackground('#FAFAFA') // '#000000'
 * ```
 */
export function getTextColorForBackground(backgroundColor: string): string {
  const whiteRatio = getContrastRatio(backgroundColor, '#ffffff');
  const blackRatio = getContrastRatio(backgroundColor, '#000000');

  return whiteRatio > blackRatio ? '#ffffff' : '#000000';
}

/**
 * Create accessible announcement
 *
 * @param message - Message to announce
 * @param priority - Announcement priority ('polite' | 'assertive')
 *
 * @example
 * ```typescript
 * announceToScreenReader('Form submitted successfully', 'polite');
 * ```
 */
export function announceToScreenReader(
  message: string,
  priority: 'polite' | 'assertive' = 'polite'
): void {
  if (typeof document === 'undefined') return;

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

  // Remove after announcement is read
  setTimeout(() => {
    document.body.removeChild(announcement);
  }, 1000);
}

/**
 * Check if user prefers reduced motion
 *
 * @returns True if user prefers reduced motion
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined') return false;
  if (!window.matchMedia) return false;

  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Check if user prefers high contrast
 *
 * @returns True if user prefers high contrast
 */
export function prefersHighContrast(): boolean {
  if (typeof window === 'undefined') return false;
  if (!window.matchMedia) return false;

  return (
    window.matchMedia('(prefers-contrast: high)').matches ||
    window.matchMedia('(prefers-contrast: more)').matches
  );
}

/**
 * Get accessible focus outline style
 *
 * @returns CSS focus outline style
 */
export function getAccessibleFocusStyle(): string {
  return '2px solid currentColor';
}

/**
 * Check if element has sufficient touch target size (WCAG 2.1)
 *
 * @param element - DOM element
 * @returns True if touch target is >= 44x44px
 */
export function hasSufficientTouchTarget(element: HTMLElement): boolean {
  const rect = element.getBoundingClientRect();
  return rect.width >= 44 && rect.height >= 44;
}
