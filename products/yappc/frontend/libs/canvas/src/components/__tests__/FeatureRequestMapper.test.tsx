/**
 * FeatureRequestMapper Tests (Journey 24.1)
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { FeatureRequestMapper } from '../FeatureRequestMapper';

describe('FeatureRequestMapper', () => {
    const mockRequests = [
        {
            id: '1',
            description: 'Dark mode support',
            count: 45,
            category: 'UI/UX',
            impact: 'high' as const,
            affectedScreens: ['Dashboard', 'Settings'],
        },
    ];

    it('should render feature request mapper', () => {
        render(<FeatureRequestMapper />);
        expect(screen.getByText('Feature Request Mapper')).toBeInTheDocument();
    });

    it('should show upload CSV button', () => {
        render(<FeatureRequestMapper />);
        expect(screen.getByRole('button', { name: /upload csv/i })).toBeInTheDocument();
    });

    it('should show AI cluster button', () => {
        render(<FeatureRequestMapper />);
        expect(screen.getByRole('button', { name: /ai cluster/i })).toBeInTheDocument();
    });

    it('should display feature requests', () => {
        render(<FeatureRequestMapper requests={mockRequests} />);
        expect(screen.getByText('Dark mode support')).toBeInTheDocument();
        expect(screen.getByText('45 requests')).toBeInTheDocument();
    });

    it('should show impact levels', () => {
        render(<FeatureRequestMapper requests={mockRequests} />);
        expect(screen.getByText('high')).toBeInTheDocument();
    });

    it('should show affected screens', () => {
        render(<FeatureRequestMapper requests={mockRequests} />);
        expect(screen.getByText('Dashboard')).toBeInTheDocument();
        expect(screen.getByText('Settings')).toBeInTheDocument();
    });

    it('should show empty state', () => {
        render(<FeatureRequestMapper requests={[]} />);
        expect(screen.getByText(/no feature requests yet/i)).toBeInTheDocument();
    });

    it('should call onClusterRequests', async () => {
        const onClusterRequests = vi.fn();
        const user = userEvent.setup();
        render(<FeatureRequestMapper requests={mockRequests} onClusterRequests={onClusterRequests} />);

        await user.click(screen.getByRole('button', { name: /ai cluster/i }));
        expect(onClusterRequests).toHaveBeenCalled();
    });

    it('should disable cluster button when no requests', () => {
        render(<FeatureRequestMapper requests={[]} />);
        expect(screen.getByRole('button', { name: /ai cluster/i })).toBeDisabled();
    });
});
