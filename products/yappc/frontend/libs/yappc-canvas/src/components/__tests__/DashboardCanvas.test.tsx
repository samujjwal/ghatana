/**
 * DashboardCanvas Tests (Journey 22.1)
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { DashboardCanvas } from '../DashboardCanvas';

describe('DashboardCanvas', () => {
    const mockNodes = [
        {
            id: '1',
            type: 'datasource' as const,
            label: 'Sales Data',
            position: { x: 0, y: 0 },
            data: { query: 'SELECT * FROM sales' },
        },
        {
            id: '2',
            type: 'chart' as const,
            label: 'Revenue Chart',
            position: { x: 100, y: 100 },
            data: { chartType: 'bar' as const },
        },
    ];

    it('should render dashboard designer', () => {
        render(<DashboardCanvas />);
        expect(screen.getByText('Dashboard Designer')).toBeInTheDocument();
    });

    it('should display nodes', () => {
        render(<DashboardCanvas nodes={mockNodes} />);
        expect(screen.getByText('Sales Data')).toBeInTheDocument();
        expect(screen.getByText('Revenue Chart')).toBeInTheDocument();
    });

    it('should show add data source button', () => {
        render(<DashboardCanvas />);
        const dataSourceBtn = screen.getAllByRole('button')[1]; // First after title
        expect(dataSourceBtn).toBeInTheDocument();
    });

    it('should show live preview toggle', () => {
        render(<DashboardCanvas />);
        expect(screen.getByRole('button', { name: /preview/i })).toBeInTheDocument();
    });

    it('should show publish button', () => {
        render(<DashboardCanvas />);
        expect(screen.getByRole('button', { name: /publish/i })).toBeInTheDocument();
    });

    it('should call onPublish when publish clicked', async () => {
        const onPublish = vi.fn();
        const user = userEvent.setup();
        render(<DashboardCanvas onPublish={onPublish} />);

        await user.click(screen.getByRole('button', { name: /publish/i }));
        expect(onPublish).toHaveBeenCalled();
    });

    it('should toggle preview mode', async () => {
        const onTogglePreview = vi.fn();
        const user = userEvent.setup();
        render(<DashboardCanvas onTogglePreview={onTogglePreview} />);

        await user.click(screen.getByRole('button', { name: /preview/i }));
        expect(onTogglePreview).toHaveBeenCalled();
    });

    it('should show live preview state', () => {
        render(<DashboardCanvas livePreview={true} />);
        expect(screen.getByRole('button', { name: /live/i })).toBeInTheDocument();
    });

    it('should show empty state', () => {
        render(<DashboardCanvas nodes={[]} />);
        expect(screen.getByText(/no dashboard components yet/i)).toBeInTheDocument();
    });

    it('should display chart type chips', () => {
        render(<DashboardCanvas nodes={mockNodes} />);
        expect(screen.getByText('bar')).toBeInTheDocument();
    });

    it('should open add dialog', async () => {
        const user = userEvent.setup();
        render(<DashboardCanvas />);

        const addButtons = screen.getAllByRole('button');
        await user.click(addButtons[1]); // Data source button

        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
});
