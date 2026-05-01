/**
 * Shape tokens for the design system
 *
 * These tokens define the border radius and other shape-related
 * properties for consistent component styling.
 * 
 * @deprecated Use @ghatana/tokens instead. This file will be removed.
 * Migration guide: Replace imports from './tokens/shape' with '@ghatana/tokens'
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
  // Accessibility: minimum visible border
  accessible: 2,
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
};

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
};
