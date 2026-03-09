/**
 * EnvironmentTabs Component
 *
 * @description Tab-based navigation for managing multiple deployment
 * environments (development, staging, production) with status indicators.
 *
 * @doc.type component
 * @doc.purpose environment-management
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <EnvironmentTabs
 *   environments={[
 *     { id: 'dev', name: 'Development', status: 'healthy' },
 *     { id: 'staging', name: 'Staging', status: 'deploying' },
 *     { id: 'prod', name: 'Production', status: 'warning' },
 *   ]}
 *   activeEnvironment="dev"
 *   onEnvironmentChange={(envId) => setActiveEnv(envId)}
 * />
 * ```
 */

import React, { useCallback, useRef, useEffect } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Environment health/deployment status
 */
export type EnvironmentStatus =
  | 'healthy'
  | 'deploying'
  | 'warning'
  | 'error'
  | 'offline'
  | 'initializing'
  | 'unknown';

/**
 * Environment type classification
 */
export type EnvironmentType = 'development' | 'staging' | 'production' | 'custom';

/**
 * Resource summary for an environment
 */
export interface EnvironmentResources {
  /** Number of services running */
  services: number;
  /** Number of instances */
  instances: number;
  /** Memory usage percentage */
  memoryPercent?: number;
  /** CPU usage percentage */
  cpuPercent?: number;
}

/**
 * Environment configuration
 */
export interface Environment {
  /** Unique identifier */
  id: string;
  /** Display name */
  name: string;
  /** Environment type */
  type: EnvironmentType;
  /** Current status */
  status: EnvironmentStatus;
  /** URL for the environment */
  url?: string;
  /** Resource summary */
  resources?: EnvironmentResources;
  /** Last deployment timestamp */
  lastDeployedAt?: Date;
  /** Whether this environment is locked */
  locked?: boolean;
  /** Lock reason */
  lockReason?: string;
  /** Custom icon */
  icon?: string;
}

/**
 * Props for the EnvironmentTabs component
 */
export interface EnvironmentTabsProps {
  /** List of environments */
  environments: Environment[];
  /** Currently active environment ID */
  activeEnvironment: string;
  /** Callback when environment is changed */
  onEnvironmentChange: (environmentId: string) => void;
  /** Callback when add environment is clicked */
  onAddEnvironment?: () => void;
  /** Callback when environment settings is clicked */
  onEnvironmentSettings?: (environmentId: string) => void;
  /** Whether adding environments is allowed */
  allowAdd?: boolean;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getStatusColor = (status: EnvironmentStatus): string => {
  const colors: Record<EnvironmentStatus, string> = {
    healthy: '#10B981',
    deploying: '#3B82F6',
    warning: '#F59E0B',
    error: '#EF4444',
    offline: '#6B7280',
    initializing: '#8B5CF6',
    unknown: '#9CA3AF',
  };
  return colors[status];
};

const getStatusLabel = (status: EnvironmentStatus): string => {
  const labels: Record<EnvironmentStatus, string> = {
    healthy: 'Healthy',
    deploying: 'Deploying',
    warning: 'Warning',
    error: 'Error',
    offline: 'Offline',
    initializing: 'Initializing',
    unknown: 'Unknown',
  };
  return labels[status];
};

const getEnvironmentIcon = (type: EnvironmentType): React.ReactNode => {
  const icons: Record<EnvironmentType, React.ReactNode> = {
    development: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="16 18 22 12 16 6" />
        <polyline points="8 6 2 12 8 18" />
      </svg>
    ),
    staging: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="3" width="18" height="18" rx="2" />
        <path d="M12 8v8" />
        <path d="M8 12h8" />
      </svg>
    ),
    production: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <path d="M12 6v6l4 2" />
      </svg>
    ),
    custom: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
      </svg>
    ),
  };
  return icons[type];
};

const formatRelativeTime = (date: Date): string => {
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHour = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHour / 24);

  if (diffSec < 60) return 'Just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  if (diffHour < 24) return `${diffHour}h ago`;
  if (diffDay < 7) return `${diffDay}d ago`;
  return date.toLocaleDateString();
};

// ============================================================================
// Sub-Components
// ============================================================================

interface StatusIndicatorProps {
  status: EnvironmentStatus;
}

const StatusIndicator: React.FC<StatusIndicatorProps> = ({ status }) => {
  const color = getStatusColor(status);
  const isAnimated = status === 'deploying' || status === 'initializing';

  return (
    <span
      className={`env-status-indicator ${isAnimated ? 'env-status-indicator--animated' : ''}`}
      style={{ backgroundColor: color }}
      aria-label={getStatusLabel(status)}
    />
  );
};

interface TabProps {
  environment: Environment;
  isActive: boolean;
  size: 'sm' | 'md' | 'lg';
  onClick: () => void;
  onSettings?: () => void;
}

const Tab: React.FC<TabProps> = ({
  environment,
  isActive,
  size,
  onClick,
  onSettings,
}) => {
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onClick();
    }
  };

  const handleSettingsClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    onSettings?.();
  };

  return (
    <div
      className={`env-tab env-tab--${size} ${isActive ? 'env-tab--active' : ''} ${environment.locked ? 'env-tab--locked' : ''}`}
      role="tab"
      aria-selected={isActive}
      aria-disabled={environment.locked}
      tabIndex={isActive ? 0 : -1}
      onClick={onClick}
      onKeyDown={handleKeyDown}
    >
      <div className="env-tab-icon" aria-hidden="true">
        {getEnvironmentIcon(environment.type)}
      </div>

      <div className="env-tab-content">
        <div className="env-tab-header">
          <span className="env-tab-name">{environment.name}</span>
          <StatusIndicator status={environment.status} />
        </div>

        {size !== 'sm' && environment.lastDeployedAt && (
          <span className="env-tab-meta">
            Deployed {formatRelativeTime(environment.lastDeployedAt)}
          </span>
        )}

        {size === 'lg' && environment.resources && (
          <div className="env-tab-resources">
            <span>{environment.resources.services} services</span>
            <span className="env-tab-separator">•</span>
            <span>{environment.resources.instances} instances</span>
          </div>
        )}
      </div>

      {environment.locked && (
        <div className="env-tab-lock" title={environment.lockReason || 'Locked'}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </svg>
        </div>
      )}

      {onSettings && isActive && !environment.locked && (
        <button
          type="button"
          className="env-tab-settings"
          onClick={handleSettingsClick}
          aria-label={`Settings for ${environment.name}`}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="1" />
            <circle cx="12" cy="5" r="1" />
            <circle cx="12" cy="19" r="1" />
          </svg>
        </button>
      )}
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const EnvironmentTabs: React.FC<EnvironmentTabsProps> = ({
  environments,
  activeEnvironment,
  onEnvironmentChange,
  onAddEnvironment,
  onEnvironmentSettings,
  allowAdd = true,
  size = 'md',
  className = '',
}) => {
  const tabsRef = useRef<HTMLDivElement>(null);

  const handleTabClick = useCallback(
    (envId: string) => {
      const env = environments.find((e) => e.id === envId);
      if (env && !env.locked) {
        onEnvironmentChange(envId);
      }
    },
    [environments, onEnvironmentChange]
  );

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!tabsRef.current?.contains(document.activeElement)) return;

      const currentIndex = environments.findIndex((env) => env.id === activeEnvironment);
      let newIndex = currentIndex;

      if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
        e.preventDefault();
        newIndex = currentIndex > 0 ? currentIndex - 1 : environments.length - 1;
      } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
        e.preventDefault();
        newIndex = currentIndex < environments.length - 1 ? currentIndex + 1 : 0;
      } else if (e.key === 'Home') {
        e.preventDefault();
        newIndex = 0;
      } else if (e.key === 'End') {
        e.preventDefault();
        newIndex = environments.length - 1;
      }

      if (newIndex !== currentIndex) {
        const newEnv = environments[newIndex];
        if (!newEnv.locked) {
          onEnvironmentChange(newEnv.id);
        }
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [environments, activeEnvironment, onEnvironmentChange]);

  const containerClasses = ['env-tabs', `env-tabs--${size}`, className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={containerClasses}>
      <div
        ref={tabsRef}
        className="env-tabs-list"
        role="tablist"
        aria-label="Environments"
      >
        {environments.map((env) => (
          <Tab
            key={env.id}
            environment={env}
            isActive={env.id === activeEnvironment}
            size={size}
            onClick={() => handleTabClick(env.id)}
            onSettings={
              onEnvironmentSettings
                ? () => onEnvironmentSettings(env.id)
                : undefined
            }
          />
        ))}

        {allowAdd && onAddEnvironment && (
          <button
            type="button"
            className={`env-tab-add env-tab-add--${size}`}
            onClick={onAddEnvironment}
            aria-label="Add environment"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            {size !== 'sm' && <span>Add</span>}
          </button>
        )}
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .env-tabs {
          display: flex;
          flex-direction: column;
        }

        .env-tabs-list {
          display: flex;
          gap: 0.5rem;
          overflow-x: auto;
          padding-bottom: 2px;
          scrollbar-width: thin;
        }

        .env-tabs-list::-webkit-scrollbar {
          height: 4px;
        }

        .env-tabs-list::-webkit-scrollbar-track {
          background: #F3F4F6;
          border-radius: 2px;
        }

        .env-tabs-list::-webkit-scrollbar-thumb {
          background: #D1D5DB;
          border-radius: 2px;
        }

        .env-tab {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.5rem 0.75rem;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.15s ease;
          white-space: nowrap;
          flex-shrink: 0;
        }

        .env-tab:hover:not(.env-tab--locked) {
          border-color: #3B82F6;
          background: #F9FAFB;
        }

        .env-tab:focus {
          outline: none;
          border-color: #3B82F6;
          box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
        }

        .env-tab--active {
          border-color: #3B82F6;
          background: #EFF6FF;
        }

        .env-tab--locked {
          opacity: 0.7;
          cursor: not-allowed;
        }

        .env-tab--sm {
          padding: 0.375rem 0.625rem;
          gap: 0.375rem;
        }

        .env-tab--lg {
          padding: 0.75rem 1rem;
          gap: 0.75rem;
        }

        .env-tab-icon {
          width: 20px;
          height: 20px;
          color: #6B7280;
          flex-shrink: 0;
        }

        .env-tab--active .env-tab-icon {
          color: #3B82F6;
        }

        .env-tab--sm .env-tab-icon {
          width: 16px;
          height: 16px;
        }

        .env-tab--lg .env-tab-icon {
          width: 24px;
          height: 24px;
        }

        .env-tab-icon svg {
          width: 100%;
          height: 100%;
        }

        .env-tab-content {
          display: flex;
          flex-direction: column;
          gap: 0.125rem;
          min-width: 0;
        }

        .env-tab-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .env-tab-name {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .env-tab--sm .env-tab-name {
          font-size: 0.75rem;
        }

        .env-tab--lg .env-tab-name {
          font-size: 1rem;
        }

        .env-tab--active .env-tab-name {
          color: #1E40AF;
        }

        .env-status-indicator {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          flex-shrink: 0;
        }

        .env-status-indicator--animated {
          animation: pulse-status 2s ease-in-out infinite;
        }

        @keyframes pulse-status {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.6; transform: scale(0.9); }
        }

        .env-tab-meta {
          font-size: 0.625rem;
          color: #9CA3AF;
        }

        .env-tab--lg .env-tab-meta {
          font-size: 0.75rem;
        }

        .env-tab-resources {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          font-size: 0.625rem;
          color: #6B7280;
        }

        .env-tab-separator {
          color: #D1D5DB;
        }

        .env-tab-lock {
          width: 16px;
          height: 16px;
          color: #9CA3AF;
          flex-shrink: 0;
        }

        .env-tab-lock svg {
          width: 100%;
          height: 100%;
        }

        .env-tab-settings {
          width: 20px;
          height: 20px;
          padding: 0;
          background: transparent;
          border: none;
          color: #6B7280;
          cursor: pointer;
          border-radius: 4px;
          flex-shrink: 0;
          opacity: 0;
          transition: opacity 0.15s ease;
        }

        .env-tab:hover .env-tab-settings,
        .env-tab:focus-within .env-tab-settings {
          opacity: 1;
        }

        .env-tab-settings:hover {
          color: #3B82F6;
          background: rgba(59, 130, 246, 0.1);
        }

        .env-tab-settings svg {
          width: 100%;
          height: 100%;
        }

        .env-tab-add {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          padding: 0.5rem 0.75rem;
          background: transparent;
          border: 1px dashed #D1D5DB;
          border-radius: 8px;
          color: #6B7280;
          font-size: 0.875rem;
          cursor: pointer;
          transition: all 0.15s ease;
          white-space: nowrap;
          flex-shrink: 0;
        }

        .env-tab-add:hover {
          border-color: #3B82F6;
          color: #3B82F6;
          background: #F9FAFB;
        }

        .env-tab-add--sm {
          padding: 0.375rem 0.5rem;
        }

        .env-tab-add--lg {
          padding: 0.75rem 1rem;
        }

        .env-tab-add svg {
          width: 16px;
          height: 16px;
        }
      `}</style>
    </div>
  );
};

EnvironmentTabs.displayName = 'EnvironmentTabs';

export default EnvironmentTabs;
