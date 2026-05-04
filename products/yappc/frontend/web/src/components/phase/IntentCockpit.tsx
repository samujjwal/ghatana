/**
 * Intent Cockpit Component
 *
 * Phase-specific cockpit for the Intent phase.
 * Primary action: Clarify problem and user outcome.
 * Default surface: Intent capture + evidence.
 *
 * @doc.type component
 * @doc.purpose Intent phase cockpit
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

export interface IntentCockpitProps {
  projectId: string;
  projectName: string;
  onCaptureIntent: (intent: string) => void;
  onReviewEvidence: () => void;
  blockers?: Blocker[];
  evidence?: EvidenceItem[];
  suggestedSteps?: SuggestedStep[];
  governanceRecords?: GovernanceRecord[];
  className?: string;
}

/**
 * Intent Cockpit Component
 *
 * Provides a focused interface for the Intent phase with:
 * - Primary action to capture user intent
 * - Blocker display for missing requirements
 * - Evidence panel showing user research and context
 * - Suggested next steps for moving forward
 * - Governance trace for audit trail
 */
export const IntentCockpit: React.FC<IntentCockpitProps> = ({
  projectId,
  projectName,
  onCaptureIntent,
  onReviewEvidence,
  blockers = [],
  evidence = [],
  suggestedSteps = [],
  governanceRecords = [],
  className = '',
}) => {
  const handleCaptureIntent = () => {
    // Open intent capture dialog
    onCaptureIntent('');
  };

  const primaryAction = (
    <PhasePrimaryActionCard
      title="Define Your Goal"
      description="Clearly state what you want to build and why. This will guide all subsequent decisions."
      actionLabel="Capture Intent"
      onAction={handleCaptureIntent}
      secondaryActionLabel="Review Evidence"
      onSecondaryAction={onReviewEvidence}
      icon="🎯"
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
      phaseName="Intent"
      phaseDescription={`Define the problem space and desired outcome for ${projectName}`}
      primaryAction={primaryAction}
      blockers={blockersPanel}
      evidence={evidencePanel}
      suggestedAutomation={suggestedNextStep}
      governanceTrace={governanceTrace}
      className={className}
    />
  );
};

export default IntentCockpit;
