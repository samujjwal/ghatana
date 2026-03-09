import { createRoot } from 'react-dom/client';
import { ErrorBoundary } from '@ghatana/ui/components/ErrorBoundary';
import App from './App';
import { Providers } from './Providers';

// Ensure the root element exists before rendering
const container = document.getElementById('root');

if (!container) {
  throw new Error('Failed to find the root element');
}

const root = createRoot(container);

// Render the app with all providers
root.render(
  <ErrorBoundary>
    <Providers>
      <App />
    </Providers>
  </ErrorBoundary>
);
