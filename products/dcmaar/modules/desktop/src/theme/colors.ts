/**
 * Centralized color palette for the DCMaar Desktop application
 *
 * This replaces all hardcoded colors throughout the application
 * to ensure consistent theming and easy customization.
 */

export const colors = {
  // Primary colors
  primary: {
    main: '#1976d2',
    light: '#42a5f5',
    dark: '#1565c0',
    contrastText: '#ffffff',
  },

  // Secondary colors
  secondary: {
    main: '#9c27b0',
    light: '#ba68c8',
    dark: '#7b1fa2',
    contrastText: '#ffffff',
  },

  // Error colors
  error: {
    main: '#d32f2f',
    light: '#ef5350',
    dark: '#c62828',
    contrastText: '#ffffff',
  },

  // Warning colors
  warning: {
    main: '#ed6c02',
    light: '#ff9800',
    dark: '#e65100',
    contrastText: '#ffffff',
  },

  // Info colors
  info: {
    main: '#0288d1',
    light: '#03a9f4',
    dark: '#01579b',
    contrastText: '#ffffff',
  },

  // Success colors
  success: {
    main: '#2e7d32',
    light: '#4caf50',
    dark: '#1b5e20',
    contrastText: '#ffffff',
  },

  // Neutral/Gray scale
  grey: {
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

  // Background colors
  background: {
    default: '#ffffff',
    paper: '#ffffff',
    dark: '#121212',
    darkPaper: '#1e1e1e',
  },

  // Text colors
  text: {
    primary: 'rgba(0, 0, 0, 0.87)',
    secondary: 'rgba(0, 0, 0, 0.6)',
    disabled: 'rgba(0, 0, 0, 0.38)',
    primaryDark: 'rgba(255, 255, 255, 0.87)',
    secondaryDark: 'rgba(255, 255, 255, 0.6)',
    disabledDark: 'rgba(255, 255, 255, 0.38)',
  },

  // Divider colors
  divider: 'rgba(0, 0, 0, 0.12)',
  dividerDark: 'rgba(255, 255, 255, 0.12)',

  // Action colors
  action: {
    active: 'rgba(0, 0, 0, 0.54)',
    hover: 'rgba(0, 0, 0, 0.04)',
    selected: 'rgba(0, 0, 0, 0.08)',
    disabled: 'rgba(0, 0, 0, 0.26)',
    disabledBackground: 'rgba(0, 0, 0, 0.12)',
    activeDark: 'rgba(255, 255, 255, 0.56)',
    hoverDark: 'rgba(255, 255, 255, 0.08)',
    selectedDark: 'rgba(255, 255, 255, 0.16)',
    disabledDark: 'rgba(255, 255, 255, 0.3)',
    disabledBackgroundDark: 'rgba(255, 255, 255, 0.12)',
  },

  // Status colors (for metrics, alerts, etc.)
  status: {
    online: '#4caf50',
    offline: '#f44336',
    warning: '#ff9800',
    idle: '#9e9e9e',
    active: '#2196f3',
  },

  // Chart colors (for data visualization)
  chart: {
    blue: '#2196f3',
    green: '#4caf50',
    red: '#f44336',
    yellow: '#ffeb3b',
    purple: '#9c27b0',
    orange: '#ff9800',
    teal: '#009688',
    pink: '#e91e63',
    indigo: '#3f51b5',
    cyan: '#00bcd4',
  },

  // Severity levels
  severity: {
    critical: '#d32f2f',
    high: '#f57c00',
    medium: '#fbc02d',
    low: '#388e3c',
    info: '#1976d2',
  },
} as const;

// Type helper for color paths
export type ColorPath =
  | keyof typeof colors
  | `${keyof typeof colors}.${string}`;

/**
 * Get a color value from the palette using dot notation
 * @example getColor('primary.main') => '#1976d2'
 */
export function getColor(path: string): string {
  const parts = path.split('.');
  let value: any = colors;

  for (const part of parts) {
    value = value[part];
    if (value === undefined) {
      console.warn(`Color path "${path}" not found in palette`);
      return colors.grey[500]; // Fallback
    }
  }

  return value as string;
}

/**
 * Create alpha variant of a color
 * @param color - Hex color code
 * @param alpha - Alpha value (0-1)
 */
export function withAlpha(color: string, alpha: number): string {
  // Convert hex to rgba
  const hex = color.replace('#', '');
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);

  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

export default colors;
