import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { PhaseProgressBar, type PhaseProgressInfo } from '../PhaseProgressBar';
import { LifecyclePhase } from '../../../types/lifecycle';

const phases: PhaseProgressInfo[] = [
    { phase: LifecyclePhase.INTENT, progress: 100, status: 'completed' },
    { phase: LifecyclePhase.SHAPE, progress: 55, status: 'in-progress' },
    { phase: LifecyclePhase.VALIDATE, progress: 0, status: 'pending' },
    { phase: LifecyclePhase.GENERATE, progress: 0, status: 'pending' },
    { phase: LifecyclePhase.RUN, progress: 0, status: 'pending' },
    { phase: LifecyclePhase.OBSERVE, progress: 0, status: 'pending' },
    { phase: LifecyclePhase.IMPROVE, progress: 0, status: 'pending' },
];

describe('PhaseProgressBar', () => {
    it('renders phase steps as shared UI buttons while retaining progress details', () => {
        render(
            <PhaseProgressBar
                phases={phases}
                currentPhase={LifecyclePhase.SHAPE}
            />
        );

        const shapeStep = screen.getByRole('button', { name: /go to shape phase/i });

        expect(shapeStep).toHaveClass('inline-flex');
        expect(shapeStep).toHaveClass('flex-col');
        expect(screen.getByText('55%')).toBeInTheDocument();
    });

    it('calls the phase navigation callback when a phase step is selected', () => {
        const onPhaseClick = vi.fn();

        render(
            <PhaseProgressBar
                phases={phases}
                currentPhase={LifecyclePhase.SHAPE}
                onPhaseClick={onPhaseClick}
            />
        );

        fireEvent.click(screen.getByRole('button', { name: /go to run phase/i }));

        expect(onPhaseClick).toHaveBeenCalledWith(LifecyclePhase.RUN);
    });
});
