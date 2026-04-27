/**
 * LifecycleGuidance unit tests
 *
 * Tests the phase-specific guidance panel that shows titles, tips, and
 * next-step actions for each lifecycle phase.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import LifecycleGuidance from '../LifecycleGuidance';
import { LifecyclePhase } from '../../../../types/lifecycle';

function renderGuidance(
    phase: string = LifecyclePhase.CONTEXT,
    overrides: Record<string, unknown> = {},
) {
    return render(
        <LifecycleGuidance
            currentPhase={phase as typeof LifecyclePhase.CONTEXT}
            onPhaseTransition={vi.fn()}
            {...overrides}
        />,
    );
}

describe('LifecycleGuidance', () => {
    describe('guidance title per phase', () => {
        it('shows "Shape Your Architecture" for SHAPE (CONTEXT) phase', () => {
            renderGuidance(LifecyclePhase.SHAPE);
            expect(screen.getByText('Shape Your Architecture')).toBeInTheDocument();
        });

        it('shows "Express Your Intent" for INTENT phase', () => {
            renderGuidance(LifecyclePhase.INTENT);
            expect(screen.getByText('Express Your Intent')).toBeInTheDocument();
        });

        it('shows "Validate Design" for VALIDATE (PLAN) phase', () => {
            renderGuidance(LifecyclePhase.VALIDATE);
            expect(screen.getByText('Validate Design')).toBeInTheDocument();
        });

        it('shows "Generate Code" for GENERATE (EXECUTE) phase', () => {
            renderGuidance(LifecyclePhase.GENERATE);
            expect(screen.getByText('Generate Code')).toBeInTheDocument();
        });

        it('shows "Observe & Monitor" for OBSERVE phase', () => {
            renderGuidance(LifecyclePhase.OBSERVE);
            expect(screen.getByText('Observe & Monitor')).toBeInTheDocument();
        });

        it('shows "Iterate & Improve" for IMPROVE (LEARN) phase', () => {
            renderGuidance(LifecyclePhase.IMPROVE);
            expect(screen.getByText('Iterate & Improve')).toBeInTheDocument();
        });
    });

    describe('guidance description', () => {
        it('shows description for SHAPE phase', () => {
            renderGuidance(LifecyclePhase.SHAPE);
            expect(
                screen.getByText('Design your application components, flows, and connections.'),
            ).toBeInTheDocument();
        });

        it('shows description for INTENT phase', () => {
            renderGuidance(LifecyclePhase.INTENT);
            expect(
                screen.getByText(
                    'Start by describing what you want to build. Use the AI chat to express your ideas.',
                ),
            ).toBeInTheDocument();
        });
    });

    describe('tips section', () => {
        it('shows "Tips for Architecture" for SHAPE phase when showTips is true', () => {
            renderGuidance(LifecyclePhase.SHAPE, { showTips: true });
            // Tips for {guidance.title.split(' ').pop()} => "Tips for Architecture"
            expect(screen.getByText('Tips for Architecture')).toBeInTheDocument();
        });

        it('shows "Tips for Intent" for INTENT phase', () => {
            renderGuidance(LifecyclePhase.INTENT, { showTips: true });
            // "Express Your Intent" => last word = "Intent"
            expect(screen.getByText('Tips for Intent')).toBeInTheDocument();
        });

        it('does not show tips section when showTips is false', () => {
            renderGuidance(LifecyclePhase.SHAPE, { showTips: false });
            expect(screen.queryByText('Tips for Architecture')).not.toBeInTheDocument();
        });

        it('expands tips on click to show tip content', () => {
            renderGuidance(LifecyclePhase.SHAPE, { showTips: true });
            const tipsHeader = screen.getByText('Tips for Architecture');
            // Click to expand
            fireEvent.click(tipsHeader.closest('[class*="cursor-pointer"]') ?? tipsHeader);
            expect(
                screen.getByText('Drag components from the palette (Frontend, API, Database)'),
            ).toBeInTheDocument();
        });
    });

    describe('Next Step section', () => {
        it('shows "Next Step" heading', () => {
            renderGuidance(LifecyclePhase.SHAPE);
            expect(screen.getByText('Next Step')).toBeInTheDocument();
        });

        it('shows next step text for SHAPE phase', () => {
            renderGuidance(LifecyclePhase.SHAPE);
            expect(
                screen.getByText(
                    'When your design is complete, validate it for gaps and risks',
                ),
            ).toBeInTheDocument();
        });

        it('renders transition buttons for valid next phases', () => {
            renderGuidance(LifecyclePhase.SHAPE);
            // SHAPE=CONTEXT can transition to PLAN (VALIDATE=PLAN) and INTENT
            const buttons = screen.getAllByRole('button');
            // Should include at least one phase transition button
            expect(buttons.length).toBeGreaterThan(0);
        });

        it('calls onPhaseTransition when clicking a transition button', () => {
            const onPhaseTransition = vi.fn();
            renderGuidance(LifecyclePhase.INTENT, { onPhaseTransition });
            // INTENT can transition to CONTEXT — multiple buttons with CONTEXT label may exist
            // (transition buttons + workflow chips); click the first one
            const contextButtons = screen.getAllByRole('button', { name: LifecyclePhase.CONTEXT });
            fireEvent.click(contextButtons[0]!);
            expect(onPhaseTransition).toHaveBeenCalledWith(LifecyclePhase.CONTEXT);
        });
    });

    describe('Full Workflow section', () => {
        it('shows "Full Workflow" heading', () => {
            renderGuidance(LifecyclePhase.SHAPE);
            expect(screen.getByText('Full Workflow')).toBeInTheDocument();
        });
    });

    describe('welcome dialog for new SHAPE projects', () => {
        it('shows welcome dialog when in SHAPE phase with no elements', () => {
            renderGuidance(LifecyclePhase.SHAPE, { hasElements: false });
            expect(screen.getByText(/Welcome to/)).toBeInTheDocument();
            expect(screen.getByText('Got it!')).toBeInTheDocument();
        });

        it('does not show welcome dialog for INTENT phase', () => {
            renderGuidance(LifecyclePhase.INTENT);
            expect(screen.queryByText('Got it!')).not.toBeInTheDocument();
        });

        it('dismisses welcome dialog on Got it! click', () => {
            renderGuidance(LifecyclePhase.SHAPE, { hasElements: false });
            const gotItButton = screen.getByText('Got it!');
            fireEvent.click(gotItButton);
            expect(screen.queryByText('Got it!')).not.toBeInTheDocument();
        });

        it('does not show welcome dialog when hasElements is true', () => {
            renderGuidance(LifecyclePhase.SHAPE, { hasElements: true });
            expect(screen.queryByText('Got it!')).not.toBeInTheDocument();
        });
    });

    describe('projectName prop', () => {
        it('shows custom project name in welcome dialog', () => {
            renderGuidance(LifecyclePhase.SHAPE, {
                projectName: 'My Awesome App',
                hasElements: false,
            });
            expect(screen.getByText('Welcome to My Awesome App!')).toBeInTheDocument();
        });
    });
});
