import enMessages from '../locales/en/common.json';
import neMessages from '../locales/ne/common.json';

export type PhrLocale = 'en' | 'ne' | 'en-XA';
export type PhrMessageKey = keyof typeof enMessages;

type MessageValues = Readonly<Record<string, string | number>>;

const supportedLocales = ['en', 'ne', 'en-XA'] as const satisfies readonly PhrLocale[];
const localizedMessages: Readonly<Record<Exclude<PhrLocale, 'en-XA'>, Readonly<Record<PhrMessageKey, string>>>> = {
  en: enMessages,
  ne: neMessages,
};

export function isPhrLocale(value: string | null | undefined): value is PhrLocale {
  return supportedLocales.includes(value as PhrLocale);
}

export function resolvePhrLocale(input?: string | null): PhrLocale {
  if (isPhrLocale(input)) {
    return input;
  }
  if (typeof window !== 'undefined') {
    const stored = window.localStorage.getItem('phr.locale');
    if (isPhrLocale(stored)) {
      return stored;
    }
    const browserLocale = window.navigator.language.split('-')[0];
    if (isPhrLocale(browserLocale)) {
      return browserLocale;
    }
  }
  return 'en';
}

export function setPhrLocale(locale: PhrLocale): void {
  if (isPhrLocale(locale)) {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem('phr.locale', locale);
    }
  }
}

export function t(key: PhrMessageKey, values: MessageValues = {}, locale: PhrLocale = resolvePhrLocale()): string {
  const baseMessage = locale === 'en-XA'
    ? pseudoLocalize(localizedMessages.en[key])
    : localizedMessages[locale][key];

  return Object.entries(values).reduce(
    (message, [name, value]) => message.split(`{${name}}`).join(String(value)),
    baseMessage,
  );
}

export function formatPhrDate(value: string | Date, locale: PhrLocale = resolvePhrLocale()): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  const intlLocale = locale === 'en-XA' ? 'en' : locale;
  return new Intl.DateTimeFormat(intlLocale, {
    dateStyle: 'medium',
    timeZone: 'Asia/Kathmandu',
  }).format(date);
}

export function formatPhrDateTime(value: string | Date, locale: PhrLocale = resolvePhrLocale()): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  const intlLocale = locale === 'en-XA' ? 'en' : locale;
  return new Intl.DateTimeFormat(intlLocale, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Kathmandu',
  }).format(date);
}

export function formatPhrPercent(value: number, locale: PhrLocale = resolvePhrLocale()): string {
  const intlLocale = locale === 'en-XA' ? 'en' : locale;
  return new Intl.NumberFormat(intlLocale, {
    style: 'percent',
    maximumFractionDigits: 0,
  }).format(value);
}

export function pseudoLocalize(message: string): string {
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
