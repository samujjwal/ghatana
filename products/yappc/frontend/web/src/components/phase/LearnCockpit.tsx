/**
 * Learn Cockpit Component
 *
 * Phase-specific cockpit for the Learn phase.
 * Primary action: Capture lessons and reusable patterns.
 * Default surface: Retrospective cockpit.
 *
 * @doc.type component
 * @doc.purpose Learn phase cockpit
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

export interface LearnCockpitProps {
  projectId: string;
  projectName: string;
  onCaptureLearnings: () => void;
  onReviewPatterns: () => void;
  blockers?: Blocker[];
  evidence?: EvidenceItem[];
  suggestedSteps?: SuggestedStep[];
  governanceRecords?: GovernanceRecord[];
  hasLearnings: boolean;
  className?: string;
}

/**
 * Learn Cockpit Component
 *
 * Provides a focused interface for the Learn phase with:
 * - Primary action to capture learnings and patterns
 * - Blocker display for missing retrospective data
 * - Evidence panel showing metrics and feedback
 * - Suggested next steps for pattern extraction
 * - Governance trace for audit trail
 */
export const LearnCockpit: React.FC<LearnCockpitProps> = ({
  projectId,
  projectName,
  onCaptureLearnings,
  onReviewPatterns,
  blockers = [],
  evidence = [],
  suggestedSteps = [],
  governanceRecords = [],
  hasLearnings,
  className = '',
}) => {
  const primaryAction = (
    <PhasePrimaryActionCard
      title={hasLearnings ? "Review Learnings" : "Capture Learnings"}
      description={hasLearnings
        ? "Review captured lessons and reusable patterns from this project cycle."
        : "Capture lessons learned and identify reusable patterns for future projects."
      }
      actionLabel={hasLearnings ? "Review Patterns" : "Capture Learnings"}
      onAction={hasLearnings ? onReviewPatterns : onCaptureLearnings}
      secondaryActionLabel={hasLearnings ? "Add More" : "Review Patterns"}
      onSecondaryAction={hasLearnings ? onCaptureLearnings : onReviewPatterns}
      icon="💡"
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
      phaseName="Learn"
      phaseDescription={`Capture lessons and reusable patterns from ${projectName}`}
      primaryAction={primaryAction}
      blockers={blockersPanel}
      evidence={evidencePanel}
      suggestedAutomation={suggestedNextStep}
      governanceTrace={governanceTrace}
      className={className}
    />
  );
};

export default LearnCockpit;
