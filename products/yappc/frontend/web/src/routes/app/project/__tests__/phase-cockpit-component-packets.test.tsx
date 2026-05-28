import React from 'react';
import { QueryClient } from '@tanstack/react-query';
import { screen } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { render } from '@/test-utils/test-utils';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { currentUserAtom } from '@/stores/user.store';
import type { PhaseCockpitPacket } from '@/types/phasePacket';

const { mockUsePhasePacket } = vi.hoisted(() => ({
  mockUsePhasePacket: vi.fn(),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'project-component' }),
    useNavigate: () => vi.fn(),
  };
});

vi.mock('@/hooks/usePhasePacket', () => ({
  usePhasePacket: mockUsePhasePacket,
}));

vi.mock('../PhaseEmbeddedSurface', () => ({
  PhaseEmbeddedSurface: ({ phase }: { readonly phase: string }) => (
    <div data-testid={`${phase}-embedded-surface`} />
  ),
}));

import { RunCockpitRoute, ShapeCockpitRoute, ValidateCockpitRoute } from '../_phaseCockpit';

function basePacket(overrides: Partial<PhaseCockpitPacket> = {}): PhaseCockpitPacket {
  const phase = overrides.phase ?? 'shape';
  return {
    phase,
    projectId: 'project-component',
    projectName: 'Component Contract Project',
    tenantId: 'tenant-component',
    workspaceId: 'workspace-component',
    workspaceName: 'Component Workspace',
    actor: {
      actorId: 'user-component',
      actorName: 'Component Tester',
      role: 'OWNER',
      isOwner: true,
      isAdmin: true,
    },
    lifecyclePhase: phase.toUpperCase(),
    tenantTier: 'PRO',
    enabledPhaseFlags: [phase],
    capabilities: {
      canRead: true,
      canCreate: true,
      canUpdate: true,
      canDelete: false,
      canApprove: true,
      canReject: true,
      canRollback: true,
    },
    blockers: [],
    readiness: {
      canAdvance: true,
      nextPhase: 'validate',
      missingPrerequisites: [],
      completenessScore: 0.91,
      isDegraded: false,
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.88,
    },
    requiredArtifacts: [{
      artifactId: 'artifact-required-1',
      artifactType: 'document',
      title: 'Shape contract',
      description: 'Canonical shape contract',
      isComplete: true,
    }],
    completedArtifacts: [{
      artifactId: 'artifact-complete-1',
      artifactType: 'document',
      version: '1.0.0',
      title: 'Intent brief',
      completedAt: '2026-05-26T18:30:00.000Z',
      completedBy: 'user-component',
      evidenceId: 'evidence-intent-1',
    }],
    activityFeed: [{
      id: 'activity-1',
      type: 'audit',
      action: 'phase.shape.ready',
      summary: 'Shape phase ready',
      actor: 'user-component',
      timestamp: '2026-05-26T18:31:00.000Z',
      severity: 'INFO',
      eventType: 'PHASE_PACKET_BUILT',
      success: true,
      outcome: 'SUCCESS',
      correlationId: 'corr-component-1',
    }],
    evidence: [{
      id: 'evidence-1',
      type: 'artifact',
      title: 'Shape evidence',
      description: 'Shape artifact evidence is available.',
      timestamp: '2026-05-26T18:31:00.000Z',
      metadata: { source: 'data-cloud' },
      evidenceId: 'evidence-1',
    }],
    governance: [{
      id: 'governance-1',
      type: 'derived',
      outcome: 'Policy allows phase action',
      actor: 'policy-engine',
      timestamp: '2026-05-26T18:31:00.000Z',
      metadata: {},
      policyDecisionId: 'policy-1',
    }],
    platformRunStatus: {
      runId: 'run-component-1',
      status: 'SUCCEEDED',
      platform: 'kernel',
      startedAt: '2026-05-26T18:30:00.000Z',
      completedAt: '2026-05-26T18:31:00.000Z',
      traceId: 'trace-component-1',
      evidenceIds: ['evidence-run-component-1'],
    },
    availableActions: [{
      actionId: `${phase}-primary`,
      label: 'Run guided action',
      description: 'Advance with backend authorization.',
      enabled: true,
      requiredPermission: 'update',
      category: 'phase-transition',
      severity: 'high',
      confirmationRequired: true,
      idempotencyKey: `${phase}-primary-component`,
      auditType: `phase.${phase}.primary.requested`,
      parameters: {},
    }],
    dashboardActions: {
      primaryAction: `${phase}-primary`,
      blockedActions: [],
      reviewRequiredActions: [],
      safeToContinueActions: [`${phase}-primary`],
    },
    healthSignals: {
      preview: {
        isHealthy: true,
        status: 'healthy',
        issues: [],
      },
      generation: {
        isHealthy: true,
        status: 'ready',
        lastGeneratedAt: '2026-05-26T18:30:00.000Z',
        issues: [],
      },
      runtime: {
        isHealthy: true,
        status: 'ready',
        lastDeployedAt: '2026-05-26T18:30:00.000Z',
        issues: [],
      },
    },
    timestamp: Date.parse('2026-05-26T18:31:00.000Z'),
    correlationId: 'corr-component-1',
    ...overrides,
  };
}

function renderCockpit(node: React.ReactNode, packet: PhaseCockpitPacket) {
  mockUsePhasePacket.mockReturnValue({
    packet,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  });

  const store = createStore();
  store.set(currentWorkspaceIdAtom, 'workspace-component');
  store.set(currentUserAtom, {
    id: 'user-component',
    email: 'component@example.com',
    name: 'Component Tester',
    role: 'ADMIN',
    tenantId: 'tenant-component',
    workspaceIds: ['workspace-component'],
  });

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(<Provider store={store}>{node}</Provider>, { queryClient });
}

describe('phase cockpit component packet rendering', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders a full backend packet with summary, evidence, governance, and enabled action', () => {
    renderCockpit(<ShapeCockpitRoute />, basePacket());

    expect(screen.getByTestId('shape-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('phase-current-state-card')).toBeInTheDocument();
    expect(screen.getByTestId('phase-current-project')).toHaveTextContent('Component Contract Project');
    expect(screen.getByTestId('phase-current-evidence')).toHaveTextContent('1 evidence item');
    expect(screen.getByTestId('phase-current-governance')).toHaveTextContent('Policy allows phase action');
    expect(screen.getByTestId('phase-current-readiness')).toHaveTextContent('Can advance');
    expect(screen.getByTestId('add-components')).not.toBeDisabled();
  });

  it('renders degraded packet recovery details and disables the primary action', () => {
    renderCockpit(<RunCockpitRoute />, basePacket({
      phase: 'run',
      lifecyclePhase: 'RUN',
      readiness: {
        canAdvance: true,
        nextPhase: 'observe',
        missingPrerequisites: [],
        completenessScore: 0.82,
        isDegraded: true,
      },
      degradedDetails: {
        dependency: 'KERNEL',
        reason: 'KERNEL_LIFECYCLE_TRUTH_QUERY_FAILED',
        truthSource: 'kernel_lifecycle_truth',
        recoveryAction: 'Replay Kernel lifecycle events before Run controls execute.',
        impactedFeatures: ['run-promote', 'observe-handoff'],
      },
    }));

    expect(screen.getByTestId('run-cockpit')).toBeInTheDocument();
    expect(screen.getByTestId('phase-degraded-dependency')).toHaveTextContent('KERNEL');
    expect(screen.getByTestId('phase-degraded-recovery')).toHaveTextContent('Replay Kernel lifecycle events');
    expect(screen.getByTestId('phase-degraded-impacted-features')).toHaveTextContent('run-promote, observe-handoff');
    expect(screen.getByTestId('check-readiness')).toBeDisabled();
  });

  it('renders unauthorized packet state before exposing phase content', () => {
    renderCockpit(<ValidateCockpitRoute />, basePacket({
      phase: 'validate',
      lifecyclePhase: 'VALIDATE',
      capabilities: {
        canRead: false,
        canCreate: false,
        canUpdate: false,
        canDelete: false,
        canApprove: false,
        canReject: false,
        canRollback: false,
      },
    }));

    expect(screen.getByText('Phase access denied')).toBeInTheDocument();
    expect(screen.getByText(/You do not have permission to access the validate phase/)).toBeInTheDocument();
    expect(screen.queryByTestId('validate-cockpit')).not.toBeInTheDocument();
  });
});
