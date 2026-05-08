import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CanvasPhaseNavigator } from '../CanvasPhaseNavigator';
import { LifecyclePhase } from '../../../types/lifecycle';
import { useLifecyclePhase } from '../../../hooks/useLifecyclePhase';

vi.mock('../../../hooks/useLifecyclePhase', () => ({
    useLifecyclePhase: vi.fn(),
}));

const mockedUseLifecyclePhase = vi.mocked(useLifecyclePhase);

describe('CanvasPhaseNavigator', () => {
    const navigateToPhase = vi.fn();

    beforeEach(() => {
        navigateToPhase.mockReset();
        mockedUseLifecyclePhase.mockReturnValue({
            currentPhase: LifecyclePhase.CONTEXT,
            projectPhase: null,
            navigateToPhase,
            canTransitionTo: () => true,
            currentLabel: 'Context',
            currentDescription: 'Capture requirements and operating context.',
            isPhase: (phase: LifecyclePhase) => phase === LifecyclePhase.CONTEXT,
            projectId: 'project-1',
            isLoading: false,
        });
    });

    it('renders phase items as shared UI buttons and preserves active phase semantics', () => {
        render(<CanvasPhaseNavigator />);

        const activeButton = screen.getByRole('button', { name: /context phase \(current\)/i });
        expect(activeButton).toHaveAttribute('aria-current', 'step');
        expect(activeButton).toHaveClass('inline-flex');
    });

    it('calls the local callback and lifecycle navigation on phase click', () => {
        const onPhaseClick = vi.fn();
        render(<CanvasPhaseNavigator onPhaseClick={onPhaseClick} />);

        fireEvent.click(screen.getByRole('button', { name: /plan phase/i }));

        expect(onPhaseClick).toHaveBeenCalledWith(LifecyclePhase.PLAN);
        expect(navigateToPhase).toHaveBeenCalledWith(LifecyclePhase.PLAN);
    });
});
