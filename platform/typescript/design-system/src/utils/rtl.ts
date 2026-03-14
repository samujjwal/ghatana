/**
 * RTL (Right-to-Left) utilities for bi-directional layout support
 */

export type Direction = 'ltr' | 'rtl';

/**
 * Detect text direction from language code
 */
export function getDirectionFromLanguage(lang: string): Direction {
  const rtlLanguages = ['ar', 'he', 'fa', 'ur', 'yi', 'ji', 'iw', 'ku', 'ps', 'sd'];
  const langCode = lang.split('-')[0].toLowerCase();
  return rtlLanguages.includes(langCode) ? 'rtl' : 'ltr';
}

/**
 * Get current document direction
 */
export function getDocumentDirection(): Direction {
  if (typeof document === 'undefined') return 'ltr';
  return (document.documentElement.dir as Direction) || 'ltr';
}

/**
 * Set document direction
 */
export function setDocumentDirection(direction: Direction): void {
  if (typeof document === 'undefined') return;
  document.documentElement.dir = direction;
  document.documentElement.lang = direction === 'rtl' ? 'ar' : 'en';
}

/**
 * Flip margin/padding values for RTL
 */
export function flipMargin(
  top?: string | number,
  right?: string | number,
  bottom?: string | number,
  left?: string | number,
  direction: Direction = 'ltr'
): { marginTop?: string | number; marginRight?: string | number; marginBottom?: string | number; marginLeft?: string | number } {
  if (direction === 'ltr') {
    return { marginTop: top, marginRight: right, marginBottom: bottom, marginLeft: left };
  }
  return { marginTop: top, marginRight: left, marginBottom: bottom, marginLeft: right };
}

/**
 * Flip padding values for RTL
 */
export function flipPadding(
  top?: string | number,
  right?: string | number,
  bottom?: string | number,
  left?: string | number,
  direction: Direction = 'ltr'
): { paddingTop?: string | number; paddingRight?: string | number; paddingBottom?: string | number; paddingLeft?: string | number } {
  if (direction === 'ltr') {
    return { paddingTop: top, paddingRight: right, paddingBottom: bottom, paddingLeft: left };
  }
  return { paddingTop: top, paddingRight: left, paddingBottom: bottom, paddingLeft: right };
}

/**
 * Get logical start/end values based on direction
 */
export function getLogicalPosition(
  start?: string | number,
  end?: string | number,
  direction: Direction = 'ltr'
): { left?: string | number; right?: string | number } {
  if (direction === 'ltr') {
    return { left: start, right: end };
  }
  return { left: end, right: start };
}

/**
 * Transform value for RTL (e.g., translateX)
 */
export function transformForRTL(value: number, direction: Direction = 'ltr'): number {
  return direction === 'rtl' ? -value : value;
}

/**
 * Get text alignment for direction
 */
export function getTextAlign(align: 'start' | 'end' | 'center', direction: Direction = 'ltr'): 'left' | 'right' | 'center' {
  if (align === 'center') return 'center';
  if (direction === 'ltr') {
    return align === 'start' ? 'left' : 'right';
  }
  return align === 'start' ? 'right' : 'left';
}

/**
 * Mirror icon for RTL (rotate 180 degrees)
 */
export function shouldMirrorIcon(direction: Direction = 'ltr'): boolean {
  return direction === 'rtl';
}

/**
 * RTL-aware flex direction
 */
export function getFlexDirection(
  direction: 'row' | 'column',
  rtlDirection: Direction = 'ltr'
): 'row' | 'row-reverse' | 'column' | 'column-reverse' {
  if (direction === 'column') return 'column';
  return rtlDirection === 'rtl' ? 'row-reverse' : 'row';
}
