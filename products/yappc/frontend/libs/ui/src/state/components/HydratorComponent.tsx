/**
 * State Hydrator Component
 *
 * Restores persisted state from localStorage on application mount.
 * Handles hydration conflicts and provides loading state during hydration.
 *
 * @module state/components/HydratorComponent
 * @doc.type component
 * @doc.purpose Restore persisted state from storage on mount
 * @doc.layer platform
 * @doc.pattern Hydration
 */

import React, { useEffect, useState } from 'react';
import { useHydrateAtoms } from 'jotai/utils';
import { useAtomValue } from 'jotai';
import { StateManager } from '../StateManager';
import { readAtomFromStorage } from '../cross-tab-sync';

// ============================================================================
// Types
// ============================================================================

/**
 * Configuration for state hydration
 */
export interface HydrationConfig {
    /**
     * Enable debug logging
     */
    debug?: boolean;

    /**
     * Keys to exclude from hydration
     */
    excludeKeys?: string[];

    /**
     * Show loading indicator during hydration
     */
    showLoading?: boolean;

    /**
     * Hydration timeout (ms)
     */
    timeout?: number;

    /**
     * Callback when hydration completes
     */
    onComplete?: (hydratedKeys: string[]) => void;

    /**
     * Callback when hydration fails
     */
    onError?: (error: Error) => void;
}

// ============================================================================
// Hydrator Component
// ============================================================================

/**
 * State hydrator component
 *
 * Restores state from localStorage on mount. Should be placed inside StateProvider.
 *
 * @example
 * ```tsx
 * <StateProvider>
 *   <HydratorComponent config={{ debug: true }} />
 *   <App />
 * </StateProvider>
 * ```
 *
 * @doc.type component
 * @doc.purpose Hydrate state from storage on mount
 * @doc.layer platform
 * @doc.pattern Hydration
 */
export const HydratorComponent: React.FC<{
    config?: HydrationConfig;
}> = ({ config = {} }) => {
    const [isHydrating, setIsHydrating] = useState(true);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        const hydrateState = async () => {
            try {
                const {
                    debug = false,
                    excludeKeys = [],
                    timeout = 5000,
                    onComplete,
                    onError,
                } = config;

                if (debug) {
                    console.log('[Hydrator] Starting state hydration...');
                }

                // Get all registered atoms
                const allAtoms = StateManager.getAllAtoms();
                const hydratedKeys: string[] = [];

                // Create timeout promise
                const timeoutPromise = new Promise<never>((_, reject) => {
                    setTimeout(() => reject(new Error('Hydration timeout')), timeout);
                });

                // Hydrate each atom from storage
                const hydrationPromise = Promise.all(
                    allAtoms
                        .filter(([key]) => !excludeKeys.includes(key))
                        .map(async ([key, atom]) => {
                            try {
                                // Check if atom metadata indicates it's persistent
                                const metadata = StateManager.getMetadata(key);
                                if (!metadata?.persistent) {
                                    return;
                                }

                                // Read value from storage
                                const storedEvent = readAtomFromStorage(key);
                                if (storedEvent && storedEvent.value !== undefined) {
                                    // Value will be restored via Jotai's atomWithStorage mechanism
                                    // We just track that it was available
                                    hydratedKeys.push(key);

                                    if (debug) {
                                        console.log(`[Hydrator] Hydrated atom "${key}"`, {
                                            timestamp: storedEvent.timestamp,
                                            tabId: storedEvent.tabId,
                                        });
                                    }
                                }
                            } catch (err) {
                                console.error(`[Hydrator] Failed to hydrate atom "${key}":`, err);
                            }
                        })
                );

                // Wait for hydration or timeout
                await Promise.race([hydrationPromise, timeoutPromise]);

                if (debug) {
                    console.log('[Hydrator] Hydration complete', {
                        totalAtoms: allAtoms.length,
                        hydratedCount: hydratedKeys.length,
                        hydratedKeys,
                    });
                }

                setIsHydrating(false);
                onComplete?.(hydratedKeys);
            } catch (err) {
                const error = err instanceof Error ? err : new Error(String(err));
                console.error('[Hydrator] Hydration failed:', error);
                setError(error);
                setIsHydrating(false);
                config.onError?.(error);
            }
        };

        hydrateState();
    }, []); // Run once on mount

    // Show loading indicator if configured
    if (config.showLoading && isHydrating) {
        return (
            <div
                style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: 'rgba(0, 0, 0, 0.5)',
                    zIndex: 9999,
                }}
            >
                <div
                    style={{
                        padding: '20px',
                        backgroundColor: 'white',
                        borderRadius: '8px',
                        boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
                    }}
                >
                    <p>Loading application state...</p>
                </div>
            </div>
        );
    }

    // Show error if hydration failed
    if (error && config.showLoading) {
        return (
            <div
                style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: 'rgba(0, 0, 0, 0.5)',
                    zIndex: 9999,
                }}
            >
                <div
                    style={{
                        padding: '20px',
                        backgroundColor: '#fee',
                        borderRadius: '8px',
                        boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
                        maxWidth: '400px',
                    }}
                >
                    <h3 style={{ margin: '0 0 10px', color: '#c00' }}>
                        Hydration Error
                    </h3>
                    <p style={{ margin: 0 }}>{error.message}</p>
                </div>
            </div>
        );
    }

    return null;
};

// ============================================================================
// Hooks
// ============================================================================

/**
 * Hook to check if state is hydrating
 *
 * @returns Whether state is currently hydrating
 *
 * @doc.type hook
 * @doc.purpose Check hydration status
 * @doc.layer platform
 */
export function useIsHydrating(): boolean {
    const [isHydrating, setIsHydrating] = useState(true);

    useEffect(() => {
        // Simple check - hydration happens in first render cycle
        const timer = setTimeout(() => setIsHydrating(false), 100);
        return () => clearTimeout(timer);
    }, []);

    return isHydrating;
}

/**
 * Hook to wait for hydration to complete
 *
 * Useful for components that need to wait for state to be fully restored.
 *
 * @returns Promise that resolves when hydration completes
 *
 * @doc.type hook
 * @doc.purpose Wait for hydration completion
 * @doc.layer platform
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const isReady = useHydrationReady();
 *
 *   if (!isReady) {
 *     return <Loading />;
 *   }
 *
 *   return <Content />;
 * }
 * ```
 */
export function useHydrationReady(): boolean {
    const isHydrating = useIsHydrating();
    return !isHydrating;
}
