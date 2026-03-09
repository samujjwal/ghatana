/**
 * Theme type definitions — local replacements for @mui/material theme types.
 * These provide the same shape without depending on the MUI package.
 */

/** Color mode */
export type PaletteMode = 'light' | 'dark';

/** Theme options shape — mirrors the commonly-used subset of MUI ThemeOptions */
export interface ThemeOptions {
  palette?: PaletteOptions;
  typography?: Record<string, unknown>;
  spacing?: number | ((factor: number) => string | number);
  breakpoints?: {
    values?: Record<string, number>;
  };
  shape?: {
    borderRadius?: number;
  };
  shadows?: string[];
  zIndex?: Record<string, number>;
  transitions?: {
    duration?: Record<string, number>;
    easing?: Record<string, string>;
  };
  components?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface PaletteOptions {
  mode?: PaletteMode;
  primary?: PaletteColorOptions;
  secondary?: PaletteColorOptions;
  error?: PaletteColorOptions;
  warning?: PaletteColorOptions;
  info?: PaletteColorOptions;
  success?: PaletteColorOptions;
  background?: {
    default?: string;
    paper?: string;
  };
  text?: {
    primary?: string;
    secondary?: string;
    disabled?: string;
  };
  divider?: string;
  [key: string]: unknown;
}

export interface PaletteColorOptions {
  light?: string;
  main?: string;
  dark?: string;
  contrastText?: string;
  [key: string]: unknown;
}
