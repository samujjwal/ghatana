/**
 * Shape Cockpit Component
 *
 * Phase-specific cockpit for the Shape phase.
 * Primary action: Convert intent into requirements and UI/page structure.
 * Default surface: Canvas/page builder.
 *
 * @doc.type component
 * @doc.purpose Shape phase cockpit
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

export interface ShapeCockpitProps {
  projectId: string;
  projectName: string;
  onOpenCanvas: () => void;
  onReviewRequirements: () => void;
  blockers?: Blocker[];
  evidence?: EvidenceItem[];
  suggestedSteps?: SuggestedStep[];
  governanceRecords?: GovernanceRecord[];
  className?: string;
}

/**
 * Shape Cockpit Component
 *
 * Provides a focused interface for the Shape phase with:
 * - Primary action to open canvas/page builder
 * - Blocker display for missing components or structure
 * - Evidence panel showing requirements and constraints
 * - Suggested next steps for design improvements
 * - Governance trace for audit trail
 */
export const ShapeCockpit: React.FC<ShapeCockpitProps> = ({
  projectId,
  projectName,
  onOpenCanvas,
  onReviewRequirements,
  blockers = [],
  evidence = [],
  suggestedSteps = [],
  governanceRecords = [],
  className = '',
}) => {
  const primaryAction = (
    <PhasePrimaryActionCard
      title="Design Your Solution"
      description="Use the canvas to structure your requirements and design the UI/page layout."
      actionLabel="Open Canvas"
      onAction={onOpenCanvas}
      secondaryActionLabel="Review Requirements"
      onSecondaryAction={onReviewRequirements}
      icon="🎨"
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
      phaseName="Shape"
      phaseDescription={`Convert your intent into structured requirements and UI/page design for ${projectName}`}
      primaryAction={primaryAction}
      blockers={blockersPanel}
      evidence={evidencePanel}
      suggestedAutomation={suggestedNextStep}
      governanceTrace={governanceTrace}
      className={className}
    />
  );
};

export default ShapeCockpit;
