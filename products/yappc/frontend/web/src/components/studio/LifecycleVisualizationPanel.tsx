import React, { useState, useEffect } from 'react';
import { cn } from '@/lib/utils';
import { Button } from '../ui/Button';
import { useTranslation } from '@ghatana/i18n';

/**
 * Lifecycle event types.
 */
export type LifecycleEventType =
  | 'product-unit.intent.created'
  | 'product-unit.intent.validated'
  | 'product-unit.intent.applied'
  | 'lifecycle.phase.started'
  | 'lifecycle.phase.completed'
  | 'lifecycle.step.started'
  | 'lifecycle.step.completed'
  | 'lifecycle.gate.evaluated'
  | 'lifecycle.artifact.recorded'
  | 'lifecycle.manifest.written'
  | 'lifecycle.deployment.completed'
  | 'lifecycle.health.checked'
  | 'lifecycle.agent.governance.evaluated'
  | 'lifecycle.approval.requested'
  | 'lifecycle.approval.decided';

/**
 * Lifecycle phases.
 */
export type LifecyclePhase =
  | 'planning'
  | 'development'
  | 'testing'
  | 'validation'
  | 'deployment'
  | 'operations'
  | 'monitoring';

/**
 * Event status types.
 */
export type EventStatus = 'running' | 'succeeded' | 'failed' | 'skipped';

/**
 * Lifecycle event data structure.
 */
export interface LifecycleEvent {
  eventId: string;
  eventType: LifecycleEventType;
  productUnitId: string;
  runId: string;
  phase: LifecyclePhase;
  timestamp: string;
  source: string;
  tenantId?: string;
  workspaceId?: string;
  projectId?: string;
  correlationId: string;
  payload: Record<string, any>;
}

/**
 * Timeline node for visualization.
 */
export interface TimelineNode {
  id: string;
  event: LifecycleEvent;
  status: EventStatus;
  duration?: number;
  children?: TimelineNode[];
}

/**
 * Studio lifecycle visualization panel.
 *
 * @doc.type component
 * @doc.purpose Lifecycle visualization for Studio mode
 * @doc.layer ui
 */
export interface LifecycleVisualizationPanelProps {
  events?: LifecycleEvent[];
  onRefresh?: () => void;
  onEventSelect?: (event: LifecycleEvent) => void;
  isLoading?: boolean;
  className?: string;
}

export function LifecycleVisualizationPanel({
  events = [],
  onRefresh,
  onEventSelect,
  isLoading = false,
  className,
}: LifecycleVisualizationPanelProps) {
  const { t } = useTranslation('studio');
  const [selectedEvent, setSelectedEvent] = useState<LifecycleEvent | null>(null);
  const [viewMode, setViewMode] = useState<'timeline' | 'phases' | 'events'>('timeline');
  const [filter, setFilter] = useState<{
    phase?: LifecyclePhase;
    eventType?: LifecycleEventType;
    search: string;
  }>({
    search: '',
  });
  const [autoRefresh, setAutoRefresh] = useState(true);

  // Auto-refresh every 15 seconds for real-time updates
  useEffect(() => {
    if (!autoRefresh || !onRefresh) return;

    const interval = setInterval(() => {
      onRefresh();
    }, 15000);

    return () => clearInterval(interval);
  }, [autoRefresh, onRefresh]);

  // Filter events
  const filteredEvents = events.filter(event => {
    if (filter.phase && event.phase !== filter.phase) return false;
    if (filter.eventType && event.eventType !== filter.eventType) return false;
    if (filter.search && !event.eventType.toLowerCase().includes(filter.search.toLowerCase())) return false;
    return true;
  });

  // Build timeline tree
  const buildTimeline = (): TimelineNode[] => {
    const eventMap = new Map<string, TimelineNode>();
    const rootNodes: TimelineNode[] = [];

    // Create nodes for all events
    filteredEvents.forEach(event => {
      const status = getEventStatus(event);
      const node: TimelineNode = {
        id: event.eventId,
        event,
        status,
        duration: event.payload.durationMs,
        children: [],
      };
      eventMap.set(event.eventId, node);
    });

    // Build hierarchy (phase -> step -> gate)
    const phaseNodes = new Map<string, TimelineNode>();
    const stepNodes = new Map<string, TimelineNode>();

    eventMap.forEach(node => {
      const { event } = node;

      if (event.eventType.startsWith('lifecycle.phase.')) {
        phaseNodes.set(event.phase, node);
        rootNodes.push(node);
      } else if (event.eventType.startsWith('lifecycle.step.')) {
        const phaseNode = phaseNodes.get(event.phase);
        if (phaseNode) {
          phaseNode.children?.push(node);
        } else {
          rootNodes.push(node);
        }
        stepNodes.set(`${event.phase}-${event.payload.stepId}`, node);
      } else if (event.eventType.startsWith('lifecycle.gate.')) {
        const stepNode = stepNodes.get(`${event.phase}-${event.payload.stepId}`);
        if (stepNode) {
          stepNode.children?.push(node);
        } else {
          const phaseNode = phaseNodes.get(event.phase);
          if (phaseNode) {
            phaseNode.children?.push(node);
          } else {
            rootNodes.push(node);
          }
        }
      } else {
        // Other events (artifacts, deployments, etc.)
        const phaseNode = phaseNodes.get(event.phase);
        if (phaseNode) {
          phaseNode.children?.push(node);
        } else {
          rootNodes.push(node);
        }
      }
    });

    // Sort by timestamp
    const sortNodes = (nodes: TimelineNode[]) => {
      nodes.sort((a, b) => new Date(a.event.timestamp).getTime() - new Date(b.event.timestamp).getTime());
      nodes.forEach(node => {
        if (node.children) {
          sortNodes(node.children);
        }
      });
    };

    sortNodes(rootNodes);
    return rootNodes;
  };

  const getEventStatus = (event: LifecycleEvent): EventStatus => {
    if (event.eventType.endsWith('.started')) return 'running';
    if (event.payload.status) return event.payload.status as EventStatus;
    return 'succeeded';
  };

  const getEventIcon = (eventType: LifecycleEventType): string => {
    if (eventType.includes('phase')) return '📋';
    if (eventType.includes('step')) return '⚙️';
    if (eventType.includes('gate')) return '🚪';
    if (eventType.includes('artifact')) return '📦';
    if (eventType.includes('deployment')) return '🚀';
    if (eventType.includes('health')) return '💚';
    if (eventType.includes('approval')) return '✅';
    if (eventType.includes('intent')) return '💡';
    return '📄';
  };

  const getStatusColor = (status: EventStatus): string => {
    switch (status) {
      case 'running': return 'text-info-600 dark:text-info-400';
      case 'succeeded': return 'text-success-600 dark:text-success-400';
      case 'failed': return 'text-danger-600 dark:text-danger-400';
      case 'skipped': return 'text-fg-muted dark:text-fg-muted';
      default: return 'text-fg-muted dark:text-fg-muted';
    }
  };

  const getStatusBgColor = (status: EventStatus): string => {
    switch (status) {
      case 'running': return 'bg-info-100 dark:bg-info-900/20';
      case 'succeeded': return 'bg-success-100 dark:bg-success-900/20';
      case 'failed': return 'bg-danger-100 dark:bg-danger-900/20';
      case 'skipped': return 'bg-surface-100 dark:bg-surface-800';
      default: return 'bg-surface-100 dark:bg-surface-800';
    }
  };

  const getPhaseColor = (phase: LifecyclePhase): string => {
    switch (phase) {
      case 'planning': return 'text-blue-600 dark:text-blue-400';
      case 'development': return 'text-purple-600 dark:text-purple-400';
      case 'testing': return 'text-orange-600 dark:text-orange-400';
      case 'validation': return 'text-yellow-600 dark:text-yellow-400';
      case 'deployment': return 'text-green-600 dark:text-green-400';
      case 'operations': return 'text-indigo-600 dark:text-indigo-400';
      case 'monitoring': return 'text-pink-600 dark:text-pink-400';
      default: return 'text-fg-muted dark:text-fg-muted';
    }
  };

  const formatTimestamp = (timestamp: string): string => {
    try {
      return new Date(timestamp).toLocaleString();
    } catch {
      return timestamp;
    }
  };

  const formatDuration = (duration?: number): string => {
    if (!duration) return '';
    if (duration < 1000) return `${duration}ms`;
    if (duration < 60000) return `${(duration / 1000).toFixed(1)}s`;
    return `${(duration / 60000).toFixed(1)}m`;
  };

  const lifecyclePhases: LifecyclePhase[] = [
    'planning',
    'development',
    'testing',
    'validation',
    'deployment',
    'operations',
    'monitoring',
  ];

  const lifecycleEventTypes: LifecycleEventType[] = [
    'product-unit.intent.created',
    'product-unit.intent.validated',
    'product-unit.intent.applied',
    'lifecycle.phase.started',
    'lifecycle.phase.completed',
    'lifecycle.step.started',
    'lifecycle.step.completed',
    'lifecycle.gate.evaluated',
    'lifecycle.artifact.recorded',
    'lifecycle.manifest.written',
    'lifecycle.deployment.completed',
    'lifecycle.health.checked',
    'lifecycle.agent.governance.evaluated',
    'lifecycle.approval.requested',
    'lifecycle.approval.decided',
  ];

  const renderTimelineNode = (node: TimelineNode, level: number = 0): React.ReactNode => {
    const isSelected = selectedEvent?.eventId === node.event.eventId;
    const hasChildren = node.children && node.children.length > 0;

    return (
      <div key={node.id} className="select-none">
        <div
          className={cn(
            'flex items-center gap-2 p-2 rounded-lg cursor-pointer transition-all',
            'hover:bg-surface-100 dark:hover:bg-surface-800',
            isSelected && 'bg-info-100 dark:bg-info-900/20 border border-info-300 dark:border-info-600',
            getStatusBgColor(node.status)
          )}
          style={{ marginLeft: `${level * 20}px` }}
          onClick={() => {
            setSelectedEvent(node.event);
            onEventSelect?.(node.event);
          }}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSelectedEvent(node.event);
              onEventSelect?.(node.event);
            }
          }}
        >
          <span className={cn('text-lg', getStatusColor(node.status))}>
            {getEventIcon(node.event.eventType)}
          </span>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-fg dark:text-fg truncate">
                {t(`lifecycle.events.${node.event.eventType}`)}
              </span>
              <span className={cn('px-2 py-1 rounded-full text-xs font-medium', getStatusColor(node.status))}>
                {t(`lifecycle.status.${node.status}`)}
              </span>
            </div>
            <div className="flex items-center gap-4 mt-1 text-xs text-fg-muted dark:text-fg-muted">
              <span className={getPhaseColor(node.event.phase)}>
                {t(`lifecycle.phases.${node.event.phase}`)}
              </span>
              <span>{formatTimestamp(node.event.timestamp)}</span>
              {node.duration && (
                <span>{formatDuration(node.duration)}</span>
              )}
              {hasChildren && (
                <span>({node.children?.length} {t('lifecycle.children')})</span>
              )}
            </div>
          </div>
        </div>
        {hasChildren && (
          <div className="ml-4 mt-1">
            {node.children!.map(child => renderTimelineNode(child, level + 1))}
          </div>
        )}
      </div>
    );
  };

  const renderPhaseView = () => {
    const phases = new Map<LifecyclePhase, LifecycleEvent[]>();

    lifecyclePhases.forEach(phase => {
      phases.set(phase, filteredEvents.filter(event => event.phase === phase));
    });

    return (
      <div className="space-y-3">
        {Array.from(phases.entries()).map(([phase, phaseEvents]) => (
          <div key={phase} className="border border-border dark:border-border rounded-lg p-3">
            <div className="flex items-center gap-2 mb-2">
              <span className={cn('text-lg font-medium', getPhaseColor(phase))}>
                {t(`lifecycle.phases.${phase}`)}
              </span>
              <span className="px-2 py-1 bg-surface-100 dark:bg-surface-800 rounded-full text-xs text-fg-muted dark:text-fg-muted">
                {phaseEvents.length} {t('lifecycle.events')}
              </span>
            </div>
            {phaseEvents.length === 0 ? (
              <p className="text-xs text-fg-muted dark:text-fg-muted italic">
                {t('lifecycle.noEventsInPhase')}
              </p>
            ) : (
              <div className="space-y-1">
                {phaseEvents.map(event => {
                  const status = getEventStatus(event);
                  const isSelected = selectedEvent?.eventId === event.eventId;
                  return (
                    <div
                      key={event.eventId}
                      className={cn(
                        'flex items-center gap-2 p-2 rounded cursor-pointer transition-all',
                        'hover:bg-surface-100 dark:hover:bg-surface-800',
                        isSelected && 'bg-info-100 dark:bg-info-900/20',
                        getStatusBgColor(status)
                      )}
                      onClick={() => {
                        setSelectedEvent(event);
                        onEventSelect?.(event);
                      }}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          setSelectedEvent(event);
                          onEventSelect?.(event);
                        }
                      }}
                    >
                      <span className={cn('text-sm', getStatusColor(status))}>
                        {getEventIcon(event.eventType)}
                      </span>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-medium text-fg dark:text-fg truncate">
                            {t(`lifecycle.events.${event.eventType}`)}
                          </span>
                          <span className={cn('px-1 py-0.5 rounded text-xs', getStatusColor(status))}>
                            {t(`lifecycle.status.${status}`)}
                          </span>
                        </div>
                        <span className="text-xs text-fg-muted dark:text-fg-muted">
                          {formatTimestamp(event.timestamp)}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        ))}
      </div>
    );
  };

  const renderEventsList = () => {
    return (
      <div className="space-y-2">
        {filteredEvents.map(event => {
          const status = getEventStatus(event);
          const isSelected = selectedEvent?.eventId === event.eventId;
          return (
            <div
              key={event.eventId}
              className={cn(
                'p-3 rounded-lg border cursor-pointer transition-all',
                'border-border dark:border-border',
                'hover:border-info-300 dark:hover:border-info-600',
                isSelected && 'border-info-500 dark:border-info-400',
                getStatusBgColor(status)
              )}
              onClick={() => {
                setSelectedEvent(event);
                onEventSelect?.(event);
              }}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  setSelectedEvent(event);
                  onEventSelect?.(event);
                }
              }}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={cn('text-lg', getStatusColor(status))}>
                      {getEventIcon(event.eventType)}
                    </span>
                    <h4 className="text-sm font-medium text-fg dark:text-fg truncate">
                      {t(`lifecycle.events.${event.eventType}`)}
                    </h4>
                    <span className={cn('px-2 py-1 rounded-full text-xs font-medium', getStatusColor(status))}>
                      {t(`lifecycle.status.${status}`)}
                    </span>
                  </div>
                  <div className="flex items-center gap-4 mt-1 text-xs text-fg-muted dark:text-fg-muted">
                    <span className={getPhaseColor(event.phase)}>
                      {t(`lifecycle.phases.${event.phase}`)}
                    </span>
                    <span>{formatTimestamp(event.timestamp)}</span>
                    {event.payload.durationMs && (
                      <span>{formatDuration(event.payload.durationMs)}</span>
                    )}
                    <span className="font-mono">{event.correlationId}</span>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* Header */}
      <div className="flex items-center justify-between p-3 border-b border-border dark:border-border">
        <h3 className="text-sm font-medium text-fg dark:text-fg">
          {t('lifecycle.title')} ({filteredEvents.length})
        </h3>
        <div className="flex items-center gap-1">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={cn(
              'text-xs',
              autoRefresh ? 'text-info-600 dark:text-info-400' : 'text-fg-muted'
            )}
            aria-label={autoRefresh ? t('lifecycle.disableAutoRefresh') : t('lifecycle.enableAutoRefresh')}
          >
            {autoRefresh ? '🔄' : '⏸'}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={onRefresh}
            disabled={isLoading}
            className="text-xs text-fg-muted hover:text-fg"
            aria-label={t('lifecycle.refresh')}
          >
            {isLoading ? '⏳' : '🔄'}
          </Button>
        </div>
      </div>

      {/* View Mode Tabs */}
      <div className="flex border-b border-border dark:border-border">
        {(['timeline', 'phases', 'events'] as const).map(mode => (
          <button
            key={mode}
            onClick={() => setViewMode(mode)}
            className={cn(
              'px-3 py-2 text-xs font-medium transition-colors',
              viewMode === mode
                ? 'text-fg dark:text-fg border-b-2 border-info-500 dark:border-info-400'
                : 'text-fg-muted dark:text-fg-muted hover:text-fg dark:hover:text-fg'
            )}
          >
            {t(`lifecycle.views.${mode}`)}
          </button>
        ))}
      </div>

      {/* Filters */}
      <div className="p-3 border-b border-border dark:border-border space-y-2">
        <div className="flex gap-2">
          <input
            type="text"
            placeholder={t('lifecycle.searchPlaceholder')}
            value={filter.search}
            onChange={(e) => setFilter(prev => ({ ...prev, search: e.target.value }))}
            className="flex-1 px-2 py-1 text-xs border border-border dark:border-border rounded bg-surface dark:bg-surface-800 text-fg dark:text-fg"
          />
          <select
            value={filter.phase || ''}
            onChange={(e) => setFilter(prev => ({
              ...prev,
              phase: e.target.value ? e.target.value as LifecyclePhase : undefined
            }))}
            className="px-2 py-1 text-xs border border-border dark:border-border rounded bg-surface dark:bg-surface-800 text-fg dark:text-fg"
          >
            <option value="">{t('lifecycle.allPhases')}</option>
            {lifecyclePhases.map(phase => (
              <option key={phase} value={phase}>
                {t(`lifecycle.phases.${phase}`)}
              </option>
            ))}
          </select>
          <select
            value={filter.eventType || ''}
            onChange={(e) => setFilter(prev => ({
              ...prev,
              eventType: e.target.value ? e.target.value as LifecycleEventType : undefined
            }))}
            className="px-2 py-1 text-xs border border-border dark:border-border rounded bg-surface dark:bg-surface-800 text-fg dark:text-fg"
          >
            <option value="">{t('lifecycle.allEventTypes')}</option>
            {lifecycleEventTypes.map(type => (
              <option key={type} value={type}>
                {getEventIcon(type)} {t(`lifecycle.events.${type}`)}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto p-3">
        {filteredEvents.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-fg-muted dark:text-fg-muted">
            <div className="text-4xl mb-2">🔄</div>
            <p className="text-sm text-center">
              {isLoading ? t('lifecycle.loading') : t('lifecycle.noEvents')}
            </p>
          </div>
        ) : (
          <>
            {viewMode === 'timeline' && (
              <div className="space-y-2">
                {buildTimeline().map(node => renderTimelineNode(node))}
              </div>
            )}
            {viewMode === 'phases' && renderPhaseView()}
            {viewMode === 'events' && renderEventsList()}
          </>
        )}
      </div>

      {/* Selected Event Details */}
      {selectedEvent && (
        <div className="border-t border-border dark:border-border p-3">
          <div className="flex items-center justify-between mb-2">
            <h4 className="text-sm font-medium text-fg dark:text-fg">
              {t('lifecycle.eventDetails')}
            </h4>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setSelectedEvent(null)}
              className="text-xs text-fg-muted hover:text-fg"
              aria-label={t('lifecycle.closeDetails')}
            >
              ✕
            </Button>
          </div>
          <div className="space-y-2 text-xs">
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.eventId')}:</span>
              <span className="font-mono text-fg dark:text-fg">{selectedEvent.eventId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.eventType')}:</span>
              <span className="text-fg dark:text-fg">
                {getEventIcon(selectedEvent.eventType)} {t(`lifecycle.events.${selectedEvent.eventType}`)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.phase')}:</span>
              <span className={cn('text-fg dark:text-fg', getPhaseColor(selectedEvent.phase))}>
                {t(`lifecycle.phases.${selectedEvent.phase}`)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.status')}:</span>
              <span className={cn('font-medium', getStatusColor(getEventStatus(selectedEvent)))}>
                {t(`lifecycle.status.${getEventStatus(selectedEvent)}`)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.timestamp')}:</span>
              <span className="text-fg dark:text-fg">{formatTimestamp(selectedEvent.timestamp)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.correlationId')}:</span>
              <span className="font-mono text-fg dark:text-fg">{selectedEvent.correlationId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.source')}:</span>
              <span className="text-fg dark:text-fg">{selectedEvent.source}</span>
            </div>
            {selectedEvent.payload.durationMs && (
              <div className="flex justify-between">
                <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.duration')}:</span>
                <span className="text-fg dark:text-fg">{formatDuration(selectedEvent.payload.durationMs)}</span>
              </div>
            )}
            <div className="pt-2 border-t border-border dark:border-border">
              <span className="text-fg-muted dark:text-fg-muted">{t('lifecycle.payload')}:</span>
              <pre className="mt-1 text-fg dark:text-fg bg-surface-100 dark:bg-surface-800 p-2 rounded text-xs overflow-auto max-h-32">
                {JSON.stringify(selectedEvent.payload, null, 2)}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Hook for fetching lifecycle events.
 *
 * @doc.type hook
 * @doc.purpose Lifecycle events data fetching
 */
export function useLifecycleEvents(productUnitId?: string, runId?: string) {
  const [events, setEvents] = useState<LifecycleEvent[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchLifecycleEvents = React.useCallback(async () => {
    if (!productUnitId) return;

    setIsLoading(true);
    setError(null);

    try {
      // This would integrate with the kernel lifecycle event provider
      // For now, we'll simulate with mock data
      const mockEvents: LifecycleEvent[] = [
        {
          eventId: 'evt-1',
          eventType: 'lifecycle.phase.started',
          productUnitId,
          runId: runId || 'run-123',
          phase: 'planning',
          timestamp: new Date(Date.now() - 300000).toISOString(),
          source: 'kernel-lifecycle',
          correlationId: 'corr-1',
          payload: { phase: 'planning', status: 'running', startedAt: new Date(Date.now() - 300000).toISOString() },
        },
        {
          eventId: 'evt-2',
          eventType: 'lifecycle.step.started',
          productUnitId,
          runId: runId || 'run-123',
          phase: 'planning',
          timestamp: new Date(Date.now() - 240000).toISOString(),
          source: 'kernel-lifecycle',
          correlationId: 'corr-1',
          payload: {
            stepId: 'step-1',
            stepKind: 'validation',
            surface: 'planning-surface',
            adapter: 'planning-adapter',
            status: 'running',
            startedAt: new Date(Date.now() - 240000).toISOString()
          },
        },
        {
          eventId: 'evt-3',
          eventType: 'lifecycle.step.completed',
          productUnitId,
          runId: runId || 'run-123',
          phase: 'planning',
          timestamp: new Date(Date.now() - 180000).toISOString(),
          source: 'kernel-lifecycle',
          correlationId: 'corr-1',
          payload: {
            stepId: 'step-1',
            stepKind: 'validation',
            surface: 'planning-surface',
            adapter: 'planning-adapter',
            status: 'succeeded',
            durationMs: 60000,
            completedAt: new Date(Date.now() - 180000).toISOString(),
            evidenceRefs: ['evidence-1', 'evidence-2']
          },
        },
        {
          eventId: 'evt-4',
          eventType: 'lifecycle.gate.evaluated',
          productUnitId,
          runId: runId || 'run-123',
          phase: 'planning',
          timestamp: new Date(Date.now() - 120000).toISOString(),
          source: 'kernel-lifecycle',
          correlationId: 'corr-1',
          payload: {
            gateId: 'gate-1',
            status: 'passed',
            required: true,
            reason: 'All validation checks passed',
            evidenceRefs: ['evidence-3'],
            durationMs: 30000
          },
        },
        {
          eventId: 'evt-5',
          eventType: 'lifecycle.artifact.recorded',
          productUnitId,
          runId: runId || 'run-123',
          phase: 'planning',
          timestamp: new Date(Date.now() - 60000).toISOString(),
          source: 'kernel-lifecycle',
          correlationId: 'corr-1',
          payload: {
            artifactId: 'artifact-1',
            artifactType: 'plan-document',
            required: true,
            path: 'plan-v1.2.3.pdf',
            fingerprint: 'sha256:abc123...',
            evidenceRefs: ['surface:planning-surface', 'version:v1.2.3', 'size:1024KB']
          },
        },
      ];

      setEvents(mockEvents);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch lifecycle events');
    } finally {
      setIsLoading(false);
    }
  }, [productUnitId, runId]);

  useEffect(() => {
    fetchLifecycleEvents();
  }, [fetchLifecycleEvents]);

  return {
    events,
    isLoading,
    error,
    refetch: fetchLifecycleEvents,
  };
}
