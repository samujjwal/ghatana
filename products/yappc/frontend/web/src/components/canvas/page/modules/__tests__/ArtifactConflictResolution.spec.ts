/**
 * Artifact Conflict Resolution Tests
 *
 * @doc.type test
 * @doc.purpose Test artifact conflict resolution UX with reload, compare, reapply, discard, retry options
 * @doc.layer product
 * @doc.pattern Component Test
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ArtifactConflictResolution } from '../ArtifactConflictResolution';

describe('ArtifactConflictResolution', () => {
  const mockConflict = {
    artifactId: 'test-artifact-1',
    localVersion: 1,
    remoteVersion: 2,
    conflictType: 'version-mismatch' as const,
    lastSyncedAt: '2026-03-27T10:00:00Z',
    remoteUpdatedAt: '2026-03-27T11:00:00Z',
  };

  const mockCallbacks = {
    onReload: vi.fn(),
    onCompare: vi.fn(),
    onReapply: vi.fn(),
    onDiscard: vi.fn(),
    onRetry: vi.fn(),
  };

  it('should render conflict information correctly', () => {
    render(<ArtifactConflictResolution conflict={mockConflict} {...mockCallbacks} />);

    expect(screen.getByText('Artifact Version Conflict')).toBeDefined();
    expect(screen.getByText(/v1/)).toBeDefined();
    expect(screen.getByText(/v2/)).toBeDefined();
  });

  it('should display correct description for version-mismatch conflict', () => {
    render(<ArtifactConflictResolution conflict={mockConflict} {...mockCallbacks} />);

    expect(screen.getByText(/The artifact on the server has been updated/)).toBeDefined();
  });

  it('should display correct description for concurrent-edit conflict', () => {
    const concurrentConflict = { ...mockConflict, conflictType: 'concurrent-edit' as const };
    render(<ArtifactConflictResolution conflict={concurrentConflict} {...mockCallbacks} />);

    expect(screen.getByText(/Another user has made changes/)).toBeDefined();
  });

  it('should display correct description for server-update conflict', () => {
    const serverConflict = { ...mockConflict, conflictType: 'server-update' as const };
    render(<ArtifactConflictResolution conflict={serverConflict} {...mockCallbacks} />);

    expect(screen.getByText(/The server has updated this artifact/)).toBeDefined();
  });

  it('should call onReload when Reload button is clicked', () => {
    render(
      React.createElement(ArtifactConflictResolution, {
        conflict: mockConflict,
        ...mockCallbacks,
      })
    );

    fireEvent.click(screen.getByText('Reload'));
    expect(mockCallbacks.onReload).toHaveBeenCalledTimes(1);
  });

  it('should call onCompare when Compare button is clicked', () => {
    render(<ArtifactConflictResolution conflict={mockConflict} {...mockCallbacks} />);

    fireEvent.click(screen.getByText('Compare'));
    expect(mockCallbacks.onCompare).toHaveBeenCalledTimes(1);
  });

  it('should call onReapply when Reapply button is clicked', () => {
    render(<ArtifactConflictResolution conflict={mockConflict} {...mockCallbacks} />);

    fireEvent.click(screen.getByText('Reapply'));
    expect(mockCallbacks.onReapply).toHaveBeenCalledTimes(1);
  });

  it('should call onDiscard when Discard button is clicked', () => {
    render(<ArtifactConflictResolution conflict={mockConflict} {...mockCallbacks} />);

    fireEvent.click(screen.getByText('Discard'));
    expect(mockCallbacks.onDiscard).toHaveBeenCalledTimes(1);
  });

  it('should call onRetry when Retry button is clicked', () => {
    render(<ArtifactConflictResolution conflict={mockConflict} {...mockCallbacks} />);

    fireEvent.click(screen.getByText('Retry'));
    expect(mockCallbacks.onRetry).toHaveBeenCalledTimes(1);
  });

  it('should display timestamps correctly', () => {
    render(<ArtifactConflictResolution conflict={mockConflict} {...mockCallbacks} />);

    expect(screen.getByText(/Last synced:/)).toBeDefined();
    expect(screen.getByText(/Remote updated:/)).toBeDefined();
  });
});
