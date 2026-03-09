/**
 * Root Route Module - React Router v7 Framework Mode
 *
 * This is the root layout that wraps all routes in the application.
 * In Framework Mode, this file is automatically loaded as the parent of all routes.
 *
 * @see https://reactrouter.com/start/framework/routing#root-route
 */
import { Suspense } from 'react';
import { Outlet, Links, Meta, Scripts, ScrollRestoration, isRouteErrorResponse, useRouteError } from 'react-router';
import { Provider } from 'jotai';
import { QueryProvider } from '@/app/providers/QueryProvider';
import { ThemeProvider } from '@/app/providers/ThemeProvider';
import { AuthProvider } from '@/app/providers/AuthProvider';
import '@/index.css';
import '@/styles/tokens.css';

/**
 * Loading fallback shown while routes are loading
 */
function LoadingFallback() {
    return (
        <div className="flex items-center justify-center h-screen bg-slate-50 dark:bg-slate-900">
            <div className="text-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
                <p className="mt-4 text-sm text-slate-600 dark:text-neutral-400">Loading...</p>
            </div>
        </div>
    );
}

/**
 * Document Layout - provides HTML structure
 */
export function Layout({ children }: { children: React.ReactNode }) {
    return (
        <html lang="en" suppressHydrationWarning>
            <head>
                <meta charSet="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <script
                    // Inline theme script to avoid flash of wrong theme before React hydrates
                    dangerouslySetInnerHTML={{
                        __html:
                            "(function(){try{var e='software-org:theme',t=window.localStorage.getItem(e)||'system',d=document.documentElement,n;t==='system'?(n=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'):n=t,d.classList.remove('light','dark'),d.classList.add(n),d.setAttribute('data-theme',t);}catch(e){}})();",
                    }}
                />
                <Meta />
                <Links />
            </head>
            <body className="min-h-screen bg-slate-50 dark:bg-slate-900" suppressHydrationWarning>
                {children}
                <ScrollRestoration />
                <Scripts />
            </body>
        </html>
    );
}

/**
 * Root component - wraps all routes with providers
 */
export default function Root() {
    return (
        <QueryProvider>
            <Provider>
                <ThemeProvider>
                    <AuthProvider>
                        <Suspense fallback={<LoadingFallback />}>
                            <Outlet />
                        </Suspense>
                    </AuthProvider>
                </ThemeProvider>
            </Provider>
        </QueryProvider>
    );
}

/**
 * Error boundary for the root route
 * 
 * Note: In React Router v7 Framework Mode, the ErrorBoundary should NOT render
 * <html>, <head>, <body> - those are provided by the Layout component.
 * The ErrorBoundary just renders the error content that goes inside the Layout.
 */
export function ErrorBoundary() {
    const error = useRouteError();

    let message = 'An unexpected error occurred';
    let status = 500;

    if (isRouteErrorResponse(error)) {
        message = error.statusText || error.data?.message || message;
        status = error.status;
    } else if (error instanceof Error) {
        message = error.message;
    }

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 flex items-center justify-center">
            <div className="max-w-lg text-center p-6">
                <div className="text-red-500 text-6xl mb-4">{status}</div>
                <h1 className="text-2xl font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                    Something went wrong
                </h1>
                <p className="text-slate-600 dark:text-neutral-400 mb-6">
                    {message}
                </p>
                <a
                    href="/"
                    className="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                >
                    Go Home
                </a>
            </div>
        </div>
    );
}
