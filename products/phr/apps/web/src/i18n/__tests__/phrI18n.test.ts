import { describe, expect, it } from 'vitest';
import enMessages from '../../locales/en/common.json';
import neMessages from '../../locales/ne/common.json';
import { formatPhrDate, pseudoLocalize, t, type PhrMessageKey } from '../phrI18n';

describe('PHR i18n', () => {
  it('keeps Nepali locale keys aligned with the English source locale', () => {
    const englishKeys = Object.keys(enMessages).sort();
    const nepaliKeys = Object.keys(neMessages).sort();

    expect(nepaliKeys).toEqual(englishKeys);
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
});
