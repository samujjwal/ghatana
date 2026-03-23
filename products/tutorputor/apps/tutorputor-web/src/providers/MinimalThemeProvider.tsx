import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { ThemeProvider as GhatanaThemeProvider } from "@ghatana/theme";

export type Theme = "light" | "dark" | "system";

export interface MinimalThemeProviderProps {
  children: ReactNode;
  storageKey?: string;
  defaultTheme?: Theme;
}

const ThemeContext = createContext<{
  theme: Theme;
  setTheme: (theme: Theme) => void;
} | null>(null);

export function MinimalThemeProvider({
  children,
  storageKey = "theme",
  defaultTheme = "system",
}: MinimalThemeProviderProps) {
  const [theme, setTheme] = useState<Theme>(() => {
    if (typeof window === "undefined") return defaultTheme;
    const stored = localStorage.getItem(storageKey);
    return (stored as Theme) || defaultTheme;
  });

  useEffect(() => {
    localStorage.setItem(storageKey, theme);
  }, [theme, storageKey]);

  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      <GhatanaThemeProvider>{children}</GhatanaThemeProvider>
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme must be used within MinimalThemeProvider");
  }
  return context;
}
