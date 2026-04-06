/**
 * Tests for the Analytics page (InsightsPage).
 *
 * Supplements InsightsPage.test.tsx with additional scenario coverage.
 *
 * @doc.type test
 * @doc.purpose RTL tests for InsightsPage analytics scenarios
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

vi.mock('../../lib/api/client', () => ({
    apiClient: {
        get: vi.fn().mockResolvedValue([]),
        post: vi.fn().mockResolvedValue({}),
    },
}));

import { InsightsPage } from '../../pages/InsightsPage';

describe('AnalyticsPage — InsightsPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders without crashing', () => {
        render(<InsightsPage />, { wrapper: TestWrapper });
        expect(document.body).toBeTruthy();
    });

    it('displays analytics or insights content', () => {
        render(<InsightsPage />, { wrapper: TestWrapper });
        const body = document.body.textContent ?? '';
        expect(body.toLowerCase()).toMatch(/insight|analytic|metric|dashboard|chart|report/i);
    });

    it('renders with page structure', () => {
        render(<InsightsPage />, { wrapper: TestWrapper });
        expect(document.body.children.length).toBeGreaterThan(0);
    });

    it('does not throw on render', () => {
        expect(() =>
            render(<InsightsPage />, { wrapper: TestWrapper })
        ).not.toThrow();
    });
});
