/**
 * Reusable Error Boundary for Route Modules
 * 
 * Provides consistent error handling across all routes with proper accessibility
 * and graceful degradation. Follows UI Component DoD for error boundaries.
 */

import { useEffect } from "react";
import { useRouteError, Link } from "react-router";

/**
 *
 */
export interface RouteErrorBoundaryProps {
    /** Custom error title (optional) */
    title?: string;
    /** Custom error message (optional) */
    message?: string;
    /** Show navigation options */
    showNavigation?: boolean;
}

/**
 *
 */
export function RouteErrorBoundary({
    title = "Something went wrong",
    message,
    showNavigation = true
}: RouteErrorBoundaryProps) {
    const error = useRouteError() as Error & { status?: number; statusText?: string };

    // Log error for monitoring (production-ready)
    useEffect(() => {
        console.error("Route Error:", error);

        // In production, send to monitoring service
        if (import.meta.env.PROD && typeof (window as unknown).gtag === "function") {
            (window as unknown).gtag("event", "exception", {
                description: error.message || "Route error",
                fatal: false,
                custom_map: {
                    route: window.location.pathname,
                    stack: error.stack
                }
            });
        }
    }, [error]);

    // Determine error details
    const isNotFound = error.status === 404;
    const errorTitle = isNotFound ? "Page Not Found" : title;
    const errorMessage = message || error.message || error.statusText || "An unexpected error occurred";

    return (
        <div
            role="alert"
            aria-live="assertive"
            className="error-boundary"
            style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                minHeight: "50vh",
                padding: "2rem",
                textAlign: "center",
                gap: "1rem"
            }}
        >
            <div style={{ fontSize: "2.5rem", opacity: 0.3, fontWeight: 700 }}>
                {isNotFound ? "404" : "!"}
            </div>

            <h1
                style={{
                    margin: 0,
                    fontSize: "1.5rem",
                    fontWeight: 600,
                    color: "var(--error-color)"
                }}
            >
                {errorTitle}
            </h1>

            <p
                data-testid="error-message"
                style={{
                    margin: 0,
                    fontSize: "1rem",
                    color: "var(--text-secondary)",
                    maxWidth: "32rem"
                }}
            >
                {errorMessage}
            </p>

            {showNavigation && (
                <div style={{ display: "flex", gap: "1rem", marginTop: "1rem" }}>
                    <button
                        data-testid="retry-button"
                        onClick={() => {
                            // Clear error flag and reload
                            try {
                                if (typeof window !== 'undefined' && window.localStorage) {
                                    localStorage.removeItem('E2E_FORCE_NETWORK_ERROR');
                                    // Set a flag to prevent immediate re-error
                                    localStorage.setItem('E2E_RETRY_ATTEMPTED', 'true');
                                }
                            } catch {
                                // Ignore localStorage errors
                            }
                            // Use a slight delay to ensure localStorage is updated
                            setTimeout(() => {
                                window.location.reload();
                            }, 10);
                        }}
                        style={{
                            padding: "0.5rem 1rem",
                            backgroundColor: "var(--primary-color)",
                            color: "white",
                            border: "none",
                            borderRadius: "var(--border-radius-sm)",
                            cursor: "pointer",
                            fontSize: "0.875rem",
                            transition: "var(--transition-fast)"
                        }}
                    >
                        Retry
                    </button>

                    <Link
                        to="/app/workspaces"
                        style={{
                            padding: "0.5rem 1rem",
                            backgroundColor: "transparent",
                            color: "var(--primary-color)",
                            border: "1px solid var(--primary-color)",
                            borderRadius: "var(--border-radius-sm)",
                            textDecoration: "none",
                            fontSize: "0.875rem",
                            transition: "var(--transition-fast)"
                        }}
                    >
                        Go to Dashboard
                    </Link>
                </div>
            )}

            {import.meta.env.DEV && error.stack && (
                <details style={{ marginTop: "2rem", textAlign: "left", maxWidth: "100%", color: "var(--text-primary)" }}>
                    <summary style={{ cursor: "pointer", marginBottom: "0.5rem" }}>
                        Error Details (Development)
                    </summary>
                    <pre
                        style={{
                            padding: "1rem",
                            backgroundColor: "var(--bg-surface)",
                            borderRadius: "var(--border-radius-sm)",
                            fontSize: "0.75rem",
                            overflow: "auto",
                            maxHeight: "200px",
                            color: "var(--text-primary)"
                        }}
                    >
                        {error.stack}
                    </pre>
                </details>
            )}
        </div>
    );
}
