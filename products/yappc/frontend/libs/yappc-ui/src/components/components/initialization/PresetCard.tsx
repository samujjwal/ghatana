/**
 * PresetCard Component
 *
 * @description Displays a preset configuration card for quick-start project
 * initialization with preview, features, and selection capability.
 *
 * @doc.type component
 * @doc.purpose preset-display-selection
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <PresetCard
 *   preset={{
 *     id: 'fullstack-ts',
 *     name: 'Full-Stack TypeScript',
 *     description: 'Next.js + tRPC + Prisma + PostgreSQL',
 *     category: 'web',
 *     features: ['Auth', 'Database', 'API'],
 *   }}
 *   onSelect={(preset) => selectPreset(preset)}
 *   selected={selectedId === 'fullstack-ts'}
 * />
 * ```
 */

import React, { useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Preset category for filtering and organization
 */
export type PresetCategory =
  | 'web'
  | 'mobile'
  | 'api'
  | 'microservices'
  | 'data'
  | 'ml'
  | 'iot'
  | 'game'
  | 'enterprise'
  | 'other';

/**
 * Technology stack configuration
 */
export interface TechStack {
  frontend?: string[];
  backend?: string[];
  database?: string[];
  infrastructure?: string[];
  devops?: string[];
}

/**
 * Estimated resource requirements
 */
export interface ResourceEstimate {
  /** Setup time in minutes */
  setupTimeMinutes: number;
  /** Monthly cost estimate in USD */
  monthlyCostUsd: number;
  /** Estimated team size */
  teamSize: string;
  /** Complexity level */
  complexity: 'simple' | 'moderate' | 'complex' | 'enterprise';
}

/**
 * Preset configuration object
 */
export interface InitializationPreset {
  /** Unique identifier */
  id: string;
  /** Display name */
  name: string;
  /** Short description */
  description: string;
  /** Detailed description */
  longDescription?: string;
  /** Category for filtering */
  category: PresetCategory;
  /** Feature list */
  features: string[];
  /** Technology stack */
  techStack: TechStack;
  /** Resource estimates */
  estimates: ResourceEstimate;
  /** Icon identifier or URL */
  icon?: string;
  /** Preview image URL */
  previewImage?: string;
  /** Whether this is a featured preset */
  featured?: boolean;
  /** Whether this is a premium preset */
  premium?: boolean;
  /** Version of the preset */
  version?: string;
  /** Author/maintainer */
  author?: string;
  /** Tags for search */
  tags?: string[];
  /** Number of users */
  usageCount?: number;
  /** Average rating (1-5) */
  rating?: number;
}

/**
 * Props for the PresetCard component
 */
export interface PresetCardProps {
  /** Preset data */
  preset: InitializationPreset;
  /** Whether this preset is selected */
  selected?: boolean;
  /** Callback when preset is selected */
  onSelect?: (preset: InitializationPreset) => void;
  /** Callback when preview is requested */
  onPreview?: (preset: InitializationPreset) => void;
  /** Display variant */
  variant?: 'default' | 'compact' | 'detailed';
  /** Whether the card is disabled */
  disabled?: boolean;
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getCategoryIcon = (category: PresetCategory): React.ReactNode => {
  const icons: Record<PresetCategory, React.ReactNode> = {
    web: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="2" y1="12" x2="22" y2="12" />
        <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
      </svg>
    ),
    mobile: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="5" y="2" width="14" height="20" rx="2" ry="2" />
        <line x1="12" y1="18" x2="12.01" y2="18" />
      </svg>
    ),
    api: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <polyline points="16 18 22 12 16 6" />
        <polyline points="8 6 2 12 8 18" />
      </svg>
    ),
    microservices: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="3" width="6" height="6" />
        <rect x="15" y="3" width="6" height="6" />
        <rect x="9" y="15" width="6" height="6" />
        <line x1="6" y1="9" x2="6" y2="15" />
        <line x1="18" y1="9" x2="18" y2="15" />
        <line x1="9" y1="18" x2="6" y2="15" />
        <line x1="15" y1="18" x2="18" y2="15" />
      </svg>
    ),
    data: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <ellipse cx="12" cy="5" rx="9" ry="3" />
        <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
        <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
      </svg>
    ),
    ml: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
      </svg>
    ),
    iot: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="3" />
        <circle cx="12" cy="12" r="7" strokeDasharray="4 2" />
        <circle cx="12" cy="12" r="10" strokeDasharray="2 4" />
      </svg>
    ),
    game: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="2" y="6" width="20" height="12" rx="2" />
        <line x1="6" y1="12" x2="10" y2="12" />
        <line x1="8" y1="10" x2="8" y2="14" />
        <circle cx="16" cy="10" r="1" />
        <circle cx="18" cy="12" r="1" />
      </svg>
    ),
    enterprise: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="3" width="18" height="18" rx="2" />
        <line x1="3" y1="9" x2="21" y2="9" />
        <line x1="9" y1="21" x2="9" y2="9" />
      </svg>
    ),
    other: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    ),
  };

  return icons[category] || icons.other;
};

const getComplexityLabel = (
  complexity: ResourceEstimate['complexity']
): { label: string; color: string } => {
  const labels: Record<typeof complexity, { label: string; color: string }> = {
    simple: { label: 'Simple', color: '#10B981' },
    moderate: { label: 'Moderate', color: '#3B82F6' },
    complex: { label: 'Complex', color: '#F59E0B' },
    enterprise: { label: 'Enterprise', color: '#8B5CF6' },
  };
  return labels[complexity];
};

const formatCost = (cost: number): string => {
  if (cost === 0) return 'Free';
  if (cost < 10) return `~$${cost}/mo`;
  return `~$${Math.round(cost)}/mo`;
};

const formatTime = (minutes: number): string => {
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins > 0 ? `${hours}h ${mins}m` : `${hours} hour${hours > 1 ? 's' : ''}`;
};

// ============================================================================
// Sub-Components
// ============================================================================

const TechBadge: React.FC<{ tech: string }> = ({ tech }) => (
  <span className="preset-tech-badge">{tech}</span>
);

const FeatureBadge: React.FC<{ feature: string }> = ({ feature }) => (
  <span className="preset-feature-badge">{feature}</span>
);

const StarRating: React.FC<{ rating: number }> = ({ rating }) => {
  const fullStars = Math.floor(rating);
  const hasHalfStar = rating % 1 >= 0.5;

  return (
    <div className="preset-rating" aria-label={`Rating: ${rating.toFixed(1)} out of 5`}>
      {[...Array(5)].map((_, i) => (
        <svg
          key={i}
          viewBox="0 0 24 24"
          fill={i < fullStars ? 'currentColor' : 'none'}
          stroke="currentColor"
          strokeWidth="2"
          className={`preset-star ${i < fullStars ? 'preset-star--filled' : ''}`}
        >
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
        </svg>
      ))}
      <span className="preset-rating-value">{rating.toFixed(1)}</span>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const PresetCard: React.FC<PresetCardProps> = ({
  preset,
  selected = false,
  onSelect,
  onPreview,
  variant = 'default',
  disabled = false,
  className = '',
}) => {
  const complexity = useMemo(
    () => getComplexityLabel(preset.estimates.complexity),
    [preset.estimates.complexity]
  );

  const allTech = useMemo(() => {
    const stack = preset.techStack;
    return [
      ...(stack.frontend || []),
      ...(stack.backend || []),
      ...(stack.database || []),
    ].slice(0, variant === 'compact' ? 3 : 6);
  }, [preset.techStack, variant]);

  const handleClick = () => {
    if (!disabled && onSelect) {
      onSelect(preset);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if ((e.key === 'Enter' || e.key === ' ') && !disabled && onSelect) {
      e.preventDefault();
      onSelect(preset);
    }
  };

  const handlePreview = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onPreview) {
      onPreview(preset);
    }
  };

  const cardClasses = [
    'preset-card',
    `preset-card--${variant}`,
    selected && 'preset-card--selected',
    disabled && 'preset-card--disabled',
    preset.featured && 'preset-card--featured',
    preset.premium && 'preset-card--premium',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <article
      className={cardClasses}
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-selected={selected}
      aria-disabled={disabled}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
    >
      {/* Header */}
      <header className="preset-card-header">
        <div className="preset-card-icon" aria-hidden="true">
          {getCategoryIcon(preset.category)}
        </div>
        <div className="preset-card-title-group">
          <h3 className="preset-card-title">{preset.name}</h3>
          {preset.version && (
            <span className="preset-card-version">v{preset.version}</span>
          )}
        </div>
        {preset.featured && (
          <span className="preset-badge preset-badge--featured">Featured</span>
        )}
        {preset.premium && (
          <span className="preset-badge preset-badge--premium">Premium</span>
        )}
      </header>

      {/* Description */}
      <p className="preset-card-description">
        {variant === 'detailed' ? preset.longDescription || preset.description : preset.description}
      </p>

      {/* Tech Stack */}
      {variant !== 'compact' && allTech.length > 0 && (
        <div className="preset-tech-stack">
          {allTech.map((tech) => (
            <TechBadge key={tech} tech={tech} />
          ))}
          {Object.values(preset.techStack).flat().length > allTech.length && (
            <span className="preset-tech-more">
              +{Object.values(preset.techStack).flat().length - allTech.length} more
            </span>
          )}
        </div>
      )}

      {/* Features */}
      {variant === 'detailed' && preset.features.length > 0 && (
        <div className="preset-features">
          <span className="preset-features-label">Features:</span>
          <div className="preset-features-list">
            {preset.features.slice(0, 5).map((feature) => (
              <FeatureBadge key={feature} feature={feature} />
            ))}
          </div>
        </div>
      )}

      {/* Estimates */}
      <div className="preset-estimates">
        <div className="preset-estimate">
          <span className="preset-estimate-label">Setup</span>
          <span className="preset-estimate-value">
            {formatTime(preset.estimates.setupTimeMinutes)}
          </span>
        </div>
        <div className="preset-estimate">
          <span className="preset-estimate-label">Cost</span>
          <span className="preset-estimate-value">
            {formatCost(preset.estimates.monthlyCostUsd)}
          </span>
        </div>
        <div className="preset-estimate">
          <span className="preset-estimate-label">Complexity</span>
          <span
            className="preset-estimate-value preset-estimate-complexity"
            style={{ color: complexity.color }}
          >
            {complexity.label}
          </span>
        </div>
      </div>

      {/* Footer */}
      <footer className="preset-card-footer">
        {preset.rating !== undefined && <StarRating rating={preset.rating} />}
        {preset.usageCount !== undefined && (
          <span className="preset-usage">
            {preset.usageCount.toLocaleString()} uses
          </span>
        )}
        {onPreview && (
          <button
            type="button"
            className="preset-preview-btn"
            onClick={handlePreview}
            aria-label={`Preview ${preset.name}`}
          >
            Preview
          </button>
        )}
      </footer>

      {/* Selection indicator */}
      {selected && (
        <div className="preset-selected-indicator" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
          </svg>
        </div>
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .preset-card {
          position: relative;
          display: flex;
          flex-direction: column;
          gap: 0.75rem;
          padding: 1.25rem;
          background: #fff;
          border: 2px solid #E5E7EB;
          border-radius: 12px;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .preset-card:hover:not(.preset-card--disabled) {
          border-color: #3B82F6;
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
          transform: translateY(-2px);
        }

        .preset-card:focus {
          outline: none;
          border-color: #3B82F6;
          box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
        }

        .preset-card--selected {
          border-color: #3B82F6;
          background: #EFF6FF;
        }

        .preset-card--disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }

        .preset-card--featured {
          border-color: #F59E0B;
        }

        .preset-card--premium {
          background: linear-gradient(135deg, #FEFCE8 0%, #FEF9C3 100%);
        }

        .preset-card--compact {
          padding: 1rem;
          gap: 0.5rem;
        }

        .preset-card--detailed {
          padding: 1.5rem;
          gap: 1rem;
        }

        .preset-card-header {
          display: flex;
          align-items: center;
          gap: 0.75rem;
        }

        .preset-card-icon {
          width: 40px;
          height: 40px;
          padding: 8px;
          background: #F3F4F6;
          border-radius: 8px;
          color: #6B7280;
          flex-shrink: 0;
        }

        .preset-card--selected .preset-card-icon {
          background: #DBEAFE;
          color: #3B82F6;
        }

        .preset-card-icon svg {
          width: 100%;
          height: 100%;
        }

        .preset-card-title-group {
          flex: 1;
          min-width: 0;
        }

        .preset-card-title {
          margin: 0;
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
          line-height: 1.25;
        }

        .preset-card-version {
          font-size: 0.75rem;
          color: #9CA3AF;
        }

        .preset-badge {
          padding: 0.125rem 0.5rem;
          font-size: 0.625rem;
          font-weight: 600;
          text-transform: uppercase;
          border-radius: 9999px;
        }

        .preset-badge--featured {
          background: #FEF3C7;
          color: #D97706;
        }

        .preset-badge--premium {
          background: #F3E8FF;
          color: #7C3AED;
        }

        .preset-card-description {
          margin: 0;
          font-size: 0.875rem;
          color: #6B7280;
          line-height: 1.5;
        }

        .preset-card--compact .preset-card-description {
          font-size: 0.75rem;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .preset-tech-stack {
          display: flex;
          flex-wrap: wrap;
          gap: 0.375rem;
        }

        .preset-tech-badge {
          padding: 0.125rem 0.5rem;
          font-size: 0.75rem;
          background: #F3F4F6;
          color: #374151;
          border-radius: 4px;
        }

        .preset-tech-more {
          padding: 0.125rem 0.5rem;
          font-size: 0.75rem;
          color: #6B7280;
        }

        .preset-features {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .preset-features-label {
          font-size: 0.75rem;
          font-weight: 500;
          color: #6B7280;
        }

        .preset-features-list {
          display: flex;
          flex-wrap: wrap;
          gap: 0.375rem;
        }

        .preset-feature-badge {
          padding: 0.25rem 0.625rem;
          font-size: 0.75rem;
          background: #DBEAFE;
          color: #1D4ED8;
          border-radius: 9999px;
        }

        .preset-estimates {
          display: flex;
          gap: 1rem;
          padding-top: 0.75rem;
          border-top: 1px solid #E5E7EB;
        }

        .preset-card--compact .preset-estimates {
          gap: 0.5rem;
          padding-top: 0.5rem;
        }

        .preset-estimate {
          display: flex;
          flex-direction: column;
          gap: 0.125rem;
        }

        .preset-estimate-label {
          font-size: 0.625rem;
          text-transform: uppercase;
          color: #9CA3AF;
          letter-spacing: 0.05em;
        }

        .preset-estimate-value {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .preset-card--compact .preset-estimate-value {
          font-size: 0.75rem;
        }

        .preset-card-footer {
          display: flex;
          align-items: center;
          gap: 1rem;
          margin-top: auto;
        }

        .preset-rating {
          display: flex;
          align-items: center;
          gap: 0.25rem;
        }

        .preset-star {
          width: 14px;
          height: 14px;
          color: #D1D5DB;
        }

        .preset-star--filled {
          color: #F59E0B;
        }

        .preset-rating-value {
          font-size: 0.75rem;
          color: #6B7280;
          margin-left: 0.25rem;
        }

        .preset-usage {
          font-size: 0.75rem;
          color: #9CA3AF;
        }

        .preset-preview-btn {
          margin-left: auto;
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

        .preset-preview-btn:hover {
          background: #EFF6FF;
        }

        .preset-selected-indicator {
          position: absolute;
          top: 0.75rem;
          right: 0.75rem;
          width: 24px;
          height: 24px;
          background: #3B82F6;
          border-radius: 50%;
          color: #fff;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .preset-selected-indicator svg {
          width: 14px;
          height: 14px;
        }
      `}</style>
    </article>
  );
};

PresetCard.displayName = 'PresetCard';

export default PresetCard;
