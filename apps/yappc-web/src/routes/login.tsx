/**
 * Login Route
 * 
 * Authentication page with proper form handling and accessibility.
 * Uses AuthService for secure authentication.
 */

import { Form, useNavigate, useSearchParams } from 'react-router';
import React from 'react';
import { Lock, User as Person, ArrowRight as ArrowForward } from 'lucide-react';
import { authService } from '../services/auth/AuthService';
import { logger } from '../utils/Logger';

import { RouteErrorBoundary } from "../components/route/ErrorBoundary";

import type { Route } from './+types/login';

/**
 * Client action for login form submission
 */
export async function clientAction({ request }: Route.ClientActionArgs) {
    const formData = await request.formData();
    const username = formData.get("username") as string;
    const password = formData.get("password") as string;
    const redirectTo = formData.get("redirectTo") || "/app/workspaces";

    if (!username || !password) {
        return {
            error: "Username and password are required"
        };
    }

    // Use AuthService for authentication
    const result = await authService.login({ username, password });

    if (result.success && result.token) {
        logger.info('Login successful, redirecting', 'login', {
            username,
            redirectTo
        });

        return new Response(null, {
            status: 302,
            headers: {
                'Location': redirectTo
            }
        } as ResponseInit);
    }

    return {
        error: result.error || "Authentication failed"
    };
}

/**
 * Login page component
 */
export default function Component() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const redirectTo = searchParams.get("redirectTo") || "/app/workspaces";
    const sessionExpired = searchParams.get("sessionExpired") === "true";
    const [isLoading, setIsLoading] = React.useState(false);
    const [error, setError] = React.useState<string | null>(null);

    // Handle demo login
    const handleDemoLogin = async () => {
        setIsLoading(true);
        setError(null);

        try {
            const result = await authService.demoLogin();
            if (result.success) {
                navigate(redirectTo);
            } else {
                setError(result.error || 'Demo login failed');
            }
        } catch (err) {
            setError('Demo login failed');
            logger.error('Demo login error', 'login', { error: err instanceof Error ? err.message : String(err) });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-bg-default flex items-center justify-center px-4">
            <div className="w-full max-w-md">
                {/* Logo */}
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-bold text-text-primary tracking-tight">
                        YAPPC
                    </h1>
                    <p className="mt-2 text-text-secondary">
                        Sign in to your account
                    </p>
                </div>

                {/* Card */}
                <div className="bg-bg-paper rounded-xl border border-divider p-6 shadow-sm">
                    {error && (
                        <div
                            data-testid="login-error"
                            className="mb-6 p-4 rounded-lg bg-error-color/10 border border-error-color/30 text-error-color text-sm"
                        >
                            {error}
                        </div>
                    )}

                    {sessionExpired && (
                        <div
                            data-testid="session-expired-message"
                            className="mb-6 p-4 rounded-lg bg-error-color/10 border border-error-color/30 text-error-color text-sm"
                        >
                            Your session has expired. Please sign in again.
                        </div>
                    )}

                    <Form method="post" className="space-y-6">
                        <div>
                            <label htmlFor="username" className="block text-sm font-medium text-text-primary mb-2">
                                Username
                            </label>
                            <div className="relative">
                                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary">
                                    <Person className="w-5 h-5" />
                                </div>
                                <input
                                    id="username"
                                    name="username"
                                    type="text"
                                    required
                                    autoComplete="username"
                                    className="w-full pl-10 pr-4 py-2.5 rounded-lg border border-divider bg-bg-surface text-text-primary focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-all outline-none"
                                    placeholder="Enter your username"
                                />
                            </div>
                        </div>

                        <div>
                            <label htmlFor="password" className="block text-sm font-medium text-text-primary mb-2">
                                Password
                            </label>
                            <div className="relative">
                                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary">
                                    <Lock className="w-5 h-5" />
                                </div>
                                <input
                                    id="password"
                                    name="password"
                                    type="password"
                                    required
                                    autoComplete="current-password"
                                    className="w-full pl-10 pr-4 py-2.5 rounded-lg border border-divider bg-bg-surface text-text-primary focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition-all outline-none"
                                    placeholder="Enter your password"
                                />
                            </div>
                        </div>

                        <input type="hidden" name="redirectTo" value={redirectTo} />

                        <button
                            type="submit"
                            disabled={isLoading}
                            className="w-full inline-flex items-center justify-center gap-2 px-5 py-3.5 bg-primary-600 hover:bg-primary-700 text-white rounded-lg font-semibold transition-all shadow-md hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 cursor-pointer border-none disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {isLoading ? (
                                <>
                                    <span>Signing In...</span>
                                </>
                            ) : (
                                <>
                                    <span>Sign In</span>
                                    <ArrowForward className="w-4 h-4" />
                                </>
                            )}
                        </button>
                    </Form>

                    <div className="mt-6 pt-6 border-t border-divider text-center">
                        <button
                            onClick={handleDemoLogin}
                            disabled={isLoading}
                            className="text-sm font-medium text-primary-600 hover:text-primary-700 transition-colors bg-transparent border-none cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {isLoading ? 'Loading...' : 'Continue as Demo User →'}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

/**
 *
 */
export function ErrorBoundary() {
    return (
        <RouteErrorBoundary
            title="Login Error"
            message="Unable to load the login page. Please check your connection and try again."
        />
    );
}