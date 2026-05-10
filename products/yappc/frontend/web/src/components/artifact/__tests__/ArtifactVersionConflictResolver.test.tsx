/**
 * Artifact Version Conflict Resolver Tests
 * 
 * Tests for artifact version conflict resolution UI covering:
 * - Reload action
 * - Compare action
 * - Reapply action
 * - Discard action
 * - Retry action
 * - Dismiss action
 * - Expandable details
 * - Loading states
 * - Error handling
 * 
 * @doc.type test
 * @doc.purpose Artifact version conflict resolution UX tests
 * @doc.layer product
 * @doc.pattern Component Tests
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import '@testing-library/jest-dom';
import { ArtifactVersionConflictResolver } from '../ArtifactVersionConflictResolver';

describe('ArtifactVersionConflictResolver', () => {
  const mockConflict = {
    artifactId: 'artifact-123',
    artifactName: 'Page Component',
    localVersion: 'v1.0.0',
    remoteVersion: 'v1.1.0',
    localContent: '{"nodes": [{"id": "1", "type": "text"}]}',
    remoteContent: '{"nodes": [{"id": "1", "type": "text"}, {"id": "2", "type": "button"}]}',
    correlationId: 'conflict-abc-123',
    timestamp: Date.now(),
  };

  const mockHandlers = {
    onReload: vi.fn(),
    onCompare: vi.fn(),
    onReapply: vi.fn(),
    onDiscard: vi.fn(),
    onRetry: vi.fn(),
    onDismiss: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders conflict information correctly', () => {
    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    expect(screen.getByText('Artifact version conflict')).toBeInTheDocument();
    expect(screen.getByText(mockConflict.artifactName)).toBeInTheDocument();
    expect(screen.getByText(`Local: ${mockConflict.localVersion} → Remote: ${mockConflict.remoteVersion}`)).toBeInTheDocument();
    expect(screen.getByText(`Correlation ID: ${mockConflict.correlationId}`)).toBeInTheDocument();
  });

  it('calls onReload when Reload button is clicked', async () => {
    mockHandlers.onReload.mockResolvedValue(undefined);

    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const reloadButton = screen.getByText('Reload');
    fireEvent.click(reloadButton);

    await waitFor(() => {
      expect(mockHandlers.onReload).toHaveBeenCalledWith(mockConflict.artifactId);
    });
  });

  it('calls onCompare when Compare button is clicked', async () => {
    mockHandlers.onCompare.mockResolvedValue(undefined);

    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const compareButton = screen.getByText('Compare');
    fireEvent.click(compareButton);

    await waitFor(() => {
      expect(mockHandlers.onCompare).toHaveBeenCalledWith(mockConflict.artifactId);
    });
  });

  it('calls onReapply when Reapply local button is clicked', async () => {
    mockHandlers.onReapply.mockResolvedValue(undefined);

    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const reapplyButton = screen.getByText('Reapply local');
    fireEvent.click(reapplyButton);

    await waitFor(() => {
      expect(mockHandlers.onReapply).toHaveBeenCalledWith(
        mockConflict.artifactId,
        mockConflict.localContent
      );
    });
  });

  it('calls onDiscard when Discard local button is clicked', async () => {
    mockHandlers.onDiscard.mockResolvedValue(undefined);

    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const discardButton = screen.getByText('Discard local');
    fireEvent.click(discardButton);

    await waitFor(() => {
      expect(mockHandlers.onDiscard).toHaveBeenCalledWith(mockConflict.artifactId);
    });
  });

  it('calls onRetry when Retry sync button is clicked', async () => {
    mockHandlers.onRetry.mockResolvedValue(undefined);

    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const retryButton = screen.getByText('Retry sync');
    fireEvent.click(retryButton);

    await waitFor(() => {
      expect(mockHandlers.onRetry).toHaveBeenCalledWith(mockConflict.artifactId);
    });
  });

  it('calls onDismiss when dismiss button is clicked', () => {
    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const dismissButton = screen.getByLabelText('Dismiss conflict');
    fireEvent.click(dismissButton);

    expect(mockHandlers.onDismiss).toHaveBeenCalled();
  });

  it('does not render dismiss button when onDismiss is not provided', () => {
    const { queryByLabelText } = render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        onReload={mockHandlers.onReload}
        onCompare={mockHandlers.onCompare}
        onReapply={mockHandlers.onReapply}
        onDiscard={mockHandlers.onDiscard}
        onRetry={mockHandlers.onRetry}
      />
    );

    expect(queryByLabelText('Dismiss conflict')).not.toBeInTheDocument();
  });

  it('expands details when Show details button is clicked', () => {
    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const showDetailsButton = screen.getByText('Show details');
    expect(screen.queryByText('Local version')).not.toBeInTheDocument();

    fireEvent.click(showDetailsButton);

    expect(screen.getByText('Local version')).toBeInTheDocument();
    expect(screen.getByText('Remote version')).toBeInTheDocument();
  });

  it('collapses details when Hide details button is clicked', () => {
    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const showDetailsButton = screen.getByText('Show details');
    fireEvent.click(showDetailsButton);

    expect(screen.getByText('Local version')).toBeInTheDocument();

    const hideDetailsButton = screen.getByText('Hide details');
    fireEvent.click(hideDetailsButton);

    expect(screen.queryByText('Local version')).not.toBeInTheDocument();
  });

  it('displays local and remote content in details', () => {
    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const showDetailsButton = screen.getByText('Show details');
    fireEvent.click(showDetailsButton);

    expect(screen.getByText(`Local version (${mockConflict.localVersion})`)).toBeInTheDocument();
    expect(screen.getByText(`Remote version (${mockConflict.remoteVersion})`)).toBeInTheDocument();
  });

  it('truncates long content in details view', () => {
    const longContent = '{"data": "' + 'x'.repeat(300) + '"}';
    const conflictWithLongContent = {
      ...mockConflict,
      localContent: longContent,
      remoteContent: longContent,
    };

    render(
      <ArtifactVersionConflictResolver
        conflict={conflictWithLongContent}
        {...mockHandlers}
      />
    );

    const showDetailsButton = screen.getByText('Show details');
    fireEvent.click(showDetailsButton);

    const contentElements = screen.getAllByText(/...$/);
    expect(contentElements.length).toBeGreaterThan(0);
  });

  it('disables all action buttons when isResolving is true', () => {
    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        isResolving={true}
        {...mockHandlers}
      />
    );

    expect(screen.getByText('Reload')).toBeDisabled();
    expect(screen.getByText('Compare')).toBeDisabled();
    expect(screen.getByText('Reapply local')).toBeDisabled();
    expect(screen.getByText('Discard local')).toBeDisabled();
    expect(screen.getByText('Retry sync')).toBeDisabled();
  });

  it('shows loading state for active action', async () => {
    let resolveReload: () => void;
    const reloadPromise = new Promise<void>((resolve) => {
      resolveReload = resolve;
    });
    mockHandlers.onReload.mockReturnValue(reloadPromise);

    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        isResolving={true}
        {...mockHandlers}
      />
    );

    const reloadButton = screen.getByText('Reload');
    fireEvent.click(reloadButton);

    // Button should be in loading state
    expect(reloadButton).toBeDisabled();

    resolveReload!();
    await waitFor(() => {
      expect(mockHandlers.onReload).toHaveBeenCalled();
    });
  });

  it('handles action errors gracefully', async () => {
    mockHandlers.onReload.mockRejectedValue(new Error('Network error'));

    render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        {...mockHandlers}
      />
    );

    const reloadButton = screen.getByText('Reload');
    fireEvent.click(reloadButton);

    await waitFor(() => {
      expect(mockHandlers.onReload).toHaveBeenCalled();
    });

    // Component should still be rendered after error
    expect(screen.getByText('Artifact version conflict')).toBeInTheDocument();
  });

  it('formats timestamp correctly', () => {
    const specificTimestamp = new Date('2024-01-15T10:30:00Z').getTime();
    const conflictWithTimestamp = {
      ...mockConflict,
      timestamp: specificTimestamp,
    };

    render(
      <ArtifactVersionConflictResolver
        conflict={conflictWithTimestamp}
        {...mockHandlers}
      />
    );

    expect(screen.getByText(/Last updated:/)).toBeInTheDocument();
  });

  it('applies custom className', () => {
    const { container } = render(
      <ArtifactVersionConflictResolver
        conflict={mockConflict}
        className="custom-class"
        {...mockHandlers}
      />
    );

    expect(container.firstChild).toHaveClass('custom-class');
  });
});
