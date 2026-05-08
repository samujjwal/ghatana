/**
 * Preview locale fixtures.
 *
 * @doc.type service
 * @doc.purpose Reusable localized content fixtures for preview host and runtime validation
 * @doc.layer product
 */

export type PreviewTextDirection = 'ltr' | 'rtl';

export interface PreviewLocaleFixture {
  readonly locale: string;
  readonly label: string;
  readonly direction: PreviewTextDirection;
  readonly headline: string;
  readonly body: string;
  readonly primaryCta: string;
  readonly dateExample: string;
  readonly currencyExample: string;
}

const PREVIEW_LOCALE_FIXTURES = [
  {
    locale: 'en-US',
    label: 'English (US)',
    direction: 'ltr',
    headline: 'Launch your product workspace',
    body: 'Review the generated experience with US English copy, date, and currency samples.',
    primaryCta: 'Start review',
    dateExample: 'May 7, 2026',
    currencyExample: '$1,249.00',
  },
  {
    locale: 'en-GB',
    label: 'English (UK)',
    direction: 'ltr',
    headline: 'Launch your product workspace',
    body: 'Review the generated experience with UK English copy, date, and currency samples.',
    primaryCta: 'Start review',
    dateExample: '7 May 2026',
    currencyExample: '£1,249.00',
  },
  {
    locale: 'ar-SA',
    label: 'Arabic (Saudi Arabia)',
    direction: 'rtl',
    headline: 'إطلاق مساحة عمل المنتج',
    body: 'راجع التجربة المولدة باستخدام أمثلة عربية للنسخ والتاريخ والعملات.',
    primaryCta: 'بدء المراجعة',
    dateExample: '٧ مايو ٢٠٢٦',
    currencyExample: '١٬٢٤٩٫٠٠ ر.س',
  },
  {
    locale: 'he-IL',
    label: 'Hebrew (Israel)',
    direction: 'rtl',
    headline: 'השקת סביבת העבודה של המוצר',
    body: 'בדקו את החוויה שנוצרה עם דוגמאות תוכן, תאריך ומטבע בעברית.',
    primaryCta: 'התחלת סקירה',
    dateExample: '7 במאי 2026',
    currencyExample: '₪1,249.00',
  },
] as const satisfies readonly PreviewLocaleFixture[];

export function getPreviewLocaleFixtures(): readonly PreviewLocaleFixture[] {
  return PREVIEW_LOCALE_FIXTURES;
}

export function getPreviewLocaleFixture(locale: string): PreviewLocaleFixture {
  return PREVIEW_LOCALE_FIXTURES.find((fixture) => fixture.locale === locale) ?? PREVIEW_LOCALE_FIXTURES[0];
}

export function getPreviewTextDirection(locale: string): PreviewTextDirection {
  return getPreviewLocaleFixture(locale).direction;
}
