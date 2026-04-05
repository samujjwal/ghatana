/**
 * Loading Spinner Component for Route Transitions
 * 
 * WCAG compliant loading indicator with proper accessibility attributes
 * and customizable appearance. Follows UI Component DoD requirements.
 */

import type { CSSProperties } from "react";

/**
 *
 */
export interface LoadingSpinnerProps {
    /** Size variant */
    size?: "sm" | "md" | "lg";
    /** Loading message for screen readers */
    message?: string;
    /** Show text label */
    showLabel?: boolean;
    /** Custom styles */
    style?: CSSProperties;
    /** Color variant */
    variant?: "primary" | "secondary";
}

const sizeMap = {
    sm: "16px",
    md: "24px",
    lg: "32px"
} as const;

const colorMap = {
    primary: "var(--primary-color)",
    secondary: "var(--secondary-color)"
} as const;

/**
 *
 */
export function LoadingSpinner({
    size = "md",
    message = "Loading content...",
    showLabel = true,
    style,
    variant = "primary"
}: LoadingSpinnerProps) {
    const spinnerSize = sizeMap[size];
    const spinnerColor = colorMap[variant];

    return (
        <div
            role="status"
            aria-live="polite"
            aria-label={message}
            style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: "0.5rem",
                padding: "1rem",
                ...style
            }}
        >
            <div
                style={{
                    width: spinnerSize,
                    height: spinnerSize,
                    border: `2px solid transparent`,
                    borderTop: `2px solid ${spinnerColor}`,
                    borderRadius: "50%",
                    animation: "spin 1s linear infinite"
                }}
                aria-hidden="true"
            />

            {showLabel && (
                <span
                    style={{
                        fontSize: "0.875rem",
                        color: "var(--text-secondary)"
                    }}
                >
                    {message}
                </span>
            )}

            {/* Screen reader only text */}
            <span className="sr-only">
                {message}
            </span>

            <style>
                {`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
          
          .sr-only {
            position: absolute;
            width: 1px;
            height: 1px;
            padding: 0;
            margin: -1px;
            overflow: hidden;
            clip: rect(0, 0, 0, 0);
            white-space: nowrap;
            border: 0;
          }

          @media (prefers-reduced-motion: reduce) {
            @keyframes spin {
              0% { transform: rotate(0deg); }
              100% { transform: rotate(0deg); }
            }
          }
        `}
            </style>
        </div>
    );
}

/**
 * Route-level loading component with consistent styling
 */
export function RouteLoadingSpinner() {
    return (
        <div
            style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                minHeight: "200px"
            }}
        >
            <LoadingSpinner
                size="lg"
                message="Loading page..."
                variant="primary"
            />
        </div>
    );
}