/**
 * AlertCard Component
 *
 * @description Displays a single alert with severity, status, and actions
 * for the operations alerts view.
 *
 * @doc.type component
 * @doc.purpose Alert display and management
 * @doc.layer presentation
 * @doc.phase 4
 */

import React from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type AlertSeverity = 'critical' | 'high' | 'medium' | 'low' | 'info';
export type AlertStatus = 'firing' | 'pending' | 'resolved' | 'silenced';

export interface AlertRule {
  id: string;
  name: string;
  condition: string;
  threshold?: number;
  windowMinutes?: number;
}

export interface Alert {
  id: string;
  title: string;
  description: string;
  severity: AlertSeverity;
  status: AlertStatus;
  source: string;
  firedAt: string;
  resolvedAt?: string;
  labels: Record<string, string>;
  rule?: AlertRule;
  assignee?: string;
}

export interface AlertCardProps {
  alert: Alert;
  onAcknowledge?: (alertId: string) => void;
  onSilence?: (alertId: string, duration: number) => void;
  onResolve?: (alertId: string) => void;
  onViewDetails?: (alertId: string) => void;
  onCreateIncident?: (alertId: string) => void;
  compact?: boolean;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getSeverityConfig = (severity: AlertSeverity) => {
  const configs: Record<AlertSeverity, { icon: string; color: string; bg: string }> = {
    critical: { icon: '🔴', color: '#DC2626', bg: 'rgba(220, 38, 38, 0.1)' },
    high: { icon: '🟠', color: '#EA580C', bg: 'rgba(234, 88, 12, 0.1)' },
    medium: { icon: '🟡', color: '#CA8A04', bg: 'rgba(202, 138, 4, 0.1)' },
    low: { icon: '🔵', color: '#2563EB', bg: 'rgba(37, 99, 235, 0.1)' },
    info: { icon: '⚪', color: '#6B7280', bg: 'rgba(107, 114, 128, 0.1)' },
  };
  return configs[severity];
};

const getStatusConfig = (status: AlertStatus) => {
  const configs: Record<AlertStatus, { label: string; color: string }> = {
    firing: { label: 'Firing', color: '#EF4444' },
    pending: { label: 'Pending', color: '#F59E0B' },
    resolved: { label: 'Resolved', color: '#10B981' },
    silenced: { label: 'Silenced', color: '#6B7280' },
  };
  return configs[status];
};

const formatRelativeTime = (dateString: string): string => {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
};

// ============================================================================
// Component
// ============================================================================

export const AlertCard: React.FC<AlertCardProps> = ({
  alert,
  onAcknowledge,
  onSilence,
  onResolve,
  onViewDetails,
  onCreateIncident,
  compact = false,
  className,
}) => {
  const severityConfig = getSeverityConfig(alert.severity);
  const statusConfig = getStatusConfig(alert.status);

  const handleSilence = (e: React.MouseEvent) => {
    e.stopPropagation();
    onSilence?.(alert.id, 30);
  };

  return (
    <div
      className={cn(
        'alert-card',
        compact && 'alert-card--compact',
        `alert-card--${alert.status}`,
        className
      )}
      onClick={() => onViewDetails?.(alert.id)}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && onViewDetails) {
          e.preventDefault();
          onViewDetails(alert.id);
        }
      }}
      role={onViewDetails ? 'button' : undefined}
      tabIndex={onViewDetails ? 0 : undefined}
    >
      {/* Severity Indicator */}
      <div
        className="alert-severity-bar"
        style={{ backgroundColor: severityConfig.color }}
      />

      {/* Main Content */}
      <div className="alert-content">
        {/* Header */}
        <div className="alert-header">
          <div className="alert-meta">
            <span className="alert-severity-icon">{severityConfig.icon}</span>
            <span
              className="alert-status"
              style={{ color: statusConfig.color }}
            >
              {statusConfig.label}
            </span>
            <span className="alert-source">{alert.source}</span>
          </div>
          <span className="alert-time">{formatRelativeTime(alert.firedAt)}</span>
        </div>

        {/* Title & Description */}
        <h4 className="alert-title">{alert.title}</h4>
        {!compact && (
          <p className="alert-description">{alert.description}</p>
        )}

        {/* Labels */}
        {!compact && Object.keys(alert.labels).length > 0 && (
          <div className="alert-labels">
            {Object.entries(alert.labels).slice(0, 3).map(([key, value]) => (
              <span key={key} className="alert-label">
                {key}: {value}
              </span>
            ))}
            {Object.keys(alert.labels).length > 3 && (
              <span className="alert-label alert-label--more">
                +{Object.keys(alert.labels).length - 3} more
              </span>
            )}
          </div>
        )}

        {/* Rule Info */}
        {!compact && alert.rule && (
          <div className="alert-rule">
            <span className="rule-label">Rule:</span>
            <span className="rule-name">{alert.rule.name}</span>
            {alert.rule.condition && (
              <span className="rule-condition">{alert.rule.condition}</span>
            )}
          </div>
        )}

        {/* Actions */}
        {alert.status === 'firing' && (
          <div className="alert-actions">
            {onAcknowledge && (
              <button
                type="button"
                className="alert-action alert-action--acknowledge"
                onClick={(e) => {
                  e.stopPropagation();
                  onAcknowledge(alert.id);
                }}
              >
                Acknowledge
              </button>
            )}
            {onSilence && (
              <button
                type="button"
                className="alert-action alert-action--silence"
                onClick={handleSilence}
              >
                Silence 30m
              </button>
            )}
            {onResolve && (
              <button
                type="button"
                className="alert-action alert-action--resolve"
                onClick={(e) => {
                  e.stopPropagation();
                  onResolve(alert.id);
                }}
              >
                Resolve
              </button>
            )}
            {onCreateIncident && (
              <button
                type="button"
                className="alert-action alert-action--incident"
                onClick={(e) => {
                  e.stopPropagation();
                  onCreateIncident(alert.id);
                }}
              >
                Create Incident
              </button>
            )}
          </div>
        )}
      </div>

      <style>{`
        .alert-card {
          display: flex;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 8px;
          overflow: hidden;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .alert-card:hover {
          border-color: #D1D5DB;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
        }

        .alert-card--firing {
          border-left-color: #EF4444;
        }

        .alert-card--compact .alert-content {
          padding: 0.75rem;
        }

        .alert-severity-bar {
          width: 4px;
          flex-shrink: 0;
        }

        .alert-content {
          flex: 1;
          padding: 1rem;
          min-width: 0;
        }

        .alert-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.5rem;
        }

        .alert-meta {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .alert-severity-icon {
          font-size: 0.875rem;
        }

        .alert-status {
          font-size: 0.75rem;
          font-weight: 600;
          text-transform: uppercase;
        }

        .alert-source {
          font-size: 0.75rem;
          color: #6B7280;
          padding: 0.125rem 0.375rem;
          background: #F3F4F6;
          border-radius: 4px;
        }

        .alert-time {
          font-size: 0.75rem;
          color: #9CA3AF;
          flex-shrink: 0;
        }

        .alert-title {
          margin: 0 0 0.25rem 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #111827;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .alert-description {
          margin: 0;
          font-size: 0.8125rem;
          color: #6B7280;
          line-height: 1.4;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .alert-labels {
          display: flex;
          flex-wrap: wrap;
          gap: 0.375rem;
          margin-top: 0.5rem;
        }

        .alert-label {
          font-size: 0.6875rem;
          color: #4B5563;
          padding: 0.125rem 0.375rem;
          background: #F9FAFB;
          border: 1px solid #E5E7EB;
          border-radius: 4px;
          font-family: monospace;
        }

        .alert-label--more {
          font-style: italic;
          font-family: inherit;
          color: #9CA3AF;
        }

        .alert-rule {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          margin-top: 0.5rem;
          font-size: 0.75rem;
        }

        .rule-label {
          color: #9CA3AF;
        }

        .rule-name {
          color: #6B7280;
          font-weight: 500;
        }

        .rule-condition {
          color: #9CA3AF;
          font-family: monospace;
          font-size: 0.6875rem;
        }

        .alert-actions {
          display: flex;
          gap: 0.5rem;
          margin-top: 0.75rem;
          padding-top: 0.75rem;
          border-top: 1px solid #F3F4F6;
        }

        .alert-action {
          font-size: 0.75rem;
          font-weight: 500;
          padding: 0.375rem 0.625rem;
          border-radius: 6px;
          border: none;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .alert-action--acknowledge {
          background: #EFF6FF;
          color: #2563EB;
        }

        .alert-action--acknowledge:hover {
          background: #DBEAFE;
        }

        .alert-action--silence {
          background: #FEF3C7;
          color: #B45309;
        }

        .alert-action--silence:hover {
          background: #FDE68A;
        }

        .alert-action--resolve {
          background: #D1FAE5;
          color: #059669;
        }

        .alert-action--resolve:hover {
          background: #A7F3D0;
        }

        .alert-action--incident {
          background: #FEE2E2;
          color: #DC2626;
        }

        .alert-action--incident:hover {
          background: #FECACA;
        }

        @media (prefers-color-scheme: dark) {
          .alert-card {
            background: #1F2937;
            border-color: #374151;
          }

          .alert-card:hover {
            border-color: #4B5563;
          }

          .alert-source {
            background: #374151;
            color: #9CA3AF;
          }

          .alert-title {
            color: #F9FAFB;
          }

          .alert-description {
            color: #9CA3AF;
          }

          .alert-label {
            background: #111827;
            border-color: #374151;
            color: #D1D5DB;
          }

          .alert-actions {
            border-top-color: #374151;
          }

          .alert-action--acknowledge {
            background: rgba(59, 130, 246, 0.2);
            color: #60A5FA;
          }

          .alert-action--silence {
            background: rgba(245, 158, 11, 0.2);
            color: #FBBF24;
          }

          .alert-action--resolve {
            background: rgba(16, 185, 129, 0.2);
            color: #34D399;
          }

          .alert-action--incident {
            background: rgba(239, 68, 68, 0.2);
            color: #F87171;
          }
        }
      `}</style>
    </div>
  );
};

AlertCard.displayName = 'AlertCard';

export default AlertCard;
