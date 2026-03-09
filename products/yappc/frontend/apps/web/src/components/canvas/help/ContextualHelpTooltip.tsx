/**
 * Contextual Help Tooltip Component
 * 
 * Smart floating hints that appear based on user context, current phase,
 * and actions. Provides just-in-time guidance without being intrusive.
 * 
 * Features:
 * - Phase-aware tips
 * - Action-triggered hints
 * - Dismissible with "don't show again" option
 * - Keyboard shortcut displays
 * - Automatic positioning
 * 
 * @doc.type component
 * @doc.purpose Contextual help and guidance
 * @doc.layer product
 * @doc.pattern Smart Tooltip Component
 */

import { useState, useEffect, useRef } from 'react';
import {
  Box,
  Typography,
  IconButton,
  Stack,
  Chip,
  Button,
  Surface as Paper,
} from '@ghatana/ui';
import { X as Close, Lightbulb, Keyboard as KeyboardOutlined, CheckCircle } from 'lucide-react';

import type { LifecyclePhase } from '../../../types/lifecycle';

// ============================================================================
// Types
// ============================================================================

export interface HelpTip {
    id: string;
    phase?: LifecyclePhase | LifecyclePhase[];
    title: string;
    message: string;
    shortcut?: string;
    action?: {
        label: string;
        onClick: () => void;
    };
    dismissible?: boolean;
    priority?: 'high' | 'medium' | 'low';
}

export interface ContextualHelpTooltipProps {
    tip: HelpTip;
    show: boolean;
    onDismiss: () => void;
    onDismissForever?: (tipId: string) => void;
    position?: 'top' | 'bottom' | 'left' | 'right' | 'center';
    anchor?: HTMLElement | null;
}

// ============================================================================
// Constants
// ============================================================================

const STORAGE_KEY_PREFIX = 'yappc:contextual-help-dismissed:';

// ============================================================================
// Main Component
// ============================================================================

export function ContextualHelpTooltip({
    tip,
    show,
    onDismiss,
    onDismissForever,
    position = 'bottom',
    anchor,
}: ContextualHelpTooltipProps) {
    const [coordinates, setCoordinates] = useState({ top: 0, left: 0 });
    const tooltipRef = useRef<HTMLDivElement>(null);

    // Calculate position based on anchor element
    useEffect(() => {
        if (!show || !anchor || !tooltipRef.current) return;

        const updatePosition = () => {
            const anchorRect = anchor.getBoundingClientRect();
            const tooltipRect = tooltipRef.current!.getBoundingClientRect();
            const viewportWidth = window.innerWidth;
            const viewportHeight = window.innerHeight;

            let top = 0;
            let left = 0;

            switch (position) {
                case 'bottom':
                    top = anchorRect.bottom + 8;
                    left = anchorRect.left + anchorRect.width / 2 - tooltipRect.width / 2;
                    break;
                case 'top':
                    top = anchorRect.top - tooltipRect.height - 8;
                    left = anchorRect.left + anchorRect.width / 2 - tooltipRect.width / 2;
                    break;
                case 'right':
                    top = anchorRect.top + anchorRect.height / 2 - tooltipRect.height / 2;
                    left = anchorRect.right + 8;
                    break;
                case 'left':
                    top = anchorRect.top + anchorRect.height / 2 - tooltipRect.height / 2;
                    left = anchorRect.left - tooltipRect.width - 8;
                    break;
                case 'center':
                    top = viewportHeight / 2 - tooltipRect.height / 2;
                    left = viewportWidth / 2 - tooltipRect.width / 2;
                    break;
            }

            // Boundary checks
            if (left + tooltipRect.width > viewportWidth - 16) {
                left = viewportWidth - tooltipRect.width - 16;
            }
            if (left < 16) {
                left = 16;
            }
            if (top + tooltipRect.height > viewportHeight - 16) {
                top = viewportHeight - tooltipRect.height - 16;
            }
            if (top < 16) {
                top = 16;
            }

            setCoordinates({ top, left });
        };

        updatePosition();
        window.addEventListener('resize', updatePosition);
        window.addEventListener('scroll', updatePosition);

        return () => {
            window.removeEventListener('resize', updatePosition);
            window.removeEventListener('scroll', updatePosition);
        };
    }, [show, anchor, position]);

    // Handle dismiss forever
    const handleDismissForever = () => {
        localStorage.setItem(`${STORAGE_KEY_PREFIX}${tip.id}`, 'true');
        onDismissForever?.(tip.id);
        onDismiss();
    };

    if (!show) return null;

    return (
        <Box
            ref={tooltipRef}
            className="fixed z-[10000] max-w-[400px]" style={{ top: coordinates.top, left: coordinates.left }}
        >
            <Paper
                elevation={8}
                className="p-4 rounded-lg relative bg-white dark:bg-gray-900 border-[2px_solid] border-blue-600"
            >
                {/* Close Button */}
                <IconButton
                    size="small"
                    onClick={onDismiss}
                    className="absolute top-[4px] right-[4px]"
                    aria-label="Close tip"
                >
                    <Close size={16} />
                </IconButton>

                <Stack spacing={1.5}>
                    {/* Header with Icon */}
                    <Stack direction="row" spacing={1} alignItems="center">
                        <Lightbulb
                            className="text-blue-600 text-xl"
                        />
                        <Typography
                            variant="subtitle2"
                            fontWeight={600}
                            className="pr-6"
                        >
                            {tip.title}
                        </Typography>
                    </Stack>

                    {/* Message */}
                    <Typography variant="body2" color="text.secondary">
                        {tip.message}
                    </Typography>

                    {/* Keyboard Shortcut */}
                    {tip.shortcut && (
                        <Stack direction="row" spacing={1} alignItems="center">
                            <KeyboardOutlined className="text-base text-gray-500" />
                            <Chip
                                label={tip.shortcut}
                                size="small"
                                variant="outlined"
                                className="text-xs font-mono"
                            />
                        </Stack>
                    )}

                    {/* Action Button */}
                    {tip.action && (
                        <Button
                            variant="contained"
                            size="small"
                            onClick={() => {
                                tip.action!.onClick();
                                onDismiss();
                            }}
                            startIcon={<CheckCircle />}
                            fullWidth
                        >
                            {tip.action.label}
                        </Button>
                    )}

                    {/* Dismiss Options */}
                    {tip.dismissible !== false && (
                        <Stack
                            direction="row"
                            spacing={1}
                            justifyContent="space-between"
                            className="pt-1"
                        >
                            <Button
                                size="small"
                                variant="text"
                                onClick={handleDismissForever}
                                className="text-xs normal-case text-gray-500" >
                                Don't show again
                            </Button>
                            <Button
                                size="small"
                                variant="text"
                                onClick={onDismiss}
                                className="text-xs normal-case"
                            >
                                Got it
                            </Button>
                        </Stack>
                    )}
                </Stack>
            </Paper>
        </Box>
    );
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Check if a tip has been dismissed forever
 */
export function isTipDismissed(tipId: string): boolean {
    return localStorage.getItem(`${STORAGE_KEY_PREFIX}${tipId}`) === 'true';
}

/**
 * Reset dismissed tips (for testing or user request)
 */
export function resetDismissedTips(): void {
    Object.keys(localStorage).forEach((key) => {
        if (key.startsWith(STORAGE_KEY_PREFIX)) {
            localStorage.removeItem(key);
        }
    });
}
