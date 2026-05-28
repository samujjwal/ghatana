import React from 'react';

import { Badge, Card, CardContent } from '@ghatana/design-system';

import type { MountedPhase } from '../../../services/phase';
import type { PhasePanelView } from '../../../types/phasePacket';

interface PhaseStatusPanelsCanonicalProps {
  readonly phase: MountedPhase;
  readonly phasePanels: readonly PhasePanelView[];
}

function variantForStatus(status: string): 'success' | 'destructive' | 'secondary' {
  const normalized = status.toLowerCase();
  if (normalized.includes('ready') || normalized.includes('healthy') || normalized.includes('success')) {
    return 'success';
  }
  if (normalized.includes('blocked') || normalized.includes('fail') || normalized.includes('error') || normalized.includes('degraded')) {
    return 'destructive';
  }
  return 'secondary';
}

export function PhaseStatusPanelsCanonical({ phase, phasePanels }: PhaseStatusPanelsCanonicalProps): React.ReactNode {
  const panel = phasePanels.find((candidate) => candidate.phase === phase);

  if (!panel) {
    return (
      <Card variant="outlined" data-testid="phase-panel-missing">
        <CardContent className="p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">
            Phase panel unavailable
          </p>
          <p className="mt-2 text-sm text-fg-muted">
            No backend phase panel data is available for this phase yet.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4" data-testid={`${phase}-backend-panel`}>
      <Card variant="outlined" data-testid={`${phase}-panel-summary`}>
        <CardContent className="p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">
                Phase status
              </p>
              <p className="mt-2 text-sm text-fg" data-testid={`${phase}-panel-summary-text`}>
                {panel.summary}
              </p>
            </div>
            <Badge variant={variantForStatus(panel.status)} data-testid={`${phase}-panel-status`}>
              {panel.status}
            </Badge>
          </div>
          <p className="mt-3 text-sm text-fg-muted" data-testid={`${phase}-panel-recommendation`}>
            {panel.recommendation}
          </p>
          <div className="mt-3 grid gap-2 text-xs text-fg-muted md:grid-cols-3">
            <p data-testid={`${phase}-panel-owner`}>
              Owner: {panel.owner}
            </p>
            <p data-testid={`${phase}-panel-confidence`}>
              Confidence: {Math.round(panel.confidence * 100)}%
            </p>
            <p data-testid={`${phase}-panel-trace`}>
              Trace: {panel.supportTrace}
            </p>
          </div>
        </CardContent>
      </Card>

      {panel.cards.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2" data-testid={`${phase}-panel-cards`}>
          {panel.cards.map((card) => (
            <Card key={card.id} variant="outlined" data-testid={`${phase}-panel-card`}>
              <CardContent className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <p className="text-sm font-semibold text-fg">{card.title}</p>
                  <Badge variant={variantForStatus(card.status)}>{card.status}</Badge>
                </div>
                <p className="mt-2 text-sm text-fg-muted">{card.detail}</p>
                {card.trace ? (
                  <p className="mt-2 text-xs text-fg-muted">
                    Trace: {card.trace}
                  </p>
                ) : null}
              </CardContent>
            </Card>
          ))}
        </div>
      ) : null}

      {panel.learningInsight ? (
        <Card variant="outlined" data-testid={`${phase}-learning-insight`}>
          <CardContent className="p-4">
            <p className="text-sm font-semibold text-fg">Learning insight</p>
            <p className="mt-2 text-sm text-fg-muted">{panel.learningInsight.learnedSignal}</p>
            <ul className="mt-3 list-disc space-y-1 pl-5 text-xs text-fg-muted" data-testid={`${phase}-learning-checklist`}>
              <li>Source: {panel.learningInsight.sourceEvent}</li>
              <li>Recommendation: {panel.learningInsight.recommendation}</li>
              <li>Approval: {panel.learningInsight.approvalRequired ? 'required' : 'not required'}</li>
              <li>Rollback: {panel.learningInsight.rollbackPath}</li>
            </ul>
          </CardContent>
        </Card>
      ) : null}

      {panel.evolutionPlan ? (
        <Card variant="outlined" data-testid={`${phase}-evolution-plan`}>
          <CardContent className="p-4">
            <p className="text-sm font-semibold text-fg">Evolution plan</p>
            <p className="mt-2 text-sm text-fg-muted">{panel.evolutionPlan.proposal}</p>
            <ol className="mt-3 space-y-2 text-xs text-fg-muted" data-testid={`${phase}-evolution-stepper`}>
              <li>1. Impact review: {panel.evolutionPlan.impactSummary}</li>
              <li>2. Diff review: {panel.evolutionPlan.diffSummary}</li>
              <li>3. Validation requirements: {panel.evolutionPlan.validationRequirements}</li>
              <li>4. Approval state: {panel.evolutionPlan.approvalState}</li>
              <li>5. Rollback path: {panel.evolutionPlan.rollbackPath}</li>
              <li>6. Re-run target: {panel.evolutionPlan.rerunTarget}</li>
            </ol>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
