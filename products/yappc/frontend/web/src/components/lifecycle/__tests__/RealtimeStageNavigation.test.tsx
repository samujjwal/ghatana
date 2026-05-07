import { fireEvent, render, screen } from '@testing-library/react';
import type React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RealtimeStageNavigation } from '../RealtimeStageNavigation';
import { useRealtimeLifecycle } from '../../../hooks/useRealtimeLifecycle';

vi.mock('../../../hooks/useRealtimeLifecycle', () => ({
    useRealtimeLifecycle: vi.fn(),
}));

vi.mock('../../ui', () => ({
    Button: ({
        children,
        className,
        ...props
    }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
        <button className={`gh-button ${className ?? ''}`} {...props}>
            {children}
        </button>
    ),
    StageNavigation: () => <div data-testid="stage-navigation" />,
}));

const mockedUseRealtimeLifecycle = vi.mocked(useRealtimeLifecycle);

describe('RealtimeStageNavigation', () => {
    const refresh = vi.fn();

    beforeEach(() => {
        refresh.mockReset();
        mockedUseRealtimeLifecycle.mockReturnValue({
            project: null,
            phases: [],
            isConnected: false,
            lastUpdate: null,
            error: 'Socket unavailable',
            refresh,
        });
    });

    it('uses design-system buttons for reconnect and dismiss actions', () => {
        render(<RealtimeStageNavigation projectId="project-1" />);

        const reconnectButton = screen.getByRole('button', { name: /reconnect/i });
        expect(reconnectButton).toHaveClass('gh-button');

        fireEvent.click(reconnectButton);
        expect(refresh).toHaveBeenCalledOnce();

        const dismissButton = screen.getByRole('button', { name: /dismiss realtime lifecycle error/i });
        expect(dismissButton).toHaveClass('gh-button');

        fireEvent.click(dismissButton);
        expect(screen.queryByText('Socket unavailable')).not.toBeInTheDocument();
    });
});
