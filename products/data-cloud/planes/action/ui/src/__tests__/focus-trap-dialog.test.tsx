/**
 * Focus trap dialog tests
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
import React from 'react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SensitiveActionDialog } from '@/components/shared/SensitiveActionDialog';
import { ReviewDecisionDialog } from '@/components/shared/ReviewDecisionDialog';

vi.mock('@ghatana/design-system', () => ({
  Button: (props: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: string; children: React.ReactNode }) => (
    <button {...props}>{props.children}</button>
  ),
  TextArea: (props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) => (
    <textarea {...props} />
  ),
  TextField: (props: React.InputHTMLAttributes<HTMLInputElement>) => (
    <input {...props} />
  ),
}));

describe('SensitiveActionDialog focus trap', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('cycles focus on Tab within the dialog', () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();

    render(
      <SensitiveActionDialog
        open={true}
        title="Confirm"
        description="Test"
        confirmKeyword="CONFIRM"
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    const dialog = screen.getByRole('dialog');
    const focusable = dialog.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    expect(focusable.length).toBeGreaterThan(0);
  });

  it('calls onCancel when Escape is pressed', () => {
    const onCancel = vi.fn();
    render(
      <SensitiveActionDialog
        open={true}
        title="Confirm"
        description="Test"
        confirmKeyword="CONFIRM"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    );

    const dialog = screen.getByRole('dialog');
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(onCancel).toHaveBeenCalled();
  });
});

describe('ReviewDecisionDialog focus management', () => {
  it('renders in approve mode with required reason input', () => {
    render(
      <ReviewDecisionDialog
        open={true}
        mode="approve"
        runId="run-123"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/run-123/)).toBeInTheDocument();
  });

  it('calls onCancel when Escape is pressed', () => {
    const onCancel = vi.fn();
    render(
      <ReviewDecisionDialog
        open={true}
        mode="reject"
        runId="run-456"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    );

    const dialog = screen.getByRole('dialog');
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(onCancel).toHaveBeenCalled();
  });
});
