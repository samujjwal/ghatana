import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PipelineListPage } from '@/pages/PipelineListPage';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';
import * as pipelineApi from '@/api/pipeline.api';
import { useAuth } from '@/context/AuthContext';

vi.mock('@/api/pipeline.api');
vi.mock('@/context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

describe('PipelineListPage', () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue({
      authToken: 'jwt-token',
      sessionToken: null,
      isAuthenticated: true,
      isBootstrappingSession: false,
      isVerifyingAuth: false,
      roles: ['operator'],
      hasRole: (role) => role === 'operator',
      hasAnyRole: (roles) => roles.includes('operator'),
      loginWithToken: vi.fn(),
      loginWithPlatform: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(pipelineApi.listPipelines).mockResolvedValue([
      {
        id: 'pipe-1',
        name: 'Fraud Triage',
        status: 'PUBLISHED',
        version: 3,
        stages: [],
      },
    ]);
    vi.mocked(pipelineApi.listPipelineVersions).mockResolvedValue([
      {
        version: 1,
        versionLabel: 'stable-v1',
        versionStatus: 'PUBLISHED',
        name: 'Fraud Triage',
        createdAt: new Date('2026-04-01T09:00:00.000Z').toISOString(),
        updatedAt: new Date('2026-04-01T09:00:00.000Z').toISOString(),
        updatedBy: 'ops-1',
      },
      {
        version: 2,
        versionLabel: 'stable-v2',
        versionStatus: 'PUBLISHED',
        name: 'Fraud Triage',
        createdAt: new Date('2026-04-10T09:00:00.000Z').toISOString(),
        updatedAt: new Date('2026-04-10T09:00:00.000Z').toISOString(),
        updatedBy: 'ops-2',
      },
      {
        version: 3,
        versionLabel: '',
        versionStatus: 'DRAFT',
        name: 'Fraud Triage',
        createdAt: new Date('2026-04-20T09:00:00.000Z').toISOString(),
        updatedAt: new Date('2026-04-20T09:00:00.000Z').toISOString(),
        updatedBy: 'ops-3',
      },
    ]);
    vi.mocked(pipelineApi.rollbackPipeline).mockResolvedValue({
      rolledBack: true,
      pipelineId: 'pipe-1',
      restoredVersion: 2,
      previousVersion: 3,
      status: 'DRAFT',
      timestamp: new Date().toISOString(),
      auditId: 'audit-rollback-1',
    });
  });

  it('opens rollback history and submits a governed rollback', async () => {
    const user = userEvent.setup();

    render(<PipelineListPage />, { wrapper: createAepTestWrapper() });

    await waitFor(() => expect(screen.getByText('Fraud Triage')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /rollback/i }));

    await waitFor(() => expect(screen.getByText('Select rollback target')).toBeInTheDocument());
    await user.click(screen.getByLabelText('v2'));
    await user.type(screen.getByLabelText('Reason'), 'Regression detected after the latest publish');
    await user.type(screen.getByLabelText('Type ROLLBACK to confirm'), 'ROLLBACK');
    await user.click(screen.getByRole('button', { name: /confirm rollback/i }));

    await waitFor(() => expect(pipelineApi.rollbackPipeline).toHaveBeenCalledWith(
      'pipe-1',
      2,
      expect.objectContaining({
        actor: 'operator',
        reason: 'Regression detected after the latest publish',
      }),
      'default',
    ));
    expect(await screen.findByText(/rolled back to version 2 with audit audit-rollback-1/i)).toBeInTheDocument();
  });
});
