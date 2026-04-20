/**
 * End-to-end critical path journey tests.
 *
 * Validates the most important user journeys through the Data Cloud UI
 * using React Testing Library — rendering key pages in sequence and verifying
 * the basic content structure renders correctly for each step.
 *
 * @doc.type test
 * @doc.purpose Critical path rendering journey for the Data Cloud UI
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
        put: vi.fn().mockResolvedValue({}),
        delete: vi.fn().mockResolvedValue(undefined),
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

vi.mock('react-router', async (importOriginal) => {
    const actual = await importOriginal<typeof import('react-router')>();
    return {
        ...actual,
        useParams: vi.fn(() => ({ id: 'journey-col-1' })),
        useNavigate: vi.fn(() => vi.fn()),
    };
});

vi.mock('../../lib/api/collections', () => ({
    collectionsApi: {
        list: vi.fn().mockResolvedValue({
            items: [
                {
                    id: 'journey-col-1',
                    name: 'Test',
                    description: 'Journey test collection',
                    schemaType: 'entity',
                    status: 'active',
                    entityCount: 10,
                    updatedAt: '2026-04-18T12:00:00Z',
                    schema: { fields: [] },
                },
            ],
        }),
        create: vi.fn().mockResolvedValue({ id: 'journey-col-1' }),
        get: vi.fn().mockResolvedValue({ id: 'journey-col-1', name: 'Test', description: 'Journey test collection', schema: { fields: [] } }),
        update: vi.fn().mockResolvedValue({}),
    },
}));

vi.mock('../../features/collection/components/CollectionForm', () => ({
    CollectionForm: () => React.createElement('form', { 'data-testid': 'collection-form' }),
}));

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

vi.mock('@ghatana/canvas/flow', () => ({
    FlowCanvas: ({ children }: { children?: React.ReactNode }) =>
        React.createElement('div', { 'data-testid': 'flow-canvas' }, children),
    FlowControls: () => React.createElement('div'),
    MarkerType: { ArrowClosed: 'arrowclosed' },
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
import { EventExplorerPage } from '../../pages/EventExplorerPage';
import { InsightsPage } from '../../pages/InsightsPage';
import { IntelligentHub } from '../../pages/IntelligentHub';
import { TrustCenter } from '../../pages/TrustCenter';
import { MemoryPlaneViewerPage } from '../../pages/MemoryPlaneViewerPage';
import { DataFabricPage } from '../../pages/DataFabricPage';
import { EditCollectionPage } from '../../pages/EditCollectionPage';

// ── Journey helpers ───────────────────────────────────────────────────────────

function renderPage(Component: React.ComponentType) {
    const result = render(React.createElement(Component), { wrapper: TestWrapper });
    cleanup(); // isolate renders
    return result;
}

// ── Critical path journeys ────────────────────────────────────────────────────

describe('CriticalPathJourney', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Step 1 — Land on Data Explorer', () => {
        it('DataExplorer renders without crashing', () => {
            render(<DataExplorer />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });
    });

    describe('Step 2 — Create a Collection', () => {
        it('CreateCollectionPage renders without crashing', () => {
            render(<CreateCollectionPage />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });

        it('CreateCollectionPage displays collection form content', () => {
            render(<CreateCollectionPage />, { wrapper: TestWrapper });
            const body = document.body.textContent ?? '';
            expect(body.toLowerCase()).toMatch(/collection|schema|creat|new/i);
            cleanup();
        });
    });

    describe('Step 3 — Edit the Collection', () => {
        it('EditCollectionPage renders without crashing', () => {
            render(<EditCollectionPage />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });
    });

    describe('Step 4 — Build a Workflow', () => {
        it('WorkflowsPage renders without crashing', () => {
            render(<WorkflowsPage />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });

        it('WorkflowsPage shows workflow content', () => {
            render(<WorkflowsPage />, { wrapper: TestWrapper });
            const body = document.body.textContent ?? '';
            expect(body.toLowerCase()).toMatch(/workflow|pipeline|automat/i);
            cleanup();
        });
    });

    describe('Step 5 — Set up a Data Fabric Pipeline', () => {
        it('DataFabricPage renders without crashing', () => {
            render(<DataFabricPage />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });
    });

    describe('Step 6 — Monitor Events', () => {
        it('EventExplorerPage renders without crashing', () => {
            render(<EventExplorerPage />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });
    });

    describe('Step 7 — Review Analytics', () => {
        it('InsightsPage renders without crashing', () => {
            render(<InsightsPage />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });
    });

    describe('Step 8 — Explore Memory Plane', () => {
        it('MemoryPlaneViewerPage renders without crashing', () => {
            render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });
    });

    describe('Step 9 — Access Intelligence Hub', () => {
        it('IntelligentHub renders without crashing', () => {
            render(<IntelligentHub />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });
    });

    describe('Step 10 — Review Governance / TrustCenter', () => {
        it('TrustCenter renders without crashing', () => {
            render(<TrustCenter />, { wrapper: TestWrapper });
            expect(document.body).toBeTruthy();
            cleanup();
        });

        it('TrustCenter displays governance content', () => {
            render(<TrustCenter />, { wrapper: TestWrapper });
            const body = document.body.textContent ?? '';
            expect(body.toLowerCase()).toMatch(/trust|govern|compli|securit/i);
            cleanup();
        });
    });
});
