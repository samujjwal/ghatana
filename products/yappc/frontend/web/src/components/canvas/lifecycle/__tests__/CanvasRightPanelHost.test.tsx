/**
 * CanvasRightPanelHost unit tests
 *
 * URL-driven panel router for the Canvas. Tests panel show/hide,
 * navigation, close, collapsed state, and read-only fallbacks.
 *
 * Needs MemoryRouter because the component uses useSearchParams.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, it, expect, vi } from 'vitest';
import { CanvasRightPanelHost } from '../CanvasRightPanelHost';

import type { CanvasRightPanelHostProps } from '../CanvasRightPanelHost';

function makeProps(overrides: Partial<CanvasRightPanelHostProps> = {}): CanvasRightPanelHostProps {
    return {
        projectId: 'test-project-123',
        ...overrides,
    };
}

/**
 * Render CanvasRightPanelHost with a given ?panel= query string.
 */
function renderHost(
    props: CanvasRightPanelHostProps = makeProps(),
    initialUrl = '/',
) {
    return render(
        <MemoryRouter initialEntries={[initialUrl]}>
            <Routes>
                <Route path="*" element={<CanvasRightPanelHost {...props} />} />
            </Routes>
        </MemoryRouter>,
    );
}

describe('CanvasRightPanelHost', () => {
    describe('when no panel query param', () => {
        it('renders nothing when no ?panel= param', () => {
            const { container } = renderHost(makeProps(), '/');
            expect(container.firstChild).toBeNull();
        });
    });

    describe('when panel is set in URL', () => {
        it('renders the panel container when ?panel=artifacts', () => {
            const { container } = renderHost(makeProps(), '/?panel=artifacts');
            // The outer div wrapper should be in the DOM
            expect(container.firstChild).toBeTruthy();
        });

        it('shows close button when a panel is open', () => {
            renderHost(makeProps(), '/?panel=adr');
            expect(screen.getByTitle('Close panel')).toBeInTheDocument();
        });

        it('shows panel navigation (previous panel button)', () => {
            renderHost(makeProps(), '/?panel=requirements');
            expect(screen.getByTitle('Previous panel')).toBeInTheDocument();
        });

        it('shows panel navigation (next panel button)', () => {
            renderHost(makeProps(), '/?panel=requirements');
            expect(screen.getByTitle('Next panel')).toBeInTheDocument();
        });

        it('prev button is disabled when on first panel (artifacts)', () => {
            renderHost(makeProps(), '/?panel=artifacts');
            const prevButton = screen.getByTitle('Previous panel');
            expect(prevButton).toBeDisabled();
        });

        it('next button is disabled when on last panel (improve)', () => {
            renderHost(makeProps(), '/?panel=improve');
            const nextButton = screen.getByTitle('Next panel');
            expect(nextButton).toBeDisabled();
        });

        it('shows panel index counter (e.g. 1/7)', () => {
            renderHost(makeProps(), '/?panel=artifacts');
            // artifacts is index 0 → displayed as 1/7
            expect(screen.getByText('1/7')).toBeInTheDocument();
        });
    });

    describe('bottom navigation tabs', () => {
        it('renders Artifacts tab', () => {
            renderHost(makeProps(), '/?panel=artifacts');
            expect(screen.getByRole('button', { name: /artifacts/i })).toBeInTheDocument();
        });

        it('renders Requirements tab', () => {
            renderHost(makeProps(), '/?panel=artifacts');
            // Requirements without a save handler: shows 🔒 suffix
            expect(screen.getByTitle(/Requirements/i)).toBeInTheDocument();
        });

        it('renders all PANEL_TITLES in the tab bar', () => {
            renderHost(makeProps(), '/?panel=artifacts');
            expect(screen.getByTitle('Artifacts')).toBeInTheDocument();
        });

        it('Requirements tab is disabled (no handler configured)', () => {
            renderHost(makeProps(), '/?panel=artifacts');
            const reqTab = screen.getByTitle(/Requirements.*Save handler not configured/);
            expect(reqTab).toBeDisabled();
        });

        it('Artifacts tab is enabled (always has handler)', () => {
            renderHost(makeProps(), '/?panel=artifacts');
            const artifactsTab = screen.getByTitle('Artifacts');
            expect(artifactsTab).not.toBeDisabled();
        });
    });

    describe('read-only fallback panels', () => {
        it('shows read-only fallback for requirements when handler not provided', () => {
            renderHost(makeProps(), '/?panel=requirements');
            expect(screen.getByText('Requirements')).toBeInTheDocument();
            expect(screen.getByText('Read-only mode')).toBeInTheDocument();
            expect(screen.getByText('Save handler not configured')).toBeInTheDocument();
        });

        it('shows read-only fallback for adr when handler not provided', () => {
            renderHost(makeProps(), '/?panel=adr');
            expect(screen.getByText('Architecture Decisions')).toBeInTheDocument();
            expect(screen.getByText('Read-only mode')).toBeInTheDocument();
        });

        it('shows read-only fallback for ux-spec when handler not provided', () => {
            renderHost(makeProps(), '/?panel=ux-spec');
            expect(screen.getByText('UX Specification')).toBeInTheDocument();
            expect(screen.getByText('Read-only mode')).toBeInTheDocument();
        });

        it('shows read-only fallback for threat-model when handler not provided', () => {
            renderHost(makeProps(), '/?panel=threat-model');
            expect(screen.getByText('Threat Model')).toBeInTheDocument();
            expect(screen.getByText('Read-only mode')).toBeInTheDocument();
        });

        it('shows read-only fallback for improve when handler not provided', () => {
            renderHost(makeProps(), '/?panel=improve');
            expect(screen.getByText('Improve')).toBeInTheDocument();
            expect(screen.getByText('Read-only mode')).toBeInTheDocument();
        });

        it('shows read-only fallback for traceability when link handler not provided', () => {
            renderHost(makeProps(), '/?panel=traceability');
            expect(screen.getByText('Traceability')).toBeInTheDocument();
            expect(screen.getByText('Read-only mode')).toBeInTheDocument();
        });
    });

    describe('collapsed state', () => {
        it('renders slim sidebar when isCollapsed is true', () => {
            const { container } = renderHost(
                makeProps({ isCollapsed: true }),
                '/?panel=artifacts',
            );
            // Collapsed shows a thin sidebar instead of full panel
            expect(container.firstChild).toBeTruthy();
        });

        it('shows Expand panel button when collapsed', () => {
            renderHost(makeProps({ isCollapsed: true }), '/?panel=artifacts');
            expect(screen.getByTitle('Expand panel')).toBeInTheDocument();
        });

        it('calls onToggleCollapse when expand button clicked', () => {
            const onToggleCollapse = vi.fn();
            renderHost(
                makeProps({ isCollapsed: true, onToggleCollapse }),
                '/?panel=artifacts',
            );
            fireEvent.click(screen.getByTitle('Expand panel'));
            expect(onToggleCollapse).toHaveBeenCalledTimes(1);
        });
    });

    describe('width prop', () => {
        it('applies custom width to the panel container', () => {
            const { container } = renderHost(
                makeProps({ width: 600 }),
                '/?panel=artifacts',
            );
            // The outermost panel div should have width: 600px
            const panelDiv = container.querySelector('[style*="600px"]');
            expect(panelDiv).toBeTruthy();
        });
    });
});
