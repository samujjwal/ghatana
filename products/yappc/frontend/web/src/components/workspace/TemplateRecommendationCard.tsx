/**
 * TemplateRecommendationCard — AI-Y9
 *
 * Displays a list of AI-personalised project template suggestions during
 * the onboarding flow. Users see the top recommendations ordered by
 * confidence and can pick one to pre-fill their first project.
 *
 * ## Usage
 * ```tsx
 * <TemplateRecommendationCard
 *   context={{ role: 'frontend-engineer', goal: 'ship a saas product' }}
 *   onSelect={(templateId) => setSelectedTemplate(templateId)}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Show AI-personalised project template recommendations in onboarding
 * @doc.layer product
 * @doc.pattern Data Display
 */

import React from 'react';
import { Loader2, AlertCircle, Sparkles, CheckCircle2 } from 'lucide-react';
import { useTemplateRecommendation } from '../../hooks/useTemplateRecommendation';
import type { TemplateRecommendationContext, TemplateRecommendation } from '../../hooks/useTemplateRecommendation';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface TemplateRecommendationCardProps {
  /** User context used to personalise recommendations. */
  context: TemplateRecommendationContext;
  /** Called when the user selects a template. */
  onSelect?: (templateId: string) => void;
  /** Currently selected template ID. */
  selectedTemplateId?: string;
  /** Additional CSS class names. */
  className?: string;
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function ConfidencePill({ confidence }: { confidence: number }) {
  const pct = Math.round(confidence * 100);
  const colorClass =
    pct >= 85 ? 'bg-success-bg text-success-color' :
    pct >= 70 ? 'bg-warning-bg text-warning-color' :
    'bg-surface text-muted';

  return (
    <span className={`rounded-full px-1.5 py-0.5 text-xs font-medium ${colorClass}`}>
      {pct}% match
    </span>
  );
}

function RecommendationItem({
  rec,
  selected,
  onSelect,
}: {
  rec: TemplateRecommendation;
  selected: boolean;
  onSelect: (id: string) => void;
}) {
  return (
    <Button
      data-testid={`template-rec-${rec.templateId}`}
      aria-pressed={selected}
      onClick={() => onSelect(rec.templateId)}
      variant="ghost"
      className={[
        'w-full rounded-lg border p-3 text-left transition-colors hover:bg-accent',
        selected ? 'border-primary bg-primary-bg' : 'border-border bg-surface',
      ].join(' ')}
    >
      <span className="flex items-start justify-between gap-2">
        <span className="space-y-0.5">
          <span className="flex items-center gap-1.5">
            {selected && <CheckCircle2 className="h-4 w-4 text-primary shrink-0" aria-hidden="true" />}
            <span className="text-sm font-medium text-foreground">{rec.name}</span>
          </span>
          <p className="text-xs text-muted line-clamp-2">{rec.description}</p>
          {rec.tags.length > 0 && (
            <span className="flex flex-wrap gap-1 pt-1">
              {rec.tags.map((tag) => (
                <span key={tag} className="rounded bg-surface px-1.5 py-0.5 text-xs text-muted border border-border">
                  {tag}
                </span>
              ))}
            </span>
          )}
        </span>
        <ConfidencePill confidence={rec.confidence} />
      </span>
    </Button>
  );
}

// ── Main component ─────────────────────────────────────────────────────────────

/**
 * Renders AI-generated template recommendations for the onboarding flow.
 * Returns `null` when both `role` and `goal` are absent in `context`.
 */
export function TemplateRecommendationCard({
  context,
  onSelect,
  selectedTemplateId,
  className,
}: TemplateRecommendationCardProps) {
  const { t } = useTranslation('common');
  const { recommendations, isLoading, isError } = useTemplateRecommendation(context);

  // Nothing to show without context
  if (!context.role && !context.goal) return null;

  if (isLoading) {
    return (
      <div data-testid="template-rec-loading" className="flex items-center gap-2 p-4 text-sm text-muted">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
        Finding templates for you…
      </div>
    );
  }

  if (isError) {
    return (
      <div data-testid="template-rec-error" className="flex items-center gap-2 p-4 text-sm text-destructive">
        <AlertCircle className="h-4 w-4" aria-hidden="true" />
        Could not load template suggestions.
      </div>
    );
  }

  if (recommendations.length === 0) return null;

  return (
    <section
      data-testid="template-rec-panel"
      aria-label={t('workspace.templateRecommendations')}
      className={['space-y-2', className].filter(Boolean).join(' ')}
    >
      <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted">
        <Sparkles className="h-3 w-3" aria-hidden="true" />
        Recommended for you
      </div>

      <div className="space-y-1.5">
        {recommendations.map((rec) => (
          <RecommendationItem
            key={rec.templateId}
            rec={rec}
            selected={selectedTemplateId === rec.templateId}
            onSelect={onSelect ?? (() => {})}
          />
        ))}
      </div>
    </section>
  );
}
