import React from 'react';
import ReactDOM from 'react-dom/client';
import { ErrorBoundary } from '@ghatana/design-system';
import { ThemeProvider } from '@ghatana/theme';
import { App } from '@/App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <ThemeProvider>
        <App />
      </ThemeProvider>
    </ErrorBoundary>
  </React.StrictMode>,
);
