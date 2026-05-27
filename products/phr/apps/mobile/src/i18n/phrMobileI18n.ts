/**
 * Minimal type-safe i18n helper for the PHR mobile application.
 *
 * Resolves keys from the active locale dictionary with optional parameter substitution.
 * Falls back to the English locale when a key is absent from the active locale.
 *
 * Locale preference is persisted in AsyncStorage for survival across app restarts.
 *
 * Supports pseudo-locale (en-XA) for layout expansion testing, matching web behavior.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { en } from '../locales/en';
import type { LocaleShape } from '../locales/en';
import { ne } from '../locales/ne';

type LocaleDict = typeof en;
type DotPath<T, Prefix extends string = ''> = {
  [K in keyof T]: T[K] extends Record<string, unknown>
    ? DotPath<T[K], `${Prefix}${K & string}.`>
    : `${Prefix}${K & string}`;
}[keyof T];

export type I18nKey = DotPath<LocaleDict>;
export type PhrLocale = 'en' | 'ne' | 'en-XA';

const locales: Record<string, LocaleShape> = { en, ne };
const LOCALE_STORAGE_KEY = 'phr-mobile-locale';

let activeLocale: string = 'en';
let localeInitialized = false;

/**
 * Initializes the locale from AsyncStorage. Should be called on app startup.
 */
export async function initializeLocale(): Promise<void> {
  if (localeInitialized) {
    return;
  }
  try {
    const storedLocale = await AsyncStorage.getItem(LOCALE_STORAGE_KEY);
    if (storedLocale && storedLocale in locales) {
      activeLocale = storedLocale;
    }
  } catch (error) {
    // If AsyncStorage fails, default to English
    console.warn('Failed to load locale from storage, using default');
  }
  localeInitialized = true;
}

export async function setLocale(locale: string): Promise<void> {
  const normalizedLocale = locale === 'en-XA' ? 'en-XA' : (locale in locales ? locale : 'en');
  activeLocale = normalizedLocale;
  
  try {
    await AsyncStorage.setItem(LOCALE_STORAGE_KEY, normalizedLocale);
  } catch (error) {
    console.warn('Failed to persist locale to storage');
  }
}

export function getLocale(): string {
  return activeLocale;
}

function getNestedValue(obj: Record<string, unknown>, keyPath: string): string | undefined {
  const parts = keyPath.split('.');
  let current: unknown = obj;
  for (const part of parts) {
    if (current == null || typeof current !== 'object') return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return typeof current === 'string' ? current : undefined;
}

/**
 * Pseudo-localizes a message by expanding vowels and wrapping in brackets.
 * Matches web behavior for layout expansion testing.
 */
function pseudoLocalize(message: string): string {
  const expanded = message
    .split('a').join('aa')
    .split('e').join('ee')
    .split('i').join('ii')
    .split('o').join('oo')
    .split('u').join('uu')
    .split('A').join('AA')
    .split('E').join('EE')
    .split('I').join('II')
    .split('O').join('OO')
    .split('U').join('UU');
  return `[${expanded}]`;
}

/**
 * Translate a key to the active locale string, with optional parameter interpolation.
 *
 * @param key    Dot-path key from the locale dictionary (e.g. 'tabs.home')
 * @param params Optional substitution map (e.g. { name: 'Alice' } for '{{name}}' placeholders)
 * @returns      Translated string; falls back to English, then to the key itself
 */
export function t(key: string, params?: Record<string, string>): string {
  let value: string;
  
  if (activeLocale === 'en-XA') {
    // Pseudo-locale: expand English text
    const enValue = getNestedValue(en as unknown as Record<string, unknown>, key) ?? key;
    value = pseudoLocalize(enValue);
  } else {
    // Normal locale: use active locale dict, fall back to English
    const dict = locales[activeLocale] ?? en;
    value =
      getNestedValue(dict as unknown as Record<string, unknown>, key) ??
      getNestedValue(en as unknown as Record<string, unknown>, key) ??
      key;
  }

  if (params != null) {
    for (const [placeholder, replacement] of Object.entries(params)) {
      value = value.replaceAll(`{{${placeholder}}}`, replacement);
    }
  }

  return value;
}
