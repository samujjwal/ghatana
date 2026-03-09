/**
 * Color tokens for the design system
 * 
 * These tokens define the color palette for the application,
 * with semantic naming for consistent usage across components.
 * All colors are designed to meet WCAG 2.1 AA contrast requirements.
 */

// Base palette
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
  
  // Secondary colors - Teal (better for accessibility than pink/red)
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
    main: '#2e7d32',  // Darker for better contrast
    dark: '#1b5e20',
    contrastText: '#ffffff',
  },
  warning: {
    light: '#ffb74d',
    main: '#ed6c02',  // Darker orange for better contrast
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
    main: '#0288d1',  // Darker blue for better contrast
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
};

// Dark theme colors
export const darkColors = {
  // Background colors
  background: {
    default: '#121212',
    paper: '#1e1e1e',
    elevated: '#2c2c2c',
    surface: '#1e1e1e',
    backdrop: 'rgba(0, 0, 0, 0.7)',
  },
  
  // Text colors
  text: {
    primary: 'rgba(255, 255, 255, 0.87)',
    secondary: 'rgba(255, 255, 255, 0.7)',
    disabled: 'rgba(255, 255, 255, 0.5)',
    hint: 'rgba(255, 255, 255, 0.5)',
    icon: 'rgba(255, 255, 255, 0.5)',
  },
  
  // UI elements
  divider: 'rgba(255, 255, 255, 0.12)',
  border: 'rgba(255, 255, 255, 0.12)',
  
  // Action states
  action: {
    active: '#fff',
    hover: 'rgba(255, 255, 255, 0.08)',
    hoverOpacity: 0.08,
    selected: 'rgba(255, 255, 255, 0.16)',
    selectedOpacity: 0.16,
    disabled: 'rgba(255, 255, 255, 0.3)',
    disabledBackground: 'rgba(255, 255, 255, 0.12)',
    disabledOpacity: 0.38,
    focus: 'rgba(255, 255, 255, 0.12)',
    focusOpacity: 0.12,
    activatedOpacity: 0.24,
  },
};
