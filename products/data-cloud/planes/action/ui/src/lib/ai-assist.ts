import type { ConfidenceTier } from '@/components/shared/ConfidenceExplanation';

export interface AiAssistSource {
  label: string;
  href?: string;
}

export type AiAssistRouting = 'advisory' | 'reviewable' | 'applied';

export function getAiConfidenceTier(confidence?: number): ConfidenceTier {
  if (confidence === undefined) {
    return 'medium';
  }
  if (confidence >= 0.8) {
    return 'high';
  }
  if (confidence >= 0.5) {
    return 'medium';
  }
  return 'low';
}

export function getAiRouting(confidence?: number): AiAssistRouting {
  if (confidence === undefined) {
    return 'reviewable';
  }
  if (confidence < 0.5) {
    return 'advisory';
  }
  if (confidence < 0.8) {
    return 'reviewable';
  }
  return 'applied';
}

export function getAiRoutingLabel(routing: AiAssistRouting): string {
  switch (routing) {
    case 'advisory':
      return 'Advisory only';
    case 'reviewable':
      return 'Review before apply';
    case 'applied':
      return 'Ready to apply';
  }
}

export function getAiRoutingDescription(routing: AiAssistRouting): string {
  switch (routing) {
    case 'advisory':
      return 'Low-confidence suggestions stay advisory until an operator reviews them manually.';
    case 'reviewable':
      return 'A human should review the rationale and citations before applying this suggestion.';
    case 'applied':
      return 'Confidence is high enough to offer a direct apply path, but the rationale remains visible.';
  }
}

export function normalizeAiSources(evidence?: unknown): AiAssistSource[] {
  if (!Array.isArray(evidence)) {
    return [];
  }

  return evidence
    .map((item, index) => {
      if (typeof item === 'string') {
        return { label: item };
      }
      if (!item || typeof item !== 'object') {
        return null;
      }

      const record = item as Record<string, unknown>;
      const href = firstString(record.url, record.href, record.evidenceUrl, record.link);
      const label =
        firstString(record.label, record.name, record.title, record.signalType, record.entityId, record.kind) ??
        `Source ${index + 1}`;
      const detail = firstString(record.description, record.value, record.id);

      return {
        label: detail && detail !== label ? `${label}: ${detail}` : label,
        href: href ?? undefined,
      } satisfies AiAssistSource;
    })
    .filter((item): item is AiAssistSource => item !== null);
}

function firstString(...values: unknown[]): string | null {
  for (const value of values) {
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim();
    }
  }
  return null;
}
