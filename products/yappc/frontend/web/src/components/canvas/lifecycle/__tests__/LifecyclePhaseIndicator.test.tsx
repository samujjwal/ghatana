/**
 * LifecyclePhaseIndicator unit tests
 *
 * Tests for all three exported components:
 * - LifecyclePhaseIndicator: chip + transition dialog
 * - CompactPhaseIndicator: small badge for toolbar
 * - PhaseProgressBar: full workflow progress overview
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import {
    LifecyclePhaseIndicator,
    CompactPhaseIndicator,
    PhaseProgressBar,
} from '../LifecyclePhaseIndicator';
import { LifecyclePhase } from '../../../../types/lifecycle';

// ─── LifecyclePhaseIndicator ─────────────────────────────────────────────────

describe('LifecyclePhaseIndicator', () => {
    const defaultProps = {
        currentPhase: LifecyclePhase.CONTEXT,
        onPhaseChange: vi.fn(),
    };

    describe('chip rendering', () => {
        it('renders the current phase label in the chip', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} />);
            expect(screen.getByText(LifecyclePhase.CONTEXT)).toBeInTheDocument();
        });

        it('renders chip for INTENT phase', () => {
            render(
                <LifecyclePhaseIndicator
                    currentPhase={LifecyclePhase.INTENT}
                    onPhaseChange={vi.fn()}
                />,
            );
            expect(screen.getByText(LifecyclePhase.INTENT)).toBeInTheDocument();
        });

        it('does not show dialog by default', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} />);
            expect(screen.queryByText('Change Lifecycle Phase')).not.toBeInTheDocument();
        });
    });

    describe('transition dialog', () => {
        it('opens dialog when chip is clicked', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} />);
            const chip = screen.getByText(LifecyclePhase.CONTEXT);
            fireEvent.click(chip);
            expect(screen.getByText('Change Lifecycle Phase')).toBeInTheDocument();
        });

        it('shows current phase in dialog body', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} />);
            fireEvent.click(screen.getByText(LifecyclePhase.CONTEXT));
            // The Typography renders "Current Phase:" and <strong>{currentPhase}</strong>
            // Use getAllByText to handle ancestor element matches
            const matches = screen.getAllByText(
                (_, el) =>
                    el?.textContent?.trim() === `Current Phase: ${LifecyclePhase.CONTEXT}` ?? false,
            );
            expect(matches.length).toBeGreaterThan(0);
        });

        it('shows current phase description in dialog', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} />);
            fireEvent.click(screen.getByText(LifecyclePhase.CONTEXT));
            // PHASE_DESCRIPTIONS[CONTEXT] = 'Capture requirements, architecture, and operating context.'
            expect(
                screen.getByText('Capture requirements, architecture, and operating context.'),
            ).toBeInTheDocument();
        });

        it('shows Transition to: label', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} />);
            fireEvent.click(screen.getByText(LifecyclePhase.CONTEXT));
            expect(screen.getByText('Transition to:')).toBeInTheDocument();
        });

        it('Cancel button closes dialog', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} />);
            fireEvent.click(screen.getByText(LifecyclePhase.CONTEXT));
            expect(screen.getByText('Change Lifecycle Phase')).toBeInTheDocument();
            fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
            expect(screen.queryByText('Change Lifecycle Phase')).not.toBeInTheDocument();
        });

        it('Change Phase button is disabled when no phase is selected (same as current)', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} />);
            fireEvent.click(screen.getByText(LifecyclePhase.CONTEXT));
            const changeButton = screen.getByRole('button', { name: 'Change Phase' });
            expect(changeButton).toBeDisabled();
        });
    });

    describe('disabled prop', () => {
        it('does not open dialog when disabled is true', () => {
            render(<LifecyclePhaseIndicator {...defaultProps} disabled />);
            // Chip has onClick=undefined when disabled, so clicking should not open dialog
            const chip = screen.getByText(LifecyclePhase.CONTEXT);
            fireEvent.click(chip);
            expect(screen.queryByText('Change Lifecycle Phase')).not.toBeInTheDocument();
        });
    });
});

// ─── CompactPhaseIndicator ───────────────────────────────────────────────────

describe('CompactPhaseIndicator', () => {
    it('renders the current phase label', () => {
        render(<CompactPhaseIndicator currentPhase={LifecyclePhase.PLAN} />);
        expect(screen.getByText(LifecyclePhase.PLAN)).toBeInTheDocument();
    });

    it('renders INTENT phase label', () => {
        render(<CompactPhaseIndicator currentPhase={LifecyclePhase.INTENT} />);
        expect(screen.getByText(LifecyclePhase.INTENT)).toBeInTheDocument();
    });

    it('renders OBSERVE phase label', () => {
        render(<CompactPhaseIndicator currentPhase={LifecyclePhase.OBSERVE} />);
        expect(screen.getByText(LifecyclePhase.OBSERVE)).toBeInTheDocument();
    });
});

// ─── PhaseProgressBar ────────────────────────────────────────────────────────

describe('PhaseProgressBar', () => {
    it('renders without crashing', () => {
        const { container } = render(
            <PhaseProgressBar currentPhase={LifecyclePhase.CONTEXT} />,
        );
        expect(container.firstChild).toBeTruthy();
    });

    it('renders the phase description caption for CONTEXT', () => {
        render(<PhaseProgressBar currentPhase={LifecyclePhase.CONTEXT} />);
        expect(
            screen.getByText('Capture requirements, architecture, and operating context.'),
        ).toBeInTheDocument();
    });

    it('renders the phase description caption for INTENT', () => {
        render(<PhaseProgressBar currentPhase={LifecyclePhase.INTENT} />);
        expect(
            screen.getByText('Define what should be built and why it matters.'),
        ).toBeInTheDocument();
    });

    it('renders progress segments (colored bar divs)', () => {
        const { container } = render(
            <PhaseProgressBar currentPhase={LifecyclePhase.PLAN} />,
        );
        // There should be multiple div segments rendered
        const bars = container.querySelectorAll('[class*="flex-1"]');
        expect(bars.length).toBeGreaterThan(0);
    });
});
