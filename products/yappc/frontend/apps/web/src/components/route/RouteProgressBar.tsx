/**
 * Route Progress Bar Component
 * 
 * Provides visual feedback during route transitions with a smooth progress indicator.
 * Features incremental progress simulation and graceful completion.
 * 
 * @doc.type component
 * @doc.purpose Visual feedback for route transitions
 * @doc.layer infrastructure
 */

import { useEffect, useState, useRef } from 'react';
import { palette, zIndex } from '@ghatana/tokens';

interface RouteProgressBarProps {
    /** Whether the route is currently loading */
    isLoading: boolean;
    /** Height of the progress bar in pixels */
    height?: number;
    /** Color of the progress bar */
    color?: string;
    /** Duration to simulate progress in milliseconds */
    simulationDuration?: number;
}

/**
 * Route progress bar that shows loading state during navigation
 * 
 * Features:
 * - Smooth incremental progress (0-90% during loading)
 * - Rapid completion animation (90-100% on finish)
 * - Automatic cleanup after completion
 * - Minimal re-renders using refs
 * 
 * @example
 * ```tsx
 * const navigation = useNavigation();
 * return <RouteProgressBar isLoading={navigation.state === "loading"} />;
 * ```
 */
export function RouteProgressBar({
    isLoading,
    height = 3,
    color = palette.primary['500'],
    simulationDuration = 1000,
}: RouteProgressBarProps) {
    const [progress, setProgress] = useState(0);
    const [isVisible, setIsVisible] = useState(false);
    const progressTimerRef = useRef<NodeJS.Timeout | null>(null);
    const cleanupTimerRef = useRef<NodeJS.Timeout | null>(null);
    const startTimeRef = useRef<number>(0);

    useEffect(() => {
        // Clear any existing timers
        if (progressTimerRef.current) {
            clearInterval(progressTimerRef.current);
            progressTimerRef.current = null;
        }
        if (cleanupTimerRef.current) {
            clearTimeout(cleanupTimerRef.current);
            cleanupTimerRef.current = null;
        }

        if (isLoading) {
            // Show the progress bar and start from 0
            setIsVisible(true);
            setProgress(0);
            startTimeRef.current = Date.now();

            // Simulate progress using a logarithmic curve (fast at start, slows down)
            // This creates a perceived loading experience while waiting for actual navigation
            progressTimerRef.current = setInterval(() => {
                setProgress((prev) => {
                    const elapsed = Date.now() - startTimeRef.current;
                    const ratio = Math.min(elapsed / simulationDuration, 1);

                    // Logarithmic curve: fast initial progress, slows to 90%
                    const target = 90 * (1 - Math.exp(-3 * ratio));

                    // Smooth interpolation
                    return prev + (target - prev) * 0.3;
                });
            }, 50); // Update every 50ms for smooth animation
        } else if (progress > 0) {
            // Loading finished - complete the progress bar
            setProgress(100);

            // Hide after completion animation
            cleanupTimerRef.current = setTimeout(() => {
                setIsVisible(false);
                setProgress(0);
            }, 300); // Wait for fade-out animation
        }

        return () => {
            if (progressTimerRef.current) {
                clearInterval(progressTimerRef.current);
            }
            if (cleanupTimerRef.current) {
                clearTimeout(cleanupTimerRef.current);
            }
        };
    }, [isLoading, simulationDuration]);

    if (!isVisible && progress === 0) {
        return null;
    }

    return (
        <div
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(progress)}
            aria-label="Page loading"
            style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                height: `${height}px`,
                zIndex: zIndex.modal + 10, // Above everything
                pointerEvents: 'none',
                backgroundColor: 'transparent',
            }}
        >
            <div
                style={{
                    width: `${progress}%`,
                    height: '100%',
                    backgroundColor: color,
                    transition: progress === 100
                        ? 'width 0.2s ease-out, opacity 0.3s ease-out'
                        : 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    opacity: isVisible ? 1 : 0,
                    boxShadow: `0 0 10px ${color}40`,
                }}
            />
        </div>
    );
}

/**
 * Mini loading indicator for nested navigation
 * Shows a smaller spinner for in-page navigation
 */
export function MiniLoadingIndicator({ isLoading }: { isLoading: boolean }) {
    if (!isLoading) return null;

    return (
        <div
            className="animate-fade-in"
            style={{
                position: 'fixed',
                bottom: '1rem',
                right: '1rem',
                zIndex: zIndex.modal,
                padding: '0.75rem',
                backgroundColor: '#f8f9fa', // Light mode surface
                borderRadius: '0.5rem',
                boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
            }}
        >
            <div
                className="animate-spin"
                style={{
                    width: '1rem',
                    height: '1rem',
                    border: `2px solid ${palette.neutral[300]}`,
                    borderTopColor: palette.primary['500'],
                    borderRadius: '50%',
                }}
            />
            <span style={{ fontSize: '0.875rem', color: palette.gray[600] }}>
                Loading...
            </span>
        </div>
    );
}
