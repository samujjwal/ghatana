/**
 * ProviderSelector Component
 *
 * @description Multi-step selector for choosing infrastructure providers
 * for different service categories with feature comparison.
 *
 * @doc.type component
 * @doc.purpose provider-selection
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <ProviderSelector
 *   category="hosting"
 *   providers={[
 *     { id: 'vercel', name: 'Vercel', features: ['Serverless', 'Edge'] },
 *     { id: 'railway', name: 'Railway', features: ['Containers', 'Auto-deploy'] },
 *   ]}
 *   selectedProvider="vercel"
 *   onSelect={(providerId) => setProvider(providerId)}
 * />
 * ```
 */

import React, { useMemo, useState, useCallback } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Service categories for provider selection
 */
export type ProviderCategory =
  | 'hosting'
  | 'database'
  | 'storage'
  | 'auth'
  | 'repository'
  | 'cicd'
  | 'monitoring'
  | 'cdn'
  | 'email'
  | 'analytics';

/**
 * Pricing tier information
 */
export interface PricingTier {
  /** Tier name */
  name: string;
  /** Price (0 for free) */
  price: number;
  /** Billing period */
  period: 'free' | 'monthly' | 'annual';
  /** Features included */
  features: string[];
  /** Recommended for */
  recommended?: boolean;
}

/**
 * Provider feature
 */
export interface ProviderFeature {
  /** Feature ID */
  id: string;
  /** Feature name */
  name: string;
  /** Whether this provider supports it */
  supported: boolean;
  /** Notes or limitations */
  notes?: string;
}

/**
 * Provider option
 */
export interface Provider {
  /** Unique identifier */
  id: string;
  /** Display name */
  name: string;
  /** Description */
  description: string;
  /** Logo URL or icon identifier */
  logo?: string;
  /** Features list */
  features: string[];
  /** Detailed feature comparison */
  featureComparison?: ProviderFeature[];
  /** Pricing tiers */
  pricing?: PricingTier[];
  /** Whether this is recommended */
  recommended?: boolean;
  /** Tags */
  tags?: string[];
  /** Regions available */
  regions?: string[];
  /** External documentation URL */
  docsUrl?: string;
  /** Whether this provider is coming soon */
  comingSoon?: boolean;
  /** Popular use cases */
  useCases?: string[];
}

/**
 * Props for the ProviderSelector component
 */
export interface ProviderSelectorProps {
  /** Category being configured */
  category: ProviderCategory;
  /** Available providers */
  providers: Provider[];
  /** Currently selected provider ID */
  selectedProvider?: string;
  /** Callback when provider is selected */
  onSelect: (providerId: string) => void;
  /** Whether to show pricing */
  showPricing?: boolean;
  /** Whether to show feature comparison */
  showFeatureComparison?: boolean;
  /** Whether to allow comparing providers */
  allowCompare?: boolean;
  /** Title override */
  title?: string;
  /** Description override */
  description?: string;
  /** Loading state */
  loading?: boolean;
  /** Display variant */
  variant?: 'cards' | 'list' | 'compact';
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getCategoryIcon = (category: ProviderCategory): React.ReactNode => {
  const icons: Record<ProviderCategory, React.ReactNode> = {
    hosting: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="2" y="2" width="20" height="8" rx="2" ry="2" />
        <rect x="2" y="14" width="20" height="8" rx="2" ry="2" />
        <line x1="6" y1="6" x2="6.01" y2="6" />
        <line x1="6" y1="18" x2="6.01" y2="18" />
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
    auth: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
      </svg>
    ),
    repository: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
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
    monitoring: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
      </svg>
    ),
    cdn: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z" />
      </svg>
    ),
    email: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
        <polyline points="22 6 12 13 2 6" />
      </svg>
    ),
    analytics: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <line x1="18" y1="20" x2="18" y2="10" />
        <line x1="12" y1="20" x2="12" y2="4" />
        <line x1="6" y1="20" x2="6" y2="14" />
      </svg>
    ),
  };
  return icons[category] || icons.hosting;
};

const getCategoryLabel = (category: ProviderCategory): string => {
  const labels: Record<ProviderCategory, string> = {
    hosting: 'Hosting Provider',
    database: 'Database Provider',
    storage: 'Storage Provider',
    auth: 'Authentication Provider',
    repository: 'Code Repository',
    cicd: 'CI/CD Provider',
    monitoring: 'Monitoring Service',
    cdn: 'CDN Provider',
    email: 'Email Service',
    analytics: 'Analytics Provider',
  };
  return labels[category];
};

const formatPrice = (price: number, period: PricingTier['period']): string => {
  if (period === 'free' || price === 0) return 'Free';
  return `$${price}/${period === 'monthly' ? 'mo' : 'yr'}`;
};

// ============================================================================
// Sub-Components
// ============================================================================

interface ProviderCardProps {
  provider: Provider;
  isSelected: boolean;
  onSelect: () => void;
  showPricing: boolean;
  variant: 'cards' | 'list' | 'compact';
}

const ProviderCard: React.FC<ProviderCardProps> = ({
  provider,
  isSelected,
  onSelect,
  showPricing,
  variant,
}) => {
  const lowestPrice = useMemo(() => {
    if (!provider.pricing || provider.pricing.length === 0) return null;
    const sorted = [...provider.pricing].sort((a, b) => a.price - b.price);
    return sorted[0];
  }, [provider.pricing]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if ((e.key === 'Enter' || e.key === ' ') && !provider.comingSoon) {
      e.preventDefault();
      onSelect();
    }
  };

  const cardClasses = [
    'provider-card',
    `provider-card--${variant}`,
    isSelected && 'provider-card--selected',
    provider.recommended && 'provider-card--recommended',
    provider.comingSoon && 'provider-card--coming-soon',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div
      className={cardClasses}
      role="option"
      aria-selected={isSelected}
      aria-disabled={provider.comingSoon}
      tabIndex={provider.comingSoon ? -1 : 0}
      onClick={provider.comingSoon ? undefined : onSelect}
      onKeyDown={handleKeyDown}
    >
      {/* Logo/Icon */}
      <div className="provider-card-logo">
        {provider.logo ? (
          <img src={provider.logo} alt={`${provider.name} logo`} />
        ) : (
          <span className="provider-card-initial">
            {provider.name.charAt(0).toUpperCase()}
          </span>
        )}
      </div>

      {/* Content */}
      <div className="provider-card-content">
        <div className="provider-card-header">
          <span className="provider-card-name">{provider.name}</span>
          {provider.recommended && (
            <span className="provider-badge provider-badge--recommended">
              Recommended
            </span>
          )}
          {provider.comingSoon && (
            <span className="provider-badge provider-badge--coming-soon">
              Coming Soon
            </span>
          )}
        </div>

        <p className="provider-card-description">{provider.description}</p>

        {variant !== 'compact' && provider.features.length > 0 && (
          <div className="provider-card-features">
            {provider.features.slice(0, 4).map((feature) => (
              <span key={feature} className="provider-feature-tag">
                {feature}
              </span>
            ))}
            {provider.features.length > 4 && (
              <span className="provider-feature-more">
                +{provider.features.length - 4} more
              </span>
            )}
          </div>
        )}

        {showPricing && lowestPrice && (
          <div className="provider-card-pricing">
            <span className="provider-price">
              Starting at {formatPrice(lowestPrice.price, lowestPrice.period)}
            </span>
          </div>
        )}
      </div>

      {/* Selection indicator */}
      {isSelected && (
        <div className="provider-selected-indicator" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
          </svg>
        </div>
      )}
    </div>
  );
};

interface FeatureComparisonTableProps {
  providers: Provider[];
  selectedProvider?: string;
}

const FeatureComparisonTable: React.FC<FeatureComparisonTableProps> = ({
  providers,
  selectedProvider,
}) => {
  const allFeatures = useMemo(() => {
    const featureSet = new Set<string>();
    providers.forEach((p) => {
      p.featureComparison?.forEach((f) => featureSet.add(f.name));
    });
    return Array.from(featureSet);
  }, [providers]);

  if (allFeatures.length === 0) return null;

  return (
    <div className="provider-comparison">
      <table className="provider-comparison-table">
        <thead>
          <tr>
            <th>Feature</th>
            {providers.map((p) => (
              <th
                key={p.id}
                className={p.id === selectedProvider ? 'comparison-selected' : ''}
              >
                {p.name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {allFeatures.map((featureName) => (
            <tr key={featureName}>
              <td>{featureName}</td>
              {providers.map((p) => {
                const feature = p.featureComparison?.find((f) => f.name === featureName);
                return (
                  <td
                    key={p.id}
                    className={p.id === selectedProvider ? 'comparison-selected' : ''}
                  >
                    {feature?.supported ? (
                      <span className="comparison-check">
                        <svg viewBox="0 0 24 24" fill="currentColor">
                          <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
                        </svg>
                      </span>
                    ) : (
                      <span className="comparison-x">—</span>
                    )}
                    {feature?.notes && (
                      <span className="comparison-notes">{feature.notes}</span>
                    )}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const ProviderSelector: React.FC<ProviderSelectorProps> = ({
  category,
  providers,
  selectedProvider,
  onSelect,
  showPricing = true,
  showFeatureComparison = false,
  allowCompare = false,
  title,
  description,
  loading = false,
  variant = 'cards',
  className = '',
}) => {
  const [showComparison, setShowComparison] = useState(false);

  const sortedProviders = useMemo(() => {
    return [...providers].sort((a, b) => {
      // Recommended first
      if (a.recommended && !b.recommended) return -1;
      if (!a.recommended && b.recommended) return 1;
      // Coming soon last
      if (a.comingSoon && !b.comingSoon) return 1;
      if (!a.comingSoon && b.comingSoon) return -1;
      return a.name.localeCompare(b.name);
    });
  }, [providers]);

  const hasComparableFeatures = useMemo(() => {
    return providers.some(
      (p) => p.featureComparison && p.featureComparison.length > 0
    );
  }, [providers]);

  const handleSelect = useCallback(
    (providerId: string) => {
      onSelect(providerId);
    },
    [onSelect]
  );

  const containerClasses = [
    'provider-selector',
    `provider-selector--${variant}`,
    loading && 'provider-selector--loading',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={containerClasses}>
      {/* Header */}
      <div className="provider-selector-header">
        <div className="provider-selector-icon" aria-hidden="true">
          {getCategoryIcon(category)}
        </div>
        <div className="provider-selector-title-group">
          <h3 className="provider-selector-title">
            {title || getCategoryLabel(category)}
          </h3>
          {description && (
            <p className="provider-selector-description">{description}</p>
          )}
        </div>
        {allowCompare && hasComparableFeatures && (
          <button
            type="button"
            className="provider-compare-btn"
            onClick={() => setShowComparison(!showComparison)}
            aria-expanded={showComparison}
          >
            {showComparison ? 'Hide' : 'Compare'}
          </button>
        )}
      </div>

      {/* Provider Grid */}
      <div
        className="provider-grid"
        role="listbox"
        aria-label={`Select ${getCategoryLabel(category)}`}
      >
        {sortedProviders.map((provider) => (
          <ProviderCard
            key={provider.id}
            provider={provider}
            isSelected={provider.id === selectedProvider}
            onSelect={() => handleSelect(provider.id)}
            showPricing={showPricing}
            variant={variant}
          />
        ))}
      </div>

      {/* Feature Comparison */}
      {showComparison && showFeatureComparison && hasComparableFeatures && (
        <FeatureComparisonTable
          providers={providers}
          selectedProvider={selectedProvider}
        />
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .provider-selector {
          display: flex;
          flex-direction: column;
          gap: 1rem;
        }

        .provider-selector--loading {
          opacity: 0.7;
          pointer-events: none;
        }

        .provider-selector-header {
          display: flex;
          align-items: flex-start;
          gap: 0.75rem;
        }

        .provider-selector-icon {
          width: 40px;
          height: 40px;
          padding: 10px;
          background: #F3F4F6;
          border-radius: 10px;
          color: #6B7280;
          flex-shrink: 0;
        }

        .provider-selector-icon svg {
          width: 100%;
          height: 100%;
        }

        .provider-selector-title-group {
          flex: 1;
        }

        .provider-selector-title {
          margin: 0;
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
        }

        .provider-selector-description {
          margin: 0.25rem 0 0;
          font-size: 0.875rem;
          color: #6B7280;
        }

        .provider-compare-btn {
          padding: 0.375rem 0.75rem;
          font-size: 0.75rem;
          font-weight: 500;
          color: #3B82F6;
          background: transparent;
          border: 1px solid #3B82F6;
          border-radius: 6px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .provider-compare-btn:hover {
          background: #EFF6FF;
        }

        .provider-grid {
          display: grid;
          gap: 0.75rem;
        }

        .provider-selector--cards .provider-grid {
          grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        }

        .provider-selector--list .provider-grid {
          grid-template-columns: 1fr;
        }

        .provider-selector--compact .provider-grid {
          grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
        }

        .provider-card {
          display: flex;
          gap: 0.75rem;
          padding: 1rem;
          background: #fff;
          border: 2px solid #E5E7EB;
          border-radius: 10px;
          cursor: pointer;
          transition: all 0.2s ease;
          position: relative;
        }

        .provider-card:hover:not(.provider-card--coming-soon) {
          border-color: #3B82F6;
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
        }

        .provider-card:focus {
          outline: none;
          border-color: #3B82F6;
          box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
        }

        .provider-card--selected {
          border-color: #3B82F6;
          background: #EFF6FF;
        }

        .provider-card--recommended {
          border-color: #10B981;
        }

        .provider-card--coming-soon {
          opacity: 0.6;
          cursor: not-allowed;
        }

        .provider-card--compact {
          padding: 0.75rem;
        }

        .provider-card--list {
          flex-direction: row;
          align-items: center;
        }

        .provider-card-logo {
          width: 40px;
          height: 40px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #F3F4F6;
          border-radius: 8px;
          overflow: hidden;
          flex-shrink: 0;
        }

        .provider-card--compact .provider-card-logo {
          width: 32px;
          height: 32px;
        }

        .provider-card-logo img {
          width: 100%;
          height: 100%;
          object-fit: contain;
        }

        .provider-card-initial {
          font-size: 1.25rem;
          font-weight: 600;
          color: #6B7280;
        }

        .provider-card-content {
          flex: 1;
          min-width: 0;
        }

        .provider-card-header {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          flex-wrap: wrap;
        }

        .provider-card-name {
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .provider-badge {
          padding: 0.125rem 0.5rem;
          font-size: 0.625rem;
          font-weight: 500;
          text-transform: uppercase;
          border-radius: 9999px;
        }

        .provider-badge--recommended {
          background: #D1FAE5;
          color: #059669;
        }

        .provider-badge--coming-soon {
          background: #E5E7EB;
          color: #6B7280;
        }

        .provider-card-description {
          margin: 0.25rem 0 0;
          font-size: 0.75rem;
          color: #6B7280;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .provider-card--compact .provider-card-description {
          -webkit-line-clamp: 1;
        }

        .provider-card-features {
          display: flex;
          flex-wrap: wrap;
          gap: 0.25rem;
          margin-top: 0.5rem;
        }

        .provider-feature-tag {
          padding: 0.125rem 0.375rem;
          font-size: 0.625rem;
          background: #F3F4F6;
          color: #374151;
          border-radius: 4px;
        }

        .provider-feature-more {
          padding: 0.125rem 0.375rem;
          font-size: 0.625rem;
          color: #9CA3AF;
        }

        .provider-card-pricing {
          margin-top: 0.5rem;
        }

        .provider-price {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .provider-selected-indicator {
          position: absolute;
          top: 0.5rem;
          right: 0.5rem;
          width: 20px;
          height: 20px;
          background: #3B82F6;
          border-radius: 50%;
          color: #fff;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .provider-selected-indicator svg {
          width: 12px;
          height: 12px;
        }

        .provider-comparison {
          overflow-x: auto;
          margin-top: 1rem;
        }

        .provider-comparison-table {
          width: 100%;
          border-collapse: collapse;
          font-size: 0.75rem;
        }

        .provider-comparison-table th,
        .provider-comparison-table td {
          padding: 0.5rem 0.75rem;
          text-align: left;
          border-bottom: 1px solid #E5E7EB;
        }

        .provider-comparison-table th {
          font-weight: 500;
          color: #6B7280;
          background: #F9FAFB;
        }

        .provider-comparison-table td {
          color: #374151;
        }

        .comparison-selected {
          background: #EFF6FF;
        }

        .comparison-check {
          color: #10B981;
        }

        .comparison-check svg {
          width: 16px;
          height: 16px;
        }

        .comparison-x {
          color: #D1D5DB;
        }

        .comparison-notes {
          display: block;
          font-size: 0.625rem;
          color: #9CA3AF;
          margin-top: 0.125rem;
        }
      `}</style>
    </div>
  );
};

ProviderSelector.displayName = 'ProviderSelector';

export default ProviderSelector;
