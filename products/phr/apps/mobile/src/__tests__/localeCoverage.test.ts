/**
 * Locale files coverage test for PHR mobile app.
 *
 * Verifies that all locale files (en, ne) have matching keys and
 * that no keys are missing or extra in any locale.
 */

import { en } from '../locales/en';
import { ne } from '../locales/ne';
import type { LocaleShape } from '../locales/en';

describe('Locale files coverage', () => {
  function getAllKeys(obj: Record<string, unknown>, prefix = ''): string[] {
    const keys: string[] = [];
    for (const key in obj) {
      const fullKey = prefix ? `${prefix}.${key}` : key;
      if (typeof obj[key] === 'object' && obj[key] !== null) {
        keys.push(...getAllKeys(obj[key] as Record<string, unknown>, fullKey));
      } else {
        keys.push(fullKey);
      }
    }
    return keys;
  }

  it('English locale has all required top-level sections', () => {
    const requiredSections = ['tabs', 'dashboard', 'records', 'consents', 'alerts', 'emergency', 'settings', 'error', 'login', 'offline', 'common', 'app', 'api', 'biometric'];
    const enKeys = Object.keys(en);
    
    for (const section of requiredSections) {
      expect(enKeys).toContain(section);
    }
  });

  it('Nepali locale has all required top-level sections', () => {
    const requiredSections = ['tabs', 'dashboard', 'records', 'consents', 'alerts', 'emergency', 'settings', 'error', 'login', 'offline', 'common', 'app', 'api', 'biometric'];
    const neKeys = Object.keys(ne);
    
    for (const section of requiredSections) {
      expect(neKeys).toContain(section);
    }
  });

  it('English and Nepali locales have matching key structure', () => {
    const enKeys = getAllKeys(en).sort();
    const neKeys = getAllKeys(ne).sort();
    
    expect(enKeys).toEqual(neKeys);
  });

  it('English locale has no empty string values', () => {
    const enKeys = getAllKeys(en);
    const emptyKeys: string[] = [];
    
    function checkEmpty(obj: Record<string, unknown>, prefix = '') {
      for (const key in obj) {
        const fullKey = prefix ? `${prefix}.${key}` : key;
        if (typeof obj[key] === 'string') {
          if ((obj[key] as string).trim() === '') {
            emptyKeys.push(fullKey);
          }
        } else if (typeof obj[key] === 'object' && obj[key] !== null) {
          checkEmpty(obj[key] as Record<string, unknown>, fullKey);
        }
      }
    }
    
    checkEmpty(en);
    expect(emptyKeys).toEqual([]);
  });

  it('Nepali locale has no empty string values', () => {
    const neKeys = getAllKeys(ne);
    const emptyKeys: string[] = [];
    
    function checkEmpty(obj: Record<string, unknown>, prefix = '') {
      for (const key in obj) {
        const fullKey = prefix ? `${prefix}.${key}` : key;
        if (typeof obj[key] === 'string') {
          if ((obj[key] as string).trim() === '') {
            emptyKeys.push(fullKey);
          }
        } else if (typeof obj[key] === 'object' && obj[key] !== null) {
          checkEmpty(obj[key] as Record<string, unknown>, fullKey);
        }
      }
    }
    
    checkEmpty(ne);
    expect(emptyKeys).toEqual([]);
  });
});
