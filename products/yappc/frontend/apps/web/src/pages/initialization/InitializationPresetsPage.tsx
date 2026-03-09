/**
 * InitializationPresetsPage
 *
 * @description Quick-start presets page for selecting pre-configured
 * technology stacks and deployment templates.
 *
 * @route /projects/:projectId/initialize/presets
 * @doc.phase 2
 * @doc.type page
 */

import React, { useState, useMemo, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { PresetCard, InitializationPreset, PresetCategory } from '@ghatana/yappc-ui';

// ============================================================================
// Types
// ============================================================================

interface PresetFilter {
  category: PresetCategory | 'all';
  searchQuery: string;
}

// ============================================================================
// Preset Data
// ============================================================================

const PRESETS: InitializationPreset[] = [
  {
    id: 'mern-stack',
    name: 'MERN Stack',
    description:
      'Full-stack JavaScript with MongoDB, Express, React, and Node.js. Perfect for modern web applications.',
    category: 'fullstack',
    techStack: [
      { name: 'MongoDB', version: '7.0', icon: '🍃' },
      { name: 'Express.js', version: '4.18', icon: '⚡' },
      { name: 'React', version: '19.0', icon: '⚛️' },
      { name: 'Node.js', version: '22', icon: '🟢' },
    ],
    features: [
      'MongoDB Atlas integration',
      'JWT authentication',
      'REST API scaffold',
      'Docker support',
    ],
    estimatedCost: { min: 0, max: 15, currency: 'USD' },
    estimatedTime: 5,
    difficulty: 'beginner',
    popularity: 4.8,
    recommended: true,
  },
  {
    id: 'pern-stack',
    name: 'PERN Stack',
    description:
      'PostgreSQL, Express, React, Node.js. Enterprise-grade relational database with modern frontend.',
    category: 'fullstack',
    techStack: [
      { name: 'PostgreSQL', version: '16', icon: '🐘' },
      { name: 'Express.js', version: '4.18', icon: '⚡' },
      { name: 'React', version: '19.0', icon: '⚛️' },
      { name: 'Node.js', version: '22', icon: '🟢' },
    ],
    features: [
      'Supabase database',
      'Prisma ORM',
      'Type-safe queries',
      'Database migrations',
    ],
    estimatedCost: { min: 0, max: 25, currency: 'USD' },
    estimatedTime: 5,
    difficulty: 'beginner',
    popularity: 4.6,
  },
  {
    id: 'jamstack',
    name: 'JAMstack',
    description:
      'JavaScript, APIs, and Markup. Static-first approach with dynamic capabilities through APIs.',
    category: 'frontend',
    techStack: [
      { name: 'Next.js', version: '15', icon: '▲' },
      { name: 'Tailwind CSS', version: '4.0', icon: '🎨' },
      { name: 'Contentful', version: 'Latest', icon: '📝' },
    ],
    features: [
      'Static site generation',
      'Edge functions',
      'Headless CMS',
      'CDN deployment',
    ],
    estimatedCost: { min: 0, max: 10, currency: 'USD' },
    estimatedTime: 3,
    difficulty: 'beginner',
    popularity: 4.5,
  },
  {
    id: 'microservices',
    name: 'Microservices',
    description:
      'Distributed architecture with containerized services. Scalable and maintainable at any size.',
    category: 'backend',
    techStack: [
      { name: 'Kubernetes', version: '1.29', icon: '☸️' },
      { name: 'Docker', version: '25', icon: '🐳' },
      { name: 'Kong', version: '3.5', icon: '🦍' },
      { name: 'Istio', version: '1.20', icon: '⛵' },
    ],
    features: [
      'Service mesh',
      'API gateway',
      'Auto-scaling',
      'Health monitoring',
    ],
    estimatedCost: { min: 50, max: 200, currency: 'USD' },
    estimatedTime: 15,
    difficulty: 'advanced',
    popularity: 4.3,
  },
  {
    id: 'serverless',
    name: 'Serverless',
    description:
      'Pay-per-use cloud functions with auto-scaling. Zero infrastructure management.',
    category: 'backend',
    techStack: [
      { name: 'AWS Lambda', version: 'Latest', icon: 'λ' },
      { name: 'API Gateway', version: 'Latest', icon: '🚪' },
      { name: 'DynamoDB', version: 'Latest', icon: '📊' },
    ],
    features: [
      'Auto-scaling to zero',
      'Pay per execution',
      'Global edge deployment',
      'Event-driven',
    ],
    estimatedCost: { min: 0, max: 25, currency: 'USD' },
    estimatedTime: 4,
    difficulty: 'intermediate',
    popularity: 4.4,
  },
  {
    id: 'java-activej',
    name: 'Java + ActiveJ',
    description:
      'High-performance Java backend with ActiveJ framework. Ideal for enterprise applications.',
    category: 'backend',
    techStack: [
      { name: 'Java', version: '21', icon: '☕' },
      { name: 'ActiveJ', version: '6.0', icon: '⚡' },
      { name: 'PostgreSQL', version: '16', icon: '🐘' },
    ],
    features: [
      'Virtual threads',
      'Async I/O',
      'DI container',
      'HTTP server',
    ],
    estimatedCost: { min: 15, max: 50, currency: 'USD' },
    estimatedTime: 6,
    difficulty: 'intermediate',
    popularity: 4.2,
  },
  {
    id: 'nextjs-prisma',
    name: 'Next.js + Prisma',
    description:
      'Full-stack React framework with type-safe database access. Modern and productive.',
    category: 'fullstack',
    techStack: [
      { name: 'Next.js', version: '15', icon: '▲' },
      { name: 'Prisma', version: '5.10', icon: '💎' },
      { name: 'PostgreSQL', version: '16', icon: '🐘' },
      { name: 'TypeScript', version: '5.4', icon: '📘' },
    ],
    features: [
      'Server components',
      'Server actions',
      'Type-safe ORM',
      'Edge runtime',
    ],
    estimatedCost: { min: 0, max: 20, currency: 'USD' },
    estimatedTime: 4,
    difficulty: 'beginner',
    popularity: 4.9,
    recommended: true,
  },
  {
    id: 'mobile-react-native',
    name: 'React Native',
    description:
      'Cross-platform mobile app development with React. One codebase for iOS and Android.',
    category: 'mobile',
    techStack: [
      { name: 'React Native', version: '0.73', icon: '📱' },
      { name: 'Expo', version: '50', icon: '📦' },
      { name: 'TypeScript', version: '5.4', icon: '📘' },
    ],
    features: [
      'Cross-platform',
      'OTA updates',
      'Native modules',
      'App Store deployment',
    ],
    estimatedCost: { min: 0, max: 30, currency: 'USD' },
    estimatedTime: 7,
    difficulty: 'intermediate',
    popularity: 4.5,
  },
  {
    id: 'ai-ml-python',
    name: 'AI/ML Python',
    description:
      'Machine learning and AI development stack with Python. Data science ready.',
    category: 'ai',
    techStack: [
      { name: 'Python', version: '3.12', icon: '🐍' },
      { name: 'FastAPI', version: '0.109', icon: '⚡' },
      { name: 'PyTorch', version: '2.2', icon: '🔥' },
      { name: 'MLflow', version: '2.10', icon: '📈' },
    ],
    features: [
      'Model training',
      'Experiment tracking',
      'Model serving',
      'GPU support',
    ],
    estimatedCost: { min: 20, max: 100, currency: 'USD' },
    estimatedTime: 8,
    difficulty: 'advanced',
    popularity: 4.6,
  },
  {
    id: 'data-pipeline',
    name: 'Data Pipeline',
    description:
      'ETL and data processing infrastructure. Stream and batch processing at scale.',
    category: 'data',
    techStack: [
      { name: 'Apache Kafka', version: '3.6', icon: '📨' },
      { name: 'Apache Spark', version: '3.5', icon: '✨' },
      { name: 'Airflow', version: '2.8', icon: '🌬️' },
    ],
    features: [
      'Stream processing',
      'Batch ETL',
      'Data lake',
      'Orchestration',
    ],
    estimatedCost: { min: 50, max: 200, currency: 'USD' },
    estimatedTime: 12,
    difficulty: 'advanced',
    popularity: 4.1,
  },
];

const CATEGORY_FILTERS: { value: PresetCategory | 'all'; label: string }[] = [
  { value: 'all', label: 'All Presets' },
  { value: 'fullstack', label: 'Full Stack' },
  { value: 'frontend', label: 'Frontend' },
  { value: 'backend', label: 'Backend' },
  { value: 'mobile', label: 'Mobile' },
  { value: 'ai', label: 'AI/ML' },
  { value: 'data', label: 'Data' },
];

// ============================================================================
// Main Page Component
// ============================================================================

export const InitializationPresetsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  const [filter, setFilter] = useState<PresetFilter>({
    category: 'all',
    searchQuery: '',
  });
  const [selectedPreset, setSelectedPreset] = useState<string | null>(null);

  // Filter presets
  const filteredPresets = useMemo(() => {
    return PRESETS.filter((preset) => {
      // Category filter
      if (filter.category !== 'all' && preset.category !== filter.category) {
        return false;
      }

      // Search filter
      if (filter.searchQuery) {
        const query = filter.searchQuery.toLowerCase();
        return (
          preset.name.toLowerCase().includes(query) ||
          preset.description.toLowerCase().includes(query) ||
          preset.techStack.some((tech) =>
            tech.name.toLowerCase().includes(query)
          )
        );
      }

      return true;
    });
  }, [filter]);

  // Sort presets (recommended first, then by popularity)
  const sortedPresets = useMemo(() => {
    return [...filteredPresets].sort((a, b) => {
      if (a.recommended && !b.recommended) return -1;
      if (!a.recommended && b.recommended) return 1;
      return (b.popularity || 0) - (a.popularity || 0);
    });
  }, [filteredPresets]);

  // Handle preset selection
  const handleSelect = useCallback(
    (presetId: string) => {
      setSelectedPreset(presetId);
      // Navigate to wizard with preset pre-configured
      navigate(`/projects/${projectId}/initialize?preset=${presetId}`);
    },
    [projectId, navigate]
  );

  // Handle custom configuration
  const handleCustom = useCallback(() => {
    navigate(`/projects/${projectId}/initialize`);
  }, [projectId, navigate]);

  return (
    <div className="initialization-presets-page">
      {/* Header */}
      <header className="presets-header">
        <div className="header-content">
          <h1 className="header-title">Choose a Configuration Preset</h1>
          <p className="header-subtitle">
            Get started quickly with a pre-configured tech stack, or customize
            everything from scratch.
          </p>
        </div>
        <button
          type="button"
          className="btn btn-secondary skip-btn"
          onClick={handleCustom}
        >
          Skip to Custom Configuration →
        </button>
      </header>

      {/* Filters */}
      <div className="presets-filters">
        <div className="filter-tabs">
          {CATEGORY_FILTERS.map((cat) => (
            <button
              key={cat.value}
              type="button"
              className={`filter-tab ${
                filter.category === cat.value ? 'filter-tab--active' : ''
              }`}
              onClick={() => setFilter({ ...filter, category: cat.value })}
            >
              {cat.label}
            </button>
          ))}
        </div>

        <div className="search-container">
          <svg
            className="search-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            type="text"
            className="search-input"
            placeholder="Search presets..."
            value={filter.searchQuery}
            onChange={(e) =>
              setFilter({ ...filter, searchQuery: e.target.value })
            }
          />
        </div>
      </div>

      {/* Presets Grid */}
      <div className="presets-grid">
        {sortedPresets.map((preset) => (
          <PresetCard
            key={preset.id}
            preset={preset}
            isSelected={selectedPreset === preset.id}
            onSelect={() => handleSelect(preset.id)}
            showDetails
          />
        ))}
      </div>

      {/* Empty State */}
      {sortedPresets.length === 0 && (
        <div className="presets-empty">
          <svg
            className="empty-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <circle cx="12" cy="12" r="10" />
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
          <h3>No presets found</h3>
          <p>Try adjusting your filters or search query</p>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setFilter({ category: 'all', searchQuery: '' })}
          >
            Clear Filters
          </button>
        </div>
      )}

      {/* Custom Configuration CTA */}
      <div className="custom-cta">
        <div className="cta-content">
          <div className="cta-icon">⚙️</div>
          <div className="cta-text">
            <h3>Need Something Different?</h3>
            <p>
              Create a custom configuration with your own tech stack and
              providers
            </p>
          </div>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={handleCustom}
        >
          Start Custom Configuration →
        </button>
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .initialization-presets-page {
          min-height: 100vh;
          padding: 2rem;
          background: #F3F4F6;
        }

        .presets-header {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
          gap: 2rem;
          margin-bottom: 2rem;
          padding: 2rem;
          background: linear-gradient(135deg, #1E40AF 0%, #3B82F6 100%);
          border-radius: 16px;
          color: #fff;
        }

        .header-content {
          flex: 1;
        }

        .header-title {
          margin: 0;
          font-size: 1.75rem;
          font-weight: 700;
        }

        .header-subtitle {
          margin: 0.5rem 0 0;
          font-size: 1rem;
          opacity: 0.9;
        }

        .skip-btn {
          flex-shrink: 0;
          color: #fff;
          background: rgba(255, 255, 255, 0.15);
          border: 1px solid rgba(255, 255, 255, 0.3);
        }

        .skip-btn:hover {
          background: rgba(255, 255, 255, 0.25);
        }

        .presets-filters {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 1rem;
          margin-bottom: 1.5rem;
          padding: 1rem;
          background: #fff;
          border-radius: 12px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        }

        .filter-tabs {
          display: flex;
          gap: 0.5rem;
          overflow-x: auto;
        }

        .filter-tab {
          padding: 0.5rem 1rem;
          font-size: 0.875rem;
          font-weight: 500;
          color: #6B7280;
          background: transparent;
          border: none;
          border-radius: 6px;
          cursor: pointer;
          white-space: nowrap;
          transition: all 0.15s ease;
        }

        .filter-tab:hover {
          color: #374151;
          background: #F3F4F6;
        }

        .filter-tab--active {
          color: #1E40AF;
          background: #EFF6FF;
        }

        .search-container {
          position: relative;
          min-width: 250px;
        }

        .search-icon {
          position: absolute;
          left: 0.75rem;
          top: 50%;
          transform: translateY(-50%);
          width: 18px;
          height: 18px;
          color: #9CA3AF;
        }

        .search-input {
          width: 100%;
          padding: 0.5rem 0.75rem 0.5rem 2.5rem;
          font-size: 0.875rem;
          border: 1px solid #D1D5DB;
          border-radius: 8px;
          transition: border-color 0.15s ease;
        }

        .search-input:focus {
          outline: none;
          border-color: #3B82F6;
          box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
        }

        .presets-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
          gap: 1.5rem;
          margin-bottom: 2rem;
        }

        .presets-empty {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 4rem 2rem;
          text-align: center;
        }

        .empty-icon {
          width: 64px;
          height: 64px;
          color: #D1D5DB;
          margin-bottom: 1rem;
        }

        .presets-empty h3 {
          margin: 0;
          font-size: 1.125rem;
          color: #374151;
        }

        .presets-empty p {
          margin: 0.5rem 0 1.5rem;
          color: #6B7280;
        }

        .custom-cta {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 2rem;
          padding: 1.5rem 2rem;
          background: #fff;
          border: 2px dashed #D1D5DB;
          border-radius: 16px;
        }

        .cta-content {
          display: flex;
          align-items: center;
          gap: 1rem;
        }

        .cta-icon {
          font-size: 2.5rem;
        }

        .cta-text h3 {
          margin: 0;
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
        }

        .cta-text p {
          margin: 0.25rem 0 0;
          font-size: 0.875rem;
          color: #6B7280;
        }

        .btn {
          padding: 0.625rem 1.25rem;
          font-size: 0.875rem;
          font-weight: 500;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .btn-primary {
          color: #fff;
          background: linear-gradient(135deg, #3B82F6 0%, #2563EB 100%);
          border: none;
        }

        .btn-primary:hover {
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
        }

        .btn-secondary {
          color: #374151;
          background: #fff;
          border: 1px solid #D1D5DB;
        }

        .btn-secondary:hover {
          background: #F9FAFB;
        }

        @media (max-width: 768px) {
          .presets-header {
            flex-direction: column;
          }

          .presets-filters {
            flex-direction: column;
            align-items: stretch;
          }

          .filter-tabs {
            justify-content: center;
          }

          .search-container {
            min-width: 100%;
          }

          .custom-cta {
            flex-direction: column;
            text-align: center;
          }

          .cta-content {
            flex-direction: column;
          }
        }
      `}</style>
    </div>
  );
};

InitializationPresetsPage.displayName = 'InitializationPresetsPage';

export default InitializationPresetsPage;
