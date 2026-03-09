/**
 * ARIA Live Region Components
 *
 * Provides accessible live regions for announcing dynamic content updates
 * to screen reader users. Follows WCAG 2.1 guidelines for live regions.
 *
 * Live regions notify assistive technologies of content changes without
 * requiring the user to navigate to the changed content.
 *
 * @doc.type component
 * @doc.purpose Accessibility - Live Announcements
 * @doc.layer infrastructure
 * @doc.pattern Accessibility Component
 *
 * @see https://www.w3.org/WAI/WCAG21/Understanding/status-messages.html
 * @see https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/ARIA_Live_Regions
 */

import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from 'react';

/**
 * Live region politeness levels
 * - 'polite': Announces when user is idle (non-urgent updates)
 * - 'assertive': Interrupts current speech (urgent updates)
 * - 'off': Disables live region
 */
export type LiveRegionPoliteness = 'polite' | 'assertive' | 'off';

export interface Announcement {
    id: string;
    message: string;
    politeness: LiveRegionPoliteness;
    timestamp: number;
}

export interface LiveRegionContextType {
    /** Announce a message to screen readers */
    announce: (message: string, politeness?: LiveRegionPoliteness) => void;
    /** Clear all announcements */
    clearAnnouncements: () => void;
}

const LiveRegionContext = createContext<LiveRegionContextType | null>(null);

/**
 * Hook to access the live region announcer
 *
 * @example
 * ```tsx
 * function SaveButton() {
 *   const { announce } = useLiveAnnouncer();
 *
 *   const handleSave = async () => {
 *     await saveData();
 *     announce('Changes saved successfully');
 *   };
 *
 *   return <button onClick={handleSave}>Save</button>;
 * }
 * ```
 */
export function useLiveAnnouncer(): LiveRegionContextType {
    const context = useContext(LiveRegionContext);
    if (!context) {
        // Return a no-op implementation if not wrapped in provider
        return {
            announce: () => { },
            clearAnnouncements: () => { },
        };
    }
    return context;
}

export interface LiveRegionProviderProps {
    children: React.ReactNode;
    /** Delay before clearing announcements (ms) */
    clearDelay?: number;
}

/**
 * LiveRegionProvider
 *
 * Provides the live region context and renders the hidden live regions.
 * Should be placed near the root of your application.
 *
 * @example
 * ```tsx
 * function App() {
 *   return (
 *     <LiveRegionProvider>
 *       <YourApp />
 *     </LiveRegionProvider>
 *   );
 * }
 * ```
 */
export function LiveRegionProvider({
    children,
    clearDelay = 1000,
}: LiveRegionProviderProps) {
    const [politeMessage, setPoliteMessage] = useState('');
    const [assertiveMessage, setAssertiveMessage] = useState('');
    const politeTimeoutRef = useRef<NodeJS.Timeout | null>(null);
    const assertiveTimeoutRef = useRef<NodeJS.Timeout | null>(null);

    const announce = useCallback(
        (message: string, politeness: LiveRegionPoliteness = 'polite') => {
            if (politeness === 'off' || !message) return;

            if (politeness === 'assertive') {
                // Clear any existing timeout
                if (assertiveTimeoutRef.current) {
                    clearTimeout(assertiveTimeoutRef.current);
                }
                // Set the message
                setAssertiveMessage(message);
                // Clear after delay
                assertiveTimeoutRef.current = setTimeout(() => {
                    setAssertiveMessage('');
                }, clearDelay);
            } else {
                // Clear any existing timeout
                if (politeTimeoutRef.current) {
                    clearTimeout(politeTimeoutRef.current);
                }
                // Set the message
                setPoliteMessage(message);
                // Clear after delay
                politeTimeoutRef.current = setTimeout(() => {
                    setPoliteMessage('');
                }, clearDelay);
            }
        },
        [clearDelay]
    );

    const clearAnnouncements = useCallback(() => {
        setPoliteMessage('');
        setAssertiveMessage('');
        if (politeTimeoutRef.current) {
            clearTimeout(politeTimeoutRef.current);
        }
        if (assertiveTimeoutRef.current) {
            clearTimeout(assertiveTimeoutRef.current);
        }
    }, []);

    // Cleanup timeouts on unmount
    useEffect(() => {
        return () => {
            if (politeTimeoutRef.current) {
                clearTimeout(politeTimeoutRef.current);
            }
            if (assertiveTimeoutRef.current) {
                clearTimeout(assertiveTimeoutRef.current);
            }
        };
    }, []);

    return (
        <LiveRegionContext.Provider value={{ announce, clearAnnouncements }}>
            {children}
            {/* Hidden live regions for screen reader announcements */}
            <div
                role="status"
                aria-live="polite"
                aria-atomic="true"
                className="sr-only"
            >
                {politeMessage}
            </div>
            <div
                role="alert"
                aria-live="assertive"
                aria-atomic="true"
                className="sr-only"
            >
                {assertiveMessage}
            </div>
        </LiveRegionContext.Provider>
    );
}

/**
 * LiveRegion Component
 *
 * A standalone live region for specific content areas.
 * Use this when you need a dedicated live region for a component.
 *
 * @example
 * ```tsx
 * function SearchResults({ count }) {
 *   return (
 *     <LiveRegion politeness="polite">
 *       {count} results found
 *     </LiveRegion>
 *   );
 * }
 * ```
 */
export interface LiveRegionProps {
    children: React.ReactNode;
    politeness?: LiveRegionPoliteness;
    /** Whether changes should be announced atomically */
    atomic?: boolean;
    /** Which parts of the region to announce */
    relevant?: 'additions' | 'removals' | 'text' | 'all' | 'additions text' | 'additions removals' | 'removals text';
    /** Whether to visually hide the region */
    visuallyHidden?: boolean;
    className?: string;
}

export function LiveRegion({
    children,
    politeness = 'polite',
    atomic = true,
    relevant = 'all',
    visuallyHidden = true,
    className = '',
}: LiveRegionProps) {
    const role = politeness === 'assertive' ? 'alert' : 'status';

    return (
        <div
            role={role}
            aria-live={politeness}
            aria-atomic={atomic}
            aria-relevant={relevant}
            className={`${visuallyHidden ? 'sr-only' : ''} ${className}`}
        >
            {children}
        </div>
    );
}

/**
 * StatusMessage Component
 *
 * A convenience component for status messages that should be announced.
 * Use for form validation, loading states, success/error messages.
 *
 * @example
 * ```tsx
 * <StatusMessage type="success">Form submitted successfully!</StatusMessage>
 * <StatusMessage type="error">Please fix the errors below.</StatusMessage>
 * <StatusMessage type="loading">Saving changes...</StatusMessage>
 * ```
 */
export interface StatusMessageProps {
    type: 'success' | 'error' | 'warning' | 'info' | 'loading';
    children: React.ReactNode;
    className?: string;
}

const STATUS_STYLES = {
    success: 'text-success-700 dark:text-success-300 bg-success-50 dark:bg-success-900/20',
    error: 'text-error-700 dark:text-error-300 bg-error-50 dark:bg-error-900/20',
    warning: 'text-warning-700 dark:text-warning-300 bg-warning-50 dark:bg-warning-900/20',
    info: 'text-info-700 dark:text-info-300 bg-info-50 dark:bg-info-900/20',
    loading: 'text-primary-700 dark:text-primary-300 bg-primary-50 dark:bg-primary-900/20',
};

export function StatusMessage({ type, children, className = '' }: StatusMessageProps) {
    const politeness = type === 'error' ? 'assertive' : 'polite';
    const role = type === 'error' ? 'alert' : 'status';

    return (
        <div
            role={role}
            aria-live={politeness}
            className={`p-3 rounded-md ${STATUS_STYLES[type]} ${className}`}
        >
            {children}
        </div>
    );
}

export default LiveRegion;
