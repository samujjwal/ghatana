/**
 * Observe Cockpit Component
 *
 * Phase-specific cockpit for the Observe phase.
 * Primary action: Review health, preview, telemetry, incidents.
 * Default surface: Observability cockpit.
 *
 * @doc.type component
 * @doc.purpose Observe phase cockpit
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

export interface ObserveCockpitProps {
  projectId: string;
  projectName: string;
  onViewMetrics: () => void;
  onViewLogs: () => void;
  onOpenPreview: () => void;
  blockers?: Blocker[];
  evidence?: EvidenceItem[];
  suggestedSteps?: SuggestedStep[];
  governanceRecords?: GovernanceRecord[];
  hasIncidents: boolean;
  className?: string;
}

/**
 * Observe Cockpit Component
 *
 * Provides a focused interface for the Observe phase with:
 * - Primary action to view metrics and logs
 * - Blocker display for critical incidents
 * - Evidence panel showing performance data
 * - Suggested next steps for incident response
 * - Governance trace for audit trail
 */
export const ObserveCockpit: React.FC<ObserveCockpitProps> = ({
  projectId,
  projectName,
  onViewMetrics,
  onViewLogs,
  onOpenPreview,
  blockers = [],
  evidence = [],
  suggestedSteps = [],
  governanceRecords = [],
  hasIncidents,
  className = '',
}) => {
  const primaryAction = (
    <PhasePrimaryActionCard
      title={hasIncidents ? "Review Incidents" : "Monitor Performance"}
      description={hasIncidents 
        ? "Review active incidents and take corrective action."
        : "Monitor application health, performance metrics, and system logs."
      }
      actionLabel="View Metrics"
      onAction={onViewMetrics}
      secondaryActionLabel="View Logs"
      onSecondaryAction={onViewLogs}
      icon={hasIncidents ? "⚠️" : "👁️"}
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
      phaseName="Observe"
      phaseDescription={`Monitor health, preview, telemetry, and incidents for ${projectName}`}
      primaryAction={primaryAction}
      blockers={blockersPanel}
      evidence={evidencePanel}
      suggestedAutomation={suggestedNextStep}
      governanceTrace={governanceTrace}
      className={className}
    />
  );
};

export default ObserveCockpit;
