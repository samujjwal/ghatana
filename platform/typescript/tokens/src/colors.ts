/**
 * Global Color Tokens for Ghatana Platform
 *
 * Unified color palette merging DCMAAR and YAPPC design systems
 * All colors meet WCAG 2.1 AA contrast requirements
 *
 * @migrated-from @ghatana/dcmaar-shared-ui-core/tokens/colors
 * @migrated-from @ghatana/yappc-ui/tokens/colors
 */

// Base color palette with full shade scales
export const palette = {
  // Primary colors - Blue (unified from both systems)
  primary: {
    50: '#e3f2fd',
    100: '#bbdefb',
    200: '#90caf9',
    300: '#64b5f6',
    400: '#42a5f5',
    500: '#1976d2', // Main primary color (YAPPC)
    600: '#1565c0',
    700: '#0d47a1',
    800: '#0a3880',
    900: '#072a60',
    contrastText: '#ffffff',
  },

  // Secondary colors - Teal
  secondary: {
    50: '#e0f2f1',
    100: '#b2dfdb',
    200: '#80cbc4',
    300: '#4db6ac',
    400: '#26a69a',
    500: '#009688', // Main secondary color
    600: '#00897b',
    700: '#00796b',
    800: '#00695c',
    900: '#004d40',
    contrastText: '#ffffff',
  },

  // Success colors - Green (merged)
  success: {
    50: '#F0FDF4',
    100: '#DCFCE7',
    200: '#BBF7D0',
    300: '#86EFAC',
    400: '#4ADE80',
    500: '#10B981', // DCMAAR shade
    600: '#059669',
    700: '#047857',
    800: '#065F46',
    900: '#064E3B',
    // Semantic variants (YAPPC)
    light: '#81c784',
    main: '#2e7d32',
    dark: '#1b5e20',
    contrastText: '#ffffff',
  },

  // Warning colors - Orange (merged)
  warning: {
    50: '#FFFBEB',
    100: '#FEF3C7',
    200: '#FDE68A',
    300: '#FCD34D',
    400: '#FBBF24',
    500: '#F59E0B', // DCMAAR shade
    600: '#D97706',
    700: '#B45309',
    800: '#92400E',
    900: '#78350F',
    // Semantic variants (YAPPC)
    light: '#ffb74d',
    main: '#ed6c02',
    dark: '#e65100',
    contrastText: '#000000de',
  },

  // Error colors - Red (merged)
  error: {
    50: '#FEF2F2',
    100: '#FEE2E2',
    200: '#FECACA',
    300: '#FCA5A5',
    400: '#F87171',
    500: '#EF4444', // DCMAAR shade
    600: '#DC2626',
    700: '#B91C1C',
    800: '#991B1B',
    900: '#7F1D1D',
    // Semantic variants (YAPPC)
    light: '#e57373',
    main: '#d32f2f',
    dark: '#c62828',
    contrastText: '#ffffff',
  },

  // Info colors - Cyan/Blue (merged)
  info: {
    50: '#F0F9FF',
    100: '#E0F2FE',
    200: '#BAE6FD',
    300: '#7DD3FC',
    400: '#38BDF8',
    500: '#06B6D4', // DCMAAR shade
    600: '#0891B2',
    700: '#0E7490',
    800: '#155E75',
    900: '#164E63',
    // Semantic variants (YAPPC)
    light: '#4fc3f7',
    main: '#0288d1',
    dark: '#01579b',
    contrastText: '#ffffff',
  },

  // Neutral colors - Grayscale (unified)
  neutral: {
    50: '#FAFAFA',
    100: '#F5F5F5',
    200: '#EEEEEE',
    300: '#E0E0E0',
    400: '#BDBDBD',
    500: '#9E9E9E',
    600: '#757575',
    700: '#616161',
    800: '#424242',
    900: '#212121',
  },

  // Gray scale (DCMAAR variant - cooler tone)
  gray: {
    50: '#F9FAFB',
    100: '#F3F4F6',
    200: '#E5E7EB',
    300: '#D1D5DB',
    400: '#9CA3AF',
    500: '#6B7280',
    600: '#4B5563',
    700: '#374151',
    800: '#1F2937',
    900: '#111827',
  },
} as const;

// Light theme colors
export const lightColors = {
  // Background colors
  background: {
    default: palette.neutral[50],
    paper: '#ffffff',
    elevated: '#ffffff',
    surface: '#f8f9fa',
    backdrop: 'rgba(0, 0, 0, 0.5)',
  },

  // Text colors
  text: {
    primary: 'rgba(0, 0, 0, 0.87)',
    secondary: 'rgba(0, 0, 0, 0.6)',
    disabled: 'rgba(0, 0, 0, 0.38)',
    hint: 'rgba(0, 0, 0, 0.38)',
    icon: 'rgba(0, 0, 0, 0.54)',
  },

  // UI elements
  divider: 'rgba(0, 0, 0, 0.12)',
  border: 'rgba(0, 0, 0, 0.12)',

  // Action states
  action: {
    active: 'rgba(0, 0, 0, 0.54)',
    hover: 'rgba(0, 0, 0, 0.04)',
    hoverOpacity: 0.04,
    selected: 'rgba(0, 0, 0, 0.08)',
    selectedOpacity: 0.08,
    disabled: 'rgba(0, 0, 0, 0.26)',
    disabledBackground: 'rgba(0, 0, 0, 0.12)',
    disabledOpacity: 0.38,
    focus: 'rgba(0, 0, 0, 0.12)',
    focusOpacity: 0.12,
    activatedOpacity: 0.12,
  },
} as const;

// Dark theme colors - GitHub-inspired cool blue-gray palette
// Cohesive, professional, easy on eyes
export const darkColors = {
  // Background colors - Blue-tinted grays for visual cohesion
  background: {
    default: '#0d1117',   // GitHub dark background
    paper: '#161b22',     // Card/panel background
    elevated: '#21262d',  // Elevated surfaces (modals, dropdowns)
    surface: '#161b22',   // General surface color
    backdrop: 'rgba(1, 4, 9, 0.8)',  // Modal backdrop
  },

  // Text colors - Soft whites for reduced eye strain
  text: {
    primary: '#e6edf3',   // Main text - soft white
    secondary: '#8b949e', // Secondary text - muted
    disabled: '#484f58',  // Disabled text
    hint: '#6e7681',      // Hint/placeholder text
    icon: '#8b949e',      // Icon color
  },

  // UI elements - Visible but subtle borders
  divider: '#30363d',     // Divider lines
  border: '#30363d',      // Border color

  // Action states
  action: {
    active: '#e6edf3',
    hover: 'rgba(177, 186, 196, 0.12)',
    hoverOpacity: 0.12,
    selected: 'rgba(177, 186, 196, 0.2)',
    selectedOpacity: 0.2,
    disabled: '#484f58',
    disabledBackground: 'rgba(110, 118, 129, 0.1)',
    disabledOpacity: 0.38,
    focus: 'rgba(88, 166, 255, 0.4)',  // Blue focus ring
    focusOpacity: 0.4,
    activatedOpacity: 0.24,
  },
} as const;

// Type exports
export type ColorScale = keyof typeof palette;
export type ColorShade = '50' | '100' | '200' | '300' | '400' | '500' | '600' | '700' | '800' | '900';
export type SemanticColorKey = 'light' | 'main' | 'dark' | 'contrastText';

/**
 * Get color value by scale and shade
 */
export function getColor(scale: ColorScale, shade: string): string {
  const colorScale = palette[scale];
  if (typeof colorScale === 'object' && shade in colorScale) {
    return (colorScale as Record<string, string>)[shade];
  }
  throw new Error(`Invalid color: ${scale}.${shade}`);
}

/**
 * Get semantic color (light/main/dark)
 */
export function getSemanticColor(
  scale: 'success' | 'warning' | 'error' | 'info',
  variant: SemanticColorKey = 'main'
): string {
  return getColor(scale, variant);
}
