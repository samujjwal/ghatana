/**
 * ExportDialog unit tests
 *
 * @doc.type test
 * @doc.purpose Verify ExportDialog renders, selects formats, and handles
 *              export lifecycle (submit → processing → ready/fail)
 * @doc.layer product
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ExportDialog, ExportArtifact, ExportFormat } from '../ExportDialog';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeArtifact(overrides: Partial<ExportArtifact> = {}): ExportArtifact {
  return {
    id: 'art-1',
    projectId: 'proj-1',
    format: 'MARKDOWN' as ExportFormat,
    status: 'READY',
    includeRequirements: true,
    includeDiagrams: true,
    includeCode: false,
    downloadUrl: 'https://cdn.example.com/export-art-1.zip',
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

function renderDialog(overrides: Partial<React.ComponentProps<typeof ExportDialog>> = {}) {
  const onClose = vi.fn();
  const onCreateExport = vi.fn();
  const props = {
    projectId: 'proj-1',
    projectName: 'My Project',
    onClose,
    onCreateExport,
    ...overrides,
  };

  const utils = render(
    <ExportDialog {...props} />
  );

  return { ...utils, onClose: props.onClose, onCreateExport: props.onCreateExport };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ExportDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the dialog with a title containing the project name', () => {
    renderDialog();
    expect(screen.getByRole('dialog', { name: /export.*My Project/i })).toBeInTheDocument();
  });

  it('renders all format options', () => {
    renderDialog();
    expect(screen.getByText('Markdown')).toBeInTheDocument();
    expect(screen.getByText('JSON')).toBeInTheDocument();
    expect(screen.getByText('HTML')).toBeInTheDocument();
    expect(screen.getByText('ZIP Bundle')).toBeInTheDocument();
  });

  it('selects a format when clicked and reflects aria-pressed', () => {
    renderDialog();
    const jsonButton = screen.getByRole('button', { name: /json/i });
    fireEvent.click(jsonButton);
    expect(jsonButton).toHaveAttribute('aria-pressed', 'true');
  });

  it('calls onClose when the close button is clicked', () => {
    const { onClose } = renderDialog();
    fireEvent.click(screen.getByRole('button', { name: /close export dialog/i }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls onClose when Cancel is clicked', () => {
    const { onClose } = renderDialog();
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls onCreateExport with correct params when Export is clicked', async () => {
    const artifact = makeArtifact({ status: 'PROCESSING', downloadUrl: undefined });
    const { onCreateExport } = renderDialog({
      onCreateExport: vi.fn().mockResolvedValue(artifact),
    });

    fireEvent.click(screen.getByRole('button', { name: /export$/i }));

    await waitFor(() =>
      expect(onCreateExport).toHaveBeenCalledWith({
        projectId: 'proj-1',
        format: 'MARKDOWN',
        includeRequirements: true,
        includeDiagrams: true,
        includeCode: false,
      })
    );
  });

  it('shows processing state after export is triggered', async () => {
    const artifact = makeArtifact({ status: 'PROCESSING', downloadUrl: undefined });
    renderDialog({
      onCreateExport: vi.fn().mockResolvedValue(artifact),
    });

    fireEvent.click(screen.getByRole('button', { name: /export$/i }));

    await waitFor(() =>
      expect(screen.getByText(/generating your export/i)).toBeInTheDocument()
    );
  });

  it('shows download button when export is READY', async () => {
    const artifact = makeArtifact({ status: 'READY' });
    renderDialog({
      onCreateExport: vi.fn().mockResolvedValue(artifact),
    });

    fireEvent.click(screen.getByRole('button', { name: /export$/i }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /download/i })).toBeInTheDocument()
    );
  });

  it('displays an error message when the mutation rejects', async () => {
    renderDialog({
      onCreateExport: vi.fn().mockRejectedValue(new Error('Server error')),
    });

    fireEvent.click(screen.getByRole('button', { name: /export$/i }));

    await waitFor(() =>
      expect(screen.getByText(/server error/i)).toBeInTheDocument()
    );
  });

  it('lists past export artifacts when provided', () => {
    const past = [
      makeArtifact({ id: 'art-old', format: 'JSON', status: 'READY' }),
    ];
    renderDialog({ pastExports: past });
    expect(screen.getByText('Previous Exports')).toBeInTheDocument();
    expect(screen.getAllByText('JSON').length).toBeGreaterThan(0);
  });

  it('toggles content inclusions with aria-pressed', () => {
    renderDialog();
    const requirementsBtn = screen.getByRole('button', { name: /requirements/i });
    // defaults to true (pressed)
    expect(requirementsBtn).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(requirementsBtn);
    expect(requirementsBtn).toHaveAttribute('aria-pressed', 'false');
  });
});
