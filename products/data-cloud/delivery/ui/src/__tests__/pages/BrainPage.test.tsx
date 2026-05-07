/**
 * Tests for the Brain / Intelligent Hub page.
 *
 * Supplements MiscPages.test.tsx with interaction scenarios for IntelligentHub.
 *
 * @doc.type test
 * @doc.purpose RTL tests for IntelligentHub (Brain page)
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import SessionBootstrap from '../../lib/auth/session';

const mockNavigate = vi.fn();

vi.mock('react-router', async (importOriginal) => {
    const actual = await importOriginal<typeof import('react-router')>();
    return {
        ...actual,
        useNavigate: () => mockNavigate,
    };
});

vi.mock('../../lib/api/client', () => ({
    apiClient: {
        get: vi.fn().mockImplementation((path: string) => {
            if (path === '/user-activity/recent') {
                return Promise.resolve({ activities: [], continueWorking: [] });
            }
            return Promise.resolve([]);
        }),
        post: vi.fn().mockResolvedValue({}),
    },
}));

import { IntelligentHub } from '../../pages/IntelligentHub';

describe('BrainPage — IntelligentHub', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockNavigate.mockReset();
        SessionBootstrap.setShellRole('primary-user');
    });

    it('renders the intelligent-hub shell with outcome launcher and unified intent input', () => {
        render(<IntelligentHub />, { wrapper: TestWrapper });

        expect(screen.getByPlaceholderText(/What do you need to do/i)).toBeInTheDocument();
        expect(screen.getByText('Next action')).toBeInTheDocument();
        expect(screen.getByText('Ask a question')).toBeInTheDocument();
        expect(screen.getByText('Build an automated flow')).toBeInTheDocument();
        expect(screen.getByText('Platform snapshot')).toBeInTheDocument();
    });

    it('keeps operator-only recommendations out of the primary-user launcher mode', () => {
        SessionBootstrap.setShellRole('primary-user');

        render(<IntelligentHub />, { wrapper: TestWrapper });

        expect(screen.queryByText('Review trust issues')).not.toBeInTheDocument();
        expect(screen.queryByText('Inspect recent failures')).not.toBeInTheDocument();
        expect(screen.getByText(/Query your data or build a pipeline/i)).toBeInTheDocument();
    });

    it('reveals operator-only launcher actions when the shell role is elevated', () => {
        SessionBootstrap.setShellRole('operator');

        render(<IntelligentHub />, { wrapper: TestWrapper });

        fireEvent.click(screen.getByRole('button', { name: /Operator/i }));

        expect(screen.getByText('Review trust issues')).toBeInTheDocument();
        expect(screen.getByText('Inspect recent failures')).toBeInTheDocument();
    });

    it('routes unified intent to the workflow launcher when the request is automation-focused', async () => {
        render(<IntelligentHub />, { wrapper: TestWrapper });

        fireEvent.change(screen.getByPlaceholderText(/What do you need to do/i), {
            target: { value: 'Create a pipeline to sync orders into analytics' },
        });
        fireEvent.click(screen.getByRole('button', { name: /^send$/i }));

        await waitFor(() => {
            expect(mockNavigate).toHaveBeenCalledWith('/pipelines/new', {
                state: { intent: 'Create a pipeline to sync orders into analytics' },
            });
        });
    });
});
