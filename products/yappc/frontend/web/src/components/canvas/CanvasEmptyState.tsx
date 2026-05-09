/**
 * Canvas Empty State Component
 * 
 * Reusable empty state UI for canvas with contextual messaging.
 * Supports custom icons, actions, and AI suggestions.
 * 
 * @doc.type component
 * @doc.purpose Reusable empty state for canvas
 * @doc.layer product
 * @doc.pattern Component
 */

import {
  Box,
  Typography,
  Button,
  Stack,
} from '@ghatana/design-system';
import { Sparkles as AutoAwesome, Plus as Add } from 'lucide-react';
import type { ReactNode } from 'react';

export interface CanvasEmptyStateProps {
    /** Primary message to display */
    message: string;

    /** Optional description */
    description?: string;

    /** Custom icon (defaults to AutoAwesome) */
    icon?: ReactNode;

    /** Primary action button */
    primaryAction?: {
        label: string;
        onClick: () => void;
        icon?: ReactNode;
    };

    /** Secondary action button */
    secondaryAction?: {
        label: string;
        onClick: () => void;
    };

    /** AI suggestion prompts */
    aiSuggestions?: string[];

    /** Show AI sparkle animation */
    showAiSparkle?: boolean;
}

/**
 * CanvasEmptyState - Contextual empty state for canvas
 * 
 * Displays when canvas has no content, with mode/level-appropriate messaging
 * and actions to help users get started.
 */
export const CanvasEmptyState = ({
    message,
    description,
    icon,
    primaryAction,
    secondaryAction,
    aiSuggestions,
    showAiSparkle = false,
}: CanvasEmptyStateProps) => {
    return (
        <Box
            className="flex flex-col items-center justify-center h-full w-full text-center p-8"
        >
            {/* Icon */}
            <Box
                className="mb-6 text-fg-muted dark:text-fg-muted opacity-[0.6] text-[64px]" style={{ animation: showAiSparkle ? 'pulse 2s ease-in-out infinite' : 'none' }}
            >
                {icon || <AutoAwesome className="text-[64px]" />}
            </Box>

            {/* Message */}
            <Typography
                variant="h5"
                className="font-semibold mb-2 text-fg dark:text-fg-muted"
            >
                {message}
            </Typography>

            {/* Description */}
            {description && (
                <Typography
                    variant="body1"
                    className="text-fg-muted dark:text-fg-muted max-w-[480px] mb-6"
                >
                    {description}
                </Typography>
            )}

            {/* Actions */}
            {(primaryAction || secondaryAction) && (
                <Stack direction="row" spacing={2} className="mb-6">
                    {primaryAction && (
                        <Button
                            variant="contained"
                            size="large"
                            startIcon={primaryAction.icon || <Add />}
                            onClick={primaryAction.onClick}
                            className="normal-case rounded-lg px-6" >
                            {primaryAction.label}
                        </Button>
                    )}

                    {secondaryAction && (
                        <Button
                            variant="outlined"
                            size="large"
                            onClick={secondaryAction.onClick}
                            className="normal-case rounded-lg px-6" >
                            {secondaryAction.label}
                        </Button>
                    )}
                </Stack>
            )}

            {/* AI Suggestions */}
            {aiSuggestions && aiSuggestions.length > 0 && (
                <Box
                    className="rounded-lg mt-4 p-4 border-['1px_solid_rgba(99] max-w-[560px]" style={{ backgroundColor: 'rgba(99' }} >
                    <Stack spacing={1} alignItems="flex-start">
                        <Typography
                            variant="caption"
                            className="font-semibold flex items-center gap-1 text-info-color"
                        >
                            <AutoAwesome className="text-base" />
                            Suggested Improvements
                        </Typography>
                        {aiSuggestions.map((suggestion, index) => (
                            <Button
                                key={index}
                                variant="text"
                                size="small"
                                className="normal-case justify-start text-fg-muted dark:text-fg-muted hover:text-info-color hover:bg-[rgba(99,_102,_241,_0.08)]"
                            >
                                {suggestion}
                            </Button>
                        ))}
                    </Stack>
                </Box>
            )}
        </Box>
    );
};

export default CanvasEmptyState;
