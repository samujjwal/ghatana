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
import { render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

vi.mock('../../lib/api/client', () => ({
    apiClient: {
        get: vi.fn().mockResolvedValue([]),
        post: vi.fn().mockResolvedValue({}),
    },
}));

import { IntelligentHub } from '../../pages/IntelligentHub';

describe('BrainPage — IntelligentHub', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders the intelligent-hub shell with ask-anything and quick-action sections', () => {
        render(<IntelligentHub />, { wrapper: TestWrapper });

        expect(screen.getByPlaceholderText(/Ask anything/i)).toBeInTheDocument();
        expect(screen.getByText('Quick Actions')).toBeInTheDocument();
        expect(screen.getByText('Insights')).toBeInTheDocument();
        expect(screen.getByText('AI Recommendations')).toBeInTheDocument();
    });
});
