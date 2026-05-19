import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';
import { beforeAll } from 'vitest';
import { initI18n } from '@ghatana/i18n';
import { STUDIO_I18N_RESOURCES } from '../i18n/studioTranslations';

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
