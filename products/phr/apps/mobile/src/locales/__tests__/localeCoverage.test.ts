/**
 * Locale coverage test for PHR mobile application.
 *
 * Verifies that all locale files (en.ts, ne.ts) have the same key structure
 * as the English locale, ensuring no translation keys are missing.
 */

import { en, type EnLocale } from '../en';
import { ne } from '../ne';

/**
 * Recursively collects all keys from a nested object as dot-notation paths.
 */
function collectKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = [];
  for (const key in obj) {
    if (Object.prototype.hasOwnProperty.call(obj, key)) {
      const value = obj[key];
      const fullKey = prefix ? `${prefix}.${key}` : key;
      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        keys.push(...collectKeys(value as Record<string, unknown>, fullKey));
      } else {
        keys.push(fullKey);
      }
    }
  }
  return keys;
}

/**
 * Recursively collects all keys including nested object paths.
 */
function collectAllPaths(obj: Record<string, unknown>, prefix = ''): string[] {
  const paths: string[] = [];
  for (const key in obj) {
    if (Object.prototype.hasOwnProperty.call(obj, key)) {
      const value = obj[key];
      const fullPath = prefix ? `${prefix}.${key}` : key;
      paths.push(fullPath);
      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        paths.push(...collectAllPaths(value as Record<string, unknown>, fullPath));
      }
    }
  }
  return paths;
}

describe('locale coverage', () => {
  const englishKeys = collectAllPaths(en as unknown as Record<string, unknown>);
  const nepaliKeys = collectAllPaths(ne as unknown as Record<string, unknown>);

  it('English locale has all required top-level sections', () => {
    const requiredSections: (keyof EnLocale)[] = [
      'tabs',
      'dashboard',
      'records',
      'consents',
      'alerts',
      'emergency',
      'settings',
      'error',
      'login',
      'offline',
      'common',
      'app',
      'api',
    ];

    for (const section of requiredSections) {
      expect(en[section]).toBeDefined();
      expect(typeof en[section]).toBe('object');
    }
  });

  it('Nepali locale has all required top-level sections', () => {
    const requiredSections: (keyof EnLocale)[] = [
      'tabs',
      'dashboard',
      'records',
      'consents',
      'alerts',
      'emergency',
      'settings',
      'error',
      'login',
      'offline',
      'common',
      'app',
      'api',
    ];

    for (const section of requiredSections) {
      expect(ne[section]).toBeDefined();
      expect(typeof ne[section]).toBe('object');
    }
  });

  it('Nepali locale has the same key structure as English', () => {
    const missingKeys = englishKeys.filter((key) => !nepaliKeys.includes(key));
    const extraKeys = nepaliKeys.filter((key) => !englishKeys.includes(key));

    if (missingKeys.length > 0) {
      console.error('Missing keys in Nepali locale:', missingKeys);
    }
    if (extraKeys.length > 0) {
      console.error('Extra keys in Nepali locale:', extraKeys);
    }

    expect(missingKeys).toEqual([]);
    expect(extraKeys).toEqual([]);
  });

  it('All translation values are non-empty strings', () => {
    const checkValues = (obj: Record<string, unknown>, path = ''): void => {
      for (const key in obj) {
        if (Object.prototype.hasOwnProperty.call(obj, key)) {
          const value = obj[key];
          const fullPath = path ? `${path}.${key}` : key;

          if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
            checkValues(value as Record<string, unknown>, fullPath);
          } else if (typeof value === 'string') {
            expect(value.trim().length).toBeGreaterThan(0);
          } else {
            throw new Error(`Value at ${fullPath} is not a string: ${typeof value}`);
          }
        }
      }
    };

    checkValues(en as unknown as Record<string, unknown>);
    checkValues(ne as unknown as Record<string, unknown>);
  });

  it('English locale has no placeholder values', () => {
    const checkPlaceholders = (obj: Record<string, unknown>, path = ''): string[] => {
      const placeholders: string[] = [];
      for (const key in obj) {
        if (Object.prototype.hasOwnProperty.call(obj, key)) {
          const value = obj[key];
          const fullPath = path ? `${path}.${key}` : key;

          if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
            placeholders.push(...checkPlaceholders(value as Record<string, unknown>, fullPath));
          } else if (typeof value === 'string') {
            const lowerValue = value.toLowerCase();
            if (lowerValue.includes('placeholder') || lowerValue.includes('todo') || lowerValue.includes('tbd')) {
              placeholders.push(fullPath);
            }
          }
        }
      }
      return placeholders;
    };

    const placeholders = checkPlaceholders(en as unknown as Record<string, unknown>);
    if (placeholders.length > 0) {
      console.error('Placeholder values found:', placeholders);
    }
    expect(placeholders).toEqual([]);
  });
});
