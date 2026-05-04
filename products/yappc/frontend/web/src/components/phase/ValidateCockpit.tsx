/**
 * Validate Cockpit Component
 *
 * Phase-specific cockpit for the Validate phase.
 * Primary action: Review and approve requirements/design/generation packet.
 * Default surface: Approval/gate cockpit.
 *
 * @doc.type component
 * @doc.purpose Validate phase cockpit
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

export interface ValidateCockpitProps {
  projectId: string;
  projectName: string;
  onApprove: () => void;
  onRequestChanges: () => void;
  blockers?: Blocker[];
  evidence?: EvidenceItem[];
  suggestedSteps?: SuggestedStep[];
  governanceRecords?: GovernanceRecord[];
  canApprove: boolean;
  approvalReason?: string;
  className?: string;
}

/**
 * Validate Cockpit Component
 *
 * Provides a focused interface for the Validate phase with:
 * - Primary action to approve or request changes
 * - Blocker display for validation failures
 * - Evidence panel showing test results and reviews
 * - Suggested next steps for addressing issues
 * - Governance trace for audit trail
 */
export const ValidateCockpit: React.FC<ValidateCockpitProps> = ({
  projectId,
  projectName,
  onApprove,
  onRequestChanges,
  blockers = [],
  evidence = [],
  suggestedSteps = [],
  governanceRecords = [],
  canApprove,
  approvalReason,
  className = '',
}) => {
  const primaryAction = (
    <PhasePrimaryActionCard
      title="Review and Approve"
      description="Review the design and requirements. Approve to proceed to generation or request changes."
      actionLabel="Approve"
      onAction={onApprove}
      secondaryActionLabel="Request Changes"
      onSecondaryAction={onRequestChanges}
      icon="✓"
      disabled={!canApprove}
      disabledReason={approvalReason}
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
      phaseName="Validate"
      phaseDescription={`Review and approve the design and requirements for ${projectName}`}
      primaryAction={primaryAction}
      blockers={blockersPanel}
      evidence={evidencePanel}
      suggestedAutomation={suggestedNextStep}
      governanceTrace={governanceTrace}
      className={className}
    />
  );
};

export default ValidateCockpit;
