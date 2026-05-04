/**
 * Run Cockpit Component
 *
 * Phase-specific cockpit for the Run phase.
 * Primary action: Prepare/execute safe run path.
 * Default surface: Capability-gated run cockpit.
 *
 * @doc.type component
 * @doc.purpose Run phase cockpit
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

export interface RunCockpitProps {
  projectId: string;
  projectName: string;
  onDeploy: () => void;
  onConfigureDeployment: () => void;
  blockers?: Blocker[];
  evidence?: EvidenceItem[];
  suggestedSteps?: SuggestedStep[];
  governanceRecords?: GovernanceRecord[];
  canDeploy: boolean;
  deploymentReason?: string;
  className?: string;
}

/**
 * Run Cockpit Component
 *
 * Provides a focused interface for the Run phase with:
 * - Primary action to deploy to target environment
 * - Blocker display for deployment readiness issues
 * - Evidence panel showing deployment configuration
 * - Suggested next steps for monitoring
 * - Governance trace for audit trail
 */
export const RunCockpit: React.FC<RunCockpitProps> = ({
  projectId,
  projectName,
  onDeploy,
  onConfigureDeployment,
  blockers = [],
  evidence = [],
  suggestedSteps = [],
  governanceRecords = [],
  canDeploy,
  deploymentReason,
  className = '',
}) => {
  const primaryAction = (
    <PhasePrimaryActionCard
      title="Deploy to Production"
      description="Deploy your application to the target environment with proper configuration."
      actionLabel="Deploy"
      onAction={onDeploy}
      secondaryActionLabel="Configure Deployment"
      onSecondaryAction={onConfigureDeployment}
      icon="🚀"
      disabled={!canDeploy}
      disabledReason={deploymentReason}
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
      phaseName="Run"
      phaseDescription={`Prepare and execute deployment for ${projectName}`}
      primaryAction={primaryAction}
      blockers={blockersPanel}
      evidence={evidencePanel}
      suggestedAutomation={suggestedNextStep}
      governanceTrace={governanceTrace}
      className={className}
    />
  );
};

export default RunCockpit;
