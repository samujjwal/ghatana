import React from 'react';
import { ErrorBoundary } from '@ghatana/design-system';
import { ThemeProvider } from '@ghatana/theme';
import { RouterProvider } from 'react-router';
import { router } from './routes';

export function App(): React.ReactElement {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <RouterProvider router={router} />
      </ThemeProvider>
    </ErrorBoundary>
  );
}

export default App;