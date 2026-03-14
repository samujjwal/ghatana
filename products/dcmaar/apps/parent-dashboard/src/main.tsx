import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ErrorBoundary } from '@ghatana/design-system'
import './index.css'
import App from './App.tsx'
import { initSentry } from './config/sentry'
import { initWebVitals } from './utils/webVitals'
import { logger } from './utils/logger'
import { configureDashboardCore } from './config/dashboardCore'

const queryClient = new QueryClient()

// Initialize development environment
async function initializeApp() {
  const rootElement = document.getElementById('root');
  if (rootElement) {
    rootElement.innerHTML = '<div style="padding:16px;font-family:system-ui, -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif;color:#111827">Bootstrapping Guardian parent dashboard…</div>';
  }
  // Basic runtime signal to confirm main.tsx executed
  console.log('[Guardian] initializeApp starting');

  configureDashboardCore()

  if (import.meta.env.MODE === 'development') {
    try {
      // First, set up mock auth immediately
      const { setupMockAuth } = await import('./dev/mockAuth');
      setupMockAuth();

      // Then check for backend and initialize demo user if needed
      const { initializeDevUser } = await import('./dev/initDevUser');
      await initializeDevUser().catch(error => {
        console.warn('Demo user initialization warning:', error.message);
      });
    } catch (error) {
      console.warn('Development initialization error:', error);
    }
  }

  // Start the app
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </ErrorBoundary>
    </StrictMode>,
  );
}

// Start the app
initializeApp();

// Initialize monitoring
try {
  initSentry();
  initWebVitals();
  logger.info('Application monitoring initialized');
} catch (error) {
  console.error('Failed to initialize monitoring:', error);
}
