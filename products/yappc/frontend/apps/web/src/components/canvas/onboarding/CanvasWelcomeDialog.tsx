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
} from '@ghatana/ui';
import { Step, StepLabel } from '@ghatana/ui';
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
        description: 'Create full-stack applications visually with AI assistance.',
        icon: LightbulbOutlined,
        tips: [
            'Design your application architecture on a visual canvas',
            'Generate production-ready code automatically',
            'Get AI-powered suggestions and validations',
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
        title: 'AI-Powered Features',
        description: 'Let AI help you build better applications faster.',
        icon: SmartToyOutlined,
        tips: [
            'Ask the AI assistant for architecture recommendations',
            'Get real-time validation feedback on your design',
            'Generate code with a single click',
            'AI suggests improvements based on best practices',
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
            size="md"
            fullWidth
            PaperProps={{
                sx: {
                    borderRadius: 3,
                    overflow: 'visible',
                },
            }}
        >
            {/* Close Button */}
            <IconButton
                onClick={handleSkip}
                className="absolute right-[8px] top-[8px] text-gray-500" aria-label="Close dialog"
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
                            tone="primary"
                            variant="outlined"
                        />
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            ~{steps.length - activeStep} min remaining
                        </Typography>
                    </Stack>

                    {/* Visual Stepper (Dots) */}
                    <Stepper
                        activeStep={activeStep}
                        alternativeLabel
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
                            className="rounded-full flex items-center justify-center w-[80px] h-[80px] text-blue-600 text-[40px] bg-blue-50" >
                            {currentStep.visual || <StepIcon className="text-[40px]" />}
                        </Box>

                        {/* Title */}
                        <Typography as="h5" fontWeight={600}>
                            {currentStep.title}
                        </Typography>

                        {/* Description */}
                        <Typography
                            as="p"
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
                                    className="p-3 rounded-lg text-left bg-gray-50 dark:bg-gray-800"
                                >
                                    <CheckCircleOutline
                                        className="mt-0.5 text-green-600 text-xl"
                                    />
                                    <Typography as="p" className="text-sm" color="text.primary">
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
                                <Typography as="p" className="text-sm" color="text.secondary">
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
                            className="text-gray-500 dark:text-gray-400"
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
                        as="span" className="text-xs text-gray-500"
                        color="text.tertiary"
                        className="block text-center mt-4"
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
