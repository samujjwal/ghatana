import React from 'react';
import ReactDOM from 'react-dom/client';
import { ThemeProvider } from '@ghatana/theme';
import { App } from './App';
import './index.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element not found');
}

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <ThemeProvider defaultTheme="light" enableStorage={false} enableSystem={false}>
      <App />
    </ThemeProvider>
  </React.StrictMode>,
);
