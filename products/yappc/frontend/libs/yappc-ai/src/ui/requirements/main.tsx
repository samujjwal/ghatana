import { ApolloProvider } from '@apollo/client';
import { Provider } from 'jotai';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import { BrowserRouter } from 'react-router-dom';
import { ErrorBoundary } from '@yappc/ui';

import App from './App.tsx';
import { apolloClient } from './services/apollo.ts';
import './index.css';

const theme = createTheme({
  palette: {
    mode: 'light',
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary boundaryName="yappc-ai-requirements">
      <Provider>
        <ApolloProvider client={apolloClient}>
          <ThemeProvider theme={theme}>
            <CssBaseline />
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </ThemeProvider>
        </ApolloProvider>
      </Provider>
    </ErrorBoundary>
  </React.StrictMode>
);
