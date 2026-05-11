import { useCallback, useEffect, useMemo, useState } from 'react';

export type InsightCategory =
  | 'code-quality'
  | 'security'
  | 'architecture'
  | 'requirement'
  | 'test-gap'
  | 'deployment'
  | 'capacity';

export type InsightSeverity = 'info' | 'warning' | 'error' | 'critical';

export interface InsightStreamItem {
  id: string;
  projectId: string;
  title: string;
  description: string;
  suggestion?: string;
  severity: InsightSeverity;
  category: InsightCategory;
  confidence: number;
  sourceRef?: string;
  createdAt: string;
  read: boolean;
}

export interface UseInsightStreamOptions {
  projectId: string;
  maxInsights?: number;
  minimumConfidence?: number;
  initialInsights?: InsightStreamItem[];
}

interface InsightEventPayload {
  projectId?: string;
  payload?: unknown;
}

type InsightEventDetail = InsightEventPayload | InsightEventPayload[] | InsightStreamItem | InsightStreamItem[];

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function toSeverity(value: unknown): InsightSeverity {
  switch (String(value ?? '').toLowerCase()) {
    case 'critical':
      return 'critical';
    case 'error':
      return 'error';
    case 'warning':
      return 'warning';
    default:
      return 'info';
  }
}

function toCategory(value: unknown): InsightCategory {
  switch (String(value ?? '').toLowerCase()) {
    case 'security':
      return 'security';
    case 'architecture':
      return 'architecture';
    case 'requirement':
    case 'requirements':
      return 'requirement';
    case 'test-gap':
    case 'test_gap':
      return 'test-gap';
    case 'deployment':
      return 'deployment';
    case 'capacity':
      return 'capacity';
    default:
      return 'code-quality';
  }
}

function normalizeInsight(value: unknown, projectId: string): InsightStreamItem | null {
  if (!isObject(value)) {
    return null;
  }

  const id = typeof value.id === 'string' ? value.id : typeof value.insightId === 'string' ? value.insightId : undefined;
  const title = typeof value.title === 'string' ? value.title : undefined;
  const description = typeof value.description === 'string' ? value.description : '';

  if (!id || !title) {
    return null;
  }

  const createdAt = typeof value.createdAt === 'string'
    ? value.createdAt
    : typeof value.generatedAt === 'string'
      ? value.generatedAt
      : new Date().toISOString();

  const confidenceRaw = value.confidence;
  const confidence = typeof confidenceRaw === 'number' ? confidenceRaw : Number(confidenceRaw ?? 0);

  return {
    id,
    projectId:
      typeof value.projectId === 'string' && value.projectId.length > 0
        ? value.projectId
        : projectId,
    title,
    description,
    suggestion: typeof value.suggestion === 'string' ? value.suggestion : undefined,
    severity: toSeverity(value.severity),
    category: toCategory(value.category ?? value.type),
    confidence: Number.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : 0,
    sourceRef: typeof value.sourceRef === 'string' ? value.sourceRef : undefined,
    createdAt,
    read: Boolean(value.read),
  };
}

function extractInsights(detail: InsightEventDetail, projectId: string): InsightStreamItem[] {
  const values = Array.isArray(detail) ? detail : [detail];

  return values.flatMap((entry) => {
    if (isObject(entry) && 'payload' in entry) {
      const payload = entry.payload;
      const payloadValues = Array.isArray(payload) ? payload : [payload];
      return payloadValues
        .map((item) => normalizeInsight(item, typeof entry.projectId === 'string' ? entry.projectId : projectId))
        .filter((item): item is InsightStreamItem => item !== null);
    }

    const normalized = normalizeInsight(entry, projectId);
    return normalized ? [normalized] : [];
  });
}

export function useInsightStream({
  projectId,
  maxInsights = 20,
  minimumConfidence = 0.6,
  initialInsights = [],
}: UseInsightStreamOptions) {
  const [insights, setInsights] = useState<InsightStreamItem[]>(() =>
    initialInsights.map((insight) => ({ ...insight, projectId: insight.projectId || projectId }))
  );

  const mergeInsights = useCallback(
    (incoming: InsightStreamItem[]) => {
      if (!incoming.length) {
        return;
      }

      setInsights((current) => {
        const next = new Map<string, InsightStreamItem>();

        [...incoming, ...current]
          .filter((insight) => insight.projectId === projectId)
          .filter((insight) => insight.confidence >= minimumConfidence)
          .forEach((insight) => {
            const existing = next.get(insight.id);
            next.set(insight.id, {
              ...insight,
              read: existing?.read ?? insight.read,
            });
          });

        return [...next.values()]
          .sort((left, right) => right.createdAt.i18n.languageCompare(left.createdAt))
          .slice(0, maxInsights);
      });
    },
    [maxInsights, minimumConfidence, projectId]
  );

  useEffect(() => {
    const handleInsightEvent = (event: Event) => {
      const customEvent = event as CustomEvent<InsightEventDetail>;
      const incoming = extractInsights(customEvent.detail, projectId);
      mergeInsights(incoming);
    };

    window.addEventListener('yappc:ai-insight', handleInsightEvent);

    return () => {
      window.removeEventListener('yappc:ai-insight', handleInsightEvent);
    };
  }, [mergeInsights, projectId]);

  const dismissInsight = useCallback((insightId: string) => {
    setInsights((current) => current.filter((insight) => insight.id !== insightId));
  }, []);

  const markAllRead = useCallback(() => {
    setInsights((current) => current.map((insight) => ({ ...insight, read: true })));
  }, []);

  const unreadCount = useMemo(
    () => insights.filter((insight) => !insight.read).length,
    [insights]
  );

  return {
    insights,
    unreadCount,
    dismissInsight,
    markAllRead,
  };
}