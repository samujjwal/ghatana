/**
 * Feature Discovery Component
 * 
 * Highlights new or important features to users with contextual tooltips
 * and guided tours. Uses feature flags to control visibility.
 * 
 * @doc.type component
 * @doc.purpose Feature discoverability and onboarding
 * @doc.layer components
 * @doc.pattern Onboarding Component
 */

import React, { useEffect, useState } from 'react';
import { useFeatureFlag } from '@ghatana/yappc-config/features/feature-flags';

interface FeatureHighlight {
    id: string;
    target: string;
    title: string;
    description: string;
    position: 'top' | 'bottom' | 'left' | 'right';
}

const FEATURE_HIGHLIGHTS: FeatureHighlight[] = [
    {
        id: 'command-palette',
        target: '[data-feature="command-palette"]',
        title: 'Command Palette',
        description: 'Press Cmd+K to quickly access any action',
        position: 'bottom',
    },
    {
        id: 'unified-header',
        target: '[data-feature="unified-header"]',
        title: 'Unified Navigation',
        description: 'All your workspaces and projects in one place',
        position: 'bottom',
    },
    {
        id: 'canvas-modes',
        target: '[data-feature="canvas-modes"]',
        title: 'Canvas Modes',
        description: 'Switch between Design, Code, and Preview modes',
        position: 'bottom',
    },
];

interface FeatureDiscoveryProps {
    /** Feature ID to highlight */
    featureId?: string;
    /** Whether to show the discovery overlay */
    isOpen?: boolean;
    /** Callback when discovery is dismissed */
    onDismiss?: () => void;
}

/**
 * Feature Discovery Tooltip
 * 
 * Shows a contextual tooltip for a specific feature.
 */
export function FeatureDiscoveryTooltip({
    featureId,
    isOpen = false,
    onDismiss,
}: FeatureDiscoveryProps) {
    const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
    const feature = FEATURE_HIGHLIGHTS.find(f => f.id === featureId);

    useEffect(() => {
        if (isOpen && feature) {
            const element = document.querySelector(feature.target);
            if (element) {
                const rect = element.getBoundingClientRect();
                setPosition({
                    top: rect.bottom + 8,
                    left: rect.left + rect.width / 2,
                });
            }
        }
    }, [isOpen, feature]);

    if (!isOpen || !feature || !position) return null;

    return (
        <div
            className="fixed z-50 max-w-xs p-4 bg-white rounded-lg shadow-lg border border-gray-200"
            style={{
                top: position.top,
                left: position.left,
                transform: 'translateX(-50%)',
            }}
            role="dialog"
            aria-label={`Feature: ${feature.title}`}
        >
            <div className="flex items-start justify-between gap-2">
                <div>
                    <h3 className="font-semibold text-gray-900">{feature.title}</h3>
                    <p className="text-sm text-gray-600 mt-1">{feature.description}</p>
                </div>
                <button
                    onClick={onDismiss}
                    className="text-gray-400 hover:text-gray-600"
                    aria-label="Dismiss"
                >
                    ×
                </button>
            </div>
            <div className="mt-3 flex justify-end">
                <button
                    onClick={onDismiss}
                    className="text-sm text-blue-600 hover:text-blue-700 font-medium"
                >
                    Got it
                </button>
            </div>
        </div>
    );
}

/**
 * Feature Discovery Provider
 * 
 * Manages feature discovery state and visibility.
 */
export function FeatureDiscoveryProvider({ children }: { children: React.ReactNode }) {
    const [activeFeature, setActiveFeature] = useState<string | null>(null);
    const [dismissedFeatures, setDismissedFeatures] = useState<string[]>(() => {
        if (typeof window !== 'undefined') {
            const stored = localStorage.getItem('yappc:dismissed-features');
            return stored ? JSON.parse(stored) : [];
        }
        return [];
    });

    const dismissFeature = (featureId: string) => {
        setDismissedFeatures(prev => {
            const updated = [...prev, featureId];
            localStorage.setItem('yappc:dismissed-features', JSON.stringify(updated));
            return updated;
        });
        setActiveFeature(null);
    };

    const showFeature = (featureId: string) => {
        if (!dismissedFeatures.includes(featureId)) {
            setActiveFeature(featureId);
        }
    };

    return (
        <FeatureDiscoveryContext.Provider
            value={{ activeFeature, dismissedFeatures, dismissFeature, showFeature }}
        >
            {children}
            <FeatureDiscoveryTooltip
                featureId={activeFeature || undefined}
                isOpen={!!activeFeature}
                onDismiss={() => activeFeature && dismissFeature(activeFeature)}
            />
        </FeatureDiscoveryContext.Provider>
    );
}

interface FeatureDiscoveryContextValue {
    activeFeature: string | null;
    dismissedFeatures: string[];
    dismissFeature: (featureId: string) => void;
    showFeature: (featureId: string) => void;
}

const FeatureDiscoveryContext = React.createContext<FeatureDiscoveryContextValue | undefined>(
    undefined
);

export function useFeatureDiscovery() {
    const context = React.useContext(FeatureDiscoveryContext);
    if (!context) {
        throw new Error('useFeatureDiscovery must be used within FeatureDiscoveryProvider');
    }
    return context;
}

/**
 * Feature Badge
 * 
 * Shows a "New" badge on features that haven't been discovered yet.
 */
export function FeatureBadge({ featureId, children }: { featureId: string; children: React.ReactNode }) {
    const { dismissedFeatures } = useFeatureDiscovery();
    const isNew = !dismissedFeatures.includes(featureId);

    return (
        <div className="relative inline-flex items-center">
            {children}
            {isNew && (
                <span className="absolute -top-1 -right-1 flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500"></span>
                </span>
            )}
        </div>
    );
}
