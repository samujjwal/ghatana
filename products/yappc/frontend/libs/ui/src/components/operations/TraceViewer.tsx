/**
 * TraceViewer Component
 *
 * @description Displays distributed traces with span hierarchy, timing,
 * and service relationships for the operations traces view.
 *
 * @doc.type component
 * @doc.purpose Distributed trace visualization
 * @doc.layer presentation
 * @doc.phase 4
 */

import React, { useState, useMemo } from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type SpanStatus = 'ok' | 'error' | 'unset';

export interface SpanTag {
  key: string;
  value: string;
}

export interface SpanLog {
  timestamp: string;
  message: string;
  level?: 'info' | 'warn' | 'error';
}

export interface Span {
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  operationName: string;
  serviceName: string;
  startTime: number;
  duration: number;
  status: SpanStatus;
  tags: SpanTag[];
  logs: SpanLog[];
}

export interface Trace {
  traceId: string;
  spans: Span[];
  startTime: number;
  duration: number;
  services: string[];
  spanCount: number;
  errorCount: number;
}

export interface TraceViewerProps {
  trace: Trace;
  onSpanClick?: (span: Span) => void;
  selectedSpanId?: string;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getServiceColor = (serviceName: string, index: number): string => {
  const colors = [
    '#3B82F6', '#10B981', '#8B5CF6', '#F59E0B', '#EF4444',
    '#06B6D4', '#EC4899', '#84CC16', '#F97316', '#6366F1',
  ];
  return colors[index % colors.length];
};

const formatDuration = (microseconds: number): string => {
  if (microseconds < 1000) return `${microseconds}μs`;
  if (microseconds < 1000000) return `${(microseconds / 1000).toFixed(2)}ms`;
  return `${(microseconds / 1000000).toFixed(2)}s`;
};

const formatTime = (timestamp: number): string => {
  return new Date(timestamp / 1000).toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3,
  });
};

// Build span hierarchy
interface SpanNode extends Span {
  children: SpanNode[];
  depth: number;
}

const buildSpanTree = (spans: Span[]): SpanNode[] => {
  const spanMap = new Map<string, SpanNode>();
  const roots: SpanNode[] = [];

  // Create nodes
  spans.forEach((span) => {
    spanMap.set(span.spanId, { ...span, children: [], depth: 0 });
  });

  // Build tree
  spans.forEach((span) => {
    const node = spanMap.get(span.spanId)!;
    if (span.parentSpanId && spanMap.has(span.parentSpanId)) {
      const parent = spanMap.get(span.parentSpanId)!;
      parent.children.push(node);
      node.depth = parent.depth + 1;
    } else {
      roots.push(node);
    }
  });

  // Flatten tree in order
  const flatten = (nodes: SpanNode[], depth = 0): SpanNode[] => {
    const result: SpanNode[] = [];
    nodes.forEach((node) => {
      node.depth = depth;
      result.push(node);
      if (node.children.length > 0) {
        result.push(...flatten(node.children, depth + 1));
      }
    });
    return result;
  };

  return flatten(roots);
};

// ============================================================================
// Span Row Sub-component
// ============================================================================

interface SpanRowProps {
  span: SpanNode;
  traceStartTime: number;
  traceDuration: number;
  serviceColors: Map<string, string>;
  isSelected: boolean;
  onClick: () => void;
}

const SpanRow: React.FC<SpanRowProps> = ({
  span,
  traceStartTime,
  traceDuration,
  serviceColors,
  isSelected,
  onClick,
}) => {
  const [expanded, setExpanded] = useState(false);

  const offsetPercent = ((span.startTime - traceStartTime) / traceDuration) * 100;
  const widthPercent = Math.max((span.duration / traceDuration) * 100, 0.5);
  const serviceColor = serviceColors.get(span.serviceName) || '#6B7280';

  return (
    <>
      <div
        className={cn(
          'span-row',
          isSelected && 'span-row--selected',
          span.status === 'error' && 'span-row--error'
        )}
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        role="button"
        tabIndex={0}
      >
        {/* Info Column */}
        <div className="span-info" style={{ paddingLeft: `${span.depth * 16 + 8}px` }}>
          {/* Expand Button */}
          {(span.tags.length > 0 || span.logs.length > 0) && (
            <button
              type="button"
              className="expand-btn"
              onClick={(e) => {
                e.stopPropagation();
                setExpanded(!expanded);
              }}
              aria-label={expanded ? 'Collapse' : 'Expand'}
            >
              {expanded ? '▼' : '▶'}
            </button>
          )}

          {/* Service Badge */}
          <span
            className="service-badge"
            style={{ backgroundColor: serviceColor }}
          >
            {span.serviceName}
          </span>

          {/* Operation Name */}
          <span className="operation-name">{span.operationName}</span>

          {/* Status Icon */}
          {span.status === 'error' && <span className="error-icon">❌</span>}
        </div>

        {/* Timeline Column */}
        <div className="span-timeline">
          <div className="timeline-track">
            <div
              className="timeline-bar"
              style={{
                left: `${offsetPercent}%`,
                width: `${widthPercent}%`,
                backgroundColor: span.status === 'error' ? '#EF4444' : serviceColor,
              }}
            >
              <span className="duration-label">{formatDuration(span.duration)}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Expanded Details */}
      {expanded && (
        <div className="span-details">
          {/* Tags */}
          {span.tags.length > 0 && (
            <div className="details-section">
              <h5 className="section-title">Tags</h5>
              <div className="tags-grid">
                {span.tags.map((tag) => (
                  <div key={tag.key} className="tag-item">
                    <span className="tag-key">{tag.key}</span>
                    <span className="tag-value">{tag.value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Logs */}
          {span.logs.length > 0 && (
            <div className="details-section">
              <h5 className="section-title">Logs</h5>
              <div className="logs-list">
                {span.logs.map((log, i) => (
                  <div
                    key={i}
                    className={cn('log-item', log.level && `log-item--${log.level}`)}
                  >
                    <span className="log-time">{formatTime(new Date(log.timestamp).getTime() * 1000)}</span>
                    <span className="log-message">{log.message}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const TraceViewer: React.FC<TraceViewerProps> = ({
  trace,
  onSpanClick,
  selectedSpanId,
  className,
}) => {
  // Build span tree
  const flattenedSpans = useMemo(() => buildSpanTree(trace.spans), [trace.spans]);

  // Assign colors to services
  const serviceColors = useMemo(() => {
    const colors = new Map<string, string>();
    trace.services.forEach((service, index) => {
      colors.set(service, getServiceColor(service, index));
    });
    return colors;
  }, [trace.services]);

  // Calculate timeline ticks
  const timelineTicks = useMemo(() => {
    const ticks = [];
    const tickCount = 5;
    for (let i = 0; i <= tickCount; i++) {
      const percentage = (i / tickCount) * 100;
      const time = trace.startTime + (trace.duration * i) / tickCount;
      ticks.push({ percentage, label: formatDuration((trace.duration * i) / tickCount) });
    }
    return ticks;
  }, [trace.startTime, trace.duration]);

  return (
    <div className={cn('trace-viewer', className)}>
      {/* Header */}
      <div className="trace-header">
        <div className="trace-info">
          <h3 className="trace-title">Trace {trace.traceId.slice(0, 8)}...</h3>
          <div className="trace-meta">
            <span className="meta-item">
              <span className="meta-label">Duration:</span>
              <span className="meta-value">{formatDuration(trace.duration)}</span>
            </span>
            <span className="meta-item">
              <span className="meta-label">Spans:</span>
              <span className="meta-value">{trace.spanCount}</span>
            </span>
            <span className="meta-item">
              <span className="meta-label">Services:</span>
              <span className="meta-value">{trace.services.length}</span>
            </span>
            {trace.errorCount > 0 && (
              <span className="meta-item meta-item--error">
                <span className="meta-label">Errors:</span>
                <span className="meta-value">{trace.errorCount}</span>
              </span>
            )}
          </div>
        </div>

        {/* Service Legend */}
        <div className="service-legend">
          {trace.services.map((service) => (
            <div key={service} className="legend-item">
              <span
                className="legend-dot"
                style={{ backgroundColor: serviceColors.get(service) }}
              />
              <span className="legend-label">{service}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Timeline Header */}
      <div className="timeline-header">
        <div className="timeline-info-col">
          <span className="col-label">Service / Operation</span>
        </div>
        <div className="timeline-col">
          <div className="timeline-ticks">
            {timelineTicks.map((tick) => (
              <span
                key={tick.percentage}
                className="tick-label"
                style={{ left: `${tick.percentage}%` }}
              >
                {tick.label}
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* Spans */}
      <div className="spans-container">
        {flattenedSpans.map((span) => (
          <SpanRow
            key={span.spanId}
            span={span}
            traceStartTime={trace.startTime}
            traceDuration={trace.duration}
            serviceColors={serviceColors}
            isSelected={selectedSpanId === span.spanId}
            onClick={() => onSpanClick?.(span)}
          />
        ))}
      </div>

      <style>{`
        .trace-viewer {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          overflow: hidden;
        }

        .trace-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          padding: 1rem;
          border-bottom: 1px solid #E5E7EB;
          background: #F9FAFB;
        }

        .trace-title {
          margin: 0 0 0.5rem 0;
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
          font-family: 'JetBrains Mono', monospace;
        }

        .trace-meta {
          display: flex;
          gap: 1rem;
        }

        .meta-item {
          display: flex;
          align-items: center;
          gap: 0.25rem;
          font-size: 0.8125rem;
        }

        .meta-label {
          color: #6B7280;
        }

        .meta-value {
          color: #111827;
          font-weight: 500;
        }

        .meta-item--error {
          color: #EF4444;
        }

        .meta-item--error .meta-label,
        .meta-item--error .meta-value {
          color: inherit;
        }

        .service-legend {
          display: flex;
          flex-wrap: wrap;
          gap: 0.75rem;
        }

        .legend-item {
          display: flex;
          align-items: center;
          gap: 0.375rem;
        }

        .legend-dot {
          width: 10px;
          height: 10px;
          border-radius: 50%;
        }

        .legend-label {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .timeline-header {
          display: flex;
          padding: 0.5rem 1rem;
          border-bottom: 1px solid #E5E7EB;
          background: #F9FAFB;
        }

        .timeline-info-col {
          width: 40%;
          min-width: 200px;
        }

        .timeline-col {
          flex: 1;
          position: relative;
        }

        .col-label {
          font-size: 0.6875rem;
          font-weight: 600;
          color: #6B7280;
          text-transform: uppercase;
          letter-spacing: 0.025em;
        }

        .timeline-ticks {
          position: relative;
          height: 20px;
        }

        .tick-label {
          position: absolute;
          transform: translateX(-50%);
          font-size: 0.6875rem;
          color: #9CA3AF;
        }

        .spans-container {
          max-height: 500px;
          overflow-y: auto;
        }

        .span-row {
          display: flex;
          padding: 0.5rem 1rem;
          border-bottom: 1px solid #F3F4F6;
          cursor: pointer;
          transition: background 0.1s ease;
        }

        .span-row:hover {
          background: #F9FAFB;
        }

        .span-row--selected {
          background: #EFF6FF;
        }

        .span-row--error {
          background: rgba(239, 68, 68, 0.05);
        }

        .span-info {
          width: 40%;
          min-width: 200px;
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .expand-btn {
          background: transparent;
          border: none;
          font-size: 0.625rem;
          color: #9CA3AF;
          cursor: pointer;
          padding: 0.125rem;
        }

        .expand-btn:hover {
          color: #6B7280;
        }

        .service-badge {
          font-size: 0.625rem;
          font-weight: 600;
          color: #fff;
          padding: 0.125rem 0.375rem;
          border-radius: 4px;
          flex-shrink: 0;
        }

        .operation-name {
          font-size: 0.8125rem;
          color: #374151;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .error-icon {
          font-size: 0.75rem;
          flex-shrink: 0;
        }

        .span-timeline {
          flex: 1;
          display: flex;
          align-items: center;
        }

        .timeline-track {
          flex: 1;
          height: 16px;
          background: #F3F4F6;
          border-radius: 4px;
          position: relative;
          overflow: hidden;
        }

        .timeline-bar {
          position: absolute;
          top: 2px;
          height: 12px;
          border-radius: 3px;
          min-width: 4px;
          display: flex;
          align-items: center;
          justify-content: flex-end;
          padding-right: 4px;
        }

        .duration-label {
          font-size: 0.625rem;
          color: #fff;
          font-weight: 500;
          white-space: nowrap;
        }

        .span-details {
          padding: 0.75rem 1rem 0.75rem calc(1rem + 32px);
          background: #F9FAFB;
          border-bottom: 1px solid #E5E7EB;
        }

        .details-section {
          margin-bottom: 0.75rem;
        }

        .details-section:last-child {
          margin-bottom: 0;
        }

        .section-title {
          margin: 0 0 0.5rem 0;
          font-size: 0.75rem;
          font-weight: 600;
          color: #6B7280;
          text-transform: uppercase;
        }

        .tags-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
          gap: 0.375rem;
        }

        .tag-item {
          display: flex;
          font-size: 0.75rem;
          background: #fff;
          padding: 0.25rem 0.5rem;
          border-radius: 4px;
          border: 1px solid #E5E7EB;
        }

        .tag-key {
          color: #6B7280;
          margin-right: 0.375rem;
        }

        .tag-value {
          color: #111827;
          font-family: 'JetBrains Mono', monospace;
          word-break: break-all;
        }

        .logs-list {
          display: flex;
          flex-direction: column;
          gap: 0.25rem;
        }

        .log-item {
          display: flex;
          gap: 0.5rem;
          font-size: 0.75rem;
          padding: 0.25rem 0.5rem;
          background: #fff;
          border-radius: 4px;
          border: 1px solid #E5E7EB;
        }

        .log-item--error {
          border-color: #FCA5A5;
          background: #FEF2F2;
        }

        .log-item--warn {
          border-color: #FCD34D;
          background: #FFFBEB;
        }

        .log-time {
          color: #9CA3AF;
          font-family: 'JetBrains Mono', monospace;
          flex-shrink: 0;
        }

        .log-message {
          color: #374151;
        }

        @media (prefers-color-scheme: dark) {
          .trace-viewer {
            background: #1F2937;
            border-color: #374151;
          }

          .trace-header {
            background: #111827;
            border-bottom-color: #374151;
          }

          .trace-title {
            color: #F9FAFB;
          }

          .meta-value {
            color: #F9FAFB;
          }

          .legend-label {
            color: #9CA3AF;
          }

          .timeline-header {
            background: #111827;
            border-bottom-color: #374151;
          }

          .span-row {
            border-bottom-color: #374151;
          }

          .span-row:hover {
            background: #111827;
          }

          .span-row--selected {
            background: rgba(59, 130, 246, 0.1);
          }

          .operation-name {
            color: #E5E7EB;
          }

          .timeline-track {
            background: #374151;
          }

          .span-details {
            background: #111827;
            border-bottom-color: #374151;
          }

          .tag-item,
          .log-item {
            background: #1F2937;
            border-color: #374151;
          }

          .tag-value,
          .log-message {
            color: #E5E7EB;
          }
        }
      `}</style>
    </div>
  );
};

TraceViewer.displayName = 'TraceViewer';

export default TraceViewer;
