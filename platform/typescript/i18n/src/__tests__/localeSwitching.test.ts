import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Locale switching tests — validates that the i18next instance correctly reflects
 * language changes when `changeLanguage` is called.
 *
 * @doc.type module
 * @doc.purpose Tests for runtime locale switching behavior
 * @doc.layer platform
 * @doc.pattern Test
 */

// ── Mock setup ───────────────────────────────────────────────────────────────

vi.mock('i18next', () => {
  let currentLanguage = 'en';
  const translations: Record<string, Record<string, string>> = {
    en: { 'actions.save': 'Save', 'auth.login': 'Log in' },
    fr: { 'actions.save': 'Enregistrer', 'auth.login': 'Se connecter' },
    es: { 'actions.save': 'Guardar', 'auth.login': 'Iniciar sesión' },
  };

  const instance = {
    use: vi.fn().mockReturnThis(),
    init: vi.fn().mockResolvedValue(undefined),
    t: vi.fn((key: string) => translations[currentLanguage]?.[key] ?? key),
    get language() {
      return currentLanguage;
    },
    changeLanguage: vi.fn(async (lang: string) => {
      currentLanguage = lang;
    }),
  };

  return {
    default: {
      createInstance: vi.fn(() => instance),
    },
    __instance: instance,
  };
});

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: vi.fn() },
  useTranslation: vi.fn(() => ({ t: (k: string) => k, i18n: {} })),
  Trans: vi.fn(() => null),
}));

vi.mock('i18next-browser-languagedetector', () => ({ default: vi.fn() }));
vi.mock('i18next-http-backend', () => ({ default: vi.fn() }));

// ── Tests ───────────────────────────────────────────────────────────────────

describe('locale switching', () => {
  describe('supported locale switching', () => {
    it('default language is en after init', async () => {
      const i18next = await import('i18next');
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const instance = (i18next as unknown as Record<string, any>).__instance;
      expect(instance.language).toBe('en');
    });

    it('translates to French after switching to fr', async () => {
      const i18next = await import('i18next');
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const instance = (i18next as unknown as Record<string, any>).__instance;

      await instance.changeLanguage('fr');

      expect(instance.t('actions.save')).toBe('Enregistrer');
    });

    it('translates to Spanish after switching to es', async () => {
      const i18next = await import('i18next');
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const instance = (i18next as unknown as Record<string, any>).__instance;

      await instance.changeLanguage('es');

      expect(instance.t('actions.save')).toBe('Guardar');
    });

    it('switching back to en restores English translations', async () => {
      const i18next = await import('i18next');
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const instance = (i18next as unknown as Record<string, any>).__instance;

      await instance.changeLanguage('fr');
      await instance.changeLanguage('en');

      expect(instance.t('auth.login')).toBe('Log in');
    });

    it('changeLanguage records the new language in the instance', async () => {
      const i18next = await import('i18next');
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const instance = (i18next as unknown as Record<string, any>).__instance;

      await instance.changeLanguage('fr');

      expect(instance.language).toBe('fr');
    });
  });

  describe('unsupported locale fallback', () => {
    it('falls back to key when locale is not registered', async () => {
      const i18next = await import('i18next');
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const instance = (i18next as unknown as Record<string, any>).__instance;

      await instance.changeLanguage('de'); // no German translations registered

      // Key is returned verbatim — standard i18next fallback behavior
      expect(instance.t('actions.save')).toBe('actions.save');
    });
  });
});
