import React from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { LifecyclePhase } from '@/types/lifecycle';
import type { ProjectWithOwnership } from '@/state/atoms/workspaceAtom';

const mockUseWorkspaceContext = vi.hoisted(() => vi.fn());
const mockProviderProps = vi.hoisted(() => vi.fn());

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'included-project', canvasId: 'canvas-1' }),
  };
});

vi.mock('@/services/canvas/lifecycle/CanvasLifecycle', () => ({
  useCanvasLifecycle: () => ({ currentPhase: LifecyclePhase.CONTEXT }),
}));

vi.mock('@/hooks/useWorkspaceData', () => ({
  useWorkspaceContext: mockUseWorkspaceContext,
}));

vi.mock('@/components/canvas/CanvasWorkspaceProvider', () => ({
  CanvasWorkspaceProvider: (props: Record<string, unknown>) => {
    mockProviderProps(props);
    return <div data-testid="canvas-workspace-provider" />;
  },
}));

import CanvasRoute from '../CanvasRoute';

function buildProject(overrides: Partial<ProjectWithOwnership>): ProjectWithOwnership {
  return {
    id: 'owned-project',
    name: 'Project',
    description: '',
    type: 'FULL_STACK',
    status: 'ACTIVE',
    lifecyclePhase: 'SHAPE',
    ownerWorkspaceId: 'ws-1',
    isOwned: true,
    isDefault: false,
    aiNextActions: [],
    aiHealthScore: 80,
    role: 'EDITOR',
    capabilities: { read: true, update: true, create: true, delete: true, comment: true },
    createdAt: '2026-04-01T00:00:00.000Z',
    updatedAt: '2026-04-02T00:00:00.000Z',
    ...overrides,
  };
}

describe('CanvasRoute access forwarding', () => {
  beforeEach(() => {
    mockProviderProps.mockClear();
    mockUseWorkspaceContext.mockReturnValue({
      ownedProjects: [buildProject({ id: 'owned-project' })],
      includedProjects: [
        buildProject({
          id: 'included-project',
          isOwned: false,
          isIncluded: true,
          readOnly: true,
          role: 'VIEWER',
          capabilities: { read: true, update: false, create: false, delete: false, comment: true },
        }),
      ],
    });
  });

  it('passes backend project access metadata into the mounted canvas provider', () => {
    render(<CanvasRoute />);

    expect(screen.getByTestId('canvas-workspace-provider')).toBeInTheDocument();
    expect(mockProviderProps).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 'included-project',
        currentPhase: LifecyclePhase.CONTEXT,
        projectAccess: expect.objectContaining({
          id: 'included-project',
          readOnly: true,
          role: 'VIEWER',
        }),
      })
    );
  });
});
