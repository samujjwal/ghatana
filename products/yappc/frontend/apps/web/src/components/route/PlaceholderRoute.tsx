/**
 * Placeholder Route Component
 * 
 * Reusable placeholder component for unimplemented routes.
 * Eliminates duplication across placeholder route implementations.
 */

import { RouteErrorBoundary } from "./ErrorBoundary";

/**
 *
 */
export interface PlaceholderRouteProps {
    /** The icon to display */
    icon: string;
    /** The route title */
    title: string;
    /** Description of what will be implemented */
    description: string;
    /** Error boundary title override */
    errorTitle?: string;
}

/**
 *
 */
export function PlaceholderRoute({
    icon,
    title,
    description,
    errorTitle
}: PlaceholderRouteProps) {
    return (
        <div style={{ textAlign: "center", padding: "3rem" }}>
            <div style={{ fontSize: "3rem", marginBottom: "1rem" }}>
                {icon}
            </div>
            <h2 style={{ margin: "0 0 0.5rem 0", fontSize: "1.5rem", fontWeight: 600 }}>
                {title} (Coming Soon)
            </h2>
            <p style={{ color: "var(--text-secondary)", fontSize: "0.875rem" }}>
                {description}
            </p>
        </div>
    );
}

/**
 * Creates a placeholder error boundary component
 */
export function createPlaceholderErrorBoundary(title: string) {
    return function ErrorBoundary() {
        return <RouteErrorBoundary title={`${title} Error`} />;
    };
}