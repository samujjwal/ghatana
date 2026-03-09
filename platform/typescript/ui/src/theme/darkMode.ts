/**
 * Dark Mode Implementation
 * Complete dark color palette and theme management
 */

import { tokens } from '@ghatana/tokens';

export interface DarkModeConfig {
  enableSystemPreference?: boolean;
  storageKey?: string;
  defaultMode?: 'light' | 'dark' | 'auto';
  transitionDuration?: number;
}

export interface ColorPalette {
  light: Record<string, string>;
  dark: Record<string, string>;
}

/**
 * Dark mode color palette mapping
 */
export const darkModeColors: ColorPalette = {
  light: {
    background: '#ffffff',
    surface: '#f5f5f5',
    surfaceVariant: '#eeeeee',
    onBackground: '#000000',
    onSurface: '#1a1a1a',
    border: '#e0e0e0',
    divider: '#f0f0f0',
    shadow: 'rgba(0, 0, 0, 0.1)',
    scrim: 'rgba(0, 0, 0, 0.32)',
  },
  dark: {
    background: '#121212',
    surface: '#1e1e1e',
    surfaceVariant: '#2a2a2a',
    onBackground: '#ffffff',
    onSurface: '#e0e0e0',
    border: '#404040',
    divider: '#2a2a2a',
    shadow: 'rgba(0, 0, 0, 0.5)',
    scrim: 'rgba(0, 0, 0, 0.87)',
  },
};

/**
 * Semantic color mappings for dark mode
 */
export const darkModeSemanticColors = {
  light: {
    success: '#2e7d32',
    warning: '#f57c00',
    error: '#c62828',
    info: '#1565c0',
  },
  dark: {
    success: '#81c784',
    warning: '#ffb74d',
    error: '#ef5350',
    info: '#64b5f6',
  },
};

/**
 * Get computed dark mode styles
 */
export function getDarkModeStyles(isDark: boolean) {
  const colors = isDark ? darkModeColors.dark : darkModeColors.light;
  const semantic = isDark ? darkModeSemanticColors.dark : darkModeSemanticColors.light;

  return {
    colors,
    semantic,
    cssVariables: {
      '--color-background': colors.background,
      '--color-surface': colors.surface,
      '--color-surface-variant': colors.surfaceVariant,
      '--color-on-background': colors.onBackground,
      '--color-on-surface': colors.onSurface,
      '--color-border': colors.border,
      '--color-divider': colors.divider,
      '--color-shadow': colors.shadow,
      '--color-scrim': colors.scrim,
      '--color-success': semantic.success,
      '--color-warning': semantic.warning,
      '--color-error': semantic.error,
      '--color-info': semantic.info,
    },
  };
}

/**
 * Apply dark mode to document
 */
export function applyDarkMode(isDark: boolean, transitionDuration: number = 300) {
  const root = document.documentElement;
  const styles = getDarkModeStyles(isDark);

  // Add transition class
  root.classList.add('theme-transitioning');

  // Apply CSS variables
  Object.entries(styles.cssVariables).forEach(([key, value]) => {
    root.style.setProperty(key, value);
  });

  // Update data attribute
  root.setAttribute('data-theme', isDark ? 'dark' : 'light');

  // Remove transition class after duration
  setTimeout(() => {
    root.classList.remove('theme-transitioning');
  }, transitionDuration);
}

/**
 * Detect system dark mode preference
 */
export function getSystemDarkModePreference(): boolean {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

/**
 * Listen for system dark mode changes
 */
export function listenToSystemDarkModeChanges(callback: (isDark: boolean) => void) {
  if (typeof window === 'undefined') return () => {};

  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  const handleChange = (e: MediaQueryListEvent) => {
    callback(e.matches);
  };

  mediaQuery.addEventListener('change', handleChange);

  return () => {
    mediaQuery.removeEventListener('change', handleChange);
  };
}

/**
 * Dark mode manager class
 */
export class DarkModeManager {
  private isDark: boolean;
  private config: Required<DarkModeConfig>;
  private unsubscribeSystemChange?: () => void;

  constructor(config: DarkModeConfig = {}) {
    this.config = {
      enableSystemPreference: config.enableSystemPreference ?? true,
      storageKey: config.storageKey ?? 'ghatana-dark-mode',
      defaultMode: config.defaultMode ?? 'auto',
      transitionDuration: config.transitionDuration ?? 300,
    };

    this.isDark = this.resolveInitialMode();
    this.initialize();
  }

  private resolveInitialMode(): boolean {
    const stored = localStorage.getItem(this.config.storageKey);
    if (stored !== null) {
      return stored === 'true';
    }

    if (this.config.defaultMode === 'auto') {
      return getSystemDarkModePreference();
    }

    return this.config.defaultMode === 'dark';
  }

  private initialize() {
    // Apply initial mode
    applyDarkMode(this.isDark, 0);

    // Listen to system changes if enabled
    if (this.config.enableSystemPreference) {
      this.unsubscribeSystemChange = listenToSystemDarkModeChanges((isDark) => {
        if (localStorage.getItem(this.config.storageKey) === null) {
          this.setDarkMode(isDark, false);
        }
      });
    }
  }

  /**
   * Set dark mode
   */
  setDarkMode(isDark: boolean, persist: boolean = true) {
    this.isDark = isDark;

    if (persist) {
      localStorage.setItem(this.config.storageKey, String(isDark));
    }

    applyDarkMode(isDark, this.config.transitionDuration);
  }

  /**
   * Toggle dark mode
   */
  toggleDarkMode() {
    this.setDarkMode(!this.isDark);
  }

  /**
   * Get current dark mode state
   */
  isDarkMode(): boolean {
    return this.isDark;
  }

  /**
   * Reset to system preference
   */
  resetToSystemPreference() {
    localStorage.removeItem(this.config.storageKey);
    const isDark = getSystemDarkModePreference();
    this.setDarkMode(isDark, false);
  }

  /**
   * Cleanup
   */
  destroy() {
    this.unsubscribeSystemChange?.();
  }
}

/**
 * Create CSS for dark mode transitions
 */
export function createDarkModeTransitionStyles(): string {
  return `
    :root.theme-transitioning,
    :root.theme-transitioning * {
      transition: background-color ${300}ms ease-in-out,
                  color ${300}ms ease-in-out,
                  border-color ${300}ms ease-in-out,
                  box-shadow ${300}ms ease-in-out !important;
    }
  `;
}

// prefersHighContrast is exported from accessibility.ts to avoid duplicates

/**
 * Get high contrast color palette
 */
export function getHighContrastPalette(isDark: boolean) {
  if (isDark) {
    return {
      background: '#000000',
      surface: '#1a1a1a',
      text: '#ffffff',
      border: '#ffffff',
    };
  }
  return {
    background: '#ffffff',
    surface: '#f5f5f5',
    text: '#000000',
    border: '#000000',
  };
}
