/**
 * HealthStatusCard Component
 *
 * @description Displays system health status with component breakdown
 * for the operations dashboard.
 *
 * @doc.type component
 * @doc.purpose System health display
 * @doc.layer presentation
 * @doc.phase 4
 */

import React, { useMemo } from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type HealthStatus = 'healthy' | 'degraded' | 'critical' | 'unknown';
export type ComponentStatus = 'operational' | 'degraded' | 'outage' | 'maintenance';

export interface HealthComponent {
  id: string;
  name: string;
  status: ComponentStatus;
  latency?: number;
  uptime?: number;
  lastChecked?: string;
}

export interface HealthStatusCardProps {
  status: HealthStatus;
  components: HealthComponent[];
  lastUpdated?: string;
  onRefresh?: () => void;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getStatusConfig = (status: HealthStatus) => {
  const configs: Record<HealthStatus, { icon: string; label: string; color: string; bg: string }> = {
    healthy: { icon: '🟢', label: 'All Systems Operational', color: '#10B981', bg: 'rgba(16, 185, 129, 0.1)' },
    degraded: { icon: '🟡', label: 'Partial System Outage', color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)' },
    critical: { icon: '🔴', label: 'Major System Outage', color: '#EF4444', bg: 'rgba(239, 68, 68, 0.1)' },
    unknown: { icon: '⚪', label: 'Status Unknown', color: '#6B7280', bg: 'rgba(107, 114, 128, 0.1)' },
  };
  return configs[status];
};

const getComponentStatusConfig = (status: ComponentStatus) => {
  const configs: Record<ComponentStatus, { icon: string; color: string }> = {
    operational: { icon: '✅', color: '#10B981' },
    degraded: { icon: '⚠️', color: '#F59E0B' },
    outage: { icon: '❌', color: '#EF4444' },
    maintenance: { icon: '🔧', color: '#6B7280' },
  };
  return configs[status];
};

// ============================================================================
// Component
// ============================================================================

export const HealthStatusCard: React.FC<HealthStatusCardProps> = ({
  status,
  components,
  lastUpdated,
  onRefresh,
  className,
}) => {
  const statusConfig = getStatusConfig(status);

  const componentStats = useMemo(() => {
    const operational = components.filter(c => c.status === 'operational').length;
    const total = components.length;
    return { operational, total };
  }, [components]);

  return (
    <div className={cn('health-status-card', className)}>
      {/* Header */}
      <div className="health-header">
        <div className="health-status">
          <span className="status-icon">{statusConfig.icon}</span>
          <div className="status-text">
            <h3 className="status-label">{statusConfig.label}</h3>
            <span className="status-summary">
              {componentStats.operational}/{componentStats.total} components operational
            </span>
          </div>
        </div>
        {onRefresh && (
          <button
            type="button"
            className="refresh-btn"
            onClick={onRefresh}
            aria-label="Refresh status"
          >
            🔄
          </button>
        )}
      </div>

      {/* Component List */}
      <div className="components-list">
        {components.map((component) => {
          const compConfig = getComponentStatusConfig(component.status);
          return (
            <div key={component.id} className="component-item">
              <span className="component-icon">{compConfig.icon}</span>
              <span className="component-name">{component.name}</span>
              {component.latency !== undefined && (
                <span className="component-latency">{component.latency}ms</span>
              )}
              {component.uptime !== undefined && (
                <span className="component-uptime">{component.uptime.toFixed(2)}%</span>
              )}
            </div>
          );
        })}
      </div>

      {/* Footer */}
      {lastUpdated && (
        <div className="health-footer">
          <span className="last-updated">Last checked: {lastUpdated}</span>
        </div>
      )}

      <style>{`
        .health-status-card {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          padding: 1rem;
        }

        .health-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          margin-bottom: 1rem;
        }

        .health-status {
          display: flex;
          align-items: center;
          gap: 0.75rem;
        }

        .status-icon {
          font-size: 1.5rem;
        }

        .status-text {
          display: flex;
          flex-direction: column;
        }

        .status-label {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #111827;
        }

        .status-summary {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .refresh-btn {
          background: transparent;
          border: none;
          font-size: 1rem;
          cursor: pointer;
          padding: 0.25rem;
          border-radius: 4px;
        }

        .refresh-btn:hover {
          background: #F3F4F6;
        }

        .components-list {
          display: flex;
          flex-wrap: wrap;
          gap: 0.5rem;
        }

        .component-item {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          padding: 0.375rem 0.625rem;
          background: #F9FAFB;
          border-radius: 6px;
          font-size: 0.75rem;
        }

        .component-icon {
          font-size: 0.875rem;
        }

        .component-name {
          color: #374151;
          font-weight: 500;
        }

        .component-latency,
        .component-uptime {
          color: #9CA3AF;
          font-size: 0.6875rem;
        }

        .health-footer {
          margin-top: 0.75rem;
          padding-top: 0.75rem;
          border-top: 1px solid #F3F4F6;
        }

        .last-updated {
          font-size: 0.6875rem;
          color: #9CA3AF;
        }

        @media (prefers-color-scheme: dark) {
          .health-status-card {
            background: #1F2937;
            border-color: #374151;
          }

          .status-label {
            color: #F9FAFB;
          }

          .status-summary {
            color: #9CA3AF;
          }

          .component-item {
            background: #111827;
          }

          .component-name {
            color: #E5E7EB;
          }

          .health-footer {
            border-top-color: #374151;
          }

          .refresh-btn:hover {
            background: #374151;
          }
        }
      `}</style>
    </div>
  );
};

HealthStatusCard.displayName = 'HealthStatusCard';

export default HealthStatusCard;
