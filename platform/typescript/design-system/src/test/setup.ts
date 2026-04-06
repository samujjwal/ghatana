/**
 * Vitest/jsdom global setup for @ghatana/design-system tests.
 *
 * Extends the `expect` object with @testing-library/jest-dom matchers
 * (e.g. `toBeInTheDocument`, `toHaveAccessibleName`, etc.).
 */
import "@testing-library/jest-dom";
import type { ReactNode } from "react";
import { vi } from "vitest";

vi.mock("@ghatana/theme", async () => {
  const actual =
    await vi.importActual<typeof import("@ghatana/theme")>("@ghatana/theme");

  return {
    ...actual,
    ThemeProvider: ({ children }: { children: ReactNode }) => children,
    useTheme: () => ({
      theme: "light" as const,
      resolvedTheme: "light" as const,
      systemTheme: "light" as const,
      setTheme: vi.fn(),
      toggleTheme: vi.fn(),
      themeDefinition: actual.createTheme("light"),
      setThemeLayers: vi.fn(),
    }),
  };
});
