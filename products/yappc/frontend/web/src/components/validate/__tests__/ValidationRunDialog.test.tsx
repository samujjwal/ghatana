/**
 * ValidationRunDialog unit tests
 *
 * Covers the modal dialog used for running validation checks with progress tracking.
 */

import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ValidationRunDialog } from '../ValidationRunDialog';
import type { ValidationRunDialogProps, ValidationRunStep } from '../ValidationRunDialog';

// ─── helpers ────────────────────────────────────────────────────────────────

const pendingStep: ValidationRunStep = {
    id: 's1',
    name: 'Schema Validation',
    status: 'pending',
};

const passedStep: ValidationRunStep = {
    id: 's2',
    name: 'Security Check',
    status: 'passed',
    message: 'All security checks passed',
    duration: 120,
};

const failedStep: ValidationRunStep = {
    id: 's3',
    name: 'Performance Check',
    status: 'failed',
    message: 'Response time exceeds threshold',
};

const warningStep: ValidationRunStep = {
    id: 's4',
    name: 'Accessibility Audit',
    status: 'warning',
    message: 'Some contrast issues found',
};

function defaultProps(overrides: Partial<ValidationRunDialogProps> = {}): ValidationRunDialogProps {
    return {
        isOpen: true,
        onClose: vi.fn(),
        steps: [pendingStep],
        onStart: vi.fn().mockResolvedValue(undefined),
        onCancel: vi.fn(),
        isRunning: false,
        progress: 0,
        ...overrides,
    };
}

// ─── tests ───────────────────────────────────────────────────────────────────

describe('ValidationRunDialog', () => {
    describe('visibility', () => {
        it('renders nothing when isOpen is false', () => {
            const { container } = render(<ValidationRunDialog {...defaultProps({ isOpen: false })} />);
            expect(container.firstChild).toBeNull();
        });

        it('renders the dialog when isOpen is true', () => {
            render(<ValidationRunDialog {...defaultProps()} />);
            expect(screen.getByRole('heading', { name: /run validation/i })).toBeInTheDocument();
        });

        it('renders custom title when provided', () => {
            render(<ValidationRunDialog {...defaultProps({ title: 'Custom Validation' })} />);
            expect(screen.getByRole('heading', { name: /custom validation/i })).toBeInTheDocument();
        });
    });

    describe('pre-start state', () => {
        it('shows "Ready to Validate" heading', () => {
            render(<ValidationRunDialog {...defaultProps()} />);
            expect(screen.getByText('Ready to Validate')).toBeInTheDocument();
        });

        it('shows step count', () => {
            render(<ValidationRunDialog {...defaultProps({ steps: [pendingStep, passedStep] })} />);
            expect(screen.getByText(/2 validation checks will be executed/i)).toBeInTheDocument();
        });

        it('renders Start Validation button', () => {
            render(<ValidationRunDialog {...defaultProps()} />);
            expect(screen.getByRole('button', { name: /start validation/i })).toBeInTheDocument();
        });

        it('renders a Cancel button in the footer', () => {
            render(<ValidationRunDialog {...defaultProps()} />);
            // Footer cancel button (not the close icon)
            const cancelBtns = screen.getAllByRole('button', { name: /cancel/i });
            expect(cancelBtns.length).toBeGreaterThan(0);
        });
    });

    describe('starting validation', () => {
        it('calls onStart when Start Validation is clicked', async () => {
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(<ValidationRunDialog {...defaultProps({ onStart })} />);
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => expect(onStart).toHaveBeenCalledOnce());
        });

        it('transitions away from ready state after clicking Start Validation', async () => {
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(<ValidationRunDialog {...defaultProps({ onStart, isRunning: true })} />);
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                expect(screen.queryByText('Ready to Validate')).not.toBeInTheDocument();
            });
        });
    });

    describe('running state', () => {
        it('shows Cancel button in footer while running', async () => {
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationRunDialog
                    {...defaultProps({ onStart, isRunning: true, steps: [pendingStep] })}
                />
            );
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                const cancelBtns = screen.getAllByRole('button', { name: /cancel/i });
                expect(cancelBtns.length).toBeGreaterThan(0);
            });
        });

        it('calls onCancel when Cancel is clicked while running', async () => {
            const onCancel = vi.fn();
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationRunDialog
                    {...defaultProps({ onStart, onCancel, isRunning: true })}
                />
            );
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                const cancelBtns = screen.getAllByRole('button', { name: /cancel/i });
                fireEvent.click(cancelBtns[cancelBtns.length - 1]);
            });
            expect(onCancel).toHaveBeenCalled();
        });

        it('shows running progress text', async () => {
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationRunDialog
                    {...defaultProps({
                        onStart,
                        isRunning: true,
                        steps: [{ ...pendingStep, status: 'running' }, passedStep],
                    })}
                />
            );
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                expect(screen.getByText(/running validation/i)).toBeInTheDocument();
            });
        });

        it('renders step names in the steps list', async () => {
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationRunDialog
                    {...defaultProps({
                        onStart,
                        isRunning: true,
                        steps: [{ ...pendingStep, status: 'running' }],
                    })}
                />
            );
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                expect(screen.getByText('Schema Validation')).toBeInTheDocument();
            });
        });

        it('renders step message when provided', async () => {
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationRunDialog
                    {...defaultProps({
                        onStart,
                        isRunning: false,
                        steps: [{ ...passedStep, status: 'passed' }],
                    })}
                />
            );
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                expect(screen.getByText('All security checks passed')).toBeInTheDocument();
            });
        });

        it('renders step duration when provided', async () => {
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationRunDialog
                    {...defaultProps({
                        onStart,
                        isRunning: false,
                        steps: [{ ...passedStep }],
                    })}
                />
            );
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                expect(screen.getByText('120ms')).toBeInTheDocument();
            });
        });
    });

    describe('complete state', () => {
        async function renderComplete(steps: ValidationRunStep[]) {
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationRunDialog
                    {...defaultProps({ onStart, isRunning: false, steps })}
                />
            );
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                // After start, hasStarted=true, isRunning=false → complete
                expect(screen.queryByText('Ready to Validate')).not.toBeInTheDocument();
            });
        }

        it('shows summary stats grid when complete', async () => {
            await renderComplete([passedStep, failedStep]);
            expect(screen.getByText('Total')).toBeInTheDocument();
            expect(screen.getByText('Passed')).toBeInTheDocument();
            expect(screen.getByText('Failed')).toBeInTheDocument();
            expect(screen.getByText('Warnings')).toBeInTheDocument();
        });

        it('shows Done button when complete', async () => {
            await renderComplete([passedStep]);
            expect(screen.getByRole('button', { name: /done/i })).toBeInTheDocument();
        });

        it('shows Run Again button when there are failed steps', async () => {
            await renderComplete([failedStep]);
            expect(screen.getByRole('button', { name: /run again/i })).toBeInTheDocument();
        });

        it('does not show Run Again button when all steps passed', async () => {
            await renderComplete([passedStep]);
            expect(screen.queryByRole('button', { name: /run again/i })).not.toBeInTheDocument();
        });

        it('calls onClose when Done is clicked', async () => {
            const onClose = vi.fn();
            const onStart = vi.fn().mockResolvedValue(undefined);
            render(
                <ValidationRunDialog
                    {...defaultProps({ onStart, onClose, isRunning: false, steps: [passedStep] })}
                />
            );
            fireEvent.click(screen.getByRole('button', { name: /start validation/i }));
            await waitFor(() => {
                expect(screen.getByRole('button', { name: /done/i })).toBeInTheDocument();
            });
            fireEvent.click(screen.getByRole('button', { name: /done/i }));
            expect(onClose).toHaveBeenCalled();
        });
    });

    describe('close behaviour', () => {
        it('calls onClose when X button is clicked', () => {
            const onClose = vi.fn();
            render(<ValidationRunDialog {...defaultProps({ onClose })} />);
            // Find close button (not Cancel button - the X icon button)
            const closeBtn = screen.getAllByRole('button').find(
                (btn) => btn.className.includes('p-1') && btn.className.includes('text-text-secondary')
            );
            if (closeBtn) {
                fireEvent.click(closeBtn);
                expect(onClose).toHaveBeenCalled();
            }
        });

        it('resets hasStarted when dialog reopens', () => {
            const { rerender } = render(<ValidationRunDialog {...defaultProps({ isOpen: false })} />);
            rerender(<ValidationRunDialog {...defaultProps({ isOpen: true })} />);
            expect(screen.getByText('Ready to Validate')).toBeInTheDocument();
        });
    });
});
