/**
 * Contextual Help Manager
 * 
 * Manages the display of contextual help tips based on user actions,
 * current phase, canvas state, and usage patterns.
 * 
 * @doc.type component
 * @doc.purpose Contextual help orchestration
 * @doc.layer product
 * @doc.pattern Smart Manager Component
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { LifecyclePhase } from '../../../types/lifecycle';
import { ContextualHelpTooltip, isTipDismissed, type HelpTip } from './ContextualHelpTooltip';

// ============================================================================
// Types
// ============================================================================

export interface ContextualHelpManagerProps {
    currentPhase: LifecyclePhase;
    canvasElementCount: number;
    hasValidationErrors: boolean;
    aiSuggestionCount: number;
    isCanvasEmpty: boolean;
}

// ============================================================================
// Predefined Help Tips
// ============================================================================

const HELP_TIPS: Record<string, HelpTip> = {
    emptyCanvas: {
        id: 'empty-canvas',
        phase: [LifecyclePhase.INTENT, LifecyclePhase.SHAPE],
        title: 'Start Building Your Application',
        message: 'Drag components from the left palette onto the canvas, or use a template to get started quickly.',
        priority: 'high',
    },

    firstComponent: {
        id: 'first-component',
        phase: [LifecyclePhase.INTENT, LifecyclePhase.SHAPE],
        title: 'Great! You Added a Component',
        message: 'Click on any component to configure its properties. You can also connect components by dragging from one to another.',
        priority: 'high',
    },

    commandPalette: {
        id: 'command-palette',
        title: 'Quick Actions with Command Palette',
        message: 'Press ⌘K (Ctrl+K on Windows) to open the command palette for quick access to all features and actions.',
        shortcut: '⌘K',
        priority: 'medium',
    },

    aiAssistant: {
        id: 'ai-assistant',
        phase: [LifecyclePhase.INTENT, LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE],
        title: 'Get AI-Powered Suggestions',
        message: 'The AI assistant has analyzed your design. Click the AI badge in the toolbar to see recommendations and improvements.',
        priority: 'high',
    },

    validationErrors: {
        id: 'validation-errors',
        phase: [LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE],
        title: 'Design Issues Detected',
        message: 'Your design has some validation errors. Click the validation score in the toolbar to see details and suggested fixes.',
        priority: 'high',
    },

    phaseTransition: {
        id: 'phase-transition-validate',
        phase: [LifecyclePhase.SHAPE],
        title: 'Ready to Validate?',
        message: 'Your architecture looks good! Move to the Validate phase to check for issues and get AI recommendations before generating code.',
        priority: 'medium',
    },

    codeGeneration: {
        id: 'code-generation',
        phase: [LifecyclePhase.GENERATE],
        title: 'Generate Production Code',
        message: 'Your design is validated and ready! Click "Gen" in the toolbar to generate production-ready code for your entire application.',
        priority: 'high',
    },

    undoRedo: {
        id: 'undo-redo',
        title: 'Undo Your Last Change',
        message: 'Made a mistake? Use ⌘Z to undo and ⌘⇧Z to redo. Your entire history is preserved.',
        shortcut: '⌘Z / ⌘⇧Z',
        priority: 'low',
    },

    saveStatus: {
        id: 'save-status',
        title: 'Auto-Save Active',
        message: 'Your work is automatically saved every few seconds. You can see the save status in the center of the toolbar.',
        priority: 'low',
    },

    abstractionLevels: {
        id: 'abstraction-levels',
        phase: [LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE],
        title: 'Drill Down for Details',
        message: 'Working on a complex component? Use the Level dropdown to drill into implementation details or zoom out to see the big picture.',
        priority: 'medium',
    },
};

// ============================================================================
// Main Component
// ============================================================================

export function ContextualHelpManager({
    currentPhase,
    canvasElementCount,
    hasValidationErrors,
    aiSuggestionCount,
    isCanvasEmpty,
}: ContextualHelpManagerProps) {
    const [currentTip, setCurrentTip] = useState<HelpTip | null>(null);
    const [anchorElement, setAnchorElement] = useState<HTMLElement | null>(null);
    const [showTip, setShowTip] = useState(false);

    const previousElementCount = useRef(canvasElementCount);
    const previousSuggestionCount = useRef(aiSuggestionCount);
    const shownTipsSession = useRef<Set<string>>(new Set());
    const tipTimeoutRef = useRef<NodeJS.Timeout | undefined>(undefined);

    // Show tip with delay and anchor
    const displayTip = useCallback((tip: HelpTip, anchor?: HTMLElement | null, delay = 1000) => {
        // Check if already dismissed forever
        if (isTipDismissed(tip.id)) return;

        // Check if already shown this session
        if (shownTipsSession.current.has(tip.id)) return;

        // Clear any existing timeout
        if (tipTimeoutRef.current) {
            clearTimeout(tipTimeoutRef.current);
        }

        tipTimeoutRef.current = setTimeout(() => {
            setCurrentTip(tip);
            setAnchorElement(anchor || null);
            setShowTip(true);
            shownTipsSession.current.add(tip.id);
        }, delay);
    }, []);

    // Dismiss current tip
    const dismissTip = useCallback(() => {
        setShowTip(false);
        setTimeout(() => {
            setCurrentTip(null);
            setAnchorElement(null);
        }, 200);
    }, []);

    // Dismiss forever
    const dismissForever = useCallback((_tipId: string) => {
        dismissTip();
    }, [dismissTip]);

    // Rule 1: Empty canvas tip
    useEffect(() => {
        if (isCanvasEmpty && canvasElementCount === 0) {
            displayTip(HELP_TIPS.emptyCanvas, null, 2000);
        }
    }, [isCanvasEmpty, canvasElementCount, displayTip]);

    // Rule 2: First component added
    useEffect(() => {
        if (previousElementCount.current === 0 && canvasElementCount === 1) {
            displayTip(HELP_TIPS.firstComponent, null, 1500);
        }
        previousElementCount.current = canvasElementCount;
    }, [canvasElementCount, displayTip]);

    // Rule 3: AI suggestions available
    useEffect(() => {
        if (previousSuggestionCount.current === 0 && aiSuggestionCount > 0) {
            // Find AI badge button in toolbar
            const aiButton = document.querySelector('[title*="AI suggestion"]') as HTMLElement;
            displayTip(HELP_TIPS.aiAssistant, aiButton, 1000);
        }
        previousSuggestionCount.current = aiSuggestionCount;
    }, [aiSuggestionCount, displayTip]);

    // Rule 4: Validation errors detected
    useEffect(() => {
        if (hasValidationErrors && canvasElementCount > 2) {
            const validationButton = document.querySelector('[title*="Validation"]') as HTMLElement;
            displayTip(HELP_TIPS.validationErrors, validationButton, 2000);
        }
    }, [hasValidationErrors, canvasElementCount, displayTip]);

    // Rule 5: Phase-specific tips
    useEffect(() => {
        if (currentPhase === LifecyclePhase.SHAPE && canvasElementCount >= 5 && !hasValidationErrors) {
            displayTip(HELP_TIPS.phaseTransition, null, 3000);
        }

        if (currentPhase === LifecyclePhase.GENERATE && canvasElementCount > 0 && !hasValidationErrors) {
            const codeGenButton = document.querySelector('[title*="Generate"]') as HTMLElement;
            displayTip(HELP_TIPS.codeGeneration, codeGenButton, 2000);
        }
    }, [currentPhase, canvasElementCount, hasValidationErrors, displayTip]);

    // Rule 6: Command palette tip (show after 30 seconds on canvas)
    useEffect(() => {
        const timer = setTimeout(() => {
            displayTip(HELP_TIPS.commandPalette, null, 0);
        }, 30000);

        return () => clearTimeout(timer);
    }, [displayTip]);

    // Cleanup
    useEffect(() => {
        return () => {
            if (tipTimeoutRef.current) {
                clearTimeout(tipTimeoutRef.current);
            }
        };
    }, []);

    if (!currentTip) return null;

    return (
        <ContextualHelpTooltip
            tip={currentTip}
            show={showTip}
            onDismiss={dismissTip}
            onDismissForever={dismissForever}
            position={anchorElement ? 'bottom' : 'center'}
            anchor={anchorElement}
        />
    );
}
