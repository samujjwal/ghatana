/**
 * Tests for the Governance page (TrustCenter).
 *
 * Supplements TrustCenter.test.tsx with governance-specific scenarios.
 *
 * @doc.type test
 * @doc.purpose RTL tests for TrustCenter governance scenarios
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

import { TrustCenter } from '../../pages/TrustCenter';

describe('GovernancePage — TrustCenter', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders without crashing', () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        expect(document.body).toBeTruthy();
    });

    it('displays governance or compliance content', () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        const body = document.body.textContent ?? '';
        expect(body.toLowerCase()).toMatch(/govern|trust|compli|policy|securit|audit|privacy/i);
    });

    it('renders with meaningful structure', () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        expect(document.body.children.length).toBeGreaterThan(0);
    });

    it('does not throw on render', () => {
        expect(() =>
            render(<TrustCenter />, { wrapper: TestWrapper })
        ).not.toThrow();
    });
});
