import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { LifecyclePhaseNavigator } from '../LifecyclePhaseNavigator';
import { LifecyclePhase } from '../../../types/lifecycle';
import { useLifecyclePhase } from '../../../hooks/useLifecyclePhase';

vi.mock('../../../hooks/useLifecyclePhase', () => ({
    useLifecyclePhase: vi.fn(),
}));

const mockedUseLifecyclePhase = vi.mocked(useLifecyclePhase);

describe('LifecyclePhaseNavigator', () => {
    const navigateToPhase = vi.fn();

    beforeEach(() => {
        navigateToPhase.mockReset();
        mockedUseLifecyclePhase.mockReturnValue({
            currentPhase: LifecyclePhase.SHAPE,
            projectPhase: null,
            navigateToPhase,
            canTransitionTo: (phase: LifecyclePhase) => (
                phase === LifecyclePhase.INTENT
                || phase === LifecyclePhase.SHAPE
                || phase === LifecyclePhase.VALIDATE
            ),
            currentLabel: 'Context',
            currentDescription: 'Shape the product context',
            isPhase: (phase: LifecyclePhase) => phase === LifecyclePhase.SHAPE,
            projectId: 'project-1',
            isLoading: false,
        });
    });

    it('renders lifecycle phases as accessible design-system buttons', () => {
        render(<LifecyclePhaseNavigator />);

        expect(screen.getByRole('navigation', { name: /lifecycle phase navigation/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /context phase \(current\)/i })).toHaveAttribute('aria-current', 'step');
        expect(screen.getByRole('button', { name: /execute phase \(not accessible\)/i })).toBeDisabled();
    });

    it('navigates only accessible phase buttons', () => {
        render(<LifecyclePhaseNavigator />);

        fireEvent.click(screen.getByRole('button', { name: /plan phase/i }));
        fireEvent.click(screen.getByRole('button', { name: /execute phase \(not accessible\)/i }));

        expect(navigateToPhase).toHaveBeenCalledOnce();
        expect(navigateToPhase).toHaveBeenCalledWith(LifecyclePhase.VALIDATE);
    });
});
