/**
 * Canvas Welcome Dialog Component
 * 
 * Interactive onboarding experience for first-time canvas users.
 * Features progressive disclosure with visual steps, quick tips,
 * and skip/never-show-again options.
 * 
 * @doc.type component
 * @doc.purpose First-time user onboarding
 * @doc.layer product
 * @doc.pattern Interactive Tutorial Component
 */

import { useState, useEffect } from 'react';
import { useTranslation } from '@ghatana/i18n';
import type { ReactNode } from 'react';
import {
  Dialog,
  DialogContent,
  Box,
  Typography,
  Button,
  IconButton,
  Stack,
  Stepper,
  Checkbox,
  FormControlLabel,
  Chip,
  LinearProgress,
} from '@ghatana/design-system';
import { Step, StepLabel } from '@ghatana/design-system';
import { X as Close, Lightbulb as LightbulbOutlined, MousePointer as MouseOutlined, Keyboard as KeyboardOutlined, Bot as SmartToyOutlined, CheckCircle as CheckCircleOutline } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface OnboardingStep {
    id: string;
    title: string;
    description: string;
    icon: React.ElementType;
    tips: string[];
    visual?: ReactNode;
}

export interface CanvasWelcomeDialogProps {
    /** Whether dialog is open */
    open: boolean;
    /** Callback when dialog closes */
    onClose: () => void;
    /** Callback when user completes onboarding */
    onComplete: () => void;
    /** Callback when user chooses to skip */
    onSkip?: () => void;
    /** Optional custom steps */
    steps?: OnboardingStep[];
}

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_STEPS: OnboardingStep[] = [
    {
        id: 'welcome',
        title: 'Welcome to YAPPC App Creator',
        description: 'Create full-stack applications visually with guided recommendations.',
        icon: LightbulbOutlined,
        tips: [
            'Design your application architecture on a visual canvas',
            'Generate reviewable code artifacts with guided checks',
            'Get contextual suggestions and validations',
            'Deploy directly to your cloud provider',
        ],
        visual: <LightbulbOutlined className="text-[40px]" />,
    },
    {
        id: 'canvas-basics',
        title: 'Canvas Basics',
        description: 'Learn how to add and connect components on the canvas.',
        icon: MouseOutlined,
        tips: [
            'Drag components from the left palette onto the canvas',
            'Click components to configure their properties',
            'Connect components by dragging from one node to another',
            'Use the mini-map to navigate large diagrams',
        ],
        visual: <MouseOutlined className="text-[40px]" />,
    },
    {
        id: 'keyboard-shortcuts',
        title: 'Keyboard Shortcuts',
        description: 'Speed up your workflow with these essential shortcuts.',
        icon: KeyboardOutlined,
        tips: [
            '⌘/Ctrl + K: Open command palette for quick actions',
            'Delete/Backspace: Remove selected elements',
            '⌘/Ctrl + Z: Undo last action',
            '⌘/Ctrl + S: Save your work',
        ],
        visual: <KeyboardOutlined className="text-[40px]" />,
    },
    {
        id: 'ai-assistant',
        title: 'Guided Intelligence Features',
        description: 'Use guided recommendations to build better applications faster.',
        icon: SmartToyOutlined,
        tips: [
            'Review architecture recommendations directly in context',
            'Get real-time validation feedback on your design',
            'Generate code with a single click',
            'Suggested improvements based on best practices',
        ],
        visual: <SmartToyOutlined className="text-[40px]" />,
    },
];

const STORAGE_KEY = 'yappc:canvas-onboarding-completed';
const NEVER_SHOW_KEY = 'yappc:canvas-onboarding-never-show';

// ============================================================================
// Main Component
// ============================================================================

export function CanvasWelcomeDialog({
    open,
    onClose,
    onComplete,
    onSkip,
    steps = DEFAULT_STEPS,
}: CanvasWelcomeDialogProps) {
    const { t } = useTranslation('common');
    const [activeStep, setActiveStep] = useState(0);
    const [neverShowAgain, setNeverShowAgain] = useState(false);

    const currentStep = steps[activeStep];
    const isLastStep = activeStep === steps.length - 1;
    const progress = ((activeStep + 1) / steps.length) * 100;

    // Handle next step
    const handleNext = () => {
        if (isLastStep) {
            handleComplete();
        } else {
            setActiveStep((prev) => prev + 1);
        }
    };

    // Handle previous step
    const handleBack = () => {
        setActiveStep((prev) => Math.max(0, prev - 1));
    };

    // Handle completion
    const handleComplete = () => {
        // Mark as completed
        localStorage.setItem(STORAGE_KEY, 'true');

        // Save never show preference
        if (neverShowAgain) {
            localStorage.setItem(NEVER_SHOW_KEY, 'true');
        }

        onComplete();
        onClose();
    };

    // Handle skip
    const handleSkip = () => {
        if (neverShowAgain) {
            localStorage.setItem(NEVER_SHOW_KEY, 'true');
        }

        onSkip?.();
        onClose();
    };

    // Keyboard navigation
    useEffect(() => {
        if (!open) return;

        const handleKeyPress = (e: KeyboardEvent) => {
            if (e.key === 'ArrowRight' && !isLastStep) {
                handleNext();
            } else if (e.key === 'ArrowLeft' && activeStep > 0) {
                handleBack();
            } else if (e.key === 'Enter' && isLastStep) {
                handleComplete();
            }
        };

        window.addEventListener('keydown', handleKeyPress);
        return () => window.removeEventListener('keydown', handleKeyPress);
    }, [open, activeStep, isLastStep]);

    const StepIcon = currentStep?.icon || LightbulbOutlined;

    return (
        <Dialog
            open={open}
            onClose={handleSkip}
        >
            {/* Close Button */}
            <IconButton
                onClick={handleSkip}
                className="absolute right-[8px] top-[8px] text-fg-muted" aria-label={t('canvasWelcome.closeDialog')}
            >
                <Close />
            </IconButton>

            <DialogContent className="p-0">
                {/* Progress Bar */}
                <LinearProgress
                    variant="determinate"
                    value={progress}
                    className="h-[4px]"
                />

                <Box className="p-8">
                    {/* Step Counter */}
                    <Stack direction="row" spacing={1} alignItems="center" className="mb-4">
                        <Chip
                            label={`Step ${activeStep + 1} of ${steps.length}`}
                            size="sm"
                            variant="outlined"
                        />
                        <Typography className="text-xs text-fg-muted" color="text.secondary">
                            ~{steps.length - activeStep} min remaining
                        </Typography>
                    </Stack>

                    {/* Visual Stepper (Dots) */}
                    <Stepper
                        activeStep={activeStep}
                        className="mb-8"
                    >
                        {steps.map((step) => (
                            <Step key={step.id}>
                                <StepLabel />
                            </Step>
                        ))}
                    </Stepper>

                    {/* Step Content */}
                    <Stack spacing={3} alignItems="center" className="text-center">
                        {/* Icon Circle */}
                        <Box
                            className="rounded-full flex items-center justify-center w-[80px] h-[80px] text-info-color text-[40px] bg-info-bg" >
                            {currentStep.visual || <StepIcon className="text-[40px]" />}
                        </Box>

                        {/* Title */}
                        <Typography variant="h5" fontWeight={600}>
                            {currentStep.title}
                        </Typography>

                        {/* Description */}
                        <Typography
                            color="text.secondary"
                            className="max-w-[480px]"
                        >
                            {currentStep.description}
                        </Typography>

                        {/* Tips List */}
                        <Stack
                            spacing={1.5}
                            className="mt-4 self-stretch max-w-[520px] mx-auto"
                        >
                            {currentStep.tips.map((tip, index) => (
                                <Stack
                                    key={index}
                                    direction="row"
                                    spacing={1.5}
                                    alignItems="flex-start"
                                    className="p-3 rounded-lg text-left bg-surface-muted dark:bg-surface"
                                >
                                    <CheckCircleOutline
                                        className="mt-0.5 text-success-color text-xl"
                                    />
                                    <Typography className="text-sm" color="text.primary">
                                        {tip}
                                    </Typography>
                                </Stack>
                            ))}
                        </Stack>
                    </Stack>

                    {/* Never Show Again Checkbox */}
                    <Box className="mt-8 flex justify-center">
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={neverShowAgain}
                                    onChange={(e) => setNeverShowAgain(e.target.checked)}
                                />
                            }
                            label={
                                <Typography className="text-sm" color="text.secondary">
                                    Don't show this again
                                </Typography>
                            }
                        />
                    </Box>

                    {/* Action Buttons */}
                    <Stack
                        direction="row"
                        spacing={2}
                        justifyContent="space-between"
                        className="mt-8"
                    >
                        {/* Left: Skip */}
                        <Button
                            variant="ghost"
                            onClick={handleSkip}
                            className="text-fg-muted dark:text-fg-muted"
                        >
                            Skip Tutorial
                        </Button>

                        {/* Right: Navigation */}
                        <Stack direction="row" spacing={1}>
                            {activeStep > 0 && (
                                <Button
                                    variant="outlined"
                                    onClick={handleBack}
                                >
                                    Back
                                </Button>
                            )}
                            <Button
                                variant="solid"
                                onClick={handleNext}
                                size="lg"
                            >
                                {isLastStep ? 'Get Started' : 'Next'}
                            </Button>
                        </Stack>
                    </Stack>

                    {/* Keyboard Hint */}
                    <Typography
                        className="mt-4 block text-center text-xs text-fg-muted"
                        color="text.tertiary"
                    >
                        Use arrow keys to navigate • Press Enter to continue
                    </Typography>
                </Box>
            </DialogContent>
        </Dialog>
    );
}

// ============================================================================
// Helper Hook
// ============================================================================

/**
 * Hook to determine if welcome dialog should be shown
 */
export function useShouldShowWelcome(): boolean {
    const [shouldShow, setShouldShow] = useState(false);

    useEffect(() => {
        const neverShow = localStorage.getItem(NEVER_SHOW_KEY) === 'true';
        const hasCompleted = localStorage.getItem(STORAGE_KEY) === 'true';

        // Show if user hasn't opted out and hasn't completed it
        setShouldShow(!neverShow && !hasCompleted);
    }, []);

    return shouldShow;
}

/**
 * Reset onboarding state (for testing or user request)
 */
export function resetOnboarding(): void {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(NEVER_SHOW_KEY);
}
