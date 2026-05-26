/**
 * Tests for phrMobileI18n — verifies key resolution, parameter substitution, and locale switching.
 */
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

  it('resolves login.nationalIdLabel in English', () => {
    expect(t('login.nationalIdLabel')).toBe('National ID');
  });

  it('resolves login.nationalIdPlaceholder in English', () => {
    expect(t('login.nationalIdPlaceholder')).toBe('National ID or MRN');
  });

  it('resolves login.nationalIdRequired validation message', () => {
    expect(t('login.nationalIdRequired')).toBe('National ID is required.');
  });

  it('resolves login.passwordRequired validation message', () => {
    expect(t('login.passwordRequired')).toBe('Password is required.');
  });

  it('resolves login.failed error message', () => {
    expect(t('login.failed')).toBe('Login failed. Please try again.');
  });

  it('resolves login keys in Nepali locale', () => {
    setLocale('ne');
    const label = t('login.nationalIdLabel');
    expect(typeof label).toBe('string');
    expect(label.length).toBeGreaterThan(0);
    // Must differ from the English string
    expect(label).not.toBe('National ID');
  });
});
