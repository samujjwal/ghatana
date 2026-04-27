/**
 * ComplianceReport Unit Tests
 *
 * Tests for the compliance report visualization component.
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ComplianceReport } from '../ComplianceReport';

// ============================================================================
// Fixtures
// ============================================================================

const makeCheck = (id: string, status: 'passed' | 'failed' | 'warning' | 'not-applicable' | 'pending', name = `Check ${id}`) => ({
    id,
    controlId: `CTRL-${id}`,
    name,
    description: `Description for check ${id}`,
    status,
    requiredArtifacts: ['code'],
    foundArtifacts: status === 'passed' ? ['code'] : [],
    missingArtifacts: status === 'failed' ? ['code'] : [],
    recommendations: status === 'failed' ? ['Fix this issue'] : undefined,
});

const makeReport = (overrides = {}) => ({
    id: 'rpt-1',
    framework: 'soc2' as const,
    workflowId: 'wf-1',
    status: 'partial' as const,
    score: 75,
    checks: [
        makeCheck('1', 'passed', 'Access Control'),
        makeCheck('2', 'failed', 'Encryption Check'),
        makeCheck('3', 'warning', 'Audit Logging'),
    ],
    summary: { total: 3, passed: 1, failed: 1, warnings: 1, notApplicable: 0 },
    generatedAt: '2026-04-20T10:00:00Z',
    ...overrides,
});

// ============================================================================
// Tests
// ============================================================================

describe('ComplianceReport', () => {
    describe('loading state', () => {
        it('shows spinner when isLoading and no report', () => {
            const { container } = render(<ComplianceReport report={null} isLoading />);
            // CircularProgress is rendered (from design-system Spinner)
            expect(container.firstChild).toBeTruthy();
            // No report content visible
            expect(screen.queryByText('SOC 2 Compliance Report')).toBeNull();
        });
    });

    describe('error state', () => {
        it('shows error message when error prop is provided', () => {
            render(<ComplianceReport report={null} error="Network failure" />);
            expect(screen.getByText('Network failure')).toBeTruthy();
        });
    });

    describe('null report state', () => {
        it('shows Run Compliance Check button when report is null', () => {
            render(<ComplianceReport report={null} onRunCheck={vi.fn()} />);
            expect(screen.getByRole('button', { name: /Run Compliance Check/i })).toBeTruthy();
        });

        it('shows empty state message', () => {
            render(<ComplianceReport report={null} />);
            expect(screen.getByText('No Compliance Report')).toBeTruthy();
        });

        it('calls onRunCheck when the Run Compliance Check button is clicked', async () => {
            const onRunCheck = vi.fn();
            render(<ComplianceReport report={null} onRunCheck={onRunCheck} />);
            await userEvent.click(screen.getByRole('button', { name: /Run Compliance Check/i }));
            expect(onRunCheck).toHaveBeenCalledOnce();
        });
    });

    describe('report display', () => {
        it('renders framework name in header', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getByText('SOC 2 Compliance Report')).toBeTruthy();
        });

        it('renders the compliance score', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getByText('75%')).toBeTruthy();
        });

        it('renders the overall status badge', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getByText('PARTIAL')).toBeTruthy();
        });

        it('renders passed count in summary', () => {
            render(<ComplianceReport report={makeReport()} />);
            // "Passed" appears in summary counter and in check status chip
            expect(screen.getAllByText('Passed').length).toBeGreaterThan(0);
        });

        it('renders failed count in summary', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getAllByText('Failed').length).toBeGreaterThan(0);
        });

        it('renders warnings count in summary', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getByText('Warnings')).toBeTruthy();
        });
    });

    describe('check list', () => {
        it('renders check names', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getByText('Access Control')).toBeTruthy();
            expect(screen.getByText('Encryption Check')).toBeTruthy();
            expect(screen.getByText('Audit Logging')).toBeTruthy();
        });

        it('renders check control IDs', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getByText('CTRL-1')).toBeTruthy();
            expect(screen.getByText('CTRL-2')).toBeTruthy();
        });

        it('renders passed check status label', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getAllByText('Passed').length).toBeGreaterThan(0);
        });

        it('renders failed check status label', () => {
            render(<ComplianceReport report={makeReport()} />);
            expect(screen.getAllByText('Failed').length).toBeGreaterThan(0);
        });
    });

    describe('action buttons', () => {
        it('renders Export button', () => {
            render(<ComplianceReport report={makeReport()} onExport={vi.fn()} />);
            expect(screen.getByRole('button', { name: /Export/i })).toBeTruthy();
        });

        it('calls onExport when Export button is clicked', async () => {
            const onExport = vi.fn();
            render(<ComplianceReport report={makeReport()} onExport={onExport} />);
            await userEvent.click(screen.getByRole('button', { name: /Export/i }));
            expect(onExport).toHaveBeenCalledOnce();
        });

        it('calls onRunCheck when Re-run button is clicked', async () => {
            const onRunCheck = vi.fn();
            render(<ComplianceReport report={makeReport()} onRunCheck={onRunCheck} />);
            await userEvent.click(screen.getByRole('button', { name: /Re-run/i }));
            expect(onRunCheck).toHaveBeenCalledOnce();
        });
    });

    describe('compliant report', () => {
        it('shows COMPLIANT status badge for compliant report', () => {
            render(<ComplianceReport report={makeReport({ status: 'compliant', score: 100 })} />);
            expect(screen.getByText('COMPLIANT')).toBeTruthy();
        });
    });

    describe('non-compliant report', () => {
        it('shows NON-COMPLIANT status badge', () => {
            render(<ComplianceReport report={makeReport({ status: 'non-compliant', score: 30 })} />);
            expect(screen.getByText('NON-COMPLIANT')).toBeTruthy();
        });
    });
});
