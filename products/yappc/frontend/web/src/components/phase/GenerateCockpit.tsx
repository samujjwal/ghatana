/**
 * Generate Cockpit Component
 *
 * Phase-specific cockpit for the Generate phase.
 * Primary action: Prepare implementation package.
 * Default surface: Generator cockpit + code preview.
 *
 * @doc.type component
 * @doc.purpose Generate phase cockpit
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

export interface GenerateCockpitProps {
  projectId: string;
  projectName: string;
  onGenerateCode: () => void;
  onPreviewCode: () => void;
  blockers?: Blocker[];
  evidence?: EvidenceItem[];
  suggestedSteps?: SuggestedStep[];
  governanceRecords?: GovernanceRecord[];
  canGenerate: boolean;
  generationReason?: string;
  className?: string;
}

/**
 * Generate Cockpit Component
 *
 * Provides a focused interface for the Generate phase with:
 * - Primary action to generate implementation code
 * - Blocker display for missing configuration
 * - Evidence panel showing generation readiness
 * - Suggested next steps for code review
 * - Governance trace for audit trail
 */
export const GenerateCockpit: React.FC<GenerateCockpitProps> = ({
  projectId,
  projectName,
  onGenerateCode,
  onPreviewCode,
  blockers = [],
  evidence = [],
  suggestedSteps = [],
  governanceRecords = [],
  canGenerate,
  generationReason,
  className = '',
}) => {
  const primaryAction = (
    <PhasePrimaryActionCard
      title="Prepare Implementation"
      description="Generate production-ready code based on your approved design and requirements."
      actionLabel="Generate Code"
      onAction={onGenerateCode}
      secondaryActionLabel="Preview Code"
      onSecondaryAction={onPreviewCode}
      icon="⚙️"
      disabled={!canGenerate}
      disabledReason={generationReason}
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
      phaseName="Generate"
      phaseDescription={`Prepare the implementation package for ${projectName}`}
      primaryAction={primaryAction}
      blockers={blockersPanel}
      evidence={evidencePanel}
      suggestedAutomation={suggestedNextStep}
      governanceTrace={governanceTrace}
      className={className}
    />
  );
};

export default GenerateCockpit;
