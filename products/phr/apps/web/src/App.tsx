import React from 'react';
import { ErrorBoundary } from '@ghatana/design-system';
import { ThemeProvider } from '@ghatana/theme';
import { RouterProvider } from 'react-router';
import { PhrAccessProvider } from './auth/PhrAccessContext';
import { router } from './routes';

export function App(): React.ReactElement {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <PhrAccessProvider>
          <RouterProvider router={router} />
        </PhrAccessProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}

export default App;
