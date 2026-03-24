/**
 * CostEstimator Component
 *
 * @description Displays estimated costs for infrastructure and services
 * with breakdown by category, provider, and environment.
 *
 * @doc.type component
 * @doc.purpose cost-estimation-display
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <CostEstimator
 *   estimates={{
 *     monthly: 150,
 *     breakdown: [
 *       { category: 'Compute', amount: 80 },
 *       { category: 'Database', amount: 50 },
 *       { category: 'Storage', amount: 20 },
 *     ],
 *   }}
 *   onOptimize={() => showOptimizationSuggestions()}
 * />
 * ```
 */

import React, { useMemo, useState } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Cost category types
 */
export type CostCategory =
  | 'compute'
  | 'database'
  | 'storage'
  | 'network'
  | 'cdn'
  | 'monitoring'
  | 'ci_cd'
  | 'other';

/**
 * Cost breakdown item
 */
export interface CostBreakdownItem {
  /** Category */
  category: CostCategory;
  /** Display label */
  label: string;
  /** Monthly cost in USD */
  amount: number;
  /** Resource count */
  resourceCount?: number;
  /** Percentage of total */
  percentage?: number;
  /** Whether this is a variable cost */
  isVariable?: boolean;
  /** Minimum expected cost */
  minAmount?: number;
  /** Maximum expected cost */
  maxAmount?: number;
  /** Usage notes */
  notes?: string;
}

/**
 * Provider cost breakdown
 */
export interface ProviderCost {
  /** Provider name */
  provider: string;
  /** Monthly cost */
  amount: number;
  /** Free tier status */
  freeTier?: boolean;
  /** Services from this provider */
  services: string[];
}

/**
 * Environment cost breakdown
 */
export interface EnvironmentCost {
  /** Environment name */
  environment: string;
  /** Monthly cost */
  amount: number;
  /** Whether this is always running */
  alwaysOn?: boolean;
}

/**
 * Cost optimization suggestion
 */
export interface CostOptimization {
  /** Suggestion ID */
  id: string;
  /** Suggestion title */
  title: string;
  /** Description */
  description: string;
  /** Potential savings */
  savingsAmount: number;
  /** Difficulty to implement */
  difficulty: 'easy' | 'medium' | 'hard';
  /** Category */
  category: CostCategory;
}

/**
 * Complete cost estimates
 */
export interface CostEstimates {
  /** Total monthly cost */
  monthly: number;
  /** Annual cost */
  annual?: number;
  /** Cost breakdown by category */
  breakdown: CostBreakdownItem[];
  /** Cost by provider */
  byProvider?: ProviderCost[];
  /** Cost by environment */
  byEnvironment?: EnvironmentCost[];
  /** Optimization suggestions */
  optimizations?: CostOptimization[];
  /** Currency */
  currency?: string;
  /** Last updated */
  updatedAt?: Date;
  /** Confidence level */
  confidence?: 'low' | 'medium' | 'high';
}

/**
 * Props for the CostEstimator component
 */
export interface CostEstimatorProps {
  /** Cost estimates data */
  estimates: CostEstimates;
  /** Callback when optimization is clicked */
  onOptimize?: () => void;
  /** Callback when breakdown item is clicked */
  onBreakdownClick?: (category: CostCategory) => void;
  /** Whether to show provider breakdown */
  showProviders?: boolean;
  /** Whether to show environment breakdown */
  showEnvironments?: boolean;
  /** Whether to show optimizations */
  showOptimizations?: boolean;
  /** Display variant */
  variant?: 'default' | 'compact' | 'detailed';
  /** Loading state */
  loading?: boolean;
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const formatCurrency = (amount: number, currency = 'USD'): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(amount);
};

const getCategoryIcon = (category: CostCategory): React.ReactNode => {
  const icons: Record<CostCategory, React.ReactNode> = {
    compute: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="4" y="4" width="16" height="16" rx="2" ry="2" />
        <rect x="9" y="9" width="6" height="6" />
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
    network: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="2" y1="12" x2="22" y2="12" />
        <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10" />
      </svg>
    ),
    cdn: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z" />
      </svg>
    ),
    monitoring: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
      </svg>
    ),
    ci_cd: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="17 1 21 5 17 9" />
        <path d="M3 11V9a4 4 0 0 1 4-4h14" />
        <polyline points="7 23 3 19 7 15" />
        <path d="M21 13v2a4 4 0 0 1-4 4H3" />
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
  return icons[category] || icons.other;
};

const getCategoryColor = (category: CostCategory): string => {
  const colors: Record<CostCategory, string> = {
    compute: '#3B82F6',
    database: '#8B5CF6',
    storage: '#10B981',
    network: '#F59E0B',
    cdn: '#EC4899',
    monitoring: '#6366F1',
    ci_cd: '#14B8A6',
    other: '#6B7280',
  };
  return colors[category];
};

const getConfidenceBadge = (
  confidence: CostEstimates['confidence']
): { label: string; color: string } => {
  const badges: Record<NonNullable<typeof confidence>, { label: string; color: string }> = {
    low: { label: 'Low Confidence', color: '#EF4444' },
    medium: { label: 'Medium Confidence', color: '#F59E0B' },
    high: { label: 'High Confidence', color: '#10B981' },
  };
  return badges[confidence || 'medium'];
};

// ============================================================================
// Sub-Components
// ============================================================================

interface CostBarProps {
  items: CostBreakdownItem[];
  total: number;
}

const CostBar: React.FC<CostBarProps> = ({ items, total }) => {
  return (
    <div className="cost-bar">
      {items.map((item) => {
        const width = total > 0 ? (item.amount / total) * 100 : 0;
        return (
          <div
            key={item.category}
            className="cost-bar-segment"
            style={{
              width: `${width}%`,
              backgroundColor: getCategoryColor(item.category),
            }}
            title={`${item.label}: ${formatCurrency(item.amount)}`}
          />
        );
      })}
    </div>
  );
};

interface BreakdownItemProps {
  item: CostBreakdownItem;
  onClick?: () => void;
}

const BreakdownItem: React.FC<BreakdownItemProps> = ({ item, onClick }) => {
  const color = getCategoryColor(item.category);

  return (
    <div
      className={`cost-breakdown-item ${onClick ? 'cost-breakdown-item--clickable' : ''}`}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      <div className="cost-breakdown-icon" style={{ backgroundColor: `${color}20`, color }}>
        {getCategoryIcon(item.category)}
      </div>
      <div className="cost-breakdown-info">
        <span className="cost-breakdown-label">{item.label}</span>
        {item.resourceCount !== undefined && (
          <span className="cost-breakdown-count">
            {item.resourceCount} resource{item.resourceCount !== 1 ? 's' : ''}
          </span>
        )}
      </div>
      <div className="cost-breakdown-amount">
        <span className="cost-breakdown-value">{formatCurrency(item.amount)}</span>
        {item.isVariable && (
          <span className="cost-breakdown-variable">
            {item.minAmount !== undefined && item.maxAmount !== undefined
              ? `${formatCurrency(item.minAmount)} - ${formatCurrency(item.maxAmount)}`
              : 'Variable'}
          </span>
        )}
      </div>
    </div>
  );
};

interface OptimizationCardProps {
  optimization: CostOptimization;
}

const OptimizationCard: React.FC<OptimizationCardProps> = ({ optimization }) => {
  const difficultyColors = {
    easy: '#10B981',
    medium: '#F59E0B',
    hard: '#EF4444',
  };

  return (
    <div className="cost-optimization">
      <div className="cost-optimization-header">
        <span className="cost-optimization-savings">
          Save {formatCurrency(optimization.savingsAmount)}/mo
        </span>
        <span
          className="cost-optimization-difficulty"
          style={{ color: difficultyColors[optimization.difficulty] }}
        >
          {optimization.difficulty}
        </span>
      </div>
      <span className="cost-optimization-title">{optimization.title}</span>
      <span className="cost-optimization-description">{optimization.description}</span>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const CostEstimator: React.FC<CostEstimatorProps> = ({
  estimates,
  onOptimize,
  onBreakdownClick,
  showProviders = true,
  showEnvironments = true,
  showOptimizations = true,
  variant = 'default',
  loading = false,
  className = '',
}) => {
  const [activeTab, setActiveTab] = useState<'breakdown' | 'providers' | 'environments'>(
    'breakdown'
  );

  const sortedBreakdown = useMemo(() => {
    return [...estimates.breakdown].sort((a, b) => b.amount - a.amount);
  }, [estimates.breakdown]);

  const totalSavings = useMemo(() => {
    return estimates.optimizations?.reduce((sum, opt) => sum + opt.savingsAmount, 0) || 0;
  }, [estimates.optimizations]);

  const confidence = getConfidenceBadge(estimates.confidence);

  const containerClasses = [
    'cost-estimator',
    `cost-estimator--${variant}`,
    loading && 'cost-estimator--loading',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={containerClasses}>
      {/* Header */}
      <div className="cost-estimator-header">
        <div className="cost-estimator-title-group">
          <h3 className="cost-estimator-title">Cost Estimate</h3>
          {estimates.confidence && (
            <span
              className="cost-estimator-confidence"
              style={{ color: confidence.color }}
            >
              {confidence.label}
            </span>
          )}
        </div>
        {onOptimize && totalSavings > 0 && (
          <button type="button" className="cost-optimize-btn" onClick={onOptimize}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
            Save up to {formatCurrency(totalSavings)}/mo
          </button>
        )}
      </div>

      {/* Total Cost */}
      <div className="cost-total">
        <div className="cost-total-main">
          <span className="cost-total-label">Monthly</span>
          <span className="cost-total-amount">
            {formatCurrency(estimates.monthly, estimates.currency)}
          </span>
        </div>
        {estimates.annual && (
          <div className="cost-total-annual">
            <span className="cost-total-label">Annual</span>
            <span className="cost-total-annual-amount">
              {formatCurrency(estimates.annual, estimates.currency)}
            </span>
          </div>
        )}
      </div>

      {/* Cost Bar */}
      {variant !== 'compact' && sortedBreakdown.length > 0 && (
        <CostBar items={sortedBreakdown} total={estimates.monthly} />
      )}

      {/* Tabs */}
      {variant === 'detailed' && (showProviders || showEnvironments) && (
        <div className="cost-tabs" role="tablist">
          <button
            type="button"
            className={`cost-tab ${activeTab === 'breakdown' ? 'cost-tab--active' : ''}`}
            role="tab"
            aria-selected={activeTab === 'breakdown'}
            onClick={() => setActiveTab('breakdown')}
          >
            By Category
          </button>
          {showProviders && estimates.byProvider && (
            <button
              type="button"
              className={`cost-tab ${activeTab === 'providers' ? 'cost-tab--active' : ''}`}
              role="tab"
              aria-selected={activeTab === 'providers'}
              onClick={() => setActiveTab('providers')}
            >
              By Provider
            </button>
          )}
          {showEnvironments && estimates.byEnvironment && (
            <button
              type="button"
              className={`cost-tab ${activeTab === 'environments' ? 'cost-tab--active' : ''}`}
              role="tab"
              aria-selected={activeTab === 'environments'}
              onClick={() => setActiveTab('environments')}
            >
              By Environment
            </button>
          )}
        </div>
      )}

      {/* Breakdown */}
      {(activeTab === 'breakdown' || variant !== 'detailed') && (
        <div className="cost-breakdown" role="list">
          {sortedBreakdown.map((item) => (
            <BreakdownItem
              key={item.category}
              item={item}
              onClick={onBreakdownClick ? () => onBreakdownClick(item.category) : undefined}
            />
          ))}
        </div>
      )}

      {/* Provider Breakdown */}
      {activeTab === 'providers' && estimates.byProvider && (
        <div className="cost-providers">
          {estimates.byProvider.map((provider) => (
            <div key={provider.provider} className="cost-provider">
              <div className="cost-provider-header">
                <span className="cost-provider-name">{provider.provider}</span>
                {provider.freeTier && (
                  <span className="cost-provider-free">Free Tier</span>
                )}
              </div>
              <span className="cost-provider-amount">
                {formatCurrency(provider.amount)}
              </span>
              <div className="cost-provider-services">
                {provider.services.join(', ')}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Environment Breakdown */}
      {activeTab === 'environments' && estimates.byEnvironment && (
        <div className="cost-environments">
          {estimates.byEnvironment.map((env) => (
            <div key={env.environment} className="cost-environment">
              <div className="cost-environment-info">
                <span className="cost-environment-name">{env.environment}</span>
                {env.alwaysOn && (
                  <span className="cost-environment-always-on">24/7</span>
                )}
              </div>
              <span className="cost-environment-amount">
                {formatCurrency(env.amount)}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Optimizations */}
      {showOptimizations &&
        estimates.optimizations &&
        estimates.optimizations.length > 0 &&
        variant === 'detailed' && (
          <div className="cost-optimizations">
            <h4 className="cost-optimizations-title">Optimization Opportunities</h4>
            <div className="cost-optimizations-list">
              {estimates.optimizations.slice(0, 3).map((opt) => (
                <OptimizationCard key={opt.id} optimization={opt} />
              ))}
            </div>
          </div>
        )}

      {/* Footer */}
      {estimates.updatedAt && (
        <div className="cost-estimator-footer">
          <span>
            Last updated:{' '}
            {estimates.updatedAt.toLocaleDateString(undefined, {
              dateStyle: 'medium',
            })}
          </span>
        </div>
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .cost-estimator {
          display: flex;
          flex-direction: column;
          gap: 1rem;
          padding: 1.25rem;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
        }

        .cost-estimator--loading {
          opacity: 0.7;
          pointer-events: none;
        }

        .cost-estimator--compact {
          padding: 1rem;
          gap: 0.75rem;
        }

        .cost-estimator--detailed {
          padding: 1.5rem;
        }

        .cost-estimator-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          flex-wrap: wrap;
          gap: 0.5rem;
        }

        .cost-estimator-title-group {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .cost-estimator-title {
          margin: 0;
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
        }

        .cost-estimator--compact .cost-estimator-title {
          font-size: 0.875rem;
        }

        .cost-estimator-confidence {
          font-size: 0.625rem;
          text-transform: uppercase;
          font-weight: 500;
        }

        .cost-optimize-btn {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          padding: 0.375rem 0.75rem;
          font-size: 0.75rem;
          font-weight: 500;
          color: #10B981;
          background: #D1FAE5;
          border: none;
          border-radius: 6px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .cost-optimize-btn:hover {
          background: #A7F3D0;
        }

        .cost-optimize-btn svg {
          width: 14px;
          height: 14px;
        }

        .cost-total {
          display: flex;
          align-items: flex-end;
          gap: 1.5rem;
        }

        .cost-total-main {
          display: flex;
          flex-direction: column;
        }

        .cost-total-label {
          font-size: 0.625rem;
          text-transform: uppercase;
          color: #6B7280;
        }

        .cost-total-amount {
          font-size: 2rem;
          font-weight: 700;
          color: #111827;
          line-height: 1.2;
        }

        .cost-estimator--compact .cost-total-amount {
          font-size: 1.5rem;
        }

        .cost-total-annual {
          display: flex;
          flex-direction: column;
          padding-bottom: 0.25rem;
        }

        .cost-total-annual-amount {
          font-size: 1.125rem;
          font-weight: 600;
          color: #6B7280;
        }

        .cost-bar {
          display: flex;
          height: 8px;
          background: #F3F4F6;
          border-radius: 4px;
          overflow: hidden;
        }

        .cost-bar-segment {
          transition: width 0.3s ease;
        }

        .cost-tabs {
          display: flex;
          gap: 0.25rem;
          border-bottom: 1px solid #E5E7EB;
          margin: 0 -1.5rem;
          padding: 0 1.5rem;
        }

        .cost-tab {
          padding: 0.5rem 1rem;
          font-size: 0.75rem;
          font-weight: 500;
          color: #6B7280;
          background: transparent;
          border: none;
          border-bottom: 2px solid transparent;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .cost-tab:hover {
          color: #374151;
        }

        .cost-tab--active {
          color: #3B82F6;
          border-bottom-color: #3B82F6;
        }

        .cost-breakdown {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .cost-breakdown-item {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          padding: 0.5rem;
          border-radius: 8px;
          transition: background 0.15s ease;
        }

        .cost-breakdown-item--clickable {
          cursor: pointer;
        }

        .cost-breakdown-item--clickable:hover {
          background: #F9FAFB;
        }

        .cost-breakdown-icon {
          width: 32px;
          height: 32px;
          padding: 6px;
          border-radius: 8px;
          flex-shrink: 0;
        }

        .cost-breakdown-icon svg {
          width: 100%;
          height: 100%;
        }

        .cost-breakdown-info {
          flex: 1;
          min-width: 0;
        }

        .cost-breakdown-label {
          display: block;
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .cost-breakdown-count {
          display: block;
          font-size: 0.625rem;
          color: #9CA3AF;
        }

        .cost-breakdown-amount {
          display: flex;
          flex-direction: column;
          align-items: flex-end;
        }

        .cost-breakdown-value {
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .cost-breakdown-variable {
          font-size: 0.625rem;
          color: #9CA3AF;
        }

        .cost-providers,
        .cost-environments {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .cost-provider,
        .cost-environment {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 0.5rem;
          padding: 0.75rem;
          background: #F9FAFB;
          border-radius: 8px;
        }

        .cost-provider-header,
        .cost-environment-info {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          flex: 1;
        }

        .cost-provider-name,
        .cost-environment-name {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .cost-provider-free {
          padding: 0.125rem 0.375rem;
          font-size: 0.625rem;
          font-weight: 500;
          color: #10B981;
          background: #D1FAE5;
          border-radius: 4px;
        }

        .cost-environment-always-on {
          padding: 0.125rem 0.375rem;
          font-size: 0.625rem;
          font-weight: 500;
          color: #F59E0B;
          background: #FEF3C7;
          border-radius: 4px;
        }

        .cost-provider-amount,
        .cost-environment-amount {
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .cost-provider-services {
          width: 100%;
          font-size: 0.75rem;
          color: #6B7280;
        }

        .cost-optimizations {
          padding-top: 1rem;
          border-top: 1px solid #E5E7EB;
        }

        .cost-optimizations-title {
          margin: 0 0 0.75rem;
          font-size: 0.875rem;
          font-weight: 500;
          color: #374151;
        }

        .cost-optimizations-list {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .cost-optimization {
          display: flex;
          flex-direction: column;
          gap: 0.25rem;
          padding: 0.75rem;
          background: #F0FDF4;
          border: 1px solid #BBF7D0;
          border-radius: 8px;
        }

        .cost-optimization-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }

        .cost-optimization-savings {
          font-size: 0.875rem;
          font-weight: 600;
          color: #059669;
        }

        .cost-optimization-difficulty {
          font-size: 0.625rem;
          text-transform: uppercase;
          font-weight: 500;
        }

        .cost-optimization-title {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .cost-optimization-description {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .cost-estimator-footer {
          padding-top: 0.75rem;
          border-top: 1px solid #E5E7EB;
          font-size: 0.75rem;
          color: #9CA3AF;
        }
      `}</style>
    </div>
  );
};

CostEstimator.displayName = 'CostEstimator';

export default CostEstimator;
