/**
 * Onboarding Tour Component Tests
 * @doc.type test
 * @doc.purpose Test onboarding tour UI and navigation
 * @doc.layer unit
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, userEvent } from '@testing-library/react';
import { OnboardingTour, defaultTourSteps } from './OnboardingTour';
import React from 'react';
import type { OnboardingTourProps, TourStep } from './OnboardingTour';

describe('OnboardingTour Component', () => {
    const defaultProps: OnboardingTourProps = {
        isOpen: true,
        onClose: vi.fn(),
        steps: defaultTourSteps,
    };

    describe('Rendering', () => {
        it('should render tour when open', () => {
            render(React.createElement(OnboardingTour, defaultProps));
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });

        it('should not render tour when closed', () => {
            render(React.createElement(OnboardingTour, { ...defaultProps, isOpen: false }));
            const dialog = screen.queryByRole('dialog');
            expect(dialog).not.toBeInTheDocument();
        });

        it('should display tour steps', () => {
            const steps: TourStep[] = [
                { id: '1', title: 'Welcome', description: 'Welcome to the app' },
                { id: '2', title: 'Features', description: 'Learn about features' },
            ];

            render(React.createElement(OnboardingTour, { ...defaultProps, steps }));

            expect(screen.getByText('Welcome')).toBeInTheDocument();
        });
    });

    describe('Navigation', () => {
        it('should navigate to next step', async () => {
            const user = userEvent.setup();
            const steps: TourStep[] = [
                { id: '1', title: 'Step 1', description: 'First step' },
                { id: '2', title: 'Step 2', description: 'Second step' },
            ];

            render(React.createElement(OnboardingTour, { ...defaultProps, steps }));

            const nextButton = screen.getByRole('button', { name: /next/i });
            await user.click(nextButton);

            expect(screen.getByText('Step 2')).toBeInTheDocument();
        });

        it('should navigate to previous step', async () => {
            const user = userEvent.setup();
            const steps: TourStep[] = [
                { id: '1', title: 'Step 1', description: 'First step' },
                { id: '2', title: 'Step 2', description: 'Second step' },
            ];

            const { rerender } = render(
                React.createElement(OnboardingTour, { ...defaultProps, steps })
            );

            // Move to second step
            await user.click(screen.getByRole('button', { name: /next/i }));

            const prevButton = screen.getByRole('button', { name: /previous/i });
            await user.click(prevButton);

            expect(screen.getByText('Step 1')).toBeInTheDocument();
        });

        it('should not show previous button on first step', () => {
            render(React.createElement(OnboardingTour, defaultProps));
            const prevButton = screen.queryByRole('button', { name: /previous/i });
            expect(prevButton).not.toBeInTheDocument();
        });

        it('should not show next button on last step', async () => {
            const steps: TourStep[] = [
                { id: '1', title: 'Only Step', description: 'Single step' },
            ];

            render(React.createElement(OnboardingTour, { ...defaultProps, steps }));
            const nextButton = screen.queryByRole('button', { name: /next/i });
            expect(nextButton).not.toBeInTheDocument();
        });
    });

    describe('Closing Tour', () => {
        it('should call onClose when close button clicked', async () => {
            const onClose = vi.fn();
            const user = userEvent.setup();

            render(React.createElement(OnboardingTour, { ...defaultProps, onClose }));

            const closeButton = screen.getByRole('button', { name: /close|skip/i });
            await user.click(closeButton);

            expect(onClose).toHaveBeenCalled();
        });

        it('should call onClose when tour completes', async () => {
            const onClose = vi.fn();
            const user = userEvent.setup();
            const steps: TourStep[] = [
                { id: '1', title: 'Step 1', description: 'First step' },
            ];

            render(React.createElement(OnboardingTour, { ...defaultProps, onClose, steps }));

            const finishButton = screen.getByRole('button', { name: /finish|done/i });
            await user.click(finishButton);

            expect(onClose).toHaveBeenCalled();
        });
    });

    describe('Accessibility', () => {
        it('should have proper ARIA labels', () => {
            render(React.createElement(OnboardingTour, defaultProps));
            expect(screen.getByRole('dialog')).toHaveAttribute('aria-label');
        });

        it('should be keyboard navigable', async () => {
            const user = userEvent.setup();
            const steps: TourStep[] = [
                { id: '1', title: 'Step 1', description: 'First step' },
                { id: '2', title: 'Step 2', description: 'Second step' },
            ];

            render(React.createElement(OnboardingTour, { ...defaultProps, steps }));

            // Tab to next button and press Enter
            await user.tab();
            await user.keyboard('{Enter}');

            expect(screen.getByText('Step 2')).toBeInTheDocument();
        });

        it('should support Escape key to close', async () => {
            const onClose = vi.fn();
            const user = userEvent.setup();

            render(React.createElement(OnboardingTour, { ...defaultProps, onClose }));

            await user.keyboard('{Escape}');

            expect(onClose).toHaveBeenCalled();
        });
    });

    describe('Default Tour Steps', () => {
        it('should have valid default steps', () => {
            expect(defaultTourSteps).toBeDefined();
            expect(Array.isArray(defaultTourSteps)).toBe(true);
            expect(defaultTourSteps.length).toBeGreaterThan(0);
        });

        it('should render with default steps', () => {
            render(React.createElement(OnboardingTour, defaultProps));
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
    });
});
