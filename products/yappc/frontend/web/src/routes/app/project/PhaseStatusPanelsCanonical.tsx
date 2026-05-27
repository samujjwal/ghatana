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
    </div>
  );
}
