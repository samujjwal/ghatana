/**
 * App Integration Example
 * 
 * Complete application setup demonstrating provider configuration,
 * router integration, and global state management.
 * 
 * @doc.type example
 * @doc.purpose Complete app setup
 * @doc.layer ui
 * @doc.pattern Application Root
 */

import React from 'react';
import { RouterProvider } from 'react-router-dom';
import { Provider as JotaiProvider } from 'jotai';
import { ToastProvider } from '../../Toast';
import { router } from './RouterExample';

// Type augmentation for Vite environment variables
// Note: In a real app, add this to vite-env.d.ts instead
declare global {
  interface ImportMetaEnv {
    readonly MODE: string;
    readonly VITE_API_BASE_URL?: string;
    readonly DEV: boolean;
    readonly PROD: boolean;
    [key: string]: unknown;
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv;
  }
}

/**
 * App props
 */
export interface AppProps {
  /**
   * Custom router instance
   */
  router?: ReturnType<typeof import('react-router-dom').createBrowserRouter>;
  
  /**
   * Toast position
   * @default 'top-right'
   */
  toastPosition?: 'top-left' | 'top-center' | 'top-right' | 'bottom-left' | 'bottom-center' | 'bottom-right';
  
  /**
   * Maximum number of toasts to show
   * @default 3
   */
  maxToasts?: number;
}

/**
 * App Component
 * 
 * Root application component with all necessary providers configured.
 * Demonstrates production-ready application setup.
 * 
 * @example Basic Usage
 * ```tsx
 * import { createRoot } from 'react-dom/client';
 * import { App } from './App';
 * 
 * const root = createRoot(document.getElementById('root')!);
 * root.render(<App />);
 * ```
 * 
 * @example Custom Configuration
 * ```tsx
 * import { createRoot } from 'react-dom/client';
 * import { App } from './App';
 * import { customRouter } from './customRouter';
 * 
 * const root = createRoot(document.getElementById('root')!);
 * root.render(
 *   <App
 *     router={customRouter}
 *     toastPosition="bottom-right"
 *     maxToasts={5}
 *   />
 * );
 * ```
 */
export function App({
  router: customRouter,
  toastPosition = 'top-right',
  maxToasts = 3,
}: AppProps = {}): React.JSX.Element {
  const routerInstance = customRouter || router;
  
  return (
    <React.StrictMode>
      <JotaiProvider>
        <ToastProvider position={toastPosition} maxToasts={maxToasts}>
          <RouterProvider router={routerInstance} />
        </ToastProvider>
      </JotaiProvider>
    </React.StrictMode>
  );
}

/**
 * Development Setup
 * 
 * Configure development environment with React DevTools and hot reloading
 */
export function setupDevEnvironment() {
  // Enable React DevTools
  if (typeof window !== 'undefined') {
    // @ts-ignore
    window.__REACT_DEVTOOLS_GLOBAL_HOOK__ = window.__REACT_DEVTOOLS_GLOBAL_HOOK__ || {
      supportsFiber: true,
      inject: () => {},
      onCommitFiberRoot: () => {},
      onCommitFiberUnmount: () => {},
    };
  }
  
  // Log environment info
  console.info('🚀 App starting in', import.meta.env.MODE, 'mode');
  console.info('📦 API URL:', import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000');
}

/**
 * Production Setup
 * 
 * Configure production optimizations and error handling
 */
export function setupProductionEnvironment() {
  // Disable console logs in production
  if (import.meta.env.PROD) {
    console.log = () => {};
    console.debug = () => {};
    console.info = () => {};
  }
  
  // Global error handler
  window.addEventListener('error', (event) => {
    console.error('Global error:', event.error);
    // Send to error tracking service (e.g., Sentry)
  });
  
  // Unhandled promise rejection handler
  window.addEventListener('unhandledrejection', (event) => {
    console.error('Unhandled promise rejection:', event.reason);
    // Send to error tracking service
  });
}

/**
 * Initialize Application
 * 
 * Main entry point for the application
 */
export function initializeApp() {
  // Setup environment
  if (import.meta.env.DEV) {
    setupDevEnvironment();
  } else {
    setupProductionEnvironment();
  }
  
  // Check for required environment variables
  const requiredEnvVars = ['VITE_API_BASE_URL'];
  const missingEnvVars = requiredEnvVars.filter(
    (varName) => !import.meta.env[varName]
  );
  
  if (missingEnvVars.length > 0) {
    console.warn('Missing environment variables:', missingEnvVars);
  }
}

// =============================================================================
// Usage Examples
// =============================================================================

/**
 * @example main.tsx (Vite Entry Point)
 * 
 * ```tsx
 * import { createRoot } from 'react-dom/client';
 * import { App, initializeApp } from './App';
 * import './index.css';
 * 
 * // Initialize app
 * initializeApp();
 * 
 * // Render app
 * const root = createRoot(document.getElementById('root')!);
 * root.render(<App />);
 * ```
 * 
 * @example Environment Variables (.env)
 * 
 * ```bash
 * # API Configuration
 * VITE_API_BASE_URL=https://api.yourapp.com
 * 
 * # Feature Flags
 * VITE_ENABLE_ANALYTICS=true
 * VITE_ENABLE_SOCIAL_LOGIN=false
 * 
 * # App Configuration
 * VITE_APP_NAME=YourApp
 * VITE_APP_VERSION=1.0.0
 * ```
 * 
 * @example Custom Router
 * 
 * ```tsx
 * import { createBrowserRouter } from 'react-router-dom';
 * import { App } from './App';
 * import { LoginPage, DashboardPage } from '@ghatana/yappc-ui';
 * 
 * const customRouter = createBrowserRouter([
 *   { path: '/login', element: <LoginPage /> },
 *   { path: '/dashboard', element: <DashboardPage /> },
 * ]);
 * 
 * root.render(<App router={customRouter} />);
 * ```
 * 
 * @example With Error Boundary
 * 
 * ```tsx
 * import { ErrorBoundary } from '@ghatana/ui';
 * 
 * root.render(
 *   <ErrorBoundary>
 *     <App />
 *   </ErrorBoundary>
 * );
 * ```
 */

export default App;
