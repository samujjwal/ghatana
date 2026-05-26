import React from 'react';
import { ErrorBoundary } from '@ghatana/design-system';
import { ThemeProvider } from '@ghatana/theme';
import { RouterProvider } from 'react-router-dom';
import { PhrAccessProvider } from './auth/PhrAccessContext';
import { PhrSessionProvider } from './auth/PhrSessionContext';
import { router } from './routes';

export function App(): React.ReactElement {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <PhrSessionProvider>
          <PhrAccessProvider>
            <RouterProvider router={router} />
          </PhrAccessProvider>
        </PhrSessionProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}

export default App;
