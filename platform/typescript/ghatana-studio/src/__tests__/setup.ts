import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';
import { beforeAll } from 'vitest';
import { initI18n } from '@ghatana/i18n';
import { STUDIO_I18N_RESOURCES } from '../i18n/studioTranslations';

function installTestStorage(name: 'localStorage' | 'sessionStorage'): void {
  const backingStore = new Map<string, string>();
  const storage: Storage = {
    get length() {
      return backingStore.size;
    },
    clear() {
      backingStore.clear();
    },
    getItem(key: string) {
      return backingStore.get(String(key)) ?? null;
    },
    key(index: number) {
      return Array.from(backingStore.keys())[index] ?? null;
    },
    removeItem(key: string) {
      backingStore.delete(String(key));
    },
    setItem(key: string, value: string) {
      backingStore.set(String(key), String(value));
    },
  };

  Object.defineProperty(globalThis, name, {
    configurable: true,
    value: storage,
  });

  if (typeof window !== 'undefined') {
    Object.defineProperty(window, name, {
      configurable: true,
      value: storage,
    });
  }
}

installTestStorage('localStorage');
installTestStorage('sessionStorage');

// Polyfill ResizeObserver for jsdom — required by @xyflow/react (canvas/HybridCanvas)
// and @ghatana/canvas FreeformLayer which uses ResizeObserver for freeform layout.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  };
}

beforeAll(async () => {
  await initI18n({
    defaultNS: 'studio',
    ns: ['studio'],
    resources: STUDIO_I18N_RESOURCES as Record<string, Record<string, Record<string, string>>>,
  });
});

afterEach(() => {
  cleanup();
});
