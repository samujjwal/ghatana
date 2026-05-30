import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, vi } from 'vitest';

vi.mock('@ghatana/theme', async () => {
  const actual = await vi.importActual<typeof import('@ghatana/theme')>('@ghatana/theme');

  return {
    ...actual,
    ThemeProvider: ({ children }: { children: ReactNode }) => children,
    useTheme: () => ({
      theme: 'light' as const,
      resolvedTheme: 'light' as const,
      systemTheme: 'light' as const,
      setTheme: vi.fn(),
      toggleTheme: vi.fn(),
      themeDefinition: actual.createTheme('light'),
      setThemeLayers: vi.fn(),
    }),
  };
});

afterEach(() => {
	cleanup();
});

Object.defineProperty(globalThis, 'localStorage', {
	value: {
		getItem: () => null,
		setItem: () => undefined,
		removeItem: () => undefined,
		clear: () => undefined,
	},
	writable: true,
});
