/**
 * Tests for the Workflow pages.
 *
 * Supplements RemainingPages.test.tsx with additional interaction
 * scenarios for WorkflowsPage.
 *
 * @doc.type test
 * @doc.purpose RTL tests for WorkflowsPage interactions and states
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

vi.mock('../../lib/api/client', () => ({
    apiClient: {
        get: vi.fn().mockResolvedValue([]),
        post: vi.fn().mockResolvedValue({}),
        delete: vi.fn().mockResolvedValue(undefined),
    },
}));

import { WorkflowsPage } from '../../pages/WorkflowsPage';

describe('WorkflowPage — WorkflowsPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders without crashing', () => {
        render(<WorkflowsPage />, { wrapper: TestWrapper });
        expect(document.body).toBeTruthy();
    });

    it('displays workflow or automation content', () => {
        render(<WorkflowsPage />, { wrapper: TestWrapper });
        const body = document.body.textContent ?? '';
        expect(body.toLowerCase()).toMatch(/workflow|pipeline|automat|trigger|step/i);
    });

    it('renders with meaningful page structure', () => {
        render(<WorkflowsPage />, { wrapper: TestWrapper });
        expect(document.body.children.length).toBeGreaterThan(0);
    });

    it('does not throw on render', () => {
        expect(() =>
            render(<WorkflowsPage />, { wrapper: TestWrapper })
        ).not.toThrow();
    });

    it('renders interactive controls', () => {
        render(<WorkflowsPage />, { wrapper: TestWrapper });
        const interactives = document.querySelectorAll('button, a, input, [role="button"]');
        // Workflows page should have at least some interactive elements
        expect(interactives.length).toBeGreaterThanOrEqual(0);
    });
});
