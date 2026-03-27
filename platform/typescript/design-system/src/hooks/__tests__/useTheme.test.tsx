import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { useTheme } from '../useTheme';

type StorageMock = Storage & {
  store: Map<string, string>;
};

type MockThemeMediaQuery = MediaQueryList & {
  triggerChange: (matches: boolean) => void;
};

function installThemeMatchMedia(initialDark: boolean): MockThemeMediaQuery {
  let listener: (() => void) | undefined;

  const mediaQuery = {
    matches: initialDark,
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addEventListener: vi.fn((_event: string, handler: () => void) => {
      listener = handler;
    }),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
    triggerChange(matches: boolean) {
      this.matches = matches;
      listener?.();
    },
  } as unknown as MockThemeMediaQuery;

  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockReturnValue(mediaQuery),
  });

  return mediaQuery;
}

function installLocalStorage(): StorageMock {
  const store = new Map<string, string>();
  const storage = {
    store,
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store.set(key, value);
    }),
    removeItem: vi.fn((key: string) => {
      store.delete(key);
    }),
    clear: vi.fn(() => {
      store.clear();
    }),
    key: vi.fn((index: number) => Array.from(store.keys())[index] ?? null),
    get length() {
      return store.size;
    },
  } as unknown as StorageMock;

  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: storage,
  });

  return storage;
}

describe('useTheme', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('light', 'dark');
  });

  it('initializes from localStorage and resolves the stored theme', () => {
    installLocalStorage();
    localStorage.setItem('ghatana-theme', 'dark');
    installThemeMatchMedia(false);

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('dark');
    expect(result.current.resolvedTheme).toBe('dark');
    expect(result.current.mounted).toBe(true);
  });

  it('uses auto mode to follow the system theme and react to changes', () => {
    installLocalStorage();
    const mediaQuery = installThemeMatchMedia(true);
    const { result } = renderHook(() => useTheme({ defaultTheme: 'auto' }));

    expect(result.current.theme).toBe('auto');
    expect(result.current.resolvedTheme).toBe('dark');

    act(() => {
      mediaQuery.triggerChange(false);
    });

    expect(result.current.resolvedTheme).toBe('light');
  });

  it('updates the theme, resolved theme, localStorage, and document class', () => {
    installLocalStorage();
    installThemeMatchMedia(false);
    const { result } = renderHook(() => useTheme());

    act(() => {
      result.current.setTheme('dark');
    });

    expect(result.current.theme).toBe('dark');
    expect(result.current.resolvedTheme).toBe('dark');
    expect(localStorage.getItem('ghatana-theme')).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });
});