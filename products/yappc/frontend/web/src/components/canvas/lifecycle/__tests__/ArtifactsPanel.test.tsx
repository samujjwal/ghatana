/**
 * ArtifactsPanel unit tests
 *
 * Tests for the lifecycle artifacts panel that lists canvas artifacts.
 * Wraps in MemoryRouter because the component uses useSearchParams.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ArtifactsPanel } from '../ArtifactsPanel';
import { LifecyclePhase } from '../../../../types/lifecycle';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

import type { ArtifactItem, ArtifactsPanelProps } from '../ArtifactsPanel';

function makeProps(overrides: Partial<ArtifactsPanelProps> = {}): ArtifactsPanelProps {
    return {
        artifacts: [],
        currentPhase: LifecyclePhase.CONTEXT,
        onCreateArtifact: vi.fn(),
        ...overrides,
    };
}

function renderPanel(props: ArtifactsPanelProps = makeProps()) {
    return render(
        <MemoryRouter>
            <ArtifactsPanel {...props} />
        </MemoryRouter>,
    );
}

describe('ArtifactsPanel', () => {
    describe('header', () => {
        it('renders the panel heading', () => {
            renderPanel();
            expect(screen.getByText('Lifecycle Artifacts')).toBeInTheDocument();
        });

        it('renders the subtitle description', () => {
            renderPanel();
            expect(
                screen.getByText('Create and manage structured artifacts for your project.'),
            ).toBeInTheDocument();
        });
    });

    describe('phase sections', () => {
        it('renders Context Phase heading (SHAPE maps to CONTEXT)', () => {
            renderPanel();
            // PHASE_LABELS[CONTEXT] = 'Context', and phases includes SHAPE (= 'CONTEXT')
            expect(screen.getByText('Context Phase')).toBeInTheDocument();
        });

        it('renders Requirements artifact button', () => {
            renderPanel();
            expect(screen.getByText('Requirements')).toBeInTheDocument();
        });

        it('renders Architecture Decision Record artifact button', () => {
            renderPanel();
            expect(screen.getByText('Architecture Decision Record')).toBeInTheDocument();
        });

        it('renders UX Specification artifact button', () => {
            renderPanel();
            expect(screen.getByText('UX Specification')).toBeInTheDocument();
        });

        it('renders artifact descriptions', () => {
            renderPanel();
            expect(screen.getByText('Epics and user stories for the system')).toBeInTheDocument();
        });

        it('renders ADR abbreviation (ARC → first 3 chars of label)', () => {
            renderPanel();
            // "Architecture Decision Record".slice(0, 3).toUpperCase() = "ARC"
            expect(screen.getByText('ARC')).toBeInTheDocument();
        });

        it('renders REQ abbreviation for Requirements', () => {
            renderPanel();
            // "Requirements".slice(0, 3).toUpperCase() = "REQ"
            expect(screen.getByText('REQ')).toBeInTheDocument();
        });

        it('renders UX abbreviation for UX Specification', () => {
            renderPanel();
            // "UX Specification".slice(0, 3).toUpperCase() = "UX "
            expect(screen.getByText(/^UX\s*$/)).toBeInTheDocument();
        });
    });

    describe('artifact status badges', () => {
        it('shows Complete badge for complete artifact', () => {
            const artifact: ArtifactItem = {
                kind: 'requirements' as LifecycleArtifactKind,
                status: 'complete',
            };
            renderPanel(makeProps({ artifacts: [artifact] }));
            expect(screen.getByText('Complete')).toBeInTheDocument();
        });

        it('shows Draft badge for draft artifact', () => {
            const artifact: ArtifactItem = {
                kind: 'requirements' as LifecycleArtifactKind,
                status: 'draft',
            };
            renderPanel(makeProps({ artifacts: [artifact] }));
            expect(screen.getByText('Draft')).toBeInTheDocument();
        });

        it('shows no badge for missing artifact status', () => {
            const artifact: ArtifactItem = {
                kind: 'requirements' as LifecycleArtifactKind,
                status: 'missing',
            };
            renderPanel(makeProps({ artifacts: [artifact] }));
            expect(screen.queryByText('Complete')).not.toBeInTheDocument();
            expect(screen.queryByText('Draft')).not.toBeInTheDocument();
        });
    });

    describe('onCreateArtifact', () => {
        it('calls onCreateArtifact when artifact status is explicitly missing', () => {
            const onCreateArtifact = vi.fn();
            const artifact: ArtifactItem = {
                kind: 'requirements' as LifecycleArtifactKind,
                status: 'missing',
            };
            renderPanel(makeProps({ artifacts: [artifact], onCreateArtifact }));
            const reqButton = screen.getByRole('button', { name: /requirements/i });
            fireEvent.click(reqButton);
            expect(onCreateArtifact).toHaveBeenCalledWith('requirements');
        });

        it('does not call onCreateArtifact when artifact is absent from list (opens panel)', () => {
            const onCreateArtifact = vi.fn();
            // No artifacts → undefined status → opens panel, not creates artifact
            renderPanel(makeProps({ onCreateArtifact }));
            const reqButton = screen.getByRole('button', { name: /requirements/i });
            fireEvent.click(reqButton);
            expect(onCreateArtifact).not.toHaveBeenCalled();
        });

        it('does not call onCreateArtifact when clicking a draft artifact', () => {
            const onCreateArtifact = vi.fn();
            const artifact: ArtifactItem = {
                kind: 'requirements' as LifecycleArtifactKind,
                status: 'draft',
            };
            renderPanel(makeProps({ artifacts: [artifact], onCreateArtifact }));
            const reqButton = screen.getByRole('button', { name: /requirements/i });
            fireEvent.click(reqButton);
            expect(onCreateArtifact).not.toHaveBeenCalled();
        });
    });

    describe('Quick Actions section', () => {
        it('renders Quick Actions heading', () => {
            renderPanel();
            expect(screen.getByText('Quick Actions')).toBeInTheDocument();
        });

        it('renders View Traceability Graph button', () => {
            renderPanel();
            expect(
                screen.getByRole('button', { name: /view traceability graph/i }),
            ).toBeInTheDocument();
        });
    });
});
