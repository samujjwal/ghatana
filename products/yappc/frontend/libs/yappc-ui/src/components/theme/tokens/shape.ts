/**
 * Shape tokens for the design system
 * 
 * These tokens define the border radius and other shape-related
 * properties for consistent component styling.
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
};

// Border width scale (in pixels)
export const borderWidth = {
  none: 0,
  thin: 1,
  medium: 2,
  thick: 4,
};

// Component-specific border radius
export const componentRadius = {
  button: borderRadius.md,
  card: borderRadius.md,
  chip: borderRadius.full,
  input: borderRadius.sm,
  panel: borderRadius.lg,
  tooltip: borderRadius.xs,
  modal: borderRadius.lg,
};

// Shape variants
export const shapeVariants = {
  rounded: {
    button: borderRadius.md,
    card: borderRadius.md,
    panel: borderRadius.lg,
  },
  soft: {
    button: borderRadius.lg,
    card: borderRadius.lg,
    panel: borderRadius.xl,
  },
  square: {
    button: borderRadius.xs,
    card: borderRadius.xs,
    panel: borderRadius.sm,
  },
};
