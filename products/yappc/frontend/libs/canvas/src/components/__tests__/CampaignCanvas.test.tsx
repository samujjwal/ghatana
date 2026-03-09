/**
 * CampaignCanvas Tests (Journey 25.1)
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { CampaignCanvas } from '../CampaignCanvas';

describe('CampaignCanvas', () => {
    const mockNodes = [
        {
            id: '1',
            channel: 'email' as const,
            label: 'Newsletter Campaign',
            kpis: {
                impressions: 10000,
                clicks: 1500,
                conversions: 250,
            },
        },
    ];

    it('should render campaign planning', () => {
        render(<CampaignCanvas />);
        expect(screen.getByText('Campaign Planning')).toBeInTheDocument();
    });

    it('should display campaign nodes', () => {
        render(<CampaignCanvas nodes={mockNodes} />);
        expect(screen.getByText('Newsletter Campaign')).toBeInTheDocument();
    });

    it('should show KPIs', () => {
        render(<CampaignCanvas nodes={mockNodes} />);
        expect(screen.getByText('10000 impressions')).toBeInTheDocument();
        expect(screen.getByText('1500 clicks')).toBeInTheDocument();
        expect(screen.getByText('250 conversions')).toBeInTheDocument();
    });

    it('should show channel type', () => {
        render(<CampaignCanvas nodes={mockNodes} />);
        expect(screen.getByText('email')).toBeInTheDocument();
    });

    it('should have add channel buttons', () => {
        render(<CampaignCanvas />);
        const buttons = screen.getAllByRole('button');
        expect(buttons.length).toBeGreaterThan(3); // Multiple channel add buttons
    });

    it('should show funnel toggle', () => {
        render(<CampaignCanvas />);
        expect(screen.getByRole('button', { name: /show funnel/i })).toBeInTheDocument();
    });

    it('should show hide funnel when active', () => {
        render(<CampaignCanvas showFunnel={true} />);
        expect(screen.getByRole('button', { name: /hide funnel/i })).toBeInTheDocument();
    });

    it('should show empty state', () => {
        render(<CampaignCanvas nodes={[]} />);
        expect(screen.getByText(/no campaign channels yet/i)).toBeInTheDocument();
    });

    it('should open add dialog', async () => {
        const user = userEvent.setup();
        render(<CampaignCanvas />);

        const addButtons = screen.getAllByRole('button');
        await user.click(addButtons[1]); // First channel add button

        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
});
