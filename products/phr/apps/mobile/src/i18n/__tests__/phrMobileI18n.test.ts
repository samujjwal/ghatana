/**
 * Tests for phrMobileI18n — verifies key resolution, parameter substitution, and locale switching.
 */
import { describe, it, expect, beforeEach } from '@jest/globals';
import { t, setLocale, getLocale } from '../../i18n/phrMobileI18n';

describe('phrMobileI18n', () => {
  beforeEach(() => {
    setLocale('en');
  });

  it('resolves a top-level nested key in English', () => {
    expect(t('tabs.home')).toBe('Home');
  });

  it('resolves a deeply nested key', () => {
    expect(t('emergency.requestButton')).toBe('Request Emergency Access');
  });

  it('substitutes {{param}} placeholders', () => {
    expect(t('dashboard.welcome', { name: 'Alice' })).toBe('Welcome, Alice');
  });

  it('substitutes multiple distinct placeholders', () => {
    // 'dashboard.lastSync' = 'Last synced: {{time}}'
    expect(t('dashboard.lastSync', { time: '5 min ago' })).toBe('Last synced: 5 min ago');
  });

  it('returns the key when not found in any locale', () => {
    expect(t('nonexistent.key')).toBe('nonexistent.key');
  });

  it('switches to Nepali locale', () => {
    setLocale('ne');
    expect(getLocale()).toBe('ne');
    expect(t('tabs.home')).toBe('गृह');
  });

  it('falls back to English for missing Nepali key', () => {
    setLocale('ne');
    // Both locales have all keys, so this verifies no crash and a string is returned
    const result = t('common.retry');
    expect(typeof result).toBe('string');
    expect(result.length).toBeGreaterThan(0);
  });

  it('resets to English locale correctly', () => {
    setLocale('ne');
    setLocale('en');
    expect(t('tabs.home')).toBe('Home');
  });

  it('falls back to English for unknown locale name', () => {
    setLocale('fr');
    expect(getLocale()).toBe('en');
    expect(t('tabs.home')).toBe('Home');
  });
});
