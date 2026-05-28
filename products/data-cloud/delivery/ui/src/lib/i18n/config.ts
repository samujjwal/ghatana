/**
 * i18n Configuration
 *
 * Internationalization configuration for Data Cloud UI.
 * Uses i18next for translation management.
 *
 * @doc.type module
 * @doc.purpose i18n configuration and initialization
 * @doc.layer frontend
 */

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// Import translation resources
import enUS from './locales/en-US.json';
import enGB from './locales/en-GB.json';

export const SUPPORTED_LOCALES = ['en-US', 'en-GB'] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: SupportedLocale = 'en-US';

/**
 * Initialize i18n with configuration
 */
export function initializeI18n(): void {
  i18n
    .use(initReactI18next)
    .init({
      resources: {
        'en-US': { translation: enUS },
        'en-GB': { translation: enGB },
      },
      lng: DEFAULT_LOCALE,
      fallbackLng: DEFAULT_LOCALE,
      interpolation: {
        escapeValue: false, // React already escapes
      },
      react: {
        useSuspense: false, // Disable suspense for simpler loading
      },
    });
}

/**
 * Get the current locale
 */
export function getCurrentLocale(): SupportedLocale {
  return (i18n.language as SupportedLocale) || DEFAULT_LOCALE;
}

/**
 * Change the current locale
 */
export function changeLocale(locale: SupportedLocale): Promise<void> {
  return i18n.changeLanguage(locale);
}

/**
 * Format a date according to locale
 */
export function formatDate(date: Date | string, locale?: SupportedLocale): string {
  const effectiveLocale = locale || getCurrentLocale();
  return new Intl.DateTimeFormat(effectiveLocale).format(new Date(date));
}

/**
 * Format a number according to locale
 */
export function formatNumber(num: number, locale?: SupportedLocale): string {
  const effectiveLocale = locale || getCurrentLocale();
  return new Intl.NumberFormat(effectiveLocale).format(num);
}

/**
 * Format a currency amount according to locale
 */
export function formatCurrency(
  amount: number,
  currency: string = 'USD',
  locale?: SupportedLocale
): string {
  const effectiveLocale = locale || getCurrentLocale();
  return new Intl.NumberFormat(effectiveLocale, {
    style: 'currency',
    currency,
  }).format(amount);
}
