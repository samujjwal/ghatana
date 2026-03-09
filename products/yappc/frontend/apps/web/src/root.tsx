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
import { ThemeProvider } from '@ghatana/theme';
import { GraphQLProvider } from '@ghatana/yappc-api';
import { WebSocketProvider } from './contexts/WebSocketContext';
import { ToastProvider } from '@ghatana/ui';
import { AuthProvider } from './providers/AuthProvider';
import { FeatureFlagProvider } from './providers/FeatureFlagProvider';
import './index.css';
import '@xyflow/react/dist/style.css';

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
      <body className="bg-gray-50 text-gray-900 dark:bg-slate-950 dark:text-gray-100 transition-colors duration-300" suppressHydrationWarning>
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
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      height: '100vh',
      background: 'linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%)',
      fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
    }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{
          display: 'inline-block',
          width: '48px',
          height: '48px',
          border: '4px solid rgba(59, 130, 246, 0.2)',
          borderTop: '4px solid #3b82f6',
          borderRadius: '50%',
          animation: 'spin 1s linear infinite',
          marginBottom: '16px',
        }}>
          <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
        </div>
        <h1 style={{
          fontSize: '24px',
          color: '#1f2937',
          marginBottom: '8px',
          fontWeight: '600',
          margin: '0',
        }}>YAPPC App Creator</h1>
        <p style={{
          color: '#6b7280',
          fontSize: '14px',
          margin: '0',
          marginTop: '8px',
        }}>Loading modules and initializing...</p>
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
    <ThemeProvider>
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
    </ThemeProvider>
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
