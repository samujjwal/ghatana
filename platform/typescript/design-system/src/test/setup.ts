/**
 * Vitest/jsdom global setup for @ghatana/design-system tests.
 *
 * Extends the `expect` object with @testing-library/jest-dom matchers
 * (e.g. `toBeInTheDocument`, `toHaveAccessibleName`, etc.).
 */
import "@testing-library/jest-dom";
import type { ReactNode } from "react";
import { vi } from "vitest";

/**
 * Install a functional localStorage mock.
 *
 * The jsdom `--localstorage-file` flag may be provided without a valid path,
 * which renders `window.localStorage` non-functional.  We override it once
 * here in the global setup so every test file starts with a working
 * localStorage.  Individual test files may call `localStorage.clear()` in
 * `beforeEach`/`afterEach` as normal.
 */
const _localStorageStore = new Map<string, string>();
const localStorageMock: Storage = {
  getItem: vi.fn((key: string) => _localStorageStore.get(key) ?? null),
  setItem: vi.fn((key: string, value: string) => { _localStorageStore.set(key, value); }),
  removeItem: vi.fn((key: string) => { _localStorageStore.delete(key); }),
  clear: vi.fn(() => { _localStorageStore.clear(); }),
  key: vi.fn((index: number) => Array.from(_localStorageStore.keys())[index] ?? null),
  get length() { return _localStorageStore.size; },
};
Object.defineProperty(globalThis, "localStorage", { configurable: true, value: localStorageMock });

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

