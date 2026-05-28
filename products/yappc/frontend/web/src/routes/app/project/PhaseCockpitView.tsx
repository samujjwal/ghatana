import React from 'react';

import { PhaseBlockerPanel } from '../../../components/phase/PhaseBlockerPanel';
import { PhaseCockpitLayout } from '../../../components/phase/PhaseCockpitLayout';
import { PhaseEvidencePanel } from '../../../components/phase/PhaseEvidencePanel';
import { PhaseGovernanceTrace } from '../../../components/phase/PhaseGovernanceTrace';
import { PhasePrimaryActionCard } from '../../../components/phase/PhasePrimaryActionCard';
import { PhaseSuggestedNextStep } from '../../../components/phase/PhaseSuggestedNextStep';
import type { Blocker as BlockerCard } from '../../../components/phase/PhaseBlockerPanel';
import type { EvidenceItem as EvidenceCard } from '../../../components/phase/PhaseEvidencePanel';
import type { GovernanceRecord as GovernanceTraceRecord } from '../../../components/phase/PhaseGovernanceTrace';
import type { SuggestedStep as SuggestedNextStep } from '../../../components/phase/PhaseSuggestedNextStep';
import type { MountedPhase } from '../../../services/phase';

import { PhaseActionSection, type PhaseActionSectionGroup } from './PhaseActionSection';
import { PhaseEmbeddedSurface } from './PhaseEmbeddedSurface';
import { PhasePacketErrorPanel } from './PhasePacketErrorPanel';

interface PhaseCockpitViewProps {
  readonly phase: MountedPhase;
  readonly phaseName: string;
  readonly phaseDescription: string;
  readonly primaryTitle: string;
  readonly primaryDescription: string;
  readonly primaryActionLabel: string;
  readonly primaryIcon: React.ReactNode;
  readonly primaryActionTestId: string;
  readonly primaryActionAriaLabel?: string;
  readonly secondaryActionLabel: string;
  readonly isPrimaryDisabled: boolean;
  readonly disabledReason?: string;
  readonly canExecutePrimaryAction: boolean;
  readonly onPrimaryAction: () => void;
  readonly onViewBlockers: () => void;
  readonly onSecondaryAction: () => void;
  readonly phaseDetailLabel: string;
  readonly phaseDetailDescription: string;
  readonly phaseDetailBody: string;
  readonly phaseDetailTitle: string;
  readonly phaseDetailLastActivityLabel: string;
  readonly phaseDetailNoRecentActivityLabel: string;
  readonly lastActivityTimestampLabel: string;
  readonly blockers: readonly BlockerCard[];
  readonly evidence: readonly EvidenceCard[];
  readonly governance: readonly GovernanceTraceRecord[];
  readonly suggestions: readonly SuggestedNextStep[];
  readonly statusPanels: React.ReactNode;
  readonly error: Error | null;
  readonly onRetry: () => void;
  readonly feedback: string | null;
  readonly actionResultMessage: string | null;
  readonly actionError: string | null;
  readonly actionSections: readonly PhaseActionSectionGroup[];
  readonly currentStateCard: React.ReactNode;
  readonly degradedDetails: React.ReactNode;
  readonly isDependencyDegraded: boolean;
}

export function PhaseCockpitView({
  phase,
  phaseName,
  phaseDescription,
  primaryTitle,
  primaryDescription,
  primaryActionLabel,
  primaryIcon,
  primaryActionTestId,
  primaryActionAriaLabel,
  secondaryActionLabel,
  isPrimaryDisabled,
  disabledReason,
  canExecutePrimaryAction,
  onPrimaryAction,
  onViewBlockers,
  onSecondaryAction,
  phaseDetailLabel,
  phaseDetailDescription,
  phaseDetailBody,
  phaseDetailTitle,
  phaseDetailLastActivityLabel,
  phaseDetailNoRecentActivityLabel,
  lastActivityTimestampLabel,
  blockers,
  evidence,
  governance,
  suggestions,
  statusPanels,
  error,
  onRetry,
  feedback,
  actionResultMessage,
  actionError,
  actionSections,
  currentStateCard,
  degradedDetails,
  isDependencyDegraded,
}: PhaseCockpitViewProps): React.ReactNode {
  const advancedDetails = (
    <div
      id={`${phase}-supporting-surface`}
      className="rounded-2xl border border-border bg-surface-raised p-4 shadow-sm"
    >
      <div className="mb-4">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-fg-muted">
          {phaseDetailLabel}
        </p>
        <h2 className="mt-2 text-lg font-semibold text-fg">{phaseDetailTitle}</h2>
        <p className="mt-1 text-sm text-fg-muted">{phaseDetailBody}</p>
      </div>
      <div className="mb-4 text-xs text-fg-muted">
        {phaseDetailLastActivityLabel}{' '}
        {lastActivityTimestampLabel || phaseDetailNoRecentActivityLabel}
      </div>
      <PhaseEmbeddedSurface phase={phase} />
    </div>
  );

  return (
    <div className="p-6 space-y-6">
      <PhaseCockpitLayout
        testId={`${phase}-cockpit`}
        phaseName={phaseName}
        phaseDescription={phaseDescription}
        primaryAction={(
          <PhasePrimaryActionCard
            title={primaryTitle}
            description={primaryDescription}
            actionLabel={primaryActionLabel}
            onAction={canExecutePrimaryAction ? onPrimaryAction : onViewBlockers}
            secondaryActionLabel={secondaryActionLabel}
            onSecondaryAction={onSecondaryAction}
            icon={primaryIcon}
            disabled={isPrimaryDisabled}
            disabledReason={disabledReason}
            testId={`${phase}-primary-action-card`}
            actionTestId={primaryActionTestId}
            secondaryActionTestId={`${phase}-review-action`}
            actionAriaLabel={primaryActionAriaLabel}
          />
        )}
        blockers={<div id={`${phase}-blocker-panel`}><PhaseBlockerPanel blockers={[...blockers]} /></div>}
        evidence={<PhaseEvidencePanel evidence={[...evidence]} />}
        suggestedAutomation={<PhaseSuggestedNextStep steps={[...suggestions]} />}
        governanceTrace={<PhaseGovernanceTrace records={[...governance]} />}
        advancedTools={advancedDetails}
        advancedToolsLabel={phaseDetailLabel}
        advancedToolsDescription={phaseDetailDescription}
      >
        <div className="space-y-4" data-testid={`${phase}-native-summary`}>
          <PhasePacketErrorPanel error={error} onRetry={onRetry} />
          {currentStateCard}
          {degradedDetails}
          {feedback ? (
            <div className="rounded-xl border border-info-border bg-info-bg p-4 text-sm text-info-color">
              {feedback}
            </div>
          ) : null}
          {actionResultMessage ? (
            <div
              className="rounded-xl border border-success-border bg-success-bg p-4 text-sm text-success-color"
              data-testid="phase-action-result"
            >
              {actionResultMessage}
            </div>
          ) : null}
          {actionSections.map((section) => (
            <PhaseActionSection
              key={section.testId}
              testId={section.testId}
              title={section.title}
              description={section.description}
              actions={section.actions}
            />
          ))}
          {actionError ? (
            <div
              className="rounded-xl border border-destructive bg-destructive/10 p-4 text-sm text-destructive"
              data-testid="phase-action-error"
            >
              {actionError}
            </div>
          ) : null}
          {isDependencyDegraded ? (
            <details
              className="rounded-xl border border-border bg-surface p-3"
              data-testid="phase-technical-details"
            >
              <summary className="cursor-pointer text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
                Technical details
              </summary>
              <div className="mt-3">{statusPanels}</div>
            </details>
          ) : statusPanels}
        </div>
      </PhaseCockpitLayout>
    </div>
  );
}
