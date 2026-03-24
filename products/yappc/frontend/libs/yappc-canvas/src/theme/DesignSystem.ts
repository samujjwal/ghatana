/**
 * Design System - Theme and Styling
 * 
 * Comprehensive design system with theme management, color palettes,
 * typography scales, and component styling utilities.
 * 
 * Features:
 * - 🎨 Theme management (light/dark/custom)
 * - 🎯 Color palette system
 * - 📝 Typography scales
 * - 📐 Spacing and sizing system
 * - 🎭 Component variants
 * - 🔄 Theme switching and persistence
 * 
 * @doc.type system
 * @doc.purpose Design system and theming
 * @doc.layer product
 * @doc.pattern Design System
 */

/**
 * Color palette
 */
export interface ColorPalette {
  primary: string;
  secondary: string;
  success: string;
  warning: string;
  error: string;
  info: string;
  neutral: {
    50: string;
    100: string;
    200: string;
    300: string;
    400: string;
    500: string;
    600: string;
    700: string;
    800: string;
    900: string;
  };
}

/**
 * Typography scale
 */
export interface TypographyScale {
  h1: {
    fontSize: string;
    fontWeight: number;
    lineHeight: string;
    letterSpacing: string;
  };
  h2: {
    fontSize: string;
    fontWeight: number;
    lineHeight: string;
    letterSpacing: string;
  };
  h3: {
    fontSize: string;
    fontWeight: number;
    lineHeight: string;
    letterSpacing: string;
  };
  body: {
    fontSize: string;
    fontWeight: number;
    lineHeight: string;
    letterSpacing: string;
  };
  caption: {
    fontSize: string;
    fontWeight: number;
    lineHeight: string;
    letterSpacing: string;
  };
}

/**
 * Spacing scale
 */
export interface SpacingScale {
  xs: string;
  sm: string;
  md: string;
  lg: string;
  xl: string;
  '2xl': string;
  '3xl': string;
  '4xl': string;
}

/**
 * Shadow definitions
 */
export interface ShadowDefinitions {
  sm: string;
  md: string;
  lg: string;
  xl: string;
  '2xl': string;
}

/**
 * Border radius scale
 */
export interface BorderRadiusScale {
  none: string;
  sm: string;
  md: string;
  lg: string;
  xl: string;
  full: string;
}

/**
 * Theme definition
 */
export interface Theme {
  name: string;
  isDark: boolean;
  colors: ColorPalette;
  typography: TypographyScale;
  spacing: SpacingScale;
  shadows: ShadowDefinitions;
  borderRadius: BorderRadiusScale;
  transitions: {
    fast: string;
    normal: string;
    slow: string;
  };
}

/**
 * Light theme
 */
export const lightTheme: Theme = {
  name: 'light',
  isDark: false,
  colors: {
    primary: '#007bff',
    secondary: '#6c757d',
    success: '#28a745',
    warning: '#ffc107',
    error: '#dc3545',
    info: '#17a2b8',
    neutral: {
      50: '#f9fafb',
      100: '#f3f4f6',
      200: '#e5e7eb',
      300: '#d1d5db',
      400: '#9ca3af',
      500: '#6b7280',
      600: '#4b5563',
      700: '#374151',
      800: '#1f2937',
      900: '#111827',
    },
  },
  typography: {
    h1: {
      fontSize: '32px',
      fontWeight: 700,
      lineHeight: '1.2',
      letterSpacing: '-0.5px',
    },
    h2: {
      fontSize: '24px',
      fontWeight: 700,
      lineHeight: '1.3',
      letterSpacing: '-0.25px',
    },
    h3: {
      fontSize: '20px',
      fontWeight: 600,
      lineHeight: '1.4',
      letterSpacing: '0px',
    },
    body: {
      fontSize: '16px',
      fontWeight: 400,
      lineHeight: '1.5',
      letterSpacing: '0px',
    },
    caption: {
      fontSize: '12px',
      fontWeight: 400,
      lineHeight: '1.4',
      letterSpacing: '0.3px',
    },
  },
  spacing: {
    xs: '4px',
    sm: '8px',
    md: '16px',
    lg: '24px',
    xl: '32px',
    '2xl': '48px',
    '3xl': '64px',
    '4xl': '96px',
  },
  shadows: {
    sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
    md: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
    lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
    '2xl': '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
  },
  borderRadius: {
    none: '0',
    sm: '2px',
    md: '4px',
    lg: '8px',
    xl: '12px',
    full: '9999px',
  },
  transitions: {
    fast: '150ms ease-in-out',
    normal: '300ms ease-in-out',
    slow: '500ms ease-in-out',
  },
};

/**
 * Dark theme
 */
export const darkTheme: Theme = {
  name: 'dark',
  isDark: true,
  colors: {
    primary: '#0d6efd',
    secondary: '#6c757d',
    success: '#198754',
    warning: '#ffc107',
    error: '#dc3545',
    info: '#0dcaf0',
    neutral: {
      50: '#f9fafb',
      100: '#f3f4f6',
      200: '#e5e7eb',
      300: '#d1d5db',
      400: '#9ca3af',
      500: '#6b7280',
      600: '#4b5563',
      700: '#374151',
      800: '#1f2937',
      900: '#111827',
    },
  },
  typography: lightTheme.typography,
  spacing: lightTheme.spacing,
  shadows: {
    sm: '0 1px 2px 0 rgba(0, 0, 0, 0.3)',
    md: '0 4px 6px -1px rgba(0, 0, 0, 0.4)',
    lg: '0 10px 15px -3px rgba(0, 0, 0, 0.4)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.4)',
    '2xl': '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
  },
  borderRadius: lightTheme.borderRadius,
  transitions: lightTheme.transitions,
};

/**
 * Theme manager
 */
export class ThemeManager {
  private currentTheme: Theme;
  private themes: Map<string, Theme> = new Map();
  private listeners: Set<(theme: Theme) => void> = new Set();
  private storageKey = 'app-theme';

  constructor(defaultTheme: Theme = lightTheme) {
    this.currentTheme = defaultTheme;
    this.themes.set(lightTheme.name, lightTheme);
    this.themes.set(darkTheme.name, darkTheme);
    
    this.loadThemeFromStorage();
  }

  /**
   * Register custom theme
   */
  registerTheme(theme: Theme): void {
    this.themes.set(theme.name, theme);
  }

  /**
   * Get theme by name
   */
  getTheme(name: string): Theme | undefined {
    return this.themes.get(name);
  }

  /**
   * Get current theme
   */
  getCurrentTheme(): Theme {
    return this.currentTheme;
  }

  /**
   * Set current theme
   */
  setTheme(name: string): boolean {
    const theme = this.themes.get(name);
    if (!theme) return false;

    this.currentTheme = theme;
    this.saveThemeToStorage();
    this.notifyListeners();
    this.applyThemeToDOM();

    return true;
  }

  /**
   * Toggle between light and dark
   */
  toggleDarkMode(): void {
    const targetTheme = this.currentTheme.isDark ? 'light' : 'dark';
    this.setTheme(targetTheme);
  }

  /**
   * Get all available themes
   */
  getAllThemes(): Theme[] {
    return Array.from(this.themes.values());
  }

  /**
   * Subscribe to theme changes
   */
  subscribe(listener: (theme: Theme) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Notify listeners
   */
  private notifyListeners(): void {
    this.listeners.forEach((listener) => listener(this.currentTheme));
  }

  /**
   * Apply theme to DOM
   */
  private applyThemeToDOM(): void {
    const root = document.documentElement;
    const theme = this.currentTheme;

    // Set CSS variables
    root.style.setProperty('--color-primary', theme.colors.primary);
    root.style.setProperty('--color-secondary', theme.colors.secondary);
    root.style.setProperty('--color-success', theme.colors.success);
    root.style.setProperty('--color-warning', theme.colors.warning);
    root.style.setProperty('--color-error', theme.colors.error);
    root.style.setProperty('--color-info', theme.colors.info);

    // Set neutral colors
    Object.entries(theme.colors.neutral).forEach(([key, value]) => {
      root.style.setProperty(`--color-neutral-${key}`, value);
    });

    // Set typography
    Object.entries(theme.typography).forEach(([key, value]) => {
      root.style.setProperty(`--font-size-${key}`, value.fontSize);
      root.style.setProperty(`--font-weight-${key}`, String(value.fontWeight));
      root.style.setProperty(`--line-height-${key}`, value.lineHeight);
    });

    // Set spacing
    Object.entries(theme.spacing).forEach(([key, value]) => {
      root.style.setProperty(`--spacing-${key}`, value);
    });

    // Set shadows
    Object.entries(theme.shadows).forEach(([key, value]) => {
      root.style.setProperty(`--shadow-${key}`, value);
    });

    // Set border radius
    Object.entries(theme.borderRadius).forEach(([key, value]) => {
      root.style.setProperty(`--radius-${key}`, value);
    });

    // Set transitions
    Object.entries(theme.transitions).forEach(([key, value]) => {
      root.style.setProperty(`--transition-${key}`, value);
    });

    // Set dark mode class
    if (theme.isDark) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }

  /**
   * Save theme to storage
   */
  private saveThemeToStorage(): void {
    try {
      localStorage.setItem(this.storageKey, this.currentTheme.name);
    } catch (error) {
      console.warn('Failed to save theme to storage:', error);
    }
  }

  /**
   * Load theme from storage
   */
  private loadThemeFromStorage(): void {
    try {
      const savedTheme = localStorage.getItem(this.storageKey);
      if (savedTheme && this.themes.has(savedTheme)) {
        this.currentTheme = this.themes.get(savedTheme)!;
      }
    } catch (error) {
      console.warn('Failed to load theme from storage:', error);
    }
  }

  /**
   * Export theme as CSS
   */
  exportAsCSS(): string {
    const theme = this.currentTheme;
    let css = ':root {\n';

    css += `  --color-primary: ${theme.colors.primary};\n`;
    css += `  --color-secondary: ${theme.colors.secondary};\n`;
    css += `  --color-success: ${theme.colors.success};\n`;
    css += `  --color-warning: ${theme.colors.warning};\n`;
    css += `  --color-error: ${theme.colors.error};\n`;
    css += `  --color-info: ${theme.colors.info};\n`;

    Object.entries(theme.colors.neutral).forEach(([key, value]) => {
      css += `  --color-neutral-${key}: ${value};\n`;
    });

    css += '}\n';

    return css;
  }
}

/**
 * Create global theme manager instance
 */
let globalThemeManager: ThemeManager | null = null;

export function getThemeManager(): ThemeManager {
  if (!globalThemeManager) {
    globalThemeManager = new ThemeManager();
  }
  return globalThemeManager;
}

export function initializeThemeManager(defaultTheme?: Theme): ThemeManager {
  globalThemeManager = new ThemeManager(defaultTheme);
  return globalThemeManager;
}
