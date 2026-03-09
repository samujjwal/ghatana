/**
 * @fileoverview React provider component for i18n context.
 *
 * @doc.type component
 * @doc.purpose i18n React provider
 * @doc.layer platform
 */

import React, { type ReactNode, useEffect, useState } from 'react';
import { I18nextProvider } from 'react-i18next';
import { initI18n, type I18nConfig } from './init';
import type { i18n as I18nInstance } from 'i18next';

interface I18nProviderProps {
  /** i18n configuration */
  config?: I18nConfig;
  /** Pre-initialized i18n instance (skips initialization) */
  instance?: I18nInstance;
  children: ReactNode;
}

/**
 * Wraps the React tree with an i18next provider.
 *
 * Either pass a pre-initialized `instance` or a `config` object.
 * When using `config`, the provider initializes i18n on mount and shows
 * nothing until ready (avoids flicker).
 *
 * ```tsx
 * <I18nProvider config={{ defaultNS: 'flashit' }}>
 *   <App />
 * </I18nProvider>
 * ```
 */
export function I18nProvider({ config, instance: externalInstance, children }: I18nProviderProps): React.JSX.Element | null {
  const [i18nInstance, setI18nInstance] = useState<I18nInstance | null>(externalInstance ?? null);

  useEffect(() => {
    if (externalInstance) return;
    let cancelled = false;
    initI18n(config).then((inst) => {
      if (!cancelled) setI18nInstance(inst);
    });
    return () => {
      cancelled = true;
    };
  }, [config, externalInstance]);

  if (!i18nInstance) return null;

  return <I18nextProvider i18n={i18nInstance}>{children}</I18nextProvider>;
}
