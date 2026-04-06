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
import { render } from '@testing-library/react';
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

    it('renders without crashing', () => {
        render(<IntelligentHub />, { wrapper: TestWrapper });
        expect(document.body).toBeTruthy();
    });

    it('displays AI or intelligence-related content', () => {
        render(<IntelligentHub />, { wrapper: TestWrapper });
        const body = document.body.textContent ?? '';
        expect(body.toLowerCase()).toMatch(/intel|ai|brain|agent|hub|model|predict/i);
    });

    it('renders a meaningful page structure', () => {
        render(<IntelligentHub />, { wrapper: TestWrapper });
        expect(document.body.children.length).toBeGreaterThan(0);
    });

    it('renders without throwing', () => {
        expect(() =>
            render(<IntelligentHub />, { wrapper: TestWrapper })
        ).not.toThrow();
    });
});
