import React, { createContext, useContext, useMemo } from 'react';

import type { I18nPrimitive, MessageKey, SupportedLocale } from './messages';
import { translate } from './messages';

type TranslationParams = Record<string, I18nPrimitive>;

interface I18nContextValue {
  locale: SupportedLocale;
  t: (key: MessageKey, params?: TranslationParams) => string;
}

const defaultContextValue: I18nContextValue = {
  locale: 'en',
  t: (key, params) => translate(key, params, 'en'),
};

const I18nContext = createContext<I18nContextValue>(defaultContextValue);

interface I18nProviderProps {
  locale?: SupportedLocale;
  children: React.ReactNode;
}

export function I18nProvider({ locale = 'en', children }: I18nProviderProps): React.ReactElement {
  const contextValue = useMemo<I18nContextValue>(
    () => ({
      locale,
      t: (key, params) => translate(key, params, locale),
    }),
    [locale],
  );

  return <I18nContext.Provider value={contextValue}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  return useContext(I18nContext);
}
