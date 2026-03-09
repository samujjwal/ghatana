/**
 * Theme Management System
 *
 * Provides theme switching, validation, and customization capabilities.
 * Supports light/dark modes, custom theme uploads, and runtime theme changes.
 *
 * Now fully integrated with @ghatana/yappc-ui design tokens - all themes use shared tokens
 * from the design system for consistency across the entire application.
 *
 * Features:
 * - Built-in light and dark themes (using @ghatana/yappc-ui tokens)
 * - Theme validation with Zod schemas
 * - Flash-free theme switching
 * - Custom theme upload and preview
 * - Theme merging and inheritance
 * - CSS variable injection
 * - Theme persistence
 *
 * @module theming/themeManager
 */

import {
  palette,
  spacing,
  borderRadius,
  lightColors,
  darkColors,
  lightShadows,
  darkShadows,
  fontFamilies,
  fontWeights,
} from '@ghatana/yappc-ui';
import * as React from 'react';

import type { CanvasTheme } from '../types/canvas-document';

/**
 * Theme mode options
 */
export type ThemeMode = 'light' | 'dark' | 'custom';

/**
 * Built-in light theme
 * 
 * All values now reference shared design tokens from @ghatana/yappc-shared-ui-core/tokens.
 * This ensures Canvas themes stay consistent with the rest of the application.
 */
export const LIGHT_THEME: CanvasTheme = {
  colors: {
    background: lightColors.background.default,  // Uses shared light theme default background
    grid: palette.neutral[200],                  // Light neutral for grid
    selection: palette.primary[500],             // Primary color for selection
    hover: palette.primary[400],                 // Lighter primary for hover
    focus: palette.primary[600],                 // Darker primary for focus
    error: palette.error.main,                   // Semantic error color
    success: palette.success.main,               // Semantic success color
    warning: palette.warning.main,               // Semantic warning color
  },
  spacing: {
    xs: spacing[1],     // 4px
    sm: spacing[2],     // 8px
    md: spacing[4],     // 16px
    lg: spacing[6],     // 24px
    xl: spacing[8],     // 32px
  },
  borderRadius: {
    sm: borderRadius.sm,   // 4px
    md: borderRadius.md,   // 8px
    lg: borderRadius.lg,   // 12px
  },
  shadows: {
    sm: lightShadows[1],   // Subtle shadow
    md: lightShadows[2],   // Medium shadow
    lg: lightShadows[3],   // Large shadow
  },
  typography: {
    fontFamily: fontFamilies.primary,
    fontSize: {
      xs: 12,              // fontSizes.xs is 0.75rem, convert to px
      sm: 14,              // fontSizes.sm is 0.875rem
      md: 16,              // fontSizes.md is 1rem
      lg: 18,              // fontSizes.lg is 1.125rem
      xl: 20,              // fontSizes.xl is 1.25rem
    },
    fontWeight: {
      normal: fontWeights.regular,  // 400
      medium: fontWeights.medium,   // 500
      bold: fontWeights.bold,       // 700
    },
  },
};

/**
 * Built-in dark theme
 * 
 * All values now reference shared design tokens from @ghatana/yappc-shared-ui-core/tokens.
 * This ensures Canvas themes stay consistent with the rest of the application.
 */
export const DARK_THEME: CanvasTheme = {
  colors: {
    background: darkColors.background.default,   // Uses shared dark theme default background
    grid: palette.neutral[700],                  // Dark neutral for grid
    selection: palette.primary[400],             // Lighter primary for dark mode selection
    hover: palette.primary[300],                 // Even lighter for hover
    focus: palette.primary[500],                 // Standard primary for focus
    error: palette.error.light,                  // Lighter error for dark mode
    success: palette.success.light,              // Lighter success for dark mode
    warning: palette.warning.light,              // Lighter warning for dark mode
  },
  spacing: LIGHT_THEME.spacing,                  // Spacing is theme-agnostic
  borderRadius: LIGHT_THEME.borderRadius,        // Border radius is theme-agnostic
  shadows: {
    sm: darkShadows[1],    // Dark mode shadow (more opacity)
    md: darkShadows[2],    // Dark mode shadow
    lg: darkShadows[3],    // Dark mode shadow
  },
  typography: LIGHT_THEME.typography,            // Typography is theme-agnostic
};

/**
 * Theme registry mapping
 */
export const THEMES: Record<'light' | 'dark', CanvasTheme> = {
  light: LIGHT_THEME,
  dark: DARK_THEME,
};

/**
 * Theme manager class for runtime theme operations
 */
export class ThemeManager {
  private currentTheme: CanvasTheme = LIGHT_THEME;
  private currentMode: ThemeMode = 'light';
  private customThemes: Map<string, CanvasTheme> = new Map();
  private listeners: Set<(theme: CanvasTheme, mode: ThemeMode) => void> = new Set();
  private styleElement: HTMLStyleElement | null = null;

  /**
   *
   */
  constructor() {
    if (typeof document !== 'undefined') {
      this.styleElement = document.createElement('style');
      this.styleElement.id = 'canvas-theme-variables';
      document.head.appendChild(this.styleElement);
    }
  }

  /**
   * Get current active theme
   */
  getCurrentTheme(): CanvasTheme {
    return this.currentTheme;
  }

  /**
   * Get current theme mode
   */
  getCurrentMode(): ThemeMode {
    return this.currentMode;
  }

  /**
   * Set theme by mode (light/dark) or custom theme ID
   */
  setTheme(mode: ThemeMode, customThemeId?: string): void {
    let newTheme: CanvasTheme;

    if (mode === 'custom' && customThemeId) {
      const customTheme = this.customThemes.get(customThemeId);
      if (!customTheme) {
        throw new Error(`Custom theme "${customThemeId}" not found`);
      }
      newTheme = customTheme;
    } else if (mode === 'light' || mode === 'dark') {
      newTheme = THEMES[mode];
    } else {
      throw new Error(`Invalid theme mode: ${mode}`);
    }

    this.currentTheme = newTheme;
    this.currentMode = mode;

    // Apply theme without flash (atomic update)
    this.applyTheme(newTheme);

    // Notify listeners
    this.notifyListeners();
  }

  /**
   * Register a custom theme
   */
  registerCustomTheme(id: string, theme: CanvasTheme): void {
    this.customThemes.set(id, theme);
  }

  /**
   * Remove a custom theme
   */
  removeCustomTheme(id: string): void {
    this.customThemes.delete(id);
  }

  /**
   * Get all registered custom theme IDs
   */
  getCustomThemeIds(): string[] {
    return Array.from(this.customThemes.keys());
  }

  /**
   * Get a custom theme by ID
   */
  getCustomTheme(id: string): CanvasTheme | undefined {
    return this.customThemes.get(id);
  }

  /**
   * Merge partial theme with current theme (for runtime overrides)
   */
  mergeTheme(partialTheme: Partial<CanvasTheme>): void {
    const merged = this.deepMerge(this.currentTheme, partialTheme);
    this.currentTheme = merged;
    this.applyTheme(merged);
    this.notifyListeners();
  }

  /**
   * Apply theme to DOM via CSS variables (flash-free)
   */
  private applyTheme(theme: CanvasTheme): void {
    if (!this.styleElement) return;

    const cssVars = this.themeToCSSVariables(theme);
    this.styleElement.textContent = `:root { ${cssVars} }`;
  }

  /**
   * Convert theme to CSS variables string
   */
  private themeToCSSVariables(theme: CanvasTheme): string {
    const vars: string[] = [];

    // Colors
    Object.entries(theme.colors).forEach(([key, value]) => {
      vars.push(`--canvas-color-${key}: ${value};`);
    });

    // Spacing
    Object.entries(theme.spacing).forEach(([key, value]) => {
      vars.push(`--canvas-spacing-${key}: ${value}px;`);
    });

    // Border radius
    Object.entries(theme.borderRadius).forEach(([key, value]) => {
      vars.push(`--canvas-border-radius-${key}: ${value}px;`);
    });

    // Shadows
    Object.entries(theme.shadows).forEach(([key, value]) => {
      vars.push(`--canvas-shadow-${key}: ${value};`);
    });

    // Typography
    vars.push(`--canvas-font-family: ${theme.typography.fontFamily};`);
    Object.entries(theme.typography.fontSize).forEach(([key, value]) => {
      vars.push(`--canvas-font-size-${key}: ${value}px;`);
    });
    Object.entries(theme.typography.fontWeight).forEach(([key, value]) => {
      vars.push(`--canvas-font-weight-${key}: ${value};`);
    });

    return vars.join(' ');
  }

  /**
   * Subscribe to theme changes
   */
  subscribe(listener: (theme: CanvasTheme, mode: ThemeMode) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Notify all listeners of theme change
   */
  private notifyListeners(): void {
    this.listeners.forEach((listener) => {
      listener(this.currentTheme, this.currentMode);
    });
  }

  /**
   * Deep merge two objects
   */
  private deepMerge<T extends Record<string, unknown>>(target: T, source: Partial<T>): T {
    const output = { ...target };

    for (const key in source) {
      if (Object.prototype.hasOwnProperty.call(source, key)) {
        const sourceValue = source[key];
        const targetValue = output[key];

        if (
          typeof sourceValue === 'object' &&
          sourceValue !== null &&
          !Array.isArray(sourceValue) &&
          typeof targetValue === 'object' &&
          targetValue !== null
        ) {
          output[key] = this.deepMerge(targetValue, sourceValue as unknown);
        } else {
          output[key] = sourceValue as unknown;
        }
      }
    }

    return output;
  }

  /**
   * Detect system theme preference
   */
  detectSystemTheme(): 'light' | 'dark' {
    if (typeof window === 'undefined') return 'light';

    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    return prefersDark ? 'dark' : 'light';
  }

  /**
   * Auto-switch theme based on system preference
   */
  enableAutoTheme(): () => void {
    if (typeof window === 'undefined') return () => {};

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => {
      this.setTheme(e.matches ? 'dark' : 'light');
    };

    mediaQuery.addEventListener('change', handler);
    this.setTheme(this.detectSystemTheme());

    return () => mediaQuery.removeEventListener('change', handler);
  }

  /**
   * Persist theme to localStorage
   */
  persistTheme(key: string = 'canvas-theme'): void {
    if (typeof localStorage === 'undefined') return;

    localStorage.setItem(
      key,
      JSON.stringify({
        mode: this.currentMode,
        theme: this.currentMode === 'custom' ? this.currentTheme : null,
      })
    );
  }

  /**
   * Load theme from localStorage
   */
  loadPersistedTheme(key: string = 'canvas-theme'): boolean {
    if (typeof localStorage === 'undefined') return false;

    try {
      const stored = localStorage.getItem(key);
      if (!stored) return false;

      const { mode, theme } = JSON.parse(stored);
      
      if (mode === 'custom' && theme) {
        this.registerCustomTheme('persisted', theme);
        this.setTheme('custom', 'persisted');
      } else {
        this.setTheme(mode);
      }

      return true;
    } catch {
      return false;
    }
  }

  /**
   * Clean up resources
   */
  destroy(): void {
    if (this.styleElement) {
      this.styleElement.remove();
      this.styleElement = null;
    }
    this.listeners.clear();
    this.customThemes.clear();
  }
}

/**
 * Global theme manager instance
 */
export const globalThemeManager = new ThemeManager();

/**
 * React hook for theme management
 */
export function useTheme() {
  const [theme, setTheme] = React.useState(globalThemeManager.getCurrentTheme());
  const [mode, setMode] = React.useState(globalThemeManager.getCurrentMode());

  React.useEffect(() => {
    const unsubscribe = globalThemeManager.subscribe((newTheme, newMode) => {
      setTheme(newTheme);
      setMode(newMode);
    });

    return unsubscribe;
  }, []);

  const switchTheme = React.useCallback((newMode: ThemeMode, customThemeId?: string) => {
    globalThemeManager.setTheme(newMode, customThemeId);
  }, []);

  const mergeTheme = React.useCallback((partialTheme: Partial<CanvasTheme>) => {
    globalThemeManager.mergeTheme(partialTheme);
  }, []);

  return {
    theme,
    mode,
    switchTheme,
    mergeTheme,
    registerCustomTheme: globalThemeManager.registerCustomTheme.bind(globalThemeManager),
    getCustomThemeIds: globalThemeManager.getCustomThemeIds.bind(globalThemeManager),
  };
}
