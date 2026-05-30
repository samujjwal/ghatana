import { describe, expect, it } from 'vitest';
import enMessages from '../../locales/en/common.json';
import neMessages from '../../locales/ne/common.json';
import { formatPhrDate, pseudoLocalize, resolvePhrLocale, t, type PhrMessageKey } from '../phrI18n';
import { phrRouteContracts } from '../../phrRouteContracts';

describe('PHR i18n', () => {
  it('keeps Nepali locale keys aligned with the English source locale', () => {
    const englishKeys = Object.keys(enMessages).sort();
    const nepaliKeys = Object.keys(neMessages).sort();

    expect(nepaliKeys).toEqual(englishKeys);
  });

  it('resolves supported locales correctly', () => {
    expect(resolvePhrLocale('en')).toBe('en');
    expect(resolvePhrLocale('ne')).toBe('ne');
    expect(resolvePhrLocale('en-XA')).toBe('en-XA');
    expect(resolvePhrLocale('invalid')).toBe('en');
    expect(resolvePhrLocale(null)).toBe('en');
    expect(resolvePhrLocale(undefined)).toBe('en');
  });

  it('interpolates localized patient metadata', () => {
    const message = t(
      'dashboard.patientMeta',
      {
        location: 'Kathmandu',
        bloodType: 'O+',
        emergencyContact: 'Sushil Shrestha',
      },
      'en',
    );

    expect(message).toContain('Kathmandu');
    expect(message).toContain('O+');
    expect(message).toContain('Sushil Shrestha');
  });

  it('formats dates through Intl for Nepal patient-facing views', () => {
    expect(formatPhrDate('2026-05-01T00:00:00Z', 'en')).toContain('2026');
  });

  it('supports pseudo-locale assertions for layout expansion checks', () => {
    const key: PhrMessageKey = 'login.title';

    expect(t(key, {}, 'en-XA')).toBe(pseudoLocalize(enMessages[key]));
    expect(t(key, {}, 'en-XA')).toMatch(/^\[.+\]$/);
  });

  it('all stable route labels have i18n keys and work with pseudo-locale', () => {
    const stableRoutes = phrRouteContracts.filter((r) => r.stability === 'stable');

    for (const route of stableRoutes) {
      if (route.i18nKey) {
        const key = route.i18nKey as PhrMessageKey;
        // Verify the key exists in English locale
        expect(enMessages).toHaveProperty(key);
        // Verify pseudo-localization works (for layout expansion testing)
        const pseudoLocalized = t(key, {}, 'en-XA');
        expect(pseudoLocalized).toMatch(/^\[.+\]$/);
      }
    }
  });
});
