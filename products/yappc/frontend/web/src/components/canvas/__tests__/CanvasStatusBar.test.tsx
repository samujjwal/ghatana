import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { CanvasStatusBar, type PhaseInfo } from '../CanvasStatusBar';
import { LifecyclePhase } from '../../../types/lifecycle';

const phases: PhaseInfo[] = [
    { phase: LifecyclePhase.INTENT, progress: 100, status: 'completed' },
    { phase: LifecyclePhase.SHAPE, progress: 65, status: 'in-progress' },
    { phase: LifecyclePhase.VALIDATE, progress: 0, status: 'pending' },
    { phase: LifecyclePhase.GENERATE, progress: 0, status: 'pending' },
    { phase: LifecyclePhase.RUN, progress: 0, status: 'pending' },
    { phase: LifecyclePhase.OBSERVE, progress: 0, status: 'pending' },
    { phase: LifecyclePhase.IMPROVE, progress: 0, status: 'pending' },
];

describe('CanvasStatusBar', () => {
    it('renders compact phase dots as shared UI buttons with accessible labels', () => {
        render(
            <CanvasStatusBar
                phases={phases}
                currentPhase={LifecyclePhase.SHAPE}
                technologies={[]}
            />
        );

        const shapeDot = screen.getByRole('button', { name: /go to shape phase/i });

        expect(shapeDot).toHaveClass('inline-flex');
        expect(shapeDot).toHaveClass('min-h-0');
    });

    it('calls the phase navigation callback when a compact phase dot is selected', () => {
        const onPhaseClick = vi.fn();

        render(
            <CanvasStatusBar
                phases={phases}
                currentPhase={LifecyclePhase.SHAPE}
                technologies={[]}
                onPhaseClick={onPhaseClick}
            />
        );

        fireEvent.click(screen.getByRole('button', { name: /go to validate phase/i }));

        expect(onPhaseClick).toHaveBeenCalledWith(LifecyclePhase.VALIDATE);
    });
});
