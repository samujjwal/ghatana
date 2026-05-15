import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';
import { beforeAll } from 'vitest';
import { initI18n } from '@ghatana/i18n';
import { STUDIO_I18N_RESOURCES } from '../i18n/studioTranslations';

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
