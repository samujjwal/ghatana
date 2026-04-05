/**
 * Incident Management Panel Tests
 *
 * Tests for incident lifecycle artifact creation, listing, and detail display.
 *
 * @doc.type test
 * @doc.purpose Verify IncidentManagementPanel behaviour
 * @doc.layer product
 * @doc.pattern Component Test
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { IncidentManagementPanel } from '../IncidentManagementPanel';
import type { ArtifactSummary, LifecycleArtifact } from '../../../services/canvas/lifecycle';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockCreateArtifact = vi.fn();
const mockUpdateArtifact = vi.fn();
const mockGetArtifact = vi.fn();
const mockGetArtifactByKind = vi.fn();

const mockService = {
    getArtifact: mockGetArtifact,
    getArtifactByKind: mockGetArtifactByKind,
};

vi.mock('../../../services/canvas/lifecycle', () => ({
    useLifecycleArtifacts: vi.fn(),
}));

vi.mock('../../../providers/AuthProvider', () => ({
    useCurrentUser: vi.fn(),
}));

import { useLifecycleArtifacts } from '../../../services/canvas/lifecycle';
import { useCurrentUser } from '../../../providers/AuthProvider';

const mockUseLifecycleArtifacts = useLifecycleArtifacts as ReturnType<typeof vi.fn>;
const mockUseCurrentUser = useCurrentUser as ReturnType<typeof vi.fn>;

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const CURRENT_USER = {
    id: 'user-42',
    name: 'Test User',
    email: 'test@example.com',
    initials: 'TU',
    isAuthenticated: true,
};

function buildIncidentSummary(overrides: Partial<ArtifactSummary> = {}): ArtifactSummary {
    return {
        id: 'incident-1',
        kind: 'incident_report' as const,
        title: 'API Gateway timeout spike',
        status: 'draft',
        phase: 'OBSERVE' as never,
        updatedAt: '2025-06-01T10:00:00Z',
        ...overrides,
    };
}

function buildIncidentArtifact(overrides: Partial<LifecycleArtifact> = {}): LifecycleArtifact {
    return {
        id: 'incident-1',
        projectId: 'proj-1',
        kind: 'incident_report' as const,
        title: 'API Gateway timeout spike',
        payload: {
            incidentStatus: 'investigating',
            severity: 'high',
            detectedAt: '2025-06-01T09:00:00Z',
            impact: 'Users unable to reach checkout',
            rootCause: 'Connection pool exhausted',
            timeline: [
                { timestamp: '2025-06-01T09:00:00Z', event: 'Alert fired', user: 'Test User' },
            ],
            mitigations: [],
            postMortemUrl: '',
        },
        status: 'draft',
        tags: [],
        version: 1,
        createdAt: '2025-06-01T09:00:00Z',
        updatedAt: '2025-06-01T10:00:00Z',
        createdBy: 'user-42',
        updatedBy: 'user-42',
        ...overrides,
    };
}

// ---------------------------------------------------------------------------
// Setup helper
// ---------------------------------------------------------------------------

function setupHooks(artifacts: ArtifactSummary[] = []): void {
    mockUseLifecycleArtifacts.mockReturnValue({
        artifacts,
        loading: false,
        error: null,
        createArtifact: mockCreateArtifact,
        updateArtifact: mockUpdateArtifact,
        service: mockService,
    });
    mockUseCurrentUser.mockReturnValue(CURRENT_USER);
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('IncidentManagementPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Empty state', () => {
        it('shows a message when there are no incidents', () => {
            setupHooks([]);

            render(<IncidentManagementPanel projectId="proj-1" />);

            expect(screen.getByText('No incidents recorded yet')).toBeInTheDocument();
        });

        it('shows placeholder when no incident is selected', () => {
            setupHooks([]);

            render(<IncidentManagementPanel projectId="proj-1" />);

            expect(screen.getByText('Select an incident to view details')).toBeInTheDocument();
        });
    });

    describe('Incident list', () => {
        it('renders incident summaries', () => {
            const summary = buildIncidentSummary();
            setupHooks([summary]);

            render(<IncidentManagementPanel projectId="proj-1" />);

            expect(screen.getByText('API Gateway timeout spike')).toBeInTheDocument();
        });

        it('loads full artifact detail on click', async () => {
            const summary = buildIncidentSummary();
            const artifact = buildIncidentArtifact();
            setupHooks([summary]);
            mockGetArtifact.mockResolvedValue(artifact);

            render(<IncidentManagementPanel projectId="proj-1" />);

            fireEvent.click(screen.getByText('API Gateway timeout spike'));

            await waitFor(() => {
                expect(mockGetArtifact).toHaveBeenCalledWith('incident-1');
            });

            expect(screen.getByText('Users unable to reach checkout')).toBeInTheDocument();
        });

        it('shows root cause in detail view', async () => {
            const summary = buildIncidentSummary();
            const artifact = buildIncidentArtifact();
            setupHooks([summary]);
            mockGetArtifact.mockResolvedValue(artifact);

            render(<IncidentManagementPanel projectId="proj-1" />);

            fireEvent.click(screen.getByText('API Gateway timeout spike'));

            await waitFor(() => {
                expect(screen.getByText('Connection pool exhausted')).toBeInTheDocument();
            });
        });
    });

    describe('New incident dialog', () => {
        it('opens dialog when New Incident button is clicked', () => {
            setupHooks([]);

            render(<IncidentManagementPanel projectId="proj-1" />);

            fireEvent.click(screen.getByRole('button', { name: /new incident/i }));

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
        });

        it('closes dialog when Cancel is clicked', () => {
            setupHooks([]);

            render(<IncidentManagementPanel projectId="proj-1" />);

            fireEvent.click(screen.getByRole('button', { name: /new incident/i }));
            fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

            expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        });

        it('disables Create Incident when title is empty', () => {
            setupHooks([]);

            render(<IncidentManagementPanel projectId="proj-1" />);

            fireEvent.click(screen.getByRole('button', { name: /new incident/i }));

            const createBtn = screen.getByRole('button', { name: /create incident/i });
            expect(createBtn).toBeDisabled();
        });

        it('enables Create Incident when title is filled', () => {
            setupHooks([]);

            render(<IncidentManagementPanel projectId="proj-1" />);

            fireEvent.click(screen.getByRole('button', { name: /new incident/i }));
            fireEvent.change(screen.getByLabelText(/title/i), {
                target: { value: 'Database write latency' },
            });

            const createBtn = screen.getByRole('button', { name: /create incident/i });
            expect(createBtn).not.toBeDisabled();
        });

        it('calls createArtifact and updateArtifact on submit', async () => {
            setupHooks([]);
            const pendingArtifact = buildIncidentArtifact({ id: 'new-1' });
            mockCreateArtifact.mockResolvedValue(pendingArtifact);
            mockGetArtifactByKind.mockResolvedValue(pendingArtifact);
            mockUpdateArtifact.mockResolvedValue(pendingArtifact);

            render(<IncidentManagementPanel projectId="proj-1" />);

            fireEvent.click(screen.getByRole('button', { name: /new incident/i }));
            fireEvent.change(screen.getByLabelText(/title/i), {
                target: { value: 'Database write latency' },
            });
            fireEvent.change(screen.getByLabelText(/impact/i), {
                target: { value: 'Writes failing for 20% of users' },
            });
            fireEvent.click(screen.getByRole('button', { name: /create incident/i }));

            await waitFor(() => {
                expect(mockCreateArtifact).toHaveBeenCalledWith('incident_report', 'user-42');
            });

            await waitFor(() => {
                expect(mockUpdateArtifact).toHaveBeenCalledWith(
                    'new-1',
                    expect.objectContaining({
                        title: 'Database write latency',
                        payload: expect.objectContaining({
                            incidentStatus: 'open',
                            severity: 'medium',
                            impact: 'Writes failing for 20% of users',
                        }),
                    }),
                    'user-42',
                );
            });
        });

        it('closes dialog after successful creation', async () => {
            setupHooks([]);
            const artifact = buildIncidentArtifact({ id: 'new-2' });
            mockCreateArtifact.mockResolvedValue(artifact);
            mockGetArtifactByKind.mockResolvedValue(artifact);
            mockUpdateArtifact.mockResolvedValue(artifact);

            render(<IncidentManagementPanel projectId="proj-1" />);

            fireEvent.click(screen.getByRole('button', { name: /new incident/i }));
            fireEvent.change(screen.getByLabelText(/title/i), {
                target: { value: 'Test incident' },
            });
            fireEvent.click(screen.getByRole('button', { name: /create incident/i }));

            await waitFor(() => {
                expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
            });
        });
    });

    describe('Severity selector', () => {
        it('defaults severity to medium', () => {
            setupHooks([]);

            render(<IncidentManagementPanel projectId="proj-1" />);
            fireEvent.click(screen.getByRole('button', { name: /new incident/i }));

            const select = screen.getByLabelText(/severity/i) as HTMLSelectElement;
            expect(select.value).toBe('medium');
        });

        it('allows changing severity', () => {
            setupHooks([]);

            render(<IncidentManagementPanel projectId="proj-1" />);
            fireEvent.click(screen.getByRole('button', { name: /new incident/i }));
            fireEvent.change(screen.getByLabelText(/severity/i), { target: { value: 'critical' } });

            const select = screen.getByLabelText(/severity/i) as HTMLSelectElement;
            expect(select.value).toBe('critical');
        });
    });
});
