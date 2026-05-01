/**
 * Color tokens for the design system
 *
 * These tokens define the color palette for the application,
 * with semantic naming for consistent usage across components.
 * All colors are designed to meet WCAG 2.1 AA contrast requirements.
 * 
 * @deprecated Use @ghatana/tokens instead. This file will be removed.
 * Migration guide: Replace imports from './tokens/colors' with '@ghatana/tokens'
 */

// Re-export from platform tokens for backwards compatibility
export { palette, lightColors, darkColors } from '@ghatana/tokens';

// Legacy exports for backwards compatibility - will be removed
export const palette = {
  // Primary colors - Blue
  primary: {
    50: '#e3f2fd',
    100: '#bbdefb',
    200: '#90caf9',
    300: '#64b5f6',
    400: '#42a5f5',
    500: '#1976d2', // Main primary color
    600: '#1565c0',
    700: '#0d47a1',
    800: '#0a3880',
    900: '#072a60',
    contrastText: '#ffffff',
  },

  // Secondary colors - Purple (updated for better accessibility)
  secondary: {
    50: '#f3e8ff',
    100: '#d8b4fe',
    200: '#c084fc',
    300: '#a855f7',
    400: '#9333ea',
    500: '#7c3aed', // Main secondary color - WCAG AA compliant
    600: '#6d28d9',
    700: '#5b21b6',
    800: '#4c1d95',
    900: '#3b0764',
    contrastText: '#ffffff',
  },

  // Neutral colors - Grayscale
  neutral: {
    50: '#fafafa',
    100: '#f5f5f5',
    200: '#eeeeee',
    300: '#e0e0e0',
    400: '#bdbdbd',
    500: '#9e9e9e',
    600: '#757575',
    700: '#616161',
    800: '#424242',
    900: '#212121',
  },

  // Semantic colors with improved contrast
  success: {
    light: '#81c784',
    main: '#2e7d32', // Darker for better contrast
    dark: '#1b5e20',
    contrastText: '#ffffff',
  },
  warning: {
    light: '#ffb74d',
    main: '#ed6c02', // Darker orange for better contrast
    dark: '#e65100',
    contrastText: '#000000de',
  },
  error: {
    light: '#e57373',
    main: '#d32f2f',
    dark: '#c62828',
    contrastText: '#ffffff',
  },
  info: {
    light: '#4fc3f7',
    main: '#0288d1', // Darker blue for better contrast
    dark: '#01579b',
    contrastText: '#ffffff',
  },
};

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

  // UI elements - Increased opacity for better visibility
  divider: 'rgba(0, 0, 0, 0.2)',
  border: 'rgba(0, 0, 0, 0.2)',

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
};

// Dark theme colors
export const darkColors = {
  // Background colors
  background: {
    default: '#0d1117',
    paper: '#161b22',
    elevated: '#21262d',
    surface: '#161b22',
    backdrop: 'rgba(1, 4, 9, 0.8)',
  },

  // Text colors
  text: {
    primary: '#e6edf3',
    secondary: '#8b949e',
    disabled: '#484f58',
    hint: '#6e7681',
    icon: '#8b949e',
  },

  // UI elements - Increased visibility for accessibility
  divider: '#484f58',
  border: '#484f58',

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
    focus: 'rgba(56, 139, 253, 0.6)',
    focusOpacity: 0.6,
    activatedOpacity: 0.24,
  },
};
