import { describe, expect, it } from 'vitest';
import i18n from '../../i18n/config';

describe('i18n config', () => {
  it('registers pseudo-locale support for regression validation', () => {
    const supported = i18n.options.supportedLngs ?? [];

    expect(supported).toContain('en');
    expect(supported).toContain('en-XA');

    const enSearchPlaceholder = i18n.getResource(
      'en',
      'translation',
      'layout.searchPlaceholder',
    ) as string;

    const pseudoSearchPlaceholder = i18n.getResource(
      'en-XA',
      'translation',
      'layout.searchPlaceholder',
    ) as string;

    expect(typeof enSearchPlaceholder).toBe('string');
    expect(typeof pseudoSearchPlaceholder).toBe('string');
    expect(pseudoSearchPlaceholder).toContain('[!!');
    expect(pseudoSearchPlaceholder).not.toBe(enSearchPlaceholder);
  });
});
