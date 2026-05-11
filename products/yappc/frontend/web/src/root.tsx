import {
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
  isRouteErrorResponse,
  useRouteError,
} from "react-router";
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GraphQLProvider } from 'yappc-core/api';
import { WebSocketProvider } from './contexts/WebSocketContext';
import { ToastProvider } from './components/common';
import { AuthProvider } from './providers/AuthProvider';
import { FeatureFlagProvider } from './providers/FeatureFlagProvider';
import { AppThemeProvider } from './theme';
import { I18nProvider } from '@ghatana/i18n';
import { initI18n } from '@ghatana/i18n';
import './index.css';
import '@xyflow/react/dist/style.css';

// Initialize i18n with HTTP backend (loads from /locales/{lng}/{ns}.json)
const i18nInstance = await initI18n({
  defaultNS: 'common',
  ns: ['common'],
  fallbackLng: 'en',
  loadPath: '/locales/{{lng}}/{{ns}}.json',
});

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>YAPPC App Creator</title>
        <meta name="csrf-token" content="test-csrf-token-12345" />
        <Meta />
        <Links />
        <script
          dangerouslySetInnerHTML={{
            __html: `
              (function() {
                const theme = localStorage.getItem('theme') || 'light';
                const html = document.documentElement;
                if (theme === 'dark') {
                  html.classList.add('dark');
                } else {
                  html.classList.remove('dark');
                }
              })();
            `,
          }}
        />
      </head>
      <body className="bg-surface-muted text-fg dark:bg-surface dark:text-fg-muted transition-colors duration-300" suppressHydrationWarning>
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

// Hydration fallback - shown while JS modules load
export function HydrateFallback() {
  return (
    <div className="flex items-center justify-center h-screen bg-gradient-to-br from-gray-100 to-gray-200 font-sans">
      <div className="text-center">
        <div className="inline-block w-12 h-12 border-4 border-info-border/20 border-t-blue-500 rounded-full animate-spin mb-4">
          <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
        </div>
        <h1 className="text-2xl font-semibold text-fg m-0">YAPPC App Creator</h1>
        <p className="text-fg-muted text-sm m-0 mt-2">Loading modules and initializing...</p>
      </div>
    </div>
  );
}

// Create TanStack Query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

export default function App() {
  return (
    <I18nProvider instance={i18nInstance}>
      <AppThemeProvider>
        <QueryClientProvider client={queryClient}>
          <GraphQLProvider>
            <WebSocketProvider
              wsUrl={import.meta.env.VITE_WEBSOCKET_URL ?? 'ws://localhost:3001/ws'}
              autoConnect={
                typeof import.meta.env?.VITE_ENABLE_REAL_WS === 'string'
                  ? import.meta.env.VITE_ENABLE_REAL_WS === 'true'
                  : false
              }
            >
              <ToastProvider>
                <AuthProvider>
                  <FeatureFlagProvider>
                    <Outlet />
                  </FeatureFlagProvider>
                </AuthProvider>
              </ToastProvider>
            </WebSocketProvider>
          </GraphQLProvider>
        </QueryClientProvider>
      </AppThemeProvider>
    </I18nProvider>
  );
}

export function ErrorBoundary() {
  const error = useRouteError();
  let details = "An unexpected error occurred.";
  let stack: string | undefined;

  if (isRouteErrorResponse(error)) {
    details = error.status === 404 ? "The requested page could not be found." : error.statusText || details;
  } else if (import.meta.env.DEV && error && error instanceof Error) {
    details = error.message;
    stack = error.stack;
  }

  return (
    <main className="pt-16 p-4 container mx-auto">
      <h1>{isRouteErrorResponse(error) ? `${error.status} ${error.statusText}` : "Error"}</h1>
      <p>{details}</p>
      {stack && (
        <pre className="w-full p-4 overflow-x-auto">
          <code>{stack}</code>
        </pre>
      )}
    </main>
  );
}
