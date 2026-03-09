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

// Type exports
export type BorderRadiusKey = keyof typeof borderRadius;
export type BorderWidthKey = keyof typeof borderWidth;
export type ComponentRadiusKey = keyof typeof componentRadius;
export type ShapeVariant = keyof typeof shapeVariants;

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
