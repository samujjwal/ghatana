import i18next from 'i18next';

export type MessageKey = string;

export const translate = (key: string, fallback?: string): string => {
  const translated = i18next.t(key);
  if (typeof translated === 'string' && translated.length > 0) {
    return translated;
  }
  return fallback ?? key;
};
