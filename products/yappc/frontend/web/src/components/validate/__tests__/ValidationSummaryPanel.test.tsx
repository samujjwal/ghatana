/**
 * ValidationSummaryPanel unit tests
 *
 * Covers summary stats, category grouping, check interactions,
 * report generation, and AI assist functionality.
 */

import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ValidationSummaryPanel } from '../ValidationSummaryPanel';
import type { ValidationSummaryPanelProps, ValidationCheck, ValidationReport } from '../ValidationSummaryPanel';

// ─── fixtures ────────────────────────────────────────────────────────────────

const makeCheck = (overrides: Partial<ValidationCheck> = {}): ValidationCheck => ({
    id: 'chk-1',
    category: 'requirements',
    name: 'Requirements completeness',
    description: 'All requirements have acceptance criteria',
    status: 'pending',
    ...overrides,
});

const passedRequirementsCheck = makeCheck({ id: 'c1', status: 'passed' });
const failedSecurityCheck = makeCheck({ id: 'c2', category: 'security', name: 'SQL Injection scan', description: 'Check for SQL injection risks', status: 'failed', details: 'Parameterized queries missing in 3 places', autoFix: true });
const warningUxCheck = makeCheck({ id: 'c3', category: 'ux', name: 'Contrast ratio', description: 'Minimum AA contrast ratio', status: 'warning' });
const pendingArchCheck = makeCheck({ id: 'c4', category: 'architecture', name: 'Dependency cycles', description: 'No circular deps', status: 'pending' });

const sampleChecks = [passedRequirementsCheck, failedSecurityCheck, warningUxCheck, pendingArchCheck];

const sampleReport: ValidationReport = {
    id: 'rpt-1',
    createdAt: '2026-04-25T10:00:00.000Z',
    summary: { total: 4, passed: 1, failed: 1, warnings: 1, skipped: 0 },
    checks: sampleChecks,
    recommendations: ['Fix SQL injection', 'Improve contrast'],
};

function defaultProps(overrides: Partial<ValidationSummaryPanelProps> = {}): ValidationSummaryPanelProps {
    return {
        projectId: 'proj-1',
        checks: sampleChecks,
        isRunning: false,
        onRunValidation: vi.fn().mockResolvedValue(undefined),
        onRunCheck: vi.fn().mockResolvedValue(undefined),
        onGenerateReport: vi.fn().mockResolvedValue(sampleReport),
        ...overrides,
    };
}

// ─── tests ───────────────────────────────────────────────────────────────────

describe('ValidationSummaryPanel', () => {
    describe('header', () => {
        it('renders Validation heading', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('Validation')).toBeInTheDocument();
        });

        it('shows passed/total checks count', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            // 1 passed out of 4 total
            expect(screen.getByText('1/4 checks passed')).toBeInTheDocument();
        });

        it('renders Run All button', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByRole('button', { name: /run all/i })).toBeInTheDocument();
        });

        it('disables Run All when isRunning is true', () => {
            render(<ValidationSummaryPanel {...defaultProps({ isRunning: true })} />);
            expect(screen.getByRole('button', { name: /running/i })).toBeDisabled();
        });

        it('calls onRunValidation when Run All is clicked', async () => {
            const onRunValidation = vi.fn().mockResolvedValue(undefined);
            render(<ValidationSummaryPanel {...defaultProps({ onRunValidation })} />);
            fireEvent.click(screen.getByRole('button', { name: /run all/i }));
            await waitFor(() => expect(onRunValidation).toHaveBeenCalledOnce());
        });

        it('does not render AI Assist button when onAIAssist is absent', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.queryByRole('button', { name: /ai assist/i })).not.toBeInTheDocument();
        });

        it('renders AI Assist button when onAIAssist is provided', () => {
            render(
                <ValidationSummaryPanel
                    {...defaultProps()}
                    onAIAssist={vi.fn().mockResolvedValue(null)}
                />
            );
            expect(screen.getByRole('button', { name: /ai assist/i })).toBeInTheDocument();
        });
    });

    describe('summary stats', () => {
        it('renders Total stat', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('Total')).toBeInTheDocument();
            // 4 total checks
            const totals = screen.getAllByText('4');
            expect(totals.length).toBeGreaterThan(0);
        });

        it('renders Passed stat', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            // 'Passed' appears as both a stat label and a check status label — assert multiple
            const passedEls = screen.getAllByText('Passed');
            expect(passedEls.length).toBeGreaterThanOrEqual(1);
        });

        it('renders Failed stat', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            const failedEls = screen.getAllByText('Failed');
            expect(failedEls.length).toBeGreaterThanOrEqual(1);
        });

        it('renders Warnings stat', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('Warnings')).toBeInTheDocument();
        });

        it('renders Pending stat', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('Pending')).toBeInTheDocument();
        });
    });

    describe('category grouping', () => {
        it('renders category labels for checks that have them', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('Requirements')).toBeInTheDocument();
            expect(screen.getByText('Security')).toBeInTheDocument();
        });

        it('does not render categories with no checks', () => {
            // Only requirements check
            render(
                <ValidationSummaryPanel
                    {...defaultProps({ checks: [passedRequirementsCheck] })}
                />
            );
            // Security category should not appear
            expect(screen.queryByText('Security')).not.toBeInTheDocument();
        });

        it('requirements category is expanded by default (check name visible)', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('Requirements completeness')).toBeInTheDocument();
        });

        it('security category is expanded by default (check name visible)', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('SQL Injection scan')).toBeInTheDocument();
        });

        it('ux category is collapsed by default (check name not visible)', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.queryByText('Contrast ratio')).not.toBeInTheDocument();
        });

        it('expands a collapsed category when its header is clicked', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            // Click the UX/Design category header
            const uxHeader = screen.getByText('UX/Design').closest('button');
            expect(uxHeader).not.toBeNull();
            fireEvent.click(uxHeader!);
            expect(screen.getByText('Contrast ratio')).toBeInTheDocument();
        });

        it('collapses an expanded category when its header is clicked again', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            const reqHeader = screen.getByText('Requirements').closest('button');
            fireEvent.click(reqHeader!);
            expect(screen.queryByText('Requirements completeness')).not.toBeInTheDocument();
        });

        it('renders category passed/total count', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            // Requirements: 1/1 passed
            expect(screen.getByText('(1/1)')).toBeInTheDocument();
        });

        it('shows failed badge for categories with failures', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('1 failed')).toBeInTheDocument();
        });

        it('renders check description inside expanded category', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('All requirements have acceptance criteria')).toBeInTheDocument();
        });

        it('renders check details when provided', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByText('Parameterized queries missing in 3 places')).toBeInTheDocument();
        });
    });

    describe('check interactions', () => {
        it('renders Re-run button for each check in expanded category', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            // Requirements category is expanded — should have Re-run
            const rerunBtns = screen.getAllByRole('button', { name: /re-run/i });
            expect(rerunBtns.length).toBeGreaterThan(0);
        });

        it('calls onRunCheck with check ID when Re-run is clicked', async () => {
            const onRunCheck = vi.fn().mockResolvedValue(undefined);
            render(<ValidationSummaryPanel {...defaultProps({ onRunCheck })} />);
            const rerunBtns = screen.getAllByRole('button', { name: /re-run/i });
            fireEvent.click(rerunBtns[0]);
            await waitFor(() => expect(onRunCheck).toHaveBeenCalledOnce());
        });

        it('renders Auto-fix button for failed checks with autoFix=true', () => {
            const autoFixFn = vi.fn().mockResolvedValue(undefined);
            render(<ValidationSummaryPanel {...defaultProps({ onAutoFix: autoFixFn })} />);
            expect(screen.getByRole('button', { name: /auto-fix/i })).toBeInTheDocument();
        });

        it('does not render Auto-fix button when onAutoFix is not provided', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.queryByRole('button', { name: /auto-fix/i })).not.toBeInTheDocument();
        });

        it('calls onAutoFix when Auto-fix is clicked', async () => {
            const onAutoFix = vi.fn().mockResolvedValue(undefined);
            render(<ValidationSummaryPanel {...defaultProps({ onAutoFix })} />);
            fireEvent.click(screen.getByRole('button', { name: /auto-fix/i }));
            await waitFor(() => expect(onAutoFix).toHaveBeenCalledWith(failedSecurityCheck.id));
        });
    });

    describe('report generation', () => {
        it('renders Generate Report button', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.getByRole('button', { name: /generate report/i })).toBeInTheDocument();
        });

        it('calls onGenerateReport when button is clicked', async () => {
            const onGenerateReport = vi.fn().mockResolvedValue(sampleReport);
            render(<ValidationSummaryPanel {...defaultProps({ onGenerateReport })} />);
            fireEvent.click(screen.getByRole('button', { name: /generate report/i }));
            await waitFor(() => expect(onGenerateReport).toHaveBeenCalledOnce());
        });

        it('shows Last report timestamp when lastReport is provided', () => {
            render(<ValidationSummaryPanel {...defaultProps({ lastReport: sampleReport })} />);
            expect(screen.getByText(/last report:/i)).toBeInTheDocument();
        });

        it('does not show Last report text when no lastReport', () => {
            render(<ValidationSummaryPanel {...defaultProps()} />);
            expect(screen.queryByText(/last report:/i)).not.toBeInTheDocument();
        });

        it('renders export buttons (PDF and MD) when onExportReport is provided and report exists', async () => {
            const onExportReport = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationSummaryPanel
                    {...defaultProps({ lastReport: sampleReport, onExportReport })}
                />
            );
            // Export buttons are labeled PDF and MD
            expect(screen.getByRole('button', { name: /pdf/i })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /^md$/i })).toBeInTheDocument();
        });
    });

    describe('AI assist', () => {
        it('calls onAIAssist with current checks when clicked', async () => {
            const onAIAssist = vi.fn().mockResolvedValue(null);
            render(<ValidationSummaryPanel {...defaultProps({ onAIAssist })} />);
            fireEvent.click(screen.getByRole('button', { name: /ai assist/i }));
            await waitFor(() => {
                expect(onAIAssist).toHaveBeenCalledWith(sampleChecks);
            });
        });

        it('shows AI suggestions when result is returned', async () => {
            const onAIAssist = vi.fn().mockResolvedValue({
                suggestions: ['Consider caching'],
                prioritizedFixes: ['Fix SQL injection first'],
            });
            render(<ValidationSummaryPanel {...defaultProps({ onAIAssist })} />);
            fireEvent.click(screen.getByRole('button', { name: /ai assist/i }));
            await waitFor(() => {
                expect(screen.getByText('Consider caching')).toBeInTheDocument();
                expect(screen.getByText('Fix SQL injection first')).toBeInTheDocument();
            });
        });

        it('shows AI Analysis heading when suggestions are shown', async () => {
            const onAIAssist = vi.fn().mockResolvedValue({
                suggestions: ['Tip'],
                prioritizedFixes: [],
            });
            render(<ValidationSummaryPanel {...defaultProps({ onAIAssist })} />);
            fireEvent.click(screen.getByRole('button', { name: /ai assist/i }));
            await waitFor(() => {
                expect(screen.getByText('AI Analysis')).toBeInTheDocument();
            });
        });

        it('dismisses AI suggestions when Dismiss is clicked', async () => {
            const onAIAssist = vi.fn().mockResolvedValue({
                suggestions: ['Consider caching'],
                prioritizedFixes: [],
            });
            render(<ValidationSummaryPanel {...defaultProps({ onAIAssist })} />);
            fireEvent.click(screen.getByRole('button', { name: /ai assist/i }));
            await waitFor(() => {
                expect(screen.getByText('Consider caching')).toBeInTheDocument();
            });
            fireEvent.click(screen.getByRole('button', { name: /dismiss/i }));
            expect(screen.queryByText('Consider caching')).not.toBeInTheDocument();
        });

        it('shows Analyzing... while AI assist is loading', async () => {
            let resolveAI!: () => void;
            const onAIAssist = vi.fn().mockReturnValue(
                new Promise<null>((resolve) => {
                    resolveAI = () => resolve(null);
                })
            );
            render(<ValidationSummaryPanel {...defaultProps({ onAIAssist })} />);
            fireEvent.click(screen.getByRole('button', { name: /ai assist/i }));
            await waitFor(() => {
                expect(screen.getByRole('button', { name: /analyzing/i })).toBeInTheDocument();
            });
            await act(async () => {
                resolveAI();
            });
        });
    });
});
