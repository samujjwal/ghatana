import { act, renderHook } from '@testing-library/react';
import { Provider } from 'jotai';
import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SessionBootstrap from '../../../../lib/auth/session';

vi.mock('../../../../lib/api/workflow-client', () => ({
  workflowClient: {
    executeWorkflow: vi.fn(),
    getExecutionStatus: vi.fn(),
    cancelExecution: vi.fn(),
  },
}));

import { workflowClient } from '../../../../lib/api/workflow-client';
import { useWorkflowExecution } from '../useWorkflowExecution';
import { ExecutionStatus } from '../../types/workflow.types';

function Wrapper({ children }: { children: React.ReactNode }) {
  return <Provider>{children}</Provider>;
}

describe('useWorkflowExecution', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    SessionBootstrap.setTenantId('tenant-workflows');
  });

  it('starts a synthetic running execution from the canonical execute response without calling unsupported status lookup', async () => {
    vi.mocked(workflowClient.executeWorkflow).mockResolvedValue({
      executionId: 'exec-1',
      workflowId: 'wf-1',
      status: ExecutionStatus.RUNNING,
    });

    const { result } = renderHook(() => useWorkflowExecution(), { wrapper: Wrapper });

    await act(async () => {
      await result.current.executeWorkflow('wf-1');
    });

    expect(workflowClient.getExecutionStatus).not.toHaveBeenCalled();
    expect(result.current.execution.id).toBe('exec-1');
    expect(result.current.execution.workflowId).toBe('wf-1');
    expect(result.current.execution.status).toBe('RUNNING');
    expect(result.current.execution.tenantId).toBe('tenant-workflows');
  });
});