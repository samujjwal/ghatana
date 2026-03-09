/**
 * ResourcesList Component
 *
 * @description Displays a list of provisioned resources with status,
 * configuration details, and action controls.
 *
 * @doc.type component
 * @doc.purpose resource-display-management
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <ResourcesList
 *   resources={[
 *     { id: '1', type: 'database', name: 'PostgreSQL', status: 'running' },
 *     { id: '2', type: 'storage', name: 'S3 Bucket', status: 'provisioning' },
 *   ]}
 *   onResourceAction={(resourceId, action) => handleAction(resourceId, action)}
 * />
 * ```
 */

import React, { useState, useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Resource type categories
 */
export type ResourceType =
  | 'repository'
  | 'database'
  | 'storage'
  | 'compute'
  | 'network'
  | 'cdn'
  | 'cache'
  | 'queue'
  | 'serverless'
  | 'container'
  | 'monitoring'
  | 'cicd'
  | 'dns'
  | 'ssl'
  | 'secret'
  | 'other';

/**
 * Resource provisioning status
 */
export type ResourceStatus =
  | 'pending'
  | 'provisioning'
  | 'running'
  | 'updating'
  | 'error'
  | 'deleting'
  | 'deleted'
  | 'stopped';

/**
 * Provider/platform for the resource
 */
export type ResourceProvider =
  | 'aws'
  | 'gcp'
  | 'azure'
  | 'vercel'
  | 'railway'
  | 'supabase'
  | 'planetscale'
  | 'github'
  | 'gitlab'
  | 'cloudflare'
  | 'custom';

/**
 * Resource configuration details
 */
export interface ResourceConfig {
  /** Region/location */
  region?: string;
  /** Instance type/tier */
  tier?: string;
  /** Storage size */
  storage?: string;
  /** Memory allocation */
  memory?: string;
  /** CPU allocation */
  cpu?: string;
  /** Connection string (masked) */
  connectionUrl?: string;
  /** Additional properties */
  [key: string]: string | number | boolean | undefined;
}

/**
 * Resource cost information
 */
export interface ResourceCost {
  /** Estimated monthly cost */
  monthlyUsd: number;
  /** Billing type */
  billingType: 'fixed' | 'usage' | 'free';
  /** Cost breakdown */
  breakdown?: {
    label: string;
    amount: number;
  }[];
}

/**
 * Resource definition
 */
export interface Resource {
  /** Unique identifier */
  id: string;
  /** Resource type */
  type: ResourceType;
  /** Display name */
  name: string;
  /** Description */
  description?: string;
  /** Current status */
  status: ResourceStatus;
  /** Provider */
  provider: ResourceProvider;
  /** Configuration details */
  config?: ResourceConfig;
  /** Cost information */
  cost?: ResourceCost;
  /** URL/endpoint */
  url?: string;
  /** Error message if status is error */
  errorMessage?: string;
  /** Creation timestamp */
  createdAt?: Date;
  /** Last updated timestamp */
  updatedAt?: Date;
  /** Dependencies on other resources */
  dependsOn?: string[];
  /** Whether this resource is deletable */
  deletable?: boolean;
}

/**
 * Available resource actions
 */
export type ResourceAction =
  | 'view'
  | 'edit'
  | 'delete'
  | 'restart'
  | 'stop'
  | 'start'
  | 'logs'
  | 'connect';

/**
 * Props for the ResourcesList component
 */
export interface ResourcesListProps {
  /** List of resources */
  resources: Resource[];
  /** Callback when an action is triggered */
  onResourceAction?: (resourceId: string, action: ResourceAction) => void;
  /** Whether to show cost information */
  showCosts?: boolean;
  /** Whether to show configuration details */
  showConfig?: boolean;
  /** Whether to allow filtering */
  allowFilter?: boolean;
  /** Whether to allow sorting */
  allowSort?: boolean;
  /** Loading state */
  loading?: boolean;
  /** Empty state message */
  emptyMessage?: string;
  /** Compact display mode */
  compact?: boolean;
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getResourceIcon = (type: ResourceType): React.ReactNode => {
  const icons: Record<ResourceType, React.ReactNode> = {
    repository: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
      </svg>
    ),
    database: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <ellipse cx="12" cy="5" rx="9" ry="3" />
        <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
        <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
      </svg>
    ),
    storage: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      </svg>
    ),
    compute: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="4" y="4" width="16" height="16" rx="2" ry="2" />
        <rect x="9" y="9" width="6" height="6" />
        <line x1="9" y1="1" x2="9" y2="4" />
        <line x1="15" y1="1" x2="15" y2="4" />
        <line x1="9" y1="20" x2="9" y2="23" />
        <line x1="15" y1="20" x2="15" y2="23" />
        <line x1="20" y1="9" x2="23" y2="9" />
        <line x1="20" y1="14" x2="23" y2="14" />
        <line x1="1" y1="9" x2="4" y2="9" />
        <line x1="1" y1="14" x2="4" y2="14" />
      </svg>
    ),
    network: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="2" y1="12" x2="22" y2="12" />
        <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
      </svg>
    ),
    cdn: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z" />
      </svg>
    ),
    cache: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
      </svg>
    ),
    queue: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <line x1="8" y1="6" x2="21" y2="6" />
        <line x1="8" y1="12" x2="21" y2="12" />
        <line x1="8" y1="18" x2="21" y2="18" />
        <line x1="3" y1="6" x2="3.01" y2="6" />
        <line x1="3" y1="12" x2="3.01" y2="12" />
        <line x1="3" y1="18" x2="3.01" y2="18" />
      </svg>
    ),
    serverless: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
        <polyline points="22 6 12 13 2 6" />
      </svg>
    ),
    container: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
        <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
      </svg>
    ),
    monitoring: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
      </svg>
    ),
    cicd: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="17 1 21 5 17 9" />
        <path d="M3 11V9a4 4 0 0 1 4-4h14" />
        <polyline points="7 23 3 19 7 15" />
        <path d="M21 13v2a4 4 0 0 1-4 4H3" />
      </svg>
    ),
    dns: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="2" />
        <path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14" />
      </svg>
    ),
    ssl: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
      </svg>
    ),
    secret: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4" />
      </svg>
    ),
    other: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="16" x2="12" y2="12" />
        <line x1="12" y1="8" x2="12.01" y2="8" />
      </svg>
    ),
  };
  return icons[type] || icons.other;
};

const getProviderLabel = (provider: ResourceProvider): string => {
  const labels: Record<ResourceProvider, string> = {
    aws: 'AWS',
    gcp: 'Google Cloud',
    azure: 'Azure',
    vercel: 'Vercel',
    railway: 'Railway',
    supabase: 'Supabase',
    planetscale: 'PlanetScale',
    github: 'GitHub',
    gitlab: 'GitLab',
    cloudflare: 'Cloudflare',
    custom: 'Custom',
  };
  return labels[provider] || provider;
};

const getStatusConfig = (
  status: ResourceStatus
): { label: string; color: string; bgColor: string } => {
  const configs: Record<typeof status, { label: string; color: string; bgColor: string }> = {
    pending: { label: 'Pending', color: '#6B7280', bgColor: '#F3F4F6' },
    provisioning: { label: 'Provisioning', color: '#3B82F6', bgColor: '#DBEAFE' },
    running: { label: 'Running', color: '#10B981', bgColor: '#D1FAE5' },
    updating: { label: 'Updating', color: '#F59E0B', bgColor: '#FEF3C7' },
    error: { label: 'Error', color: '#EF4444', bgColor: '#FEE2E2' },
    deleting: { label: 'Deleting', color: '#EF4444', bgColor: '#FEE2E2' },
    deleted: { label: 'Deleted', color: '#6B7280', bgColor: '#F3F4F6' },
    stopped: { label: 'Stopped', color: '#6B7280', bgColor: '#F3F4F6' },
  };
  return configs[status];
};

const formatCost = (cost: number): string => {
  if (cost === 0) return 'Free';
  if (cost < 1) return `$${cost.toFixed(2)}`;
  return `$${cost.toFixed(2)}`;
};

// ============================================================================
// Sub-Components
// ============================================================================

interface ResourceItemProps {
  resource: Resource;
  showCosts: boolean;
  showConfig: boolean;
  compact: boolean;
  onAction?: (action: ResourceAction) => void;
}

const ResourceItem: React.FC<ResourceItemProps> = ({
  resource,
  showCosts,
  showConfig,
  compact,
  onAction,
}) => {
  const [expanded, setExpanded] = useState(false);
  const statusConfig = getStatusConfig(resource.status);
  const isAnimated = ['provisioning', 'updating', 'deleting'].includes(resource.status);

  const getAvailableActions = (): ResourceAction[] => {
    const actions: ResourceAction[] = ['view'];
    if (resource.status === 'running') {
      actions.push('logs', 'connect', 'stop');
    }
    if (resource.status === 'stopped') {
      actions.push('start');
    }
    if (resource.status === 'error') {
      actions.push('restart', 'logs');
    }
    if (resource.deletable !== false && resource.status !== 'deleting') {
      actions.push('delete');
    }
    return actions;
  };

  return (
    <div
      className={`resource-item ${compact ? 'resource-item--compact' : ''} ${resource.status === 'error' ? 'resource-item--error' : ''}`}
    >
      <div className="resource-item-main">
        <div className="resource-item-icon" aria-hidden="true">
          {getResourceIcon(resource.type)}
        </div>

        <div className="resource-item-info">
          <div className="resource-item-header">
            <span className="resource-item-name">{resource.name}</span>
            <span
              className={`resource-item-status ${isAnimated ? 'resource-item-status--animated' : ''}`}
              style={{
                color: statusConfig.color,
                backgroundColor: statusConfig.bgColor,
              }}
            >
              {statusConfig.label}
            </span>
          </div>

          {resource.description && !compact && (
            <p className="resource-item-description">{resource.description}</p>
          )}

          <div className="resource-item-meta">
            <span className="resource-item-type">{resource.type}</span>
            <span className="resource-item-separator">•</span>
            <span className="resource-item-provider">
              {getProviderLabel(resource.provider)}
            </span>
            {resource.config?.region && (
              <>
                <span className="resource-item-separator">•</span>
                <span className="resource-item-region">{resource.config.region}</span>
              </>
            )}
          </div>

          {resource.status === 'error' && resource.errorMessage && (
            <div className="resource-item-error" role="alert">
              {resource.errorMessage}
            </div>
          )}
        </div>

        {showCosts && resource.cost && (
          <div className="resource-item-cost">
            <span className="resource-item-cost-label">Monthly</span>
            <span className="resource-item-cost-value">
              {formatCost(resource.cost.monthlyUsd)}
            </span>
          </div>
        )}

        {onAction && (
          <div className="resource-item-actions">
            {getAvailableActions().slice(0, compact ? 2 : 3).map((action) => (
              <button
                key={action}
                type="button"
                className={`resource-action-btn resource-action-btn--${action}`}
                onClick={() => onAction(action)}
                aria-label={`${action} ${resource.name}`}
              >
                {action}
              </button>
            ))}
            {!compact && showConfig && resource.config && (
              <button
                type="button"
                className="resource-expand-btn"
                onClick={() => setExpanded(!expanded)}
                aria-expanded={expanded}
                aria-label={expanded ? 'Collapse details' : 'Expand details'}
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  style={{ transform: expanded ? 'rotate(180deg)' : 'none' }}
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </button>
            )}
          </div>
        )}
      </div>

      {expanded && showConfig && resource.config && (
        <div className="resource-item-details">
          <div className="resource-config-grid">
            {Object.entries(resource.config)
              .filter(([key]) => !['connectionUrl'].includes(key))
              .map(([key, value]) => (
                <div key={key} className="resource-config-item">
                  <span className="resource-config-key">{key}</span>
                  <span className="resource-config-value">{String(value)}</span>
                </div>
              ))}
          </div>
          {resource.url && (
            <div className="resource-url">
              <span className="resource-url-label">URL:</span>
              <a
                href={resource.url}
                target="_blank"
                rel="noopener noreferrer"
                className="resource-url-link"
              >
                {resource.url}
              </a>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const ResourcesList: React.FC<ResourcesListProps> = ({
  resources,
  onResourceAction,
  showCosts = true,
  showConfig = true,
  allowFilter = true,
  allowSort = true,
  loading = false,
  emptyMessage = 'No resources provisioned yet',
  compact = false,
  className = '',
}) => {
  const [filterType, setFilterType] = useState<ResourceType | 'all'>('all');
  const [filterStatus, setFilterStatus] = useState<ResourceStatus | 'all'>('all');
  const [sortBy, setSortBy] = useState<'name' | 'type' | 'status' | 'cost'>('name');

  const filteredResources = useMemo(() => {
    let filtered = [...resources];

    if (filterType !== 'all') {
      filtered = filtered.filter((r) => r.type === filterType);
    }

    if (filterStatus !== 'all') {
      filtered = filtered.filter((r) => r.status === filterStatus);
    }

    filtered.sort((a, b) => {
      switch (sortBy) {
        case 'type':
          return a.type.localeCompare(b.type);
        case 'status':
          return a.status.localeCompare(b.status);
        case 'cost':
          return (b.cost?.monthlyUsd || 0) - (a.cost?.monthlyUsd || 0);
        case 'name':
        default:
          return a.name.localeCompare(b.name);
      }
    });

    return filtered;
  }, [resources, filterType, filterStatus, sortBy]);

  const resourceTypes = useMemo(() => {
    const types = new Set(resources.map((r) => r.type));
    return Array.from(types).sort();
  }, [resources]);

  const totalCost = useMemo(() => {
    return resources.reduce((sum, r) => sum + (r.cost?.monthlyUsd || 0), 0);
  }, [resources]);

  const containerClasses = [
    'resources-list',
    compact && 'resources-list--compact',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={containerClasses}>
      {/* Header */}
      <div className="resources-list-header">
        <div className="resources-list-title">
          <h3>Resources</h3>
          <span className="resources-count">{resources.length} total</span>
        </div>

        {showCosts && totalCost > 0 && (
          <div className="resources-total-cost">
            <span className="resources-cost-label">Est. Monthly</span>
            <span className="resources-cost-value">${totalCost.toFixed(2)}</span>
          </div>
        )}
      </div>

      {/* Filters */}
      {allowFilter && resources.length > 0 && (
        <div className="resources-filters">
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value as ResourceType | 'all')}
            className="resources-filter-select"
            aria-label="Filter by type"
          >
            <option value="all">All Types</option>
            {resourceTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>

          <select
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value as ResourceStatus | 'all')}
            className="resources-filter-select"
            aria-label="Filter by status"
          >
            <option value="all">All Status</option>
            <option value="running">Running</option>
            <option value="provisioning">Provisioning</option>
            <option value="error">Error</option>
            <option value="stopped">Stopped</option>
          </select>

          {allowSort && (
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as typeof sortBy)}
              className="resources-filter-select"
              aria-label="Sort by"
            >
              <option value="name">Sort by Name</option>
              <option value="type">Sort by Type</option>
              <option value="status">Sort by Status</option>
              {showCosts && <option value="cost">Sort by Cost</option>}
            </select>
          )}
        </div>
      )}

      {/* List */}
      <div className="resources-items" role="list">
        {loading ? (
          <div className="resources-loading">
            <div className="resources-loading-spinner" />
            <span>Loading resources...</span>
          </div>
        ) : filteredResources.length === 0 ? (
          <div className="resources-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
              <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
              <line x1="12" y1="22.08" x2="12" y2="12" />
            </svg>
            <span>{emptyMessage}</span>
          </div>
        ) : (
          filteredResources.map((resource) => (
            <ResourceItem
              key={resource.id}
              resource={resource}
              showCosts={showCosts}
              showConfig={showConfig}
              compact={compact}
              onAction={
                onResourceAction
                  ? (action) => onResourceAction(resource.id, action)
                  : undefined
              }
            />
          ))
        )}
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .resources-list {
          display: flex;
          flex-direction: column;
          gap: 1rem;
        }

        .resources-list-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }

        .resources-list-title {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .resources-list-title h3 {
          margin: 0;
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
        }

        .resources-count {
          padding: 0.125rem 0.5rem;
          font-size: 0.75rem;
          color: #6B7280;
          background: #F3F4F6;
          border-radius: 9999px;
        }

        .resources-total-cost {
          display: flex;
          flex-direction: column;
          align-items: flex-end;
        }

        .resources-cost-label {
          font-size: 0.625rem;
          text-transform: uppercase;
          color: #9CA3AF;
        }

        .resources-cost-value {
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
        }

        .resources-filters {
          display: flex;
          gap: 0.5rem;
          flex-wrap: wrap;
        }

        .resources-filter-select {
          padding: 0.375rem 0.75rem;
          font-size: 0.75rem;
          color: #374151;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 6px;
          cursor: pointer;
        }

        .resources-filter-select:focus {
          outline: none;
          border-color: #3B82F6;
          box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
        }

        .resources-items {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .resource-item {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 8px;
          overflow: hidden;
          transition: border-color 0.15s ease;
        }

        .resource-item:hover {
          border-color: #D1D5DB;
        }

        .resource-item--error {
          border-color: #FCA5A5;
        }

        .resource-item-main {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          padding: 0.75rem 1rem;
        }

        .resources-list--compact .resource-item-main {
          padding: 0.5rem 0.75rem;
        }

        .resource-item-icon {
          width: 36px;
          height: 36px;
          padding: 8px;
          background: #F3F4F6;
          border-radius: 8px;
          color: #6B7280;
          flex-shrink: 0;
        }

        .resource-item--compact .resource-item-icon {
          width: 28px;
          height: 28px;
          padding: 5px;
        }

        .resource-item-icon svg {
          width: 100%;
          height: 100%;
        }

        .resource-item-info {
          flex: 1;
          min-width: 0;
        }

        .resource-item-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          flex-wrap: wrap;
        }

        .resource-item-name {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .resource-item-status {
          padding: 0.125rem 0.5rem;
          font-size: 0.625rem;
          font-weight: 500;
          text-transform: uppercase;
          border-radius: 9999px;
        }

        .resource-item-status--animated {
          animation: pulse-status 2s ease-in-out infinite;
        }

        @keyframes pulse-status {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.7; }
        }

        .resource-item-description {
          margin: 0.25rem 0 0;
          font-size: 0.75rem;
          color: #6B7280;
        }

        .resource-item-meta {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          margin-top: 0.25rem;
          font-size: 0.625rem;
          color: #9CA3AF;
          text-transform: capitalize;
        }

        .resource-item-separator {
          color: #D1D5DB;
        }

        .resource-item-error {
          margin-top: 0.25rem;
          padding: 0.25rem 0.5rem;
          font-size: 0.75rem;
          color: #DC2626;
          background: #FEE2E2;
          border-radius: 4px;
        }

        .resource-item-cost {
          display: flex;
          flex-direction: column;
          align-items: flex-end;
          padding-left: 1rem;
          border-left: 1px solid #E5E7EB;
        }

        .resource-item-cost-label {
          font-size: 0.625rem;
          text-transform: uppercase;
          color: #9CA3AF;
        }

        .resource-item-cost-value {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .resource-item-actions {
          display: flex;
          align-items: center;
          gap: 0.25rem;
        }

        .resource-action-btn {
          padding: 0.25rem 0.5rem;
          font-size: 0.625rem;
          font-weight: 500;
          text-transform: capitalize;
          color: #6B7280;
          background: #F3F4F6;
          border: none;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .resource-action-btn:hover {
          color: #3B82F6;
          background: #DBEAFE;
        }

        .resource-action-btn--delete:hover {
          color: #DC2626;
          background: #FEE2E2;
        }

        .resource-expand-btn {
          width: 24px;
          height: 24px;
          padding: 0;
          background: transparent;
          border: none;
          color: #6B7280;
          cursor: pointer;
          border-radius: 4px;
        }

        .resource-expand-btn:hover {
          color: #3B82F6;
          background: #F3F4F6;
        }

        .resource-expand-btn svg {
          width: 16px;
          height: 16px;
          transition: transform 0.2s ease;
        }

        .resource-item-details {
          padding: 0.75rem 1rem;
          background: #F9FAFB;
          border-top: 1px solid #E5E7EB;
        }

        .resource-config-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
          gap: 0.5rem;
        }

        .resource-config-item {
          display: flex;
          flex-direction: column;
        }

        .resource-config-key {
          font-size: 0.625rem;
          text-transform: uppercase;
          color: #9CA3AF;
        }

        .resource-config-value {
          font-size: 0.75rem;
          color: #111827;
          word-break: break-all;
        }

        .resource-url {
          margin-top: 0.5rem;
          padding-top: 0.5rem;
          border-top: 1px solid #E5E7EB;
          font-size: 0.75rem;
        }

        .resource-url-label {
          color: #6B7280;
          margin-right: 0.5rem;
        }

        .resource-url-link {
          color: #3B82F6;
          text-decoration: none;
          word-break: break-all;
        }

        .resource-url-link:hover {
          text-decoration: underline;
        }

        .resources-loading,
        .resources-empty {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 0.75rem;
          padding: 3rem 1rem;
          color: #6B7280;
          text-align: center;
        }

        .resources-loading-spinner {
          width: 24px;
          height: 24px;
          border: 2px solid #E5E7EB;
          border-top-color: #3B82F6;
          border-radius: 50%;
          animation: spin 1s linear infinite;
        }

        @keyframes spin {
          to { transform: rotate(360deg); }
        }

        .resources-empty svg {
          width: 48px;
          height: 48px;
          color: #D1D5DB;
        }
      `}</style>
    </div>
  );
};

ResourcesList.displayName = 'ResourcesList';

export default ResourcesList;
