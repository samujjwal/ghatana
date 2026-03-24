/**
 * Tests for BuildPipelineCanvas component (Journey 30.1)
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { BuildPipelineCanvas, BuildStep } from '../devsecops/BuildPipelineCanvas';

describe('BuildPipelineCanvas', () => {
    const mockSteps: BuildStep[] = [
        {
            id: '1',
            name: 'Install Dependencies',
            duration: 120,
            parallel: false,
            cacheEnabled: true,
            cacheStrategy: 'npm',
        },
        {
            id: '2',
            name: 'Run Tests',
            duration: 180,
            parallel: true,
            cacheEnabled: false,
        },
    ];

    it('renders build pipeline title', () => {
        render(<BuildPipelineCanvas steps={[]} />);
        expect(screen.getByText('Build Pipeline Optimization')).toBeInTheDocument();
    });

    it('renders add step button', () => {
        render(<BuildPipelineCanvas steps={[]} />);
        expect(screen.getByText('Add Step')).toBeInTheDocument();
    });

    it('displays build steps', () => {
        render(<BuildPipelineCanvas steps={mockSteps} />);

        expect(screen.getByText('Install Dependencies')).toBeInTheDocument();
        expect(screen.getByText('Run Tests')).toBeInTheDocument();
    });

    it('formats duration correctly', () => {
        render(<BuildPipelineCanvas steps={mockSteps} />);

        expect(screen.getByText('2m 0s')).toBeInTheDocument();
        expect(screen.getByText('3m 0s')).toBeInTheDocument();
    });

    it('displays parallel badge for parallel steps', () => {
        render(<BuildPipelineCanvas steps={mockSteps} />);

        const parallelChips = screen.getAllByText('Parallel');
        expect(parallelChips.length).toBe(2); // one in step, one in summary
    });

    it('displays cache strategy', () => {
        render(<BuildPipelineCanvas steps={mockSteps} />);
        expect(screen.getByText('npm')).toBeInTheDocument();
    });

    it('displays total duration', () => {
        render(<BuildPipelineCanvas steps={mockSteps} totalDuration={300} />);
        expect(screen.getByText('Total: 5m 0s')).toBeInTheDocument();
    });

    it('displays parallelizable badge when enabled', () => {
        render(<BuildPipelineCanvas steps={[]} parallelizable={true} />);

        const parallelChips = screen.getAllByText('Parallelizable');
        expect(parallelChips.length).toBeGreaterThan(0);
    });

    it('opens add step dialog on button click', async () => {
        render(<BuildPipelineCanvas steps={[]} />);

        const addButton = screen.getByText('Add Step');
        fireEvent.click(addButton);

        await waitFor(() => {
            expect(screen.getByText('Add Build Step')).toBeInTheDocument();
        });
    });

    it('renders step name input in dialog', async () => {
        render(<BuildPipelineCanvas steps={[]} />);

        const addButton = screen.getByText('Add Step');
        fireEvent.click(addButton);

        await waitFor(() => {
            expect(screen.getByLabelText('Step Name')).toBeInTheDocument();
        });
    });

    it('renders duration input in dialog', async () => {
        render(<BuildPipelineCanvas steps={[]} />);

        const addButton = screen.getByText('Add Step');
        fireEvent.click(addButton);

        await waitFor(() => {
            expect(screen.getByLabelText('Duration (seconds)')).toBeInTheDocument();
        });
    });

    it('renders cache strategy selector in dialog', async () => {
        render(<BuildPipelineCanvas steps={[]} />);

        const addButton = screen.getByText('Add Step');
        fireEvent.click(addButton);

        await waitFor(() => {
            expect(screen.getByLabelText('Cache Strategy')).toBeInTheDocument();
        });
    });

    it('calls onAddStep when adding a new step', async () => {
        const onAddStep = vi.fn();
        render(<BuildPipelineCanvas steps={[]} onAddStep={onAddStep} />);

        const addButton = screen.getByText('Add Step');
        fireEvent.click(addButton);

        await waitFor(() => {
            const nameInput = screen.getByLabelText('Step Name');
            fireEvent.change(nameInput, { target: { value: 'Build' } });
        });

        const confirmButton = screen.getByRole('button', { name: 'Add' });
        fireEvent.click(confirmButton);

        await waitFor(() => {
            expect(onAddStep).toHaveBeenCalledTimes(1);
            expect(onAddStep).toHaveBeenCalledWith(expect.objectContaining({
                name: 'Build',
            }));
        });
    });

    it('calls onDeleteStep when deleting a step', () => {
        const onDeleteStep = vi.fn();
        render(<BuildPipelineCanvas steps={mockSteps} onDeleteStep={onDeleteStep} />);

        const deleteButtons = screen.getAllByText('×');
        fireEvent.click(deleteButtons[0]);

        expect(onDeleteStep).toHaveBeenCalledWith('1');
    });

    it('displays empty state when no steps', () => {
        render(<BuildPipelineCanvas steps={[]} />);

        expect(screen.getByText('No build steps yet')).toBeInTheDocument();
        expect(screen.getByText('Add build steps to optimize your pipeline')).toBeInTheDocument();
    });

    it('disables add button when name is empty', async () => {
        render(<BuildPipelineCanvas steps={[]} />);

        const addButton = screen.getByText('Add Step');
        fireEvent.click(addButton);

        await waitFor(() => {
            const confirmButton = screen.getByRole('button', { name: 'Add' });
            expect(confirmButton).toBeDisabled();
        });
    });

    it('closes dialog on cancel', async () => {
        render(<BuildPipelineCanvas steps={[]} />);

        const addButton = screen.getByText('Add Step');
        fireEvent.click(addButton);

        await waitFor(() => {
            expect(screen.getByText('Add Build Step')).toBeInTheDocument();
        });

        const cancelButton = screen.getByRole('button', { name: 'Cancel' });
        fireEvent.click(cancelButton);

        await waitFor(() => {
            expect(screen.queryByText('Add Build Step')).not.toBeInTheDocument();
        });
    });
});
