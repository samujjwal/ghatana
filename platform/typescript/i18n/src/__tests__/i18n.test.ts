import { describe, it, expect, vi } from 'vitest';

// Mock i18next before importing
vi.mock('i18next', () => {
  const instance = {
    use: vi.fn().mockReturnThis(),
    init: vi.fn().mockResolvedValue(undefined),
    t: vi.fn((key: string) => key),
    language: 'en',
  };
  return {
    default: {
      createInstance: vi.fn(() => instance),
    },
  };
});

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: vi.fn() },
  useTranslation: vi.fn(() => ({ t: (k: string) => k, i18n: {} })),
  Trans: vi.fn(() => null),
}));

vi.mock('i18next-browser-languagedetector', () => ({
  default: vi.fn(),
}));

vi.mock('i18next-http-backend', () => ({
  default: vi.fn(),
}));

describe('initI18n', () => {
  it('should initialize with default config', async () => {
    const { initI18n } = await import('../init');
    const instance = await initI18n();
    expect(instance).toBeDefined();
  });

  it('should accept custom namespace', async () => {
    const { initI18n } = await import('../init');
    const instance = await initI18n({
      defaultNS: 'myapp',
      ns: ['myapp', 'common'],
    });
    expect(instance).toBeDefined();
  });

  it('should accept inline resources', async () => {
    const { initI18n } = await import('../init');
    const instance = await initI18n({
      resources: {
        en: {
          common: { greeting: 'Hello' },
        },
      },
    });
    expect(instance).toBeDefined();
  });

  it('should accept custom load path', async () => {
    const { initI18n } = await import('../init');
    const instance = await initI18n({
      loadPath: '/custom/locales/{{lng}}/{{ns}}.json',
    });
    expect(instance).toBeDefined();
  });
});

describe('I18nProvider', () => {
  it('should export I18nProvider component', async () => {
    const mod = await import('../index');
    expect(mod.I18nProvider).toBeDefined();
  });

  it('should re-export useTranslation', async () => {
    const mod = await import('../index');
    expect(mod.useTranslation).toBeDefined();
  });

  it('should re-export Trans', async () => {
    const mod = await import('../index');
    expect(mod.Trans).toBeDefined();
  });
});

describe('default translations', () => {
  it('should have English common translations', async () => {
    const translations = await import('../locales/en/common.json');
    expect(translations).toBeDefined();
    expect(typeof translations).toBe('object');
  });
});
