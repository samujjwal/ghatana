/**
 * FeatureFlagRow Component
 *
 * @description Row component for displaying and managing feature flags
 * with environment status, rollout controls, and targeting rules.
 *
 * @doc.phase 3
 * @doc.component FeatureFlagRow
 */

import React, { useCallback, useMemo, useState } from 'react';

// ============================================================================
// Types
// ============================================================================

export type FlagType = 'boolean' | 'percentage' | 'variant' | 'targeting';
export type Environment = 'development' | 'staging' | 'production';

export interface EnvironmentStatus {
  environment: Environment;
  enabled: boolean;
  rolloutPercentage?: number;
  lastUpdated?: string;
  updatedBy?: string;
}

export interface TargetingRule {
  id: string;
  attribute: string;
  operator: 'equals' | 'contains' | 'in' | 'not_in' | 'regex';
  value: string | string[];
}

export interface FlagVariant {
  id: string;
  name: string;
  value: string;
  weight: number;
}

export interface FeatureFlag {
  id: string;
  key: string;
  name: string;
  description?: string;
  type: FlagType;
  tags?: string[];
  environments: EnvironmentStatus[];
  targetingRules?: TargetingRule[];
  variants?: FlagVariant[];
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  stale?: boolean;
  permanent?: boolean;
}

export interface FeatureFlagRowProps {
  flag: FeatureFlag;
  onToggle?: (flag: FeatureFlag, env: Environment, enabled: boolean) => void;
  onRolloutChange?: (flag: FeatureFlag, env: Environment, percentage: number) => void;
  onEdit?: (flag: FeatureFlag) => void;
  onDelete?: (flag: FeatureFlag) => void;
  onViewHistory?: (flag: FeatureFlag) => void;
  expanded?: boolean;
  onExpandToggle?: (flagId: string, expanded: boolean) => void;
}

// ============================================================================
// Constants
// ============================================================================

const ENV_CONFIG = {
  development: { label: 'Dev', color: '#6B7280', bg: '#F3F4F6' },
  staging: { label: 'Staging', color: '#F59E0B', bg: '#FEF3C7' },
  production: { label: 'Prod', color: '#10B981', bg: '#D1FAE5' },
};

const TYPE_ICONS: Record<FlagType, string> = {
  boolean: '🔘',
  percentage: '📊',
  variant: '🎲',
  targeting: '🎯',
};

// ============================================================================
// Utility Functions
// ============================================================================

const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
};

// ============================================================================
// Main Component
// ============================================================================

export const FeatureFlagRow: React.FC<FeatureFlagRowProps> = ({
  flag,
  onToggle,
  onRolloutChange,
  onEdit,
  onDelete,
  onViewHistory,
  expanded = false,
  onExpandToggle,
}) => {
  const [localRollout, setLocalRollout] = useState<Record<Environment, number>>({
    development: 0,
    staging: 0,
    production: 0,
  });

  // Initialize local rollout state
  useMemo(() => {
    const rollouts: Record<Environment, number> = {
      development: 0,
      staging: 0,
      production: 0,
    };
    flag.environments.forEach((env) => {
      rollouts[env.environment] = env.rolloutPercentage || 0;
    });
    setLocalRollout(rollouts);
  }, [flag.environments]);

  const getEnvStatus = useCallback(
    (env: Environment): EnvironmentStatus | undefined => {
      return flag.environments.find((e) => e.environment === env);
    },
    [flag.environments]
  );

  const handleToggle = useCallback(
    (env: Environment, enabled: boolean) => {
      if (onToggle) {
        onToggle(flag, env, enabled);
      }
    },
    [onToggle, flag]
  );

  const handleRolloutDrag = useCallback(
    (env: Environment, percentage: number) => {
      setLocalRollout((prev) => ({ ...prev, [env]: percentage }));
    },
    []
  );

  const handleRolloutCommit = useCallback(
    (env: Environment) => {
      if (onRolloutChange) {
        onRolloutChange(flag, env, localRollout[env]);
      }
    },
    [onRolloutChange, flag, localRollout]
  );

  const handleExpand = useCallback(() => {
    if (onExpandToggle) {
      onExpandToggle(flag.id, !expanded);
    }
  }, [onExpandToggle, flag.id, expanded]);

  const handleEdit = useCallback(() => {
    if (onEdit) onEdit(flag);
  }, [onEdit, flag]);

  const handleDelete = useCallback(() => {
    if (onDelete) onDelete(flag);
  }, [onDelete, flag]);

  const handleViewHistory = useCallback(() => {
    if (onViewHistory) onViewHistory(flag);
  }, [onViewHistory, flag]);

  return (
    <div className={`feature-flag-row ${expanded ? 'feature-flag-row--expanded' : ''}`}>
      {/* Main Row */}
      <div className="row-main">
        {/* Expand Toggle */}
        <button
          type="button"
          className="expand-toggle"
          onClick={handleExpand}
          aria-expanded={expanded}
          aria-label={expanded ? 'Collapse' : 'Expand'}
        >
          {expanded ? '▼' : '▶'}
        </button>

        {/* Flag Info */}
        <div className="flag-info">
          <div className="flag-header">
            <span className="flag-type-icon" title={flag.type}>
              {TYPE_ICONS[flag.type]}
            </span>
            <h4 className="flag-name">{flag.name}</h4>
            {flag.stale && (
              <span className="flag-badge flag-badge--stale" title="Stale flag">
                ⚠️ Stale
              </span>
            )}
            {flag.permanent && (
              <span className="flag-badge flag-badge--permanent" title="Permanent flag">
                🔒 Permanent
              </span>
            )}
          </div>
          <code className="flag-key">{flag.key}</code>
          {flag.tags && flag.tags.length > 0 && (
            <div className="flag-tags">
              {flag.tags.map((tag) => (
                <span key={tag} className="tag">
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>

        {/* Environment Status */}
        <div className="env-status">
          {(['development', 'staging', 'production'] as Environment[]).map((env) => {
            const status = getEnvStatus(env);
            const config = ENV_CONFIG[env];
            const isEnabled = status?.enabled || false;
            const rollout = localRollout[env];

            return (
              <div key={env} className="env-cell">
                <span
                  className="env-label"
                  style={{ color: config.color, background: config.bg }}
                >
                  {config.label}
                </span>
                <div className="env-controls">
                  {/* Toggle Switch */}
                  <label className="toggle-switch">
                    <input
                      type="checkbox"
                      checked={isEnabled}
                      onChange={(e) => handleToggle(env, e.target.checked)}
                      aria-label={`Enable ${flag.name} in ${env}`}
                    />
                    <span className="toggle-slider" />
                  </label>

                  {/* Rollout Percentage (for percentage type) */}
                  {flag.type === 'percentage' && isEnabled && (
                    <div className="rollout-control">
                      <input
                        type="range"
                        min="0"
                        max="100"
                        value={rollout}
                        onChange={(e) =>
                          handleRolloutDrag(env, parseInt(e.target.value, 10))
                        }
                        onMouseUp={() => handleRolloutCommit(env)}
                        onTouchEnd={() => handleRolloutCommit(env)}
                        className="rollout-slider"
                        aria-label={`Rollout percentage for ${env}`}
                      />
                      <span className="rollout-value">{rollout}%</span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Actions */}
        <div className="row-actions">
          <button
            type="button"
            className="action-btn"
            onClick={handleViewHistory}
            title="View History"
          >
            📜
          </button>
          <button
            type="button"
            className="action-btn"
            onClick={handleEdit}
            title="Edit Flag"
          >
            ✏️
          </button>
          <button
            type="button"
            className="action-btn action-btn--danger"
            onClick={handleDelete}
            title="Delete Flag"
          >
            🗑️
          </button>
        </div>
      </div>

      {/* Expanded Details */}
      {expanded && (
        <div className="row-details">
          {/* Description */}
          {flag.description && (
            <div className="detail-section">
              <h5 className="detail-label">Description</h5>
              <p className="detail-text">{flag.description}</p>
            </div>
          )}

          {/* Targeting Rules */}
          {flag.targetingRules && flag.targetingRules.length > 0 && (
            <div className="detail-section">
              <h5 className="detail-label">Targeting Rules</h5>
              <div className="targeting-rules">
                {flag.targetingRules.map((rule, index) => (
                  <div key={rule.id} className="rule">
                    <span className="rule-index">{index + 1}</span>
                    <code className="rule-attribute">{rule.attribute}</code>
                    <span className="rule-operator">{rule.operator}</span>
                    <code className="rule-value">
                      {Array.isArray(rule.value)
                        ? rule.value.join(', ')
                        : rule.value}
                    </code>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Variants */}
          {flag.variants && flag.variants.length > 0 && (
            <div className="detail-section">
              <h5 className="detail-label">Variants</h5>
              <div className="variants">
                {flag.variants.map((variant) => (
                  <div key={variant.id} className="variant">
                    <span className="variant-name">{variant.name}</span>
                    <code className="variant-value">{variant.value}</code>
                    <span className="variant-weight">{variant.weight}%</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Metadata */}
          <div className="detail-section">
            <h5 className="detail-label">Metadata</h5>
            <div className="metadata">
              <span className="meta-item">
                Created: {formatDate(flag.createdAt)} by {flag.createdBy}
              </span>
              <span className="meta-item">
                Updated: {formatDate(flag.updatedAt)}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .feature-flag-row {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 8px;
          transition: all 0.15s ease;
        }

        .feature-flag-row:hover {
          border-color: #D1D5DB;
        }

        .feature-flag-row--expanded {
          border-color: #3B82F6;
        }

        .row-main {
          display: grid;
          grid-template-columns: auto 1fr auto auto;
          align-items: center;
          gap: 1rem;
          padding: 1rem;
        }

        .expand-toggle {
          width: 24px;
          height: 24px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #F9FAFB;
          border: 1px solid #E5E7EB;
          border-radius: 4px;
          font-size: 0.625rem;
          color: #6B7280;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .expand-toggle:hover {
          background: #F3F4F6;
          color: #374151;
        }

        .flag-info {
          min-width: 0;
        }

        .flag-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-bottom: 0.25rem;
        }

        .flag-type-icon {
          font-size: 0.875rem;
        }

        .flag-name {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #111827;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .flag-badge {
          padding: 0.125rem 0.5rem;
          border-radius: 4px;
          font-size: 0.625rem;
          font-weight: 500;
          white-space: nowrap;
        }

        .flag-badge--stale {
          background: #FEF3C7;
          color: #D97706;
        }

        .flag-badge--permanent {
          background: #EDE9FE;
          color: #7C3AED;
        }

        .flag-key {
          display: block;
          font-size: 0.75rem;
          font-family: 'Monaco', 'Consolas', monospace;
          color: #6B7280;
          background: #F9FAFB;
          padding: 0.125rem 0.375rem;
          border-radius: 4px;
          margin-bottom: 0.375rem;
        }

        .flag-tags {
          display: flex;
          flex-wrap: wrap;
          gap: 0.25rem;
        }

        .tag {
          padding: 0.125rem 0.375rem;
          background: #EFF6FF;
          color: #3B82F6;
          font-size: 0.625rem;
          font-weight: 500;
          border-radius: 4px;
        }

        .env-status {
          display: flex;
          gap: 1.5rem;
        }

        .env-cell {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.5rem;
        }

        .env-label {
          padding: 0.125rem 0.5rem;
          border-radius: 4px;
          font-size: 0.6875rem;
          font-weight: 600;
          text-transform: uppercase;
        }

        .env-controls {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.375rem;
        }

        .toggle-switch {
          position: relative;
          display: inline-block;
          width: 36px;
          height: 20px;
        }

        .toggle-switch input {
          opacity: 0;
          width: 0;
          height: 0;
        }

        .toggle-slider {
          position: absolute;
          cursor: pointer;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background-color: #D1D5DB;
          transition: 0.3s;
          border-radius: 20px;
        }

        .toggle-slider::before {
          position: absolute;
          content: "";
          height: 16px;
          width: 16px;
          left: 2px;
          bottom: 2px;
          background-color: white;
          transition: 0.3s;
          border-radius: 50%;
        }

        .toggle-switch input:checked + .toggle-slider {
          background-color: #10B981;
        }

        .toggle-switch input:checked + .toggle-slider::before {
          transform: translateX(16px);
        }

        .rollout-control {
          display: flex;
          align-items: center;
          gap: 0.375rem;
        }

        .rollout-slider {
          width: 60px;
          height: 4px;
          -webkit-appearance: none;
          appearance: none;
          background: #E5E7EB;
          border-radius: 2px;
        }

        .rollout-slider::-webkit-slider-thumb {
          -webkit-appearance: none;
          appearance: none;
          width: 12px;
          height: 12px;
          border-radius: 50%;
          background: #3B82F6;
          cursor: pointer;
        }

        .rollout-value {
          font-size: 0.6875rem;
          font-weight: 600;
          color: #374151;
          min-width: 32px;
          text-align: right;
        }

        .row-actions {
          display: flex;
          gap: 0.375rem;
        }

        .action-btn {
          width: 28px;
          height: 28px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #F9FAFB;
          border: 1px solid #E5E7EB;
          border-radius: 6px;
          font-size: 0.75rem;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .action-btn:hover {
          background: #F3F4F6;
        }

        .action-btn--danger:hover {
          background: #FEE2E2;
          border-color: #FECACA;
        }

        .row-details {
          padding: 1rem;
          border-top: 1px solid #E5E7EB;
          background: #F9FAFB;
        }

        .detail-section {
          margin-bottom: 1rem;
        }

        .detail-section:last-child {
          margin-bottom: 0;
        }

        .detail-label {
          margin: 0 0 0.5rem;
          font-size: 0.6875rem;
          font-weight: 600;
          color: #6B7280;
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }

        .detail-text {
          margin: 0;
          font-size: 0.875rem;
          color: #374151;
          line-height: 1.5;
        }

        .targeting-rules {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .rule {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.5rem;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 6px;
        }

        .rule-index {
          width: 20px;
          height: 20px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #E5E7EB;
          border-radius: 50%;
          font-size: 0.6875rem;
          font-weight: 600;
          color: #374151;
        }

        .rule-attribute {
          padding: 0.125rem 0.375rem;
          background: #EFF6FF;
          color: #1D4ED8;
          font-size: 0.75rem;
          border-radius: 4px;
        }

        .rule-operator {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .rule-value {
          padding: 0.125rem 0.375rem;
          background: #F3F4F6;
          color: #374151;
          font-size: 0.75rem;
          border-radius: 4px;
        }

        .variants {
          display: flex;
          flex-direction: column;
          gap: 0.375rem;
        }

        .variant {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          padding: 0.5rem;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 6px;
        }

        .variant-name {
          font-size: 0.8125rem;
          font-weight: 500;
          color: #111827;
        }

        .variant-value {
          flex: 1;
          padding: 0.125rem 0.375rem;
          background: #F3F4F6;
          color: #374151;
          font-size: 0.75rem;
          border-radius: 4px;
        }

        .variant-weight {
          padding: 0.125rem 0.5rem;
          background: #EDE9FE;
          color: #7C3AED;
          font-size: 0.6875rem;
          font-weight: 600;
          border-radius: 4px;
        }

        .metadata {
          display: flex;
          flex-wrap: wrap;
          gap: 1rem;
        }

        .meta-item {
          font-size: 0.75rem;
          color: #6B7280;
        }
      `}</style>
    </div>
  );
};

FeatureFlagRow.displayName = 'FeatureFlagRow';

export default FeatureFlagRow;
