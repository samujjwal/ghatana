/**
 * Canvas Theme Provider
 *
 * React component that provides theme context and manages theme switching.
 * Automatically applies theme CSS variables and handles system preference changes.
 *
 * @doc.type component
 * @doc.purpose Theme management and provider
 * @doc.layer presentation
 */

import React, { useEffect } from "react";
import { useAtom, useAtomValue } from "jotai";
import {
  themeModeAtom,
  currentThemeAtom,
  applyThemeVariables,
  initializeTheme,
} from "./theme";

interface ThemeProviderProps {
  children: React.ReactNode;
}

/**
 * Theme Provider Component
 *
 * Wraps the canvas application and provides theme management.
 * Automatically applies theme CSS variables and listens for system preference changes.
 *
 * @example
 * ```tsx
 * import { ThemeProvider } from '@ghatana/canvas/theme';
 *
 * function App() {
 *   return (
 *     <ThemeProvider>
 *       <CanvasChromeLayout>
 *         {/* Your canvas content *\/}
 *       </CanvasChromeLayout>
 *     </ThemeProvider>
 *   );
 * }
 * ```
 */
export const ThemeProvider: React.FC<ThemeProviderProps> = ({ children }) => {
  const [themeMode] = useAtom(themeModeAtom);
  const currentTheme = useAtomValue(currentThemeAtom);

  // Initialize theme on mount
  useEffect(() => {
    initializeTheme(themeMode);
  }, []);

  // Apply theme whenever it changes
  useEffect(() => {
    applyThemeVariables(currentTheme);
  }, [currentTheme]);

  // Listen for system preference changes when in system mode
  useEffect(() => {
    if (themeMode !== "system" || typeof window === "undefined") return;

    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");

    const handleChange = () => {
      // Theme will be recomputed automatically via currentThemeAtom
      // This just triggers a re-render
    };

    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener("change", handleChange);
      return () => mediaQuery.removeEventListener("change", handleChange);
    } else {
      // Fallback for older browsers
      mediaQuery.addListener(handleChange);
      return () => mediaQuery.removeListener(handleChange);
    }
  }, [themeMode]);

  return <>{children}</>;
};

/**
 * Theme Toggle Button Component
 *
 * Provides a button to toggle between light, dark, and system themes.
 *
 * @example
 * ```tsx
 * import { ThemeToggle } from '@ghatana/canvas/theme';
 *
 * function TopBar() {
 *   return (
 *     <div>
 *       <ThemeToggle />
 *     </div>
 *   );
 * }
 * ```
 */
export const ThemeToggle: React.FC = () => {
  const [themeMode, setThemeMode] = useAtom(themeModeAtom);
  const currentTheme = useAtomValue(currentThemeAtom);

  const handleToggle = () => {
    const modes: Array<"light" | "dark" | "system"> = [
      "light",
      "dark",
      "system",
    ];
    const currentIndex = modes.indexOf(themeMode);
    const nextIndex = (currentIndex + 1) % modes.length;
    setThemeMode(modes[nextIndex]);
  };

  const getIcon = () => {
    if (themeMode === "system") {
      return "🖥️";
    }
    return currentTheme.mode === "dark" ? "🌙" : "☀️";
  };

  const getLabel = () => {
    if (themeMode === "system") {
      return `System (${currentTheme.mode})`;
    }
    return themeMode === "dark" ? "Dark" : "Light";
  };

  return (
    <button
      onClick={handleToggle}
      style={{
        display: "flex",
        alignItems: "center",
        gap: "8px",
        padding: "8px 12px",
        border: "1px solid var(--canvas-border-primary)",
        borderRadius: "6px",
        backgroundColor: "var(--canvas-bg-secondary)",
        color: "var(--canvas-text-primary)",
        cursor: "pointer",
        fontSize: "14px",
        transition: "all 0.2s",
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.backgroundColor = "var(--canvas-bg-tertiary)";
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.backgroundColor = "var(--canvas-bg-secondary)";
      }}
      aria-label={`Switch theme (current: ${getLabel()})`}
      title={`Current theme: ${getLabel()}. Click to switch.`}
    >
      <span style={{ fontSize: "18px" }}>{getIcon()}</span>
      <span>{getLabel()}</span>
    </button>
  );
};

/**
 * Compact Theme Toggle Icon Button
 *
 * Icon-only version of the theme toggle for compact layouts.
 *
 * @example
 * ```tsx
 * import { ThemeToggleIcon } from '@ghatana/canvas/theme';
 *
 * function CompactToolbar() {
 *   return (
 *     <div>
 *       <ThemeToggleIcon />
 *     </div>
 *   );
 * }
 * ```
 */
export const ThemeToggleIcon: React.FC = () => {
  const [themeMode, setThemeMode] = useAtom(themeModeAtom);
  const currentTheme = useAtomValue(currentThemeAtom);

  const handleToggle = () => {
    const modes: Array<"light" | "dark" | "system"> = [
      "light",
      "dark",
      "system",
    ];
    const currentIndex = modes.indexOf(themeMode);
    const nextIndex = (currentIndex + 1) % modes.length;
    setThemeMode(modes[nextIndex]);
  };

  const getIcon = () => {
    if (themeMode === "system") {
      return "🖥️";
    }
    return currentTheme.mode === "dark" ? "🌙" : "☀️";
  };

  const getLabel = () => {
    if (themeMode === "system") {
      return `System (${currentTheme.mode})`;
    }
    return themeMode === "dark" ? "Dark" : "Light";
  };

  return (
    <button
      onClick={handleToggle}
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "36px",
        height: "36px",
        border: "none",
        borderRadius: "6px",
        backgroundColor: "transparent",
        color: "var(--canvas-text-primary)",
        cursor: "pointer",
        fontSize: "18px",
        transition: "all 0.2s",
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.backgroundColor = "var(--canvas-bg-tertiary)";
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.backgroundColor = "transparent";
      }}
      aria-label={`Switch theme (current: ${getLabel()})`}
      title={`Current theme: ${getLabel()}. Click to switch.`}
    >
      {getIcon()}
    </button>
  );
};
