/**
 * Global Border Tokens for Ghatana Platform
 *
 * Unified border radius and width system
 *
 * @migrated-from @ghatana/yappc-ui/tokens/shape
 */

// Border radius scale (in pixels)
export const borderRadius = {
  none: 0,
  xs: 2,
  sm: 4,
  md: 8,
  lg: 12,
  xl: 16,
  '2xl': 24,
  '3xl': 32,
  full: 9999,
} as const;

// Border width scale (in pixels)
export const borderWidth = {
  none: 0,
  thin: 1,
  medium: 2,
  thick: 4,
  // Accessibility: minimum visible border
  accessible: 2,
} as const;

// Component-specific border radius
export const componentRadius = {
  button: borderRadius.md,
  card: borderRadius.md,
  chip: borderRadius.full,
  input: borderRadius.sm,
  panel: borderRadius.lg,
  tooltip: borderRadius.xs,
  modal: borderRadius.lg,
  badge: borderRadius.full,
  avatar: borderRadius.full,
  checkbox: borderRadius.xs,
  radio: borderRadius.full,
  switch: borderRadius.full,
  // Size aliases for ergonomic access (e.g. componentRadius.md)
  sm: borderRadius.sm,
  md: borderRadius.md,
  lg: borderRadius.lg,
} as const;

// Shape variants for different design systems
export const shapeVariants = {
  rounded: {
    button: borderRadius.md,
    card: borderRadius.md,
    panel: borderRadius.lg,
    input: borderRadius.sm,
  },
  soft: {
    button: borderRadius.lg,
    card: borderRadius.lg,
    panel: borderRadius.xl,
    input: borderRadius.md,
  },
  square: {
    button: borderRadius.xs,
    card: borderRadius.xs,
    panel: borderRadius.sm,
    input: borderRadius.xs,
  },
  sharp: {
    button: borderRadius.none,
    card: borderRadius.none,
    panel: borderRadius.none,
    input: borderRadius.none,
  },
} as const;

// Focus state tokens for accessibility
export const focusRing = {
  width: 2,
  offset: 2,
  color: {
    light: 'rgba(25, 118, 210, 0.6)',  // Primary blue with good visibility
    dark: 'rgba(56, 139, 253, 0.6)',   // Lighter blue for dark mode
    highContrast: '#0000ff',           // Pure blue for high contrast mode
  },
  style: 'solid',
  borderRadius: borderRadius.sm,
} as const;

// High contrast mode tokens
export const highContrast = {
  border: {
    light: '#000000',
    dark: '#ffffff',
  },
  focus: {
    color: '#0000ff',
    width: 3,
  },
  text: {
    primary: {
      light: '#000000',
      dark: '#ffffff',
    },
    secondary: {
      light: '#000000',
      dark: '#ffffff',
    },
  },
  background: {
    default: {
      light: '#ffffff',
      dark: '#000000',
    },
    paper: {
      light: '#ffffff',
      dark: '#000000',
    },
  },
} as const;

// Type exports
export type BorderRadiusKey = keyof typeof borderRadius;
export type BorderWidthKey = keyof typeof borderWidth;
export type ComponentRadiusKey = keyof typeof componentRadius;
export type ShapeVariant = keyof typeof shapeVariants;
export type FocusRingColorKey = keyof typeof focusRing.color;

/**
 * Get border radius value
 */
export function getBorderRadius(key: BorderRadiusKey): number {
  return borderRadius[key];
}

/**
 * Get border radius as CSS string
 */
export function getBorderRadiusCSS(key: BorderRadiusKey): string {
  return `${borderRadius[key]}px`;
}

/**
 * Get component-specific border radius
 */
export function getComponentRadius(component: ComponentRadiusKey): number {
  return componentRadius[component];
}

/**
 * Get shape variant radius for a component
 */
export function getShapeVariantRadius(
  variant: ShapeVariant,
  component: keyof typeof shapeVariants.rounded
): number {
  return shapeVariants[variant][component];
}

/**
 * Get focus ring CSS for accessibility
 */
export function getFocusRing(mode: 'light' | 'dark' | 'highContrast' = 'light'): string {
  const color = focusRing.color[mode];
  return `${focusRing.width}px solid ${color}`;
}

/**
 * Get focus ring with offset for accessibility
 */
export function getFocusRingWithOffset(mode: 'light' | 'dark' | 'highContrast' = 'light'): string {
  const ring = getFocusRing(mode);
  return `0 0 0 ${focusRing.offset}px ${ring}`;
}
