/**
 * @fileoverview Simplified DevSecOps Dashboard
 * Streamlined view focusing on critical metrics and alerts
 * 
 * @doc.type component
 * @doc.purpose Simplified DevSecOps monitoring dashboard
 * @doc.layer presentation
 * @doc.pattern Dashboard
 */

import React, { useState, useEffect, useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface ComponentHealth {
  id: string;
  name: string;
  status: 'healthy' | 'warning' | 'critical' | 'unknown';
  score: number;
  lastUpdated: string;
  metrics: {
    availability: number;
    latency: number;
    errors: number;
  };
}

export interface SecurityAlert {
  id: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  title: string;
  description: string;
  component: string;
  timestamp: string;
  acknowledged: boolean;
}

export interface DeploymentStatus {
  id: string;
  service: string;
  version: string;
  environment: string;
  status: 'deploying' | 'deployed' | 'failed' | 'rolling_back';
  progress: number;
  startedAt: string;
}

export interface SimplifiedDashboardProps {
  components?: ComponentHealth[];
  alerts?: SecurityAlert[];
  deployments?: DeploymentStatus[];
  onComponentClick?: (id: string) => void;
  onAlertAcknowledge?: (id: string) => void;
  onViewDetails?: () => void;
  className?: string;
}

// ============================================================================
// Simplified Dashboard Component
// ============================================================================

/**
 * Simplified DevSecOps Dashboard
 * @doc.purpose Provide at-a-glance system health and critical alerts
 */
export const SimplifiedDevSecOpsDashboard: React.FC<SimplifiedDashboardProps> = ({
  components = [],
  alerts = [],
  deployments = [],
  onComponentClick,
  onAlertAcknowledge,
  onViewDetails,
  className,
}) => {
  const [selectedFilter, setSelectedFilter] = useState<'all' | 'critical' | 'warning'>('all');
  const [showAcknowledged, setShowAcknowledged] = useState(false);

  // Calculate summary statistics
  const summary = useMemo(() => {
    const critical = components.filter(c => c.status === 'critical').length;
    const warning = components.filter(c => c.status === 'warning').length;
    const healthy = components.filter(c => c.status === 'healthy').length;
    const criticalAlerts = alerts.filter(a => a.severity === 'critical' && !a.acknowledged).length;
    
    return { critical, warning, healthy, total: components.length, criticalAlerts };
  }, [components, alerts]);

  // Filter components
  const filteredComponents = useMemo(() => {
    if (selectedFilter === 'all') return components;
    return components.filter(c => c.status === selectedFilter);
  }, [components, selectedFilter]);

  // Filter alerts
  const filteredAlerts = useMemo(() => {
    let result = alerts;
    if (!showAcknowledged) {
      result = result.filter(a => !a.acknowledged);
    }
    return result.slice(0, 5); // Show only top 5
  }, [alerts, showAcknowledged]);

  // Active deployments
  const activeDeployments = useMemo(() => {
    return deployments.filter(d => d.status === 'deploying' || d.status === 'rolling_back');
  }, [deployments]);

  return (
    <div className={`simplified-dashboard ${className || ''}`} style={containerStyle}>
      {/* Header */}
      <div style={headerStyle}>
        <div>
          <h2 style={{ margin: 0, fontSize: '24px', fontWeight: 600 }}>System Health</h2>
          <p style={{ margin: '4px 0 0 0', color: '#6b7280', fontSize: '14px' }}>
            Real-time overview of critical systems
          </p>
        </div>
        <button 
          onClick={onViewDetails}
          style={viewDetailsButtonStyle}
        >
          View Full Details →
        </button>
      </div>

      {/* Summary Cards */}
      <div style={summaryContainerStyle}>
        <SummaryCard 
          title="Critical"
          value={summary.critical}
          total={summary.total}
          color="#dc2626"
          icon="⚠️"
          onClick={() => setSelectedFilter('critical')}
          isActive={selectedFilter === 'critical'}
        />
        <SummaryCard 
          title="Warning"
          value={summary.warning}
          total={summary.total}
          color="#f59e0b"
          icon="⚡"
          onClick={() => setSelectedFilter('warning')}
          isActive={selectedFilter === 'warning'}
        />
        <SummaryCard 
          title="Healthy"
          value={summary.healthy}
          total={summary.total}
          color="#10b981"
          icon="✓"
          onClick={() => setSelectedFilter('all')}
          isActive={selectedFilter === 'all'}
        />
        <SummaryCard 
          title="Unacknowledged Alerts"
          value={summary.criticalAlerts}
          total={alerts.length}
          color="#7c3aed"
          icon="🔔"
          onClick={() => {}}
          isActive={false}
        />
      </div>

      {/* Active Deployments */}
      {activeDeployments.length > 0 && (
        <div style={sectionStyle}>
          <h3 style={sectionTitleStyle}>🚀 Active Deployments</h3>
          <div style={deploymentsContainerStyle}>
            {activeDeployments.map(deployment => (
              <DeploymentCard key={deployment.id} deployment={deployment} />
            ))}
          </div>
        </div>
      )}

      {/* Critical Alerts */}
      {filteredAlerts.length > 0 && (
        <div style={sectionStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <h3 style={{ ...sectionTitleStyle, margin: 0 }}>🚨 Critical Alerts</h3>
            <label style={{ fontSize: '13px', color: '#6b7280', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <input 
                type="checkbox" 
                checked={showAcknowledged}
                onChange={(e) => setShowAcknowledged(e.target.checked)}
              />
              Show acknowledged
            </label>
          </div>
          <div style={alertsContainerStyle}>
            {filteredAlerts.map(alert => (
              <AlertCard 
                key={alert.id} 
                alert={alert} 
                onAcknowledge={() => onAlertAcknowledge?.(alert.id)}
              />
            ))}
          </div>
        </div>
      )}

      {/* Component List */}
      <div style={sectionStyle}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <h3 style={{ ...sectionTitleStyle, margin: 0 }}>📊 Components</h3>
          <div style={{ display: 'flex', gap: '8px' }}>
            {(['all', 'critical', 'warning'] as const).map(filter => (
              <button
                key={filter}
                onClick={() => setSelectedFilter(filter)}
                style={{
                  padding: '4px 12px',
                  borderRadius: '4px',
                  border: 'none',
                  fontSize: '13px',
                  cursor: 'pointer',
                  background: selectedFilter === filter ? '#0066cc' : '#f3f4f6',
                  color: selectedFilter === filter ? 'white' : '#374151',
                }}
              >
                {filter.charAt(0).toUpperCase() + filter.slice(1)}
              </button>
            ))}
          </div>
        </div>
        <div style={componentsContainerStyle}>
          {filteredComponents.slice(0, 10).map(component => (
            <ComponentCard 
              key={component.id} 
              component={component}
              onClick={() => onComponentClick?.(component.id)}
            />
          ))}
          {filteredComponents.length > 10 && (
            <div style={{ textAlign: 'center', padding: '16px', color: '#6b7280', fontSize: '14px' }}>
              +{filteredComponents.length - 10} more components
            </div>
          )}
        </div>
      </div>

      {/* Quick Actions */}
      <div style={quickActionsStyle}>
        <QuickActionButton label="Run Health Check" icon="🔍" />
        <QuickActionButton label="View Logs" icon="📋" />
        <QuickActionButton label="Export Report" icon="📊" />
        <QuickActionButton label="Schedule Maintenance" icon="🔧" />
      </div>
    </div>
  );
};

// ============================================================================
// Sub-components
// ============================================================================

interface SummaryCardProps {
  title: string;
  value: number;
  total: number;
  color: string;
  icon: string;
  onClick: () => void;
  isActive: boolean;
}

const SummaryCard: React.FC<SummaryCardProps> = ({ title, value, total, color, icon, onClick, isActive }) => (
  <div 
    onClick={onClick}
    style={{
      ...summaryCardStyle,
      borderLeft: `4px solid ${color}`,
      background: isActive ? `${color}10` : 'white',
    }}
  >
    <div style={{ fontSize: '28px', marginBottom: '4px' }}>{icon}</div>
    <div style={{ fontSize: '32px', fontWeight: 700, color }}>{value}</div>
    <div style={{ fontSize: '13px', color: '#6b7280' }}>{title}</div>
    <div style={{ fontSize: '12px', color: '#9ca3af', marginTop: '4px' }}>
      of {total}
    </div>
  </div>
);

const DeploymentCard: React.FC<{ deployment: DeploymentStatus }> = ({ deployment }) => {
  const statusColors = {
    deploying: '#0066cc',
    deployed: '#10b981',
    failed: '#dc2626',
    rolling_back: '#f59e0b',
  };

  return (
    <div style={deploymentCardStyle}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontWeight: 600, fontSize: '14px' }}>{deployment.service}</div>
          <div style={{ fontSize: '12px', color: '#6b7280' }}>
            v{deployment.version} → {deployment.environment}
          </div>
        </div>
        <div style={{ 
          padding: '4px 8px', 
          borderRadius: '4px', 
          fontSize: '12px',
          background: `${statusColors[deployment.status]}20`,
          color: statusColors[deployment.status],
          fontWeight: 500,
        }}>
          {deployment.status.replace('_', ' ')}
        </div>
      </div>
      <div style={{ marginTop: '8px' }}>
        <div style={{ 
          height: '4px', 
          background: '#e5e7eb', 
          borderRadius: '2px',
          overflow: 'hidden',
        }}>
          <div style={{
            height: '100%',
            width: `${deployment.progress}%`,
            background: statusColors[deployment.status],
            transition: 'width 0.3s ease',
          }} />
        </div>
        <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '4px', textAlign: 'right' }}>
          {deployment.progress}%
        </div>
      </div>
    </div>
  );
};

const AlertCard: React.FC<{ alert: SecurityAlert; onAcknowledge: () => void }> = ({ alert, onAcknowledge }) => {
  const severityColors = {
    critical: '#dc2626',
    high: '#ea580c',
    medium: '#f59e0b',
    low: '#6b7280',
  };

  return (
    <div style={{
      ...alertCardStyle,
      borderLeft: `3px solid ${severityColors[alert.severity]}`,
      opacity: alert.acknowledged ? 0.6 : 1,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
            <span style={{
              padding: '2px 6px',
              borderRadius: '3px',
              fontSize: '11px',
              fontWeight: 600,
              textTransform: 'uppercase',
              background: `${severityColors[alert.severity]}20`,
              color: severityColors[alert.severity],
            }}>
              {alert.severity}
            </span>
            <span style={{ fontSize: '12px', color: '#9ca3af' }}>
              {new Date(alert.timestamp).toLocaleTimeString()}
            </span>
          </div>
          <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '2px' }}>
            {alert.title}
          </div>
          <div style={{ fontSize: '13px', color: '#6b7280' }}>
            {alert.description}
          </div>
          <div style={{ fontSize: '12px', color: '#9ca3af', marginTop: '4px' }}>
            Component: {alert.component}
          </div>
        </div>
        {!alert.acknowledged && (
          <button
            onClick={onAcknowledge}
            style={{
              padding: '4px 8px',
              fontSize: '12px',
              border: '1px solid #d1d5db',
              borderRadius: '4px',
              background: 'white',
              cursor: 'pointer',
              marginLeft: '8px',
            }}
          >
            Ack
          </button>
        )}
      </div>
    </div>
  );
};

const ComponentCard: React.FC<{ component: ComponentHealth; onClick: () => void }> = ({ component, onClick }) => {
  const statusColors = {
    healthy: '#10b981',
    warning: '#f59e0b',
    critical: '#dc2626',
    unknown: '#6b7280',
  };

  const statusIcons = {
    healthy: '✓',
    warning: '⚡',
    critical: '✕',
    unknown: '?',
  };

  return (
    <div onClick={onClick} style={componentCardStyle}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div style={{
          width: '36px',
          height: '36px',
          borderRadius: '8px',
          background: `${statusColors[component.status]}20`,
          color: statusColors[component.status],
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '16px',
          fontWeight: 700,
        }}>
          {statusIcons[component.status]}
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 600, fontSize: '14px' }}>{component.name}</div>
          <div style={{ fontSize: '12px', color: '#6b7280' }}>
            Score: {component.score}/100
          </div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '11px', color: '#9ca3af' }}>
            {component.metrics.availability}% up
          </div>
          <div style={{ fontSize: '11px', color: '#9ca3af' }}>
            {component.metrics.latency}ms
          </div>
        </div>
      </div>
    </div>
  );
};

const QuickActionButton: React.FC<{ label: string; icon: string }> = ({ label, icon }) => (
  <button style={quickActionButtonStyle}>
    <span style={{ fontSize: '16px' }}>{icon}</span>
    <span style={{ fontSize: '13px' }}>{label}</span>
  </button>
);

// ============================================================================
// Styles
// ============================================================================

const containerStyle: React.CSSProperties = {
  padding: '24px',
  background: '#f9fafb',
  borderRadius: '12px',
  fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
};

const headerStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'flex-start',
  marginBottom: '24px',
};

const viewDetailsButtonStyle: React.CSSProperties = {
  padding: '8px 16px',
  background: '#0066cc',
  color: 'white',
  border: 'none',
  borderRadius: '6px',
  fontSize: '14px',
  cursor: 'pointer',
  fontWeight: 500,
};

const summaryContainerStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(4, 1fr)',
  gap: '16px',
  marginBottom: '24px',
};

const summaryCardStyle: React.CSSProperties = {
  padding: '16px',
  background: 'white',
  borderRadius: '8px',
  boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
  cursor: 'pointer',
  transition: 'all 0.2s',
};

const sectionStyle: React.CSSProperties = {
  marginBottom: '24px',
};

const sectionTitleStyle: React.CSSProperties = {
  fontSize: '16px',
  fontWeight: 600,
  color: '#111827',
};

const deploymentsContainerStyle: React.CSSProperties = {
  display: 'grid',
  gap: '12px',
};

const deploymentCardStyle: React.CSSProperties = {
  padding: '12px',
  background: 'white',
  borderRadius: '8px',
  boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
};

const alertsContainerStyle: React.CSSProperties = {
  display: 'grid',
  gap: '8px',
};

const alertCardStyle: React.CSSProperties = {
  padding: '12px',
  background: 'white',
  borderRadius: '8px',
  boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
};

const componentsContainerStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(2, 1fr)',
  gap: '12px',
};

const componentCardStyle: React.CSSProperties = {
  padding: '12px',
  background: 'white',
  borderRadius: '8px',
  boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
  cursor: 'pointer',
  transition: 'box-shadow 0.2s',
};

const quickActionsStyle: React.CSSProperties = {
  display: 'flex',
  gap: '12px',
  flexWrap: 'wrap',
};

const quickActionButtonStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '6px',
  padding: '8px 12px',
  background: 'white',
  border: '1px solid #e5e7eb',
  borderRadius: '6px',
  cursor: 'pointer',
  color: '#374151',
};

export default SimplifiedDevSecOpsDashboard;
