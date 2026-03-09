/**
 * @fileoverview i18next initialization with sensible defaults.
 *
 * Configures i18next with:
 * - Browser language detection
 * - Fallback to English
 * - Namespace support
 * - Optional HTTP backend for loading translations
 *
 * @doc.type module
 * @doc.purpose i18next initialization factory
 * @doc.layer platform
 */

import i18n, { type i18n as I18nInstance } from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import HttpBackend from 'i18next-http-backend';

/**
 * Configuration options for initializing i18n.
 */
export interface I18nConfig {
  /** Default namespace (e.g. product name) */
  defaultNS?: string;
  /** Namespaces to preload */
  ns?: string[];
  /** Fallback language. Defaults to 'en'. */
  fallbackLng?: string;
  /** Base path for translation JSON files. Defaults to '/locales'. */
  loadPath?: string;
  /** Inline resources (skip HTTP backend). Key = lng, value = { ns: translations } */
  resources?: Record<string, Record<string, Record<string, string>>>;
  /** Enable debug logging. Defaults to false. */
  debug?: boolean;
}

/**
 * Initialize the shared i18n instance.
 *
 * Call once at application startup before rendering any React components:
 * ```ts
 * import { initI18n } from '@ghatana/i18n';
 * await initI18n({ defaultNS: 'flashit' });
 * ```
 *
 * @returns The configured i18next instance.
 */
export async function initI18n(config: I18nConfig = {}): Promise<I18nInstance> {
  const {
    defaultNS = 'common',
    ns = ['common'],
    fallbackLng = 'en',
    loadPath = '/locales/{{lng}}/{{ns}}.json',
    resources,
    debug = false,
  } = config;

  const instance = i18n.createInstance();

  // Only add HTTP backend when no inline resources are provided
  if (!resources) {
    instance.use(HttpBackend);
  }

  instance.use(LanguageDetector).use(initReactI18next);

  await instance.init({
    fallbackLng,
    defaultNS,
    ns,
    debug,
    interpolation: {
      escapeValue: false, // React already escapes
    },
    ...(resources
      ? { resources }
      : {
          backend: {
            loadPath,
          },
        }),
  });

  return instance;
}
