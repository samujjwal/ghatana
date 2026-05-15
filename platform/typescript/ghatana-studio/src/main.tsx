import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { I18nProvider } from '@ghatana/i18n';
import App from './App';
import { createKernelLifecycleClient } from './api/kernelLifecycleClient';
import { StudioLifecycleDataProvider } from './data/StudioLifecycleDataContext';
import { STUDIO_I18N_RESOURCES } from './i18n/studioTranslations';
import './index.css';

const studioEnv = (import.meta as unknown as { env?: Record<string, string | undefined> }).env;
const kernelApiBaseUrl = studioEnv?.VITE_GHATANA_KERNEL_API_BASE_URL?.trim();
const kernelLifecycleClient =
  kernelApiBaseUrl === undefined || kernelApiBaseUrl.length === 0
    ? undefined
    : createKernelLifecycleClient({ baseUrl: kernelApiBaseUrl });

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <I18nProvider
      config={{
        defaultNS: 'studio',
        ns: ['studio'],
        resources: STUDIO_I18N_RESOURCES,
      }}
    >
      <StudioLifecycleDataProvider client={kernelLifecycleClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </StudioLifecycleDataProvider>
    </I18nProvider>
  </React.StrictMode>,
);
