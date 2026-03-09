import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { WorkspaceOnboardingBanner } from '@/shared/components/WorkspaceOnboardingBanner';
import { getWorkspaceOnboarding } from '@/config/workspacePresets';

function renderWithRouter(ui: React.ReactNode) {
    return render(<MemoryRouter>{ui}</MemoryRouter>);
}

describe('WorkspaceOnboardingBanner', () => {
    const onboarding = getWorkspaceOnboarding('engineer')!;

    beforeEach(() => {
        window.localStorage.clear();
    });

    it('renders persona-specific onboarding content', () => {
        renderWithRouter(<WorkspaceOnboardingBanner personaId="engineer" />);

        expect(screen.getByText(onboarding.title)).toBeInTheDocument();
        expect(screen.getByText(onboarding.subtitle)).toBeInTheDocument();
        expect(screen.getByText(onboarding.primaryCtaLabel)).toBeInTheDocument();
    });

    it('persists dismissal state in localStorage', () => {
        renderWithRouter(<WorkspaceOnboardingBanner personaId="engineer" />);

        const dismissButton = screen.getByRole('button', { name: /dismiss/i });
        fireEvent.click(dismissButton);

        expect(screen.queryByText(onboarding.title)).not.toBeInTheDocument();
        expect(window.localStorage.getItem(onboarding.dismissalKey)).toBe('true');
    });

    it('does not render when already dismissed in localStorage', () => {
        window.localStorage.setItem(onboarding.dismissalKey, 'true');

        renderWithRouter(<WorkspaceOnboardingBanner personaId="engineer" />);

        expect(screen.queryByText(onboarding.title)).not.toBeInTheDocument();
    });
});
