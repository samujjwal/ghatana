import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { useDarkMode } from '../useDarkMode';

type StorageMock = Storage & {
  store: Map<string, string>;
};

function installDarkModeMatchMedia(initialDark: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockReturnValue({
      matches: initialDark,
      media: '(prefers-color-scheme: dark)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  });
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

describe('useDarkMode', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  it('uses the stored dark-mode preference when available', () => {
    installLocalStorage();
    localStorage.setItem('ghatana-dark-mode', 'true');
    installDarkModeMatchMedia(false);

    const { result } = renderHook(() => useDarkMode());

    expect(result.current.isDark).toBe(true);
    expect(result.current.mounted).toBe(true);
  });

  it('supports explicit dark-mode updates and toggling', () => {
    installLocalStorage();
    installDarkModeMatchMedia(false);
    const { result } = renderHook(() => useDarkMode());

    act(() => {
      result.current.setDarkMode(true);
    });

    expect(result.current.isDark).toBe(true);
    expect(localStorage.getItem('ghatana-dark-mode')).toBe('true');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');

    act(() => {
      result.current.toggleDarkMode();
    });

    expect(result.current.isDark).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('resets to the system preference and clears persisted state', () => {
    installLocalStorage();
    installDarkModeMatchMedia(true);
    localStorage.setItem('ghatana-dark-mode', 'false');
    const { result } = renderHook(() => useDarkMode());

    act(() => {
      result.current.resetToSystemPreference();
    });

    expect(localStorage.getItem('ghatana-dark-mode')).toBeNull();
    expect(result.current.isDark).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});