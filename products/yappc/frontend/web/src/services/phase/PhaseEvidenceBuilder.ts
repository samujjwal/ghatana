import type { EvidenceItem } from '@/components/phase/PhaseEvidencePanel';
import type { GovernanceRecord } from '@/components/phase/PhaseGovernanceTrace';

import type {
  MountedPhase,
  PhaseActivityEvent,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
} from './types';

export function buildPhaseEvidence(
  phase: MountedPhase,
  project: PhaseProjectSnapshot,
  activity: PhaseActivityEvent[],
  preview: PhaseTransitionPreviewSnapshot | null,
): EvidenceItem[] {
  const evidence: EvidenceItem[] = [];

  if (project.lifecyclePhase) {
    evidence.push({
      id: `${phase}-current-phase`,
      type: 'artifact',
      title: 'Current lifecycle phase',
      description: 'Backed lifecycle state from the project record.',
      value: project.lifecyclePhase,
      source: 'project API',
      timestamp: project.updatedAt,
    });
  }

  if (typeof project.healthScore === 'number') {
    evidence.push({
      id: `${phase}-health-score`,
      type: 'metric',
      title: 'Health score',
      description: 'Current health signal derived from the mounted project overview response.',
      value: project.healthScore,
      source: 'project API',
      timestamp: project.updatedAt,
    });
  }

  if (preview) {
    evidence.push({
      id: `${phase}-readiness`,
      type: 'metric',
      title: 'Readiness prediction',
      description: preview.canAdvance
        ? 'Lifecycle checks currently allow promotion to the next step.'
        : 'Lifecycle checks still report blockers for the next step.',
      value: `${Math.round(preview.readiness ?? 0)}%`,
      source: 'phase transition API',
      timestamp: preview.checkedAt,
    });
  }

  activity.slice(0, 3).forEach((entry) => {
    evidence.push({
      id: `${phase}-activity-${entry.id}`,
      type: entry.source === 'audit' ? 'artifact' : 'observation',
      title: entry.action,
      description: entry.summary,
      source: entry.source,
      timestamp: entry.timestamp,
    });
  });

  return evidence;
}

export function buildPhaseGovernanceRecords(
  activity: PhaseActivityEvent[],
): GovernanceRecord[] {
  return activity.slice(0, 5).map((entry) => ({
    id: entry.id,
    artifactId: entry.id,
    action: entry.action,
    actor: entry.actor ?? 'system',
    timestamp: entry.timestamp,
    reviewState: entry.success === false ? 'rejected' : 'approved',
    source: entry.source === 'audit' ? 'backed' : 'derived',
    metadata: {
      summary: entry.summary,
      severity: entry.severity ?? undefined,
    },
  }));
}
