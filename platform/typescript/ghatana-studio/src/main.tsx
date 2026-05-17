import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { I18nProvider } from '@ghatana/i18n';
import { ThemeProvider } from '@ghatana/theme';
import App from './App';
import { createKernelLifecycleClient } from './api/kernelLifecycleClient';
import { StudioLifecycleDataProvider } from './data/StudioLifecycleDataContext';
import { STUDIO_I18N_RESOURCES } from './i18n/studioTranslations';
import { resolveStudioRuntimeContext } from './config/studioRuntimeContext';
import './index.css';

const runtimeContext = resolveStudioRuntimeContext();

const kernelLifecycleClient =
  runtimeContext.status === 'configured'
    ? createKernelLifecycleClient({
        baseUrl: runtimeContext.identity.baseUrl,
        tenantId: runtimeContext.identity.tenantId,
        workspaceId: runtimeContext.identity.workspaceId,
        projectId: runtimeContext.identity.projectId,
        authToken: runtimeContext.identity.authToken,
      })
    : undefined;

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <I18nProvider
      config={{
        defaultNS: 'studio',
        ns: ['studio'],
        resources: STUDIO_I18N_RESOURCES,
      }}
    >
      <ThemeProvider>
        <StudioLifecycleDataProvider
          client={kernelLifecycleClient}
          runtimeContext={runtimeContext}
        >
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </StudioLifecycleDataProvider>
      </ThemeProvider>
    </I18nProvider>
  </React.StrictMode>,
);
