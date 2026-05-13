import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  MissingScopeContextError,
  fetchPhaseTransitionPreview,
  fetchProjectSnapshot,
} from '../PhaseCockpitDataService';

const { getProjectMock, getNextPhaseMock } = vi.hoisted(() => ({
  getProjectMock: vi.fn(),
  getNextPhaseMock: vi.fn(),
}));

vi.mock('@/clients/generated/api', () => ({
  ProjectsService: {
    getProject: getProjectMock,
  },
  LifecycleService: {
    getNextPhase: getNextPhaseMock,
  },
}));

describe('PhaseCockpitDataService', () => {
  beforeEach(() => {
    getProjectMock.mockReset();
    getNextPhaseMock.mockReset();
  });

  it('throws MissingScopeContextError when workspaceId is missing', async () => {
    await expect(fetchProjectSnapshot('project-1', '')).rejects.toMatchObject({
      name: 'MissingScopeContextError',
      missingScope: 'workspaceId',
      operation: 'fetchProjectSnapshot',
    } satisfies Partial<MissingScopeContextError>);
  });

  it('throws MissingScopeContextError when projectId is missing', async () => {
    await expect(fetchProjectSnapshot('', 'workspace-1')).rejects.toMatchObject({
      name: 'MissingScopeContextError',
      missingScope: 'projectId',
      operation: 'fetchProjectSnapshot',
    } satisfies Partial<MissingScopeContextError>);
  });

  it('normalizes generated project payload currentPhase to lifecyclePhase', async () => {
    getProjectMock.mockResolvedValue({
      project: {
        id: 'project-1',
        ownerWorkspaceId: 'workspace-1',
        currentPhase: 'GENERATE',
      },
    });

    const snapshot = await fetchProjectSnapshot('project-1', 'workspace-1');

    expect(getProjectMock).toHaveBeenCalledWith('project-1', 'workspace-1');
    expect(snapshot.lifecyclePhase).toBe('GENERATE');
  });

  it('uses generated next-phase service for transition preview', async () => {
    getNextPhaseMock.mockResolvedValue({
      id: 'VALIDATE',
      name: 'Validate',
      order: 3,
    });

    const preview = await fetchPhaseTransitionPreview('SHAPE', 'project-1');

    expect(getNextPhaseMock).toHaveBeenCalledWith('SHAPE');
    expect(preview).toMatchObject({
      projectId: 'project-1',
      currentPhase: 'SHAPE',
      nextPhase: 'VALIDATE',
      canAdvance: true,
    });
  });
});
