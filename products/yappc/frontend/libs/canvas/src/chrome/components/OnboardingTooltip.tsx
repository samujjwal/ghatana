/**
 * OnboardingTooltip Component
 * 
 * Contextual tooltips for first-time user guidance
 * "Learning by doing" - appears after specific actions
 * 
 * Features:
 * - Appears 500ms after triggering action
 * - Dismissible (never shows twice)
 * - Stored in localStorage
 * - Non-blocking, skippable
 * - 5 total hints: frame, shape, connect, group, keyboard
 * 
 * @doc.type component
 * @doc.purpose First-time user onboarding
 * @doc.layer components
 */

import { X as CloseIcon } from 'lucide-react';
import {
  Box,
  Button,
  IconButton,
  Typography,
} from '@ghatana/ui';
import { useAtom } from 'jotai';
import React, { useEffect, useState } from 'react';

import { chromeShownHintsAtom, chromeOnboardingStepAtom } from '../state/chrome-atoms';
import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

const { SPACING, COLORS, TYPOGRAPHY, FONT_WEIGHT, RADIUS, SHADOWS, Z_INDEX } = CANVAS_TOKENS;

export interface OnboardingTooltipProps {
    /** Unique hint ID */
    hintId: string;

    /** Target element selector or position */
    target?: string | { x: number; y: number };

    /** Tooltip title */
    title: string;

    /** Tooltip message */
    message: string;

    /** Optional action button */
    action?: {
        label: string;
        onClick: () => void;
    };

    /** Show arrow pointing to target */
    showArrow?: boolean;

    /** Placement relative to target */
    placement?: 'top' | 'bottom' | 'left' | 'right';

    /** Show delay in ms */
    showDelay?: number;

    /** Force show even if already seen */
    forceShow?: boolean;
}

/**
 * Calculate tooltip position based on target
 */
function calculatePosition(
    target: string | { x: number; y: number } | undefined,
    placement: 'top' | 'bottom' | 'left' | 'right' = 'top'
): { x: number; y: number } | null {
    if (!target) return null;

    if (typeof target === 'string') {
        const element = document.querySelector(target);
        if (!element) return null;

        const rect = element.getBoundingClientRect();
        const offset = 16;

        switch (placement) {
            case 'top':
                return { x: rect.left + rect.width / 2, y: rect.top - offset };
            case 'bottom':
                return { x: rect.left + rect.width / 2, y: rect.bottom + offset };
            case 'left':
                return { x: rect.left - offset, y: rect.top + rect.height / 2 };
            case 'right':
                return { x: rect.right + offset, y: rect.top + rect.height / 2 };
        }
    }

    return target;
}

/**
 * OnboardingTooltip - Contextual tooltip for first-time guidance
 */
export function OnboardingTooltip({
    hintId,
    target,
    title,
    message,
    action,
    showArrow = true,
    placement = 'top',
    showDelay = 500,
    forceShow = false,
}: OnboardingTooltipProps) {
    const [shownHints, setShownHints] = useAtom(chromeShownHintsAtom);
    const [visible, setVisible] = useState(false);
    const [position, setPosition] = useState<{ x: number; y: number } | null>(null);

    // Check if hint has been shown
    const hasBeenShown = shownHints.has(hintId);

    // Update position when target changes
    useEffect(() => {
        if (!hasBeenShown || forceShow) {
            const timer = setTimeout(() => {
                const pos = calculatePosition(target, placement);
                if (pos) {
                    setPosition(pos);
                    setVisible(true);
                }
            }, showDelay);

            return () => clearTimeout(timer);
        }
    }, [target, placement, showDelay, hasBeenShown, forceShow]);

    const handleDismiss = () => {
        setVisible(false);

        // Mark as shown (persist in localStorage via atom)
        const newShownHints = new Set(shownHints);
        newShownHints.add(hintId);
        setShownHints(newShownHints);

        // Also persist in localStorage
        if (typeof window !== 'undefined') {
            const stored = localStorage.getItem('canvas.hints.shown') || '[]';
            const hints = JSON.parse(stored);
            if (!hints.includes(hintId)) {
                hints.push(hintId);
                localStorage.setItem('canvas.hints.shown', JSON.stringify(hints));
            }
        }
    };

    const handleAction = () => {
        action?.onClick();
        handleDismiss();
    };

    if (!visible || !position || (hasBeenShown && !forceShow)) {
        return null;
    }

    // Calculate transform based on placement
    const getTransform = () => {
        switch (placement) {
            case 'top':
                return 'translate(-50%, -100%)';
            case 'bottom':
                return 'translate(-50%, 0)';
            case 'left':
                return 'translate(-100%, -50%)';
            case 'right':
                return 'translate(0, -50%)';
            default:
                return 'translate(-50%, -100%)';
        }
    };

    // Calculate arrow position
    const getArrowStyles = () => {
        const arrowSize = 8;
        const arrowStyles: React.CSSProperties = {
            position: 'absolute',
            width: 0,
            height: 0,
            borderStyle: 'solid',
        };

        switch (placement) {
            case 'top':
                return {
                    ...arrowStyles,
                    bottom: -arrowSize,
                    left: '50%',
                    transform: 'translateX(-50%)',
                    borderWidth: `${arrowSize}px ${arrowSize}px 0 ${arrowSize}px`,
                    borderColor: `${COLORS.TEXT_PRIMARY} transparent transparent transparent`,
                };
            case 'bottom':
                return {
                    ...arrowStyles,
                    top: -arrowSize,
                    left: '50%',
                    transform: 'translateX(-50%)',
                    borderWidth: `0 ${arrowSize}px ${arrowSize}px ${arrowSize}px`,
                    borderColor: `transparent transparent ${COLORS.TEXT_PRIMARY} transparent`,
                };
            case 'left':
                return {
                    ...arrowStyles,
                    right: -arrowSize,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    borderWidth: `${arrowSize}px 0 ${arrowSize}px ${arrowSize}px`,
                    borderColor: `transparent transparent transparent ${COLORS.TEXT_PRIMARY}`,
                };
            case 'right':
                return {
                    ...arrowStyles,
                    left: -arrowSize,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    borderWidth: `${arrowSize}px ${arrowSize}px ${arrowSize}px 0`,
                    borderColor: `transparent ${COLORS.TEXT_PRIMARY} transparent transparent`,
                };
        }
    };

    return (
        <Box
            className="fixed max-w-[320px] pointer-events-auto" style={{ left: position.x, top: position.y, transform: getTransform(), zIndex: Z_INDEX.TOOLTIP, backgroundColor: COLORS.TEXT_PRIMARY, color: COLORS.TEXT_INVERSE, borderRadius: RADIUS.MD, boxShadow: SHADOWS.XL, padding: SPACING.MD }}
        >
            {/* Arrow */}
            {showArrow && <Box style={getArrowStyles()} />}

            {/* Close button */}
            <Box className="flex justify-between items-start mb-2" >
                <Typography
                    variant="subtitle1"
                    style={{
                        fontWeight: FONT_WEIGHT.SEMIBOLD,
                        fontSize: TYPOGRAPHY.SM,
                        paddingRight: SPACING.SM,
                    }}
                >
                    💡 {title}
                </Typography>
                <IconButton
                    onClick={handleDismiss}
                    size="small"
                    aria-label="Dismiss hint"
                    className="p-0 ml-auto hover:bg-[rgba(255,_255,_255,_0.1)]" style={{ color: COLORS.TEXT_INVERSE }}
                >
                    <CloseIcon size={16} />
                </IconButton>
            </Box>

            {/* Message */}
            <Typography
                variant="body2"
                className="leading-normal" style={{ fontSize: TYPOGRAPHY.SM, marginBottom: action ? SPACING.MD : 0 }}
            >
                {message}
            </Typography>

            {/* Action button */}
            {action && (
                <Box className="flex gap-2 mt-4" >
                    <Button
                        onClick={handleAction}
                        variant="contained"
                        size="small"
                        className="bg-[rgba(255,_255,_255,_0.2)] normal-case hover:bg-[rgba(255,_255,_255,_0.3)]" style={{ color: COLORS.TEXT_INVERSE, fontSize: TYPOGRAPHY.XS }}
                    >
                        {action.label}
                    </Button>
                    <Button
                        onClick={handleDismiss}
                        variant="text"
                        size="small"
                        className="normal-case opacity-[0.7] hover:opacity-100 hover:bg-[rgba(255,_255,_255,_0.1)]" style={{ color: COLORS.TEXT_INVERSE, fontSize: TYPOGRAPHY.XS }}
                    >
                        Got it
                    </Button>
                </Box>
            )}
        </Box>
    );
}

/**
 * Predefined onboarding hints
 */
export const ONBOARDING_HINTS = {
    FIRST_FRAME: {
        hintId: 'first-frame',
        title: 'Great! Your first frame',
        message: 'Double-click to add content, or drag shapes from the left rail',
        showDelay: 500,
    },
    FIRST_SHAPE: {
        hintId: 'first-shape',
        title: 'Nice! Now connect them',
        message: 'Click any edge dot or press C to create connectors between shapes',
        showDelay: 500,
    },
    FIRST_CONNECTION: {
        hintId: 'first-connection',
        title: 'Looking good!',
        message: 'Select multiple elements with Shift or drag, then press ⌘G to group',
        showDelay: 500,
    },
    FIRST_GROUP: {
        hintId: 'first-group',
        title: 'You\'re all set!',
        message: 'Press ⌘K anytime for quick commands, or ⌘/ to see all shortcuts',
        action: {
            label: 'Show shortcuts',
            onClick: () => {
                // Will be provided by the implementing component
            },
        },
        showDelay: 500,
    },
    KEYBOARD_SHORTCUTS: {
        hintId: 'keyboard-shortcuts',
        title: 'Pro tip: Keyboard shortcuts',
        message: 'Press ⌘/ to see all available shortcuts and become a power user',
        showDelay: 3000,
    },
} as const;

/**
 * Hook to manage onboarding flow
 */
export function useOnboardingFlow() {
    const [onboardingStep, setOnboardingStep] = useAtom(chromeOnboardingStepAtom);
    const [shownHints] = useAtom(chromeShownHintsAtom);

    // Load shown hints from localStorage on mount
    useEffect(() => {
        if (typeof window !== 'undefined') {
            const stored = localStorage.getItem('canvas.hints.shown');
            if (stored) {
                try {
                    const hints = JSON.parse(stored);
                    // Will be synced via atom
                } catch (error) {
                    console.warn('Failed to load shown hints:', error);
                }
            }
        }
    }, []);

    const markStepComplete = (step: number) => {
        setOnboardingStep(Math.max(onboardingStep, step + 1));
    };

    const resetOnboarding = () => {
        setOnboardingStep(0);
        if (typeof window !== 'undefined') {
            localStorage.removeItem('canvas.hints.shown');
        }
    };

    const hasSeenHint = (hintId: string) => {
        return shownHints.has(hintId);
    };

    return {
        onboardingStep,
        markStepComplete,
        resetOnboarding,
        hasSeenHint,
        isOnboardingComplete: onboardingStep >= 5,
    };
}
