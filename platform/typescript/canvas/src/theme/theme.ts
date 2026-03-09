/**
 * Canvas Theme System
 *
 * Comprehensive theme system with dark mode support.
 * Provides CSS variables, theme atoms, and theme utilities.
 *
 * @doc.type theme
 * @doc.purpose Theme management and dark mode support
 * @doc.layer presentation
 */

import { atom } from "jotai";
import { atomWithStorage } from "jotai/utils";

// ============================================================================
// THEME TYPES
// ============================================================================

export type ThemeMode = "light" | "dark" | "system";

export interface ThemeColors {
  // Background colors
  background: {
    primary: string;
    secondary: string;
    tertiary: string;
    elevated: string;
  };
  // Text colors
  text: {
    primary: string;
    secondary: string;
    tertiary: string;
    disabled: string;
    inverse: string;
  };
  // Border colors
  border: {
    primary: string;
    secondary: string;
    focus: string;
    error: string;
  };
  // Interactive colors
  interactive: {
    primary: string;
    primaryHover: string;
    primaryActive: string;
    secondary: string;
    secondaryHover: string;
    danger: string;
    dangerHover: string;
  };
  // Status colors
  status: {
    success: string;
    warning: string;
    error: string;
    info: string;
  };
  // Canvas-specific colors
  canvas: {
    grid: string;
    selection: string;
    selectionBorder: string;
    guideline: string;
    frame: string;
  };
}

export interface Theme {
  mode: "light" | "dark";
  colors: ThemeColors;
}

// ============================================================================
// THEME DEFINITIONS
// ============================================================================

export const lightTheme: Theme = {
  mode: "light",
  colors: {
    background: {
      primary: "#ffffff",
      secondary: "#f9fafb",
      tertiary: "#f3f4f6",
      elevated: "#ffffff",
    },
    text: {
      primary: "#111827",
      secondary: "#6b7280",
      tertiary: "#9ca3af",
      disabled: "#d1d5db",
      inverse: "#ffffff",
    },
    border: {
      primary: "#e5e7eb",
      secondary: "#d1d5db",
      focus: "#3b82f6",
      error: "#ef4444",
    },
    interactive: {
      primary: "#3b82f6",
      primaryHover: "#2563eb",
      primaryActive: "#1d4ed8",
      secondary: "#6b7280",
      secondaryHover: "#4b5563",
      danger: "#ef4444",
      dangerHover: "#dc2626",
    },
    status: {
      success: "#10b981",
      warning: "#f59e0b",
      error: "#ef4444",
      info: "#3b82f6",
    },
    canvas: {
      grid: "#e5e7eb",
      selection: "rgba(59, 130, 246, 0.1)",
      selectionBorder: "#3b82f6",
      guideline: "#f59e0b",
      frame: "#9ca3af",
    },
  },
};

export const darkTheme: Theme = {
  mode: "dark",
  colors: {
    background: {
      primary: "#111827",
      secondary: "#1f2937",
      tertiary: "#374151",
      elevated: "#1f2937",
    },
    text: {
      primary: "#f9fafb",
      secondary: "#d1d5db",
      tertiary: "#9ca3af",
      disabled: "#6b7280",
      inverse: "#111827",
    },
    border: {
      primary: "#374151",
      secondary: "#4b5563",
      focus: "#60a5fa",
      error: "#f87171",
    },
    interactive: {
      primary: "#3b82f6",
      primaryHover: "#60a5fa",
      primaryActive: "#93c5fd",
      secondary: "#6b7280",
      secondaryHover: "#9ca3af",
      danger: "#ef4444",
      dangerHover: "#f87171",
    },
    status: {
      success: "#34d399",
      warning: "#fbbf24",
      error: "#f87171",
      info: "#60a5fa",
    },
    canvas: {
      grid: "#374151",
      selection: "rgba(96, 165, 250, 0.15)",
      selectionBorder: "#60a5fa",
      guideline: "#fbbf24",
      frame: "#6b7280",
    },
  },
};

// ============================================================================
// THEME ATOMS
// ============================================================================

/**
 * Theme mode atom with localStorage persistence
 * Defaults to 'system' which follows OS preference
 */
export const themeModeAtom = atomWithStorage<ThemeMode>(
  "canvas-theme-mode",
  "system",
);

/**
 * Resolved theme atom (computed from mode and system preference)
 */
export const resolvedThemeAtom = atom<"light" | "dark">((get) => {
  const mode = get(themeModeAtom);

  if (mode === "system") {
    // Check system preference
    if (typeof window !== "undefined" && window.matchMedia) {
      return window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light";
    }
    return "light";
  }

  return mode;
});

/**
 * Current theme atom (computed from resolved theme)
 */
export const currentThemeAtom = atom<Theme>((get) => {
  const resolved = get(resolvedThemeAtom);
  return resolved === "dark" ? darkTheme : lightTheme;
});

// ============================================================================
// THEME UTILITIES
// ============================================================================

/**
 * Apply theme CSS variables to document root
 */
export function applyThemeVariables(theme: Theme): void {
  if (typeof document === "undefined") return;

  const root = document.documentElement;
  const { colors } = theme;

  // Background colors
  root.style.setProperty("--canvas-bg-primary", colors.background.primary);
  root.style.setProperty("--canvas-bg-secondary", colors.background.secondary);
  root.style.setProperty("--canvas-bg-tertiary", colors.background.tertiary);
  root.style.setProperty("--canvas-bg-elevated", colors.background.elevated);

  // Text colors
  root.style.setProperty("--canvas-text-primary", colors.text.primary);
  root.style.setProperty("--canvas-text-secondary", colors.text.secondary);
  root.style.setProperty("--canvas-text-tertiary", colors.text.tertiary);
  root.style.setProperty("--canvas-text-disabled", colors.text.disabled);
  root.style.setProperty("--canvas-text-inverse", colors.text.inverse);

  // Border colors
  root.style.setProperty("--canvas-border-primary", colors.border.primary);
  root.style.setProperty("--canvas-border-secondary", colors.border.secondary);
  root.style.setProperty("--canvas-border-focus", colors.border.focus);
  root.style.setProperty("--canvas-border-error", colors.border.error);

  // Interactive colors
  root.style.setProperty(
    "--canvas-interactive-primary",
    colors.interactive.primary,
  );
  root.style.setProperty(
    "--canvas-interactive-primary-hover",
    colors.interactive.primaryHover,
  );
  root.style.setProperty(
    "--canvas-interactive-primary-active",
    colors.interactive.primaryActive,
  );
  root.style.setProperty(
    "--canvas-interactive-secondary",
    colors.interactive.secondary,
  );
  root.style.setProperty(
    "--canvas-interactive-secondary-hover",
    colors.interactive.secondaryHover,
  );
  root.style.setProperty(
    "--canvas-interactive-danger",
    colors.interactive.danger,
  );
  root.style.setProperty(
    "--canvas-interactive-danger-hover",
    colors.interactive.dangerHover,
  );

  // Status colors
  root.style.setProperty("--canvas-status-success", colors.status.success);
  root.style.setProperty("--canvas-status-warning", colors.status.warning);
  root.style.setProperty("--canvas-status-error", colors.status.error);
  root.style.setProperty("--canvas-status-info", colors.status.info);

  // Canvas-specific colors
  root.style.setProperty("--canvas-grid", colors.canvas.grid);
  root.style.setProperty("--canvas-selection", colors.canvas.selection);
  root.style.setProperty(
    "--canvas-selection-border",
    colors.canvas.selectionBorder,
  );
  root.style.setProperty("--canvas-guideline", colors.canvas.guideline);
  root.style.setProperty("--canvas-frame", colors.canvas.frame);

  // Set data attribute for theme mode
  root.setAttribute("data-theme", theme.mode);
}

/**
 * Get color value from theme
 */
export function getThemeColor(theme: Theme, path: string): string {
  const parts = path.split(".");
  let value: unknown = theme.colors;

  for (const part of parts) {
    if (value && typeof value === "object" && part in value) {
      value = (value as Record<string, unknown>)[part];
    } else {
      return "";
    }
  }

  return typeof value === "string" ? value : "";
}

/**
 * Initialize theme system
 * Sets up system preference listener and applies initial theme
 */
export function initializeTheme(mode: ThemeMode = "system"): void {
  if (typeof window === "undefined") return;

  // Apply initial theme
  const theme = mode === "dark" ? darkTheme : lightTheme;
  applyThemeVariables(theme);

  // Listen for system preference changes
  if (mode === "system" && window.matchMedia) {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");

    const handleChange = (e: MediaQueryListEvent) => {
      const newTheme = e.matches ? darkTheme : lightTheme;
      applyThemeVariables(newTheme);
    };

    // Modern browsers
    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener("change", handleChange);
    } else {
      // Fallback for older browsers
      mediaQuery.addListener(handleChange);
    }
  }
}

/**
 * Toggle between light and dark mode
 */
export function toggleTheme(currentMode: ThemeMode): ThemeMode {
  if (currentMode === "system") {
    return "light";
  }
  return currentMode === "light" ? "dark" : "light";
}

/**
 * Get CSS variable value
 */
export function getCSSVariable(name: string): string {
  if (typeof document === "undefined") return "";
  return getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
}

/**
 * Set CSS variable value
 */
export function setCSSVariable(name: string, value: string): void {
  if (typeof document === "undefined") return;
  document.documentElement.style.setProperty(name, value);
}
