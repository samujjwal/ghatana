/**
 * Unit tests for OnboardingChecklist component.
 * 
 * Covers: rendering, progress tracking, category expansion, step toggling,
 * localStorage persistence, and reset functionality.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import OnboardingChecklist from '../OnboardingChecklist';

// jsdom does not persist localStorage between tests — clear before each
beforeEach(() => {
    localStorage.clear();
});

describe('OnboardingChecklist', () => {
    it('renders the checklist heading', () => {
        render(<OnboardingChecklist />);
        expect(screen.getByText('Canvas Onboarding Checklist')).toBeTruthy();
    });

    it('shows "0 of N steps completed" on initial render', () => {
        render(<OnboardingChecklist />);
        expect(screen.getByText(/0 of \d+ steps completed/i)).toBeTruthy();
    });

    it('shows 0% progress on initial render', () => {
        render(<OnboardingChecklist />);
        expect(screen.getByText('0%')).toBeTruthy();
    });

    it('renders Reset button', () => {
        render(<OnboardingChecklist />);
        expect(screen.getByRole('button', { name: /reset/i })).toBeTruthy();
    });

    it('renders category section headings', () => {
        render(<OnboardingChecklist />);
        // Category labels as defined in the component's categories map
        expect(screen.getByText('Getting Started')).toBeTruthy();
    });

    it('toggles a step checkbox on click', () => {
        render(<OnboardingChecklist />);
        // Find all checkboxes; click the first one
        const checkboxes = screen.getAllByRole('checkbox');
        expect(checkboxes.length).toBeGreaterThan(0);
        fireEvent.click(checkboxes[0]);
        // After clicking, completed count should be 1
        expect(screen.getByText(/1 of \d+ steps completed/i)).toBeTruthy();
    });

    it('unchecks a step on second click', () => {
        render(<OnboardingChecklist />);
        const checkboxes = screen.getAllByRole('checkbox');
        fireEvent.click(checkboxes[0]); // check
        fireEvent.click(checkboxes[0]); // uncheck
        expect(screen.getByText(/0 of \d+ steps completed/i)).toBeTruthy();
    });

    it('updates progress percentage after checking a step', () => {
        render(<OnboardingChecklist />);
        const checkboxes = screen.getAllByRole('checkbox');
        fireEvent.click(checkboxes[0]);
        // 1/11 = ~9%
        const progressText = screen.getByText(/\d+%/);
        expect(progressText.textContent).not.toBe('0%');
    });

    it('shows congratulations when all steps completed', () => {
        render(<OnboardingChecklist />);
        const checkboxes = screen.getAllByRole('checkbox');
        checkboxes.forEach((cb) => fireEvent.click(cb));
        expect(screen.getByText(/congratulations/i)).toBeTruthy();
    });

    it('resets progress when Reset button is clicked', () => {
        render(<OnboardingChecklist />);
        // Check a step
        const checkboxes = screen.getAllByRole('checkbox');
        fireEvent.click(checkboxes[0]);
        expect(screen.getByText(/1 of \d+ steps completed/i)).toBeTruthy();
        // Reset
        fireEvent.click(screen.getByRole('button', { name: /reset/i }));
        expect(screen.getByText(/0 of \d+ steps completed/i)).toBeTruthy();
    });

    it('persists completed steps to localStorage', () => {
        render(<OnboardingChecklist />);
        const checkboxes = screen.getAllByRole('checkbox');
        fireEvent.click(checkboxes[0]);
        // localStorage should be updated
        const stored = localStorage.getItem('canvas-onboarding-completed');
        expect(stored).toBeTruthy();
    });

    it('restores completed steps from localStorage on mount', () => {
        // Pre-populate localStorage with one step completed
        localStorage.setItem('canvas-onboarding-completed', JSON.stringify(['install']));
        render(<OnboardingChecklist />);
        expect(screen.getByText(/1 of \d+ steps completed/i)).toBeTruthy();
    });

    it('shows "Install YAPPC Canvas" step text', () => {
        render(<OnboardingChecklist />);
        expect(screen.getByText('Install YAPPC Canvas')).toBeTruthy();
    });

    it('shows "Create Your First Canvas" step text', () => {
        render(<OnboardingChecklist />);
        expect(screen.getByText('Create Your First Canvas')).toBeTruthy();
    });
});
