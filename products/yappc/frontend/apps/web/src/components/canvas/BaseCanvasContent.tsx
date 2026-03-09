/**
 * Base Canvas Content Component
 * 
 * Abstract base component providing common functionality for all canvas content types.
 * Handles empty states, loading, errors, and provides reusable patterns.
 * 
 * @doc.type component
 * @doc.purpose Base component for canvas content
 * @doc.layer product
 * @doc.pattern Template Method
 */

import { Box } from '@ghatana/ui';
import { CanvasEmptyState } from './CanvasEmptyState';
import type { ReactNode } from 'react';
import { useAtomValue } from 'jotai';
import { canvasModeAtom, abstractionLevelAtom } from '../../state/atoms/toolbarAtom';
import { getCanvasState } from '../../config/canvas-states';

export interface BaseCanvasContentProps {
    /** Whether canvas has content */
    hasContent?: boolean;

    /** Loading state */
    isLoading?: boolean;

    /** Error state */
    error?: Error | null;

    /** Children to render when has content */
    children?: ReactNode;

    /** Override empty state props */
    emptyStateOverride?: Partial<React.ComponentProps<typeof CanvasEmptyState>>;
}

/**
 * BaseCanvasContent - Template for all canvas content components
 * 
 * Provides consistent structure for:
 * - Empty states with mode/level-aware messaging
 * - Loading states
 * - Error handling
 * - Content rendering
 */
export const BaseCanvasContent = ({
    hasContent = false,
    isLoading = false,
    error = null,
    children,
    emptyStateOverride,
}: BaseCanvasContentProps) => {
    const mode = useAtomValue(canvasModeAtom);
    const level = useAtomValue(abstractionLevelAtom);
    const state = getCanvasState(mode, level);

    // Loading state
    if (isLoading) {
        return (
            <Box
                className="flex items-center justify-center h-full w-full"
            >
                <CanvasEmptyState
                    message="Loading..."
                    icon={<Box className="animate-spin">⚙️</Box>}
                    showAiSparkle
                />
            </Box>
        );
    }

    // Error state
    if (error) {
        return (
            <Box
                className="flex items-center justify-center h-full w-full"
            >
                <CanvasEmptyState
                    message="Something went wrong"
                    description={error.message}
                    icon={<span>⚠️</span>}
                    primaryAction={{
                        label: 'Retry',
                        onClick: () => window.location.reload(),
                    }}
                />
            </Box>
        );
    }

    // Empty state - use configuration from registry
    if (!hasContent) {
        return (
            <CanvasEmptyState
                message={state.emptyStateMessage}
                description={`${state.canvasContent} • ${state.aiAssistant.name}`}
                aiSuggestions={state.useCases}
                showAiSparkle
                {...emptyStateOverride}
            />
        );
    }

    // Content state
    return (
        <Box
            className="h-full w-full overflow-auto"
        >
            {children}
        </Box>
    );
};

export default BaseCanvasContent;
