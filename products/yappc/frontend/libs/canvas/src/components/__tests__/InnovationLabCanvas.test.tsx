/**
 * Tests for InnovationLabCanvas component (Journey 32.1)
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { InnovationLabCanvas, Prototype } from '../components/InnovationLabCanvas';

describe('InnovationLabCanvas', () => {
    const mockPrototypes: Prototype[] = [
        {
            id: '1',
            name: 'AI Chatbot',
            description: 'Customer service chatbot',
            status: 'testing',
            abTestResults: {
                variantA: 45,
                variantB: 55,
                winner: 'B',
            },
        },
        {
            id: '2',
            name: 'Dark Mode',
            description: 'Dark theme for app',
            status: 'validated',
            roi: {
                investment: 50000,
                expectedReturn: 150000,
                timeToReturn: 12,
            },
        },
    ];

    it('renders innovation lab title', () => {
        render(<InnovationLabCanvas prototypes={[]} />);
        expect(screen.getByText('Innovation Lab')).toBeInTheDocument();
    });

    it('renders new prototype button', () => {
        render(<InnovationLabCanvas prototypes={[]} />);
        expect(screen.getByText('New Prototype')).toBeInTheDocument();
    });

    it('displays prototype names', () => {
        render(<InnovationLabCanvas prototypes={mockPrototypes} />);

        expect(screen.getByText('AI Chatbot')).toBeInTheDocument();
        expect(screen.getByText('Dark Mode')).toBeInTheDocument();
    });

    it('displays prototype descriptions', () => {
        render(<InnovationLabCanvas prototypes={mockPrototypes} />);

        expect(screen.getByText('Customer service chatbot')).toBeInTheDocument();
        expect(screen.getByText('Dark theme for app')).toBeInTheDocument();
    });

    it('displays status chips', () => {
        render(<InnovationLabCanvas prototypes={mockPrototypes} />);

        expect(screen.getByText('testing')).toBeInTheDocument();
        expect(screen.getByText('validated')).toBeInTheDocument();
    });

    it('displays A/B test results', () => {
        render(<InnovationLabCanvas prototypes={mockPrototypes} />);

        expect(screen.getByText('A/B Test Results')).toBeInTheDocument();
        expect(screen.getByText('Variant A: 45%')).toBeInTheDocument();
        expect(screen.getByText('Variant B: 55%')).toBeInTheDocument();
        expect(screen.getByText('Winner: B')).toBeInTheDocument();
    });

    it('displays ROI calculator', () => {
        render(<InnovationLabCanvas prototypes={mockPrototypes} />);

        expect(screen.getByText('ROI Calculator')).toBeInTheDocument();
        expect(screen.getByText('Investment: $50,000')).toBeInTheDocument();
        expect(screen.getByText('Return: $150,000')).toBeInTheDocument();
        expect(screen.getByText('ROI: 200.0%')).toBeInTheDocument();
        expect(screen.getByText('12 months')).toBeInTheDocument();
    });

    it('renders run A/B test button', () => {
        render(<InnovationLabCanvas prototypes={mockPrototypes} />);

        const abTestButtons = screen.getAllByText('Run A/B Test');
        expect(abTestButtons.length).toBeGreaterThan(0);
    });

    it('calls onRunABTest when button is clicked', () => {
        const onRunABTest = vi.fn();
        render(<InnovationLabCanvas prototypes={mockPrototypes} onRunABTest={onRunABTest} />);

        const abTestButtons = screen.getAllByText('Run A/B Test');
        fireEvent.click(abTestButtons[0]);

        expect(onRunABTest).toHaveBeenCalledWith('1');
    });

    it('renders promote to roadmap button for validated prototypes', () => {
        render(<InnovationLabCanvas prototypes={mockPrototypes} />);

        expect(screen.getByText('Promote to Roadmap')).toBeInTheDocument();
    });

    it('calls onPromoteToRoadmap when button is clicked', () => {
        const onPromoteToRoadmap = vi.fn();
        render(<InnovationLabCanvas prototypes={mockPrototypes} onPromoteToRoadmap={onPromoteToRoadmap} />);

        const promoteButton = screen.getByText('Promote to Roadmap');
        fireEvent.click(promoteButton);

        expect(onPromoteToRoadmap).toHaveBeenCalledWith('2');
    });

    it('opens add prototype dialog on button click', async () => {
        render(<InnovationLabCanvas prototypes={[]} />);

        const newButton = screen.getByText('New Prototype');
        fireEvent.click(newButton);

        await waitFor(() => {
            expect(screen.getByText('New Prototype')).toBeInTheDocument();
        });
    });

    it('renders prototype name input in dialog', async () => {
        render(<InnovationLabCanvas prototypes={[]} />);

        const newButton = screen.getByText('New Prototype');
        fireEvent.click(newButton);

        await waitFor(() => {
            expect(screen.getByLabelText('Prototype Name')).toBeInTheDocument();
        });
    });

    it('renders description input in dialog', async () => {
        render(<InnovationLabCanvas prototypes={[]} />);

        const newButton = screen.getByText('New Prototype');
        fireEvent.click(newButton);

        await waitFor(() => {
            expect(screen.getByLabelText('Description')).toBeInTheDocument();
        });
    });

    it('calls onAddPrototype when creating a new prototype', async () => {
        const onAddPrototype = vi.fn();
        render(<InnovationLabCanvas prototypes={[]} onAddPrototype={onAddPrototype} />);

        const newButton = screen.getByText('New Prototype');
        fireEvent.click(newButton);

        await waitFor(() => {
            const nameInput = screen.getByLabelText('Prototype Name');
            fireEvent.change(nameInput, { target: { value: 'New Feature' } });
        });

        const createButton = screen.getByRole('button', { name: 'Create' });
        fireEvent.click(createButton);

        await waitFor(() => {
            expect(onAddPrototype).toHaveBeenCalledTimes(1);
            expect(onAddPrototype).toHaveBeenCalledWith(expect.objectContaining({
                name: 'New Feature',
                status: 'concept',
            }));
        });
    });

    it('calls onDeletePrototype when deleting a prototype', () => {
        const onDeletePrototype = vi.fn();
        render(<InnovationLabCanvas prototypes={mockPrototypes} onDeletePrototype={onDeletePrototype} />);

        const deleteButtons = screen.getAllByText('×');
        fireEvent.click(deleteButtons[0]);

        expect(onDeletePrototype).toHaveBeenCalledWith('1');
    });

    it('displays empty state when no prototypes', () => {
        render(<InnovationLabCanvas prototypes={[]} />);

        expect(screen.getByText('No prototypes yet')).toBeInTheDocument();
        expect(screen.getByText('Create prototypes to test new ideas and innovations')).toBeInTheDocument();
    });

    it('disables create button when name is empty', async () => {
        render(<InnovationLabCanvas prototypes={[]} />);

        const newButton = screen.getByText('New Prototype');
        fireEvent.click(newButton);

        await waitFor(() => {
            const createButton = screen.getByRole('button', { name: 'Create' });
            expect(createButton).toBeDisabled();
        });
    });

    it('closes dialog on cancel', async () => {
        render(<InnovationLabCanvas prototypes={[]} />);

        const newButton = screen.getByText('New Prototype');
        fireEvent.click(newButton);

        await waitFor(() => {
            expect(screen.getAllByText('New Prototype').length).toBeGreaterThan(1);
        });

        const cancelButton = screen.getByRole('button', { name: 'Cancel' });
        fireEvent.click(cancelButton);

        await waitFor(() => {
            // Only the button text should remain (not dialog title)
            const newPrototypeTexts = screen.getAllByText('New Prototype');
            expect(newPrototypeTexts.length).toBe(1);
        });
    });

    it('renders calculate ROI button', () => {
        render(<InnovationLabCanvas prototypes={mockPrototypes} />);

        const roiButtons = screen.getAllByText('Calculate ROI');
        expect(roiButtons.length).toBeGreaterThan(0);
    });
});
