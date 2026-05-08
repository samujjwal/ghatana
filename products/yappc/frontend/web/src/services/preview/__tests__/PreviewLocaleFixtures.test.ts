import { describe, expect, it } from 'vitest';

import {
  getPreviewLocaleFixture,
  getPreviewLocaleFixtures,
  getPreviewTextDirection,
} from '../PreviewLocaleFixtures';

describe('PreviewLocaleFixtures', () => {
  it('provides reusable LTR and RTL localized preview fixtures', () => {
    const fixtures = getPreviewLocaleFixtures();

    expect(fixtures.map((fixture) => fixture.locale)).toEqual(['en-US', 'en-GB', 'ar-SA', 'he-IL']);
    expect(getPreviewTextDirection('ar-SA')).toBe('rtl');
    expect(getPreviewTextDirection('he-IL')).toBe('rtl');
    expect(getPreviewTextDirection('en-US')).toBe('ltr');
  });

  it('falls back to the default fixture for unknown locales', () => {
    expect(getPreviewLocaleFixture('fr-FR')).toMatchObject({
      locale: 'en-US',
      headline: 'Launch your product workspace',
    });
  });
});
