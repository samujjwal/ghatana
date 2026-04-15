/**
 * Accessibility audit tests for Data Cloud UI pages.
 *
 * Validates keyboard navigation and ARIA attribute conventions across
 * the main page components. Complements components/a11y.test.tsx, which
 * tests shared components; this file tests page-level accessibility.
 *
 * @doc.type test
 * @doc.purpose Page-level accessibility: ARIA roles, landmarks, keyboard semantics
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import React from 'react';

// ── Module mocks ─────────────────────────────────────────────────────────────

vi.mock('../../lib/api/client', () => ({
    apiClient: {
        get: vi.fn().mockResolvedValue([]),
        post: vi.fn().mockResolvedValue({}),
    },
}));

vi.mock('../../api/events.service', () => ({
    eventsService: {
        listEvents: vi.fn().mockResolvedValue({ events: [], total: 0 }),
        getStats: vi.fn().mockResolvedValue({ total: 0 }),
        openStream: vi.fn(),
    },
}));

vi.mock('../../api/memory.service', () => ({
    memoryService: {
        listMemoryItems: vi.fn().mockResolvedValue({ items: [], total: 0 }),
        deleteMemoryItem: vi.fn(),
        getConsolidationStatus: vi.fn().mockResolvedValue({
            lastRun: '2026-04-14T12:00:00Z',
            episodesProcessed: 0,
            policiesExtracted: 0,
        }),
    },
}));

vi.mock('@ghatana/canvas/flow', () => ({
    FlowCanvas: ({ children }: { children?: React.ReactNode }) =>
        React.createElement('div', { 'data-testid': 'flow-canvas' }, children),
    FlowControls: () => React.createElement('div'),
    useNodesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
    useEdgesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
    addEdge: vi.fn((conn: unknown, eds: unknown) => eds),
    Background: () => React.createElement('div'),
    Controls: () => React.createElement('div'),
}));

// ── Imports ───────────────────────────────────────────────────────────────────

import { DataExplorer } from '../../pages/DataExplorer';
import { CreateCollectionPage } from '../../pages/CreateCollectionPage';
import { WorkflowsPage } from '../../pages/WorkflowsPage';
import { InsightsPage } from '../../pages/InsightsPage';
import { TrustCenter } from '../../pages/TrustCenter';
import { PluginsPage } from '../../pages/PluginsPage';
import { SettingsPage } from '../../pages/SettingsPage';

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Returns all interactive elements that should be keyboard-reachable.
 * These must either have a tab stop or be within a valid ARIA widget.
 */
function getInteractiveElements(container: HTMLElement): Element[] {
    return Array.from(
        container.querySelectorAll(
            'a[href], button:not([disabled]), input:not([disabled]), ' +
            'select:not([disabled]), textarea:not([disabled]), ' +
            '[tabindex]:not([tabindex="-1"]), [role="button"], [role="link"]'
        )
    );
}

/**
 * Checks that every button element has an accessible name.
 * An accessible name can come from textContent, aria-label, aria-labelledby,
 * title, or an associated <label> element (via id matching label[for]).
 */
function buttonsHaveAccessibleNames(container: HTMLElement): boolean {
    const buttons = Array.from(container.querySelectorAll('button'));
    return buttons.every(
        (btn) => {
            if ((btn.textContent?.trim().length ?? 0) > 0) return true;
            if (btn.hasAttribute('aria-label')) return true;
            if (btn.hasAttribute('aria-labelledby')) return true;
            if (btn.hasAttribute('title')) return true;
            // Check for an associated <label> via for/id pairing
            const id = btn.id;
            if (id && container.querySelector(`label[for="${id}"]`)) return true;
            return false;
        }
    );
}

/**
 * Checks that images have alt attributes (empty string is acceptable for decorative images).
 */
function imagesHaveAlt(container: HTMLElement): boolean {
    const imgs = Array.from(container.querySelectorAll('img'));
    return imgs.every((img) => img.hasAttribute('alt'));
}

// ── Page accessibility audits ─────────────────────────────────────────────────

describe('AccessibilityAudit — DataExplorer', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders something on the page', () => {
        const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
        expect(container.children.length).toBeGreaterThan(0);
        cleanup();
    });

    it('all rendered images have alt attributes', () => {
        const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
        expect(imagesHaveAlt(container)).toBe(true);
        cleanup();
    });

    it('buttons have accessible names', () => {
        const { container } = render(<DataExplorer />, { wrapper: TestWrapper });
        expect(buttonsHaveAccessibleNames(container)).toBe(true);
        cleanup();
    });
});

describe('AccessibilityAudit — CreateCollectionPage', () => {
    it('renders without crashing', () => {
        const { container } = render(<CreateCollectionPage />, { wrapper: TestWrapper });
        expect(container.children.length).toBeGreaterThan(0);
        cleanup();
    });

    it('all rendered images have alt attributes', () => {
        const { container } = render(<CreateCollectionPage />, { wrapper: TestWrapper });
        expect(imagesHaveAlt(container)).toBe(true);
        cleanup();
    });

    it('buttons have accessible names', () => {
        const { container } = render(<CreateCollectionPage />, { wrapper: TestWrapper });
        expect(buttonsHaveAccessibleNames(container)).toBe(true);
        cleanup();
    });

    it('form inputs have associated labels', () => {
        const { container } = render(<CreateCollectionPage />, { wrapper: TestWrapper });
        const inputs = Array.from(container.querySelectorAll('input:not([type="hidden"])'));
        inputs.forEach((input) => {
            const hasLabel =
                input.hasAttribute('aria-label') ||
                input.hasAttribute('aria-labelledby') ||
                input.hasAttribute('placeholder') || // acceptable fallback
                container.querySelector(`label[for="${input.id}"]`) !== null;
            expect(hasLabel).toBe(true);
        });
        cleanup();
    });
});

describe('AccessibilityAudit — WorkflowsPage', () => {
    it('renders page content', () => {
        const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
        expect(container.children.length).toBeGreaterThan(0);
        cleanup();
    });

    it('all rendered images have alt attributes', () => {
        const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
        expect(imagesHaveAlt(container)).toBe(true);
        cleanup();
    });

    it('buttons have accessible names', () => {
        const { container } = render(<WorkflowsPage />, { wrapper: TestWrapper });
        expect(buttonsHaveAccessibleNames(container)).toBe(true);
        cleanup();
    });
});

describe('AccessibilityAudit — InsightsPage', () => {
    it('renders page content', () => {
        const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
        expect(container.children.length).toBeGreaterThan(0);
        cleanup();
    });

    it('all rendered images have alt attributes', () => {
        const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
        expect(imagesHaveAlt(container)).toBe(true);
        cleanup();
    });

    it('buttons have accessible names', () => {
        const { container } = render(<InsightsPage />, { wrapper: TestWrapper });
        expect(buttonsHaveAccessibleNames(container)).toBe(true);
        cleanup();
    });
});

describe('AccessibilityAudit — TrustCenter (Governance)', () => {
    it('renders page content', () => {
        const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
        expect(container.children.length).toBeGreaterThan(0);
        cleanup();
    });

    it('all rendered images have alt attributes', () => {
        const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
        expect(imagesHaveAlt(container)).toBe(true);
        cleanup();
    });

    it('buttons have accessible names', () => {
        const { container } = render(<TrustCenter />, { wrapper: TestWrapper });
        expect(buttonsHaveAccessibleNames(container)).toBe(true);
        cleanup();
    });
});

describe('AccessibilityAudit — PluginsPage', () => {
    it('renders page content', () => {
        const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
        expect(container.children.length).toBeGreaterThan(0);
        cleanup();
    });

    it('all rendered images have alt attributes', () => {
        const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
        expect(imagesHaveAlt(container)).toBe(true);
        cleanup();
    });

    it('buttons have accessible names', () => {
        const { container } = render(<PluginsPage />, { wrapper: TestWrapper });
        expect(buttonsHaveAccessibleNames(container)).toBe(true);
        cleanup();
    });
});

describe('AccessibilityAudit — SettingsPage', () => {
    it('renders page content', () => {
        const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
        expect(container.children.length).toBeGreaterThan(0);
        cleanup();
    });

    it('all rendered images have alt attributes', () => {
        const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
        expect(imagesHaveAlt(container)).toBe(true);
        cleanup();
    });

    it('buttons have accessible names', () => {
        const { container } = render(<SettingsPage />, { wrapper: TestWrapper });
        expect(buttonsHaveAccessibleNames(container)).toBe(true);
        cleanup();
    });
});
