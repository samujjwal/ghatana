/**
 * Evolve Cockpit Component
 *
 * Phase-specific cockpit for the Evolve phase.
 * Primary action: Plan next cycle.
 * Default surface: Roadmap/backlog cockpit.
 *
 * @doc.type component
 * @doc.purpose Evolve phase cockpit
 * @doc.layer product
 * @doc.pattern Phase Cockpit
 */

import React from 'react';
import {
  PhaseCockpitLayout,
  PhasePrimaryActionCard,
  PhaseBlockerPanel,
  PhaseEvidencePanel,
  PhaseSuggestedNextStep,
  PhaseGovernanceTrace,
} from './index';
import type { Blocker, EvidenceItem, SuggestedStep, GovernanceRecord } from './index';

export interface EvolveCockpitProps {
  projectId: string;
  projectName: string;
  onPlanNextCycle: () => void;
  onReviewBacklog: () => void;
  blockers?: Blocker[];
  evidence?: EvidenceItem[];
  suggestedSteps?: SuggestedStep[];
  governanceRecords?: GovernanceRecord[];
  hasBacklog: boolean;
  className?: string;
}

/**
 * Evolve Cockpit Component
 *
 * Provides a focused interface for the Evolve phase with:
 * - Primary action to plan the next improvement cycle
 * - Blocker display for missing roadmap data
 * - Evidence panel showing project metrics and feedback
 * - Suggested next steps for backlog prioritization
 * - Governance trace for audit trail
 */
export const EvolveCockpit: React.FC<EvolveCockpitProps> = ({
  projectId,
  projectName,
  onPlanNextCycle,
  onReviewBacklog,
  blockers = [],
  evidence = [],
  suggestedSteps = [],
  governanceRecords = [],
  hasBacklog,
  className = '',
}) => {
  const primaryAction = (
    <PhasePrimaryActionCard
      title="Plan Next Cycle"
      description="Plan the next improvement cycle based on learnings and feedback from this iteration."
      actionLabel="Plan Next Cycle"
      onAction={onPlanNextCycle}
      secondaryActionLabel="Review Backlog"
      onSecondaryAction={onReviewBacklog}
      icon="🔄"
    />
  );

  const blockersPanel = blockers.length > 0 ? (
    <PhaseBlockerPanel blockers={blockers} />
  ) : null;

  const evidencePanel = evidence.length > 0 ? (
    <PhaseEvidencePanel evidence={evidence} />
  ) : null;

  const suggestedNextStep = suggestedSteps.length > 0 ? (
    <PhaseSuggestedNextStep steps={suggestedSteps} />
  ) : null;

  const governanceTrace = governanceRecords.length > 0 ? (
    <PhaseGovernanceTrace records={governanceRecords} />
  ) : null;

  return (
    <PhaseCockpitLayout
      phaseName="Evolve"
      phaseDescription={`Plan the next improvement cycle for ${projectName}`}
      primaryAction={primaryAction}
      blockers={blockersPanel}
      evidence={evidencePanel}
      suggestedAutomation={suggestedNextStep}
      governanceTrace={governanceTrace}
      className={className}
    />
  );
};

export default EvolveCockpit;
