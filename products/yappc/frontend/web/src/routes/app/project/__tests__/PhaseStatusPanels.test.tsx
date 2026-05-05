import React from 'react';
import { render, screen } from '@/test-utils/test-utils';
import { describe, expect, it } from 'vitest';

import { PhaseStatusPanels } from '../PhaseStatusPanels';

describe('PhaseStatusPanels', () => {
  it('renders intent-native status panels with artifact snapshot', () => {
    render(
      <PhaseStatusPanels
        phase="intent"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'INTENT',
          nextPhase: 'SHAPE',
          canAdvance: false,
          readiness: 72,
          blockers: ['Missing goals'],
          requiredArtifacts: ['Intent brief'],
          completedArtifacts: ['Problem statement'],
          estimatedReadyIn: '2h',
          estimatedReadyInHours: 2,
          predictionConfidence: 0.7,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('intent-goal-clarity')).toHaveTextContent('Intent quality is currently 72%');
    expect(screen.getByTestId('intent-artifact-snapshot')).toBeInTheDocument();
    expect(screen.getByTestId('intent-artifact')).toHaveTextContent('Problem statement');
  });

  it('renders shape-native status panels with readiness and activity', () => {
    render(
      <PhaseStatusPanels
        phase="shape"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'SHAPE',
          nextPhase: 'VALIDATE',
          canAdvance: false,
          readiness: 68,
          blockers: ['Schema missing'],
          requiredArtifacts: ['Component map', 'Data contracts'],
          completedArtifacts: ['Intent brief'],
          estimatedReadyIn: '3h',
          estimatedReadyInHours: 3,
          predictionConfidence: 0.75,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[{ id: 'b1', severity: 'warning', description: 'Schema missing', source: 'governance' }]}
        activity={[
          { id: 'e1', source: 'audit', action: 'shape.updated', summary: 'Updated architecture nodes', timestamp: '2026-05-04T10:00:00.000Z', actor: 'user', severity: null, success: true },
        ]}
      />,
    );

    expect(screen.getByTestId('shape-architecture-readiness')).toHaveTextContent('1 blocker(s) are preventing lifecycle promotion.');
    expect(screen.getAllByTestId('shape-artifact')).toHaveLength(2);
    expect(screen.getByTestId('shape-activity-event')).toHaveTextContent('Updated architecture nodes');
  });

  it('renders validation gates with required approvals when validate phase data exists', () => {
    render(
      <PhaseStatusPanels
        phase="validate"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'VALIDATE',
          nextPhase: 'GENERATE',
          canAdvance: true,
          readiness: 90,
          blockers: [],
          requiredArtifacts: ['Security review'],
          completedArtifacts: ['Requirements packet'],
          estimatedReadyIn: 'Ready now',
          estimatedReadyInHours: 0,
          predictionConfidence: 0.8,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('validation-status')).toHaveTextContent(/passed/i);
    expect(screen.getByTestId('approval-gates')).toBeInTheDocument();
    expect(screen.getByTestId('required-approval')).toHaveTextContent('Security review');
  });

  it('renders observe summary panels when no activity or incidents are available', () => {
    render(
      <PhaseStatusPanels
        phase="observe"
        preview={null}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('metrics-panel')).toHaveTextContent('No recent backed events have been recorded yet.');
    expect(screen.getByTestId('incidents-panel')).toHaveTextContent('No active incident-level blockers are surfaced here.');
  });

  it('renders generate readiness badge as ready when no blockers and preview confirms advance', () => {
    render(
      <PhaseStatusPanels
        phase="generate"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'GENERATE',
          nextPhase: 'RUN',
          canAdvance: true,
          readiness: 100,
          blockers: [],
          requiredArtifacts: ['Dashboard.tsx', 'schema.graphql'],
          completedArtifacts: [],
          estimatedReadyIn: null,
          estimatedReadyInHours: null,
          predictionConfidence: 0.95,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('codegen-readiness-badge')).toHaveTextContent(/ready/i);
    expect(screen.getByTestId('codegen-readiness-score')).toHaveTextContent('100%');
    const files = screen.getAllByTestId('generated-file');
    expect(files).toHaveLength(2);
    expect(files[0]).toHaveTextContent('Dashboard.tsx');
  });

  it('renders generate readiness badge as not ready when blockers exist', () => {
    render(
      <PhaseStatusPanels
        phase="generate"
        preview={null}
        blockers={[{ id: 'b1', severity: 'error', description: 'Missing schema', source: 'governance' }]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('codegen-readiness-badge')).toHaveTextContent(/not ready/i);
    expect(screen.getByTestId('generated-file-empty')).toBeInTheDocument();
  });

  it('renders recent activity timeline for generate phase', () => {
    render(
      <PhaseStatusPanels
        phase="generate"
        preview={null}
        blockers={[]}
        activity={[
          { id: 'e1', source: 'lifecycle', action: 'artifact.created', summary: 'Schema artifact created', timestamp: '2026-05-04T10:00:00.000Z', actor: 'system', severity: null, success: true },
          { id: 'e2', source: 'audit', action: 'artifact.validated', summary: 'Validation passed', timestamp: '2026-05-04T09:00:00.000Z', actor: 'operator', severity: null, success: true },
        ]}
      />,
    );

    expect(screen.getByTestId('generate-activity-timeline')).toBeInTheDocument();
    expect(screen.getByText('Schema artifact created')).toBeInTheDocument();
  });

  it('renders signal timeline for observe phase with recent events', () => {
    render(
      <PhaseStatusPanels
        phase="observe"
        preview={null}
        blockers={[]}
        activity={[
          { id: 'e1', source: 'lifecycle', action: 'health.check', summary: 'Health check passed', timestamp: '2026-05-04T10:00:00.000Z', actor: null, severity: null, success: true },
          { id: 'e2', source: 'audit', action: 'alert.fired', summary: 'Error rate spike', timestamp: '2026-05-04T09:30:00.000Z', actor: null, severity: 'error', success: false },
        ]}
      />,
    );

    expect(screen.getByTestId('observe-signal-timeline')).toBeInTheDocument();
    const events = screen.getAllByTestId('signal-event');
    expect(events).toHaveLength(2);
    expect(events[0]).toHaveTextContent('Health check passed');
    expect(events[1]).toHaveTextContent('Error rate spike');
  });
});
