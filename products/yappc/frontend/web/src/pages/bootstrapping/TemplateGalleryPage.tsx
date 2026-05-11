import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { useTranslation } from '@ghatana/i18n';

// ============================================================================
// Types
// ============================================================================

interface Template {
  id: string;
  name: string;
  description: string;
  category: string;
  language: string;
  framework: string;
  stars: number;
  tags: string[];
  icon: string;
}

type Category = 'all' | 'microservice' | 'webapp' | 'library' | 'cli' | 'data';

// ============================================================================
// Mock data
// ============================================================================

const TEMPLATES: Template[] = [
  { id: '1', name: 'ActiveJ Microservice', description: 'High-performance microservice with HTTP, DI, and observability', category: 'microservice', language: 'Java 21', framework: 'ActiveJ', stars: 342, tags: ['REST', 'gRPC', 'Docker'], icon: '⚡' },
  { id: '2', name: 'React Dashboard', description: 'Full-featured admin dashboard with Tailwind and TanStack', category: 'webapp', language: 'TypeScript', framework: 'React 19', stars: 528, tags: ['Vite', 'Jotai', 'Tailwind'], icon: '🎨' },
  { id: '3', name: 'Event Processor', description: 'Stream processing service with event sourcing and CQRS', category: 'microservice', language: 'Java 21', framework: 'ActiveJ', stars: 187, tags: ['Kafka', 'EventSourcing', 'CQRS'], icon: '📡' },
  { id: '4', name: 'Platform Library', description: 'Shared Java library with proper packaging and publishing', category: 'library', language: 'Java 21', framework: 'Gradle', stars: 95, tags: ['Maven Central', 'Javadoc'], icon: '📚' },
  { id: '5', name: 'CLI Tool', description: 'Picocli-based command-line application with GraalVM native', category: 'cli', language: 'Java 21', framework: 'Picocli', stars: 73, tags: ['GraalVM', 'Native'], icon: '🖥️' },
  { id: '6', name: 'Data Pipeline', description: 'ETL pipeline with schema validation and data quality checks', category: 'data', language: 'Python 3.12', framework: 'Polars', stars: 214, tags: ['ETL', 'Validation'], icon: '🔄' },
  { id: '7', name: 'GraphQL API', description: 'GraphQL server with schema-first design and DataLoaders', category: 'microservice', language: 'Java 21', framework: 'graphql-java', stars: 156, tags: ['GraphQL', 'DataLoader'], icon: '🔗' },
  { id: '8', name: 'Mobile App', description: 'React Native app with navigation, state, and offline support', category: 'webapp', language: 'TypeScript', framework: 'React Native', stars: 310, tags: ['Expo', 'Navigation'], icon: '📱' },
  { id: '9', name: 'ML Service', description: 'Machine learning model serving with REST and gRPC endpoints', category: 'data', language: 'Python 3.12', framework: 'FastAPI', stars: 267, tags: ['ML', 'ONNX', 'Docker'], icon: '🧠' },
];

const CATEGORIES: { key: Category; label: string; icon: string }[] = [
  { key: 'all', label: 'All', icon: '📋' },
  { key: 'microservice', label: 'Microservices', icon: '⚡' },
  { key: 'webapp', label: 'Web Apps', icon: '🌐' },
  { key: 'library', label: 'Libraries', icon: '📚' },
  { key: 'cli', label: 'CLI Tools', icon: '🖥️' },
  { key: 'data', label: 'Data & ML', icon: '📊' },
];

// ============================================================================
// Component
// ============================================================================

const TemplateGalleryPage: React.FC = () => {
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<Category>('all');
  const [sortBy, setSortBy] = useState<'stars' | 'name'>('stars');
  const { t } = useTranslation('common');

  const { data: templates } = useQuery<Template[]>({
    queryKey: ['template-gallery'],
    queryFn: () => Promise.resolve(TEMPLATES),
    staleTime: Infinity,
  });

  const filtered = useMemo(() => {
    let list = templates ?? [];
    if (category !== 'all') {
      list = list.filter((t) => t.category === category);
    }
    if (search) {
      const q = search.toLowerCase();
      list = list.filter(
        (t) =>
          t.name.toLowerCase().includes(q) ||
          t.description.toLowerCase().includes(q) ||
          t.tags.some((tag) => tag.toLowerCase().includes(q)),
      );
    }
    return [...list].sort((a, b) =>
      sortBy === 'stars' ? b.stars - a.stars : a.name.i18n.languageCompare(b.name),
    );
  }, [templates, category, search, sortBy]);

  return (
    <div className="min-h-screen bg-surface-muted p-6">
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-fg">Template Gallery</h1>
          <p className="mt-2 text-fg-muted">
            Choose a project template to bootstrap your application.
          </p>
        </div>

        {/* Search & Sort */}
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-muted">🔍</span>
            <Input
              type="text"
              placeholder={t('templateGallery.searchPlaceholder')}
              value={search}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearch(e.target.value)}
              fullWidth
              size="sm"
              className="rounded-lg border bg-white py-2 pl-10 pr-4 text-sm focus:border-info-border focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <Select
            value={sortBy}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              setSortBy(e.target.value as 'stars' | 'name')
            }
            className="rounded-lg border bg-white px-3 py-2 text-sm"
          >
            <option value="stars">Most Popular</option>
            <option value="name">Alphabetical</option>
          </Select>
        </div>

        {/* Category Chips */}
        <div className="mb-6 flex flex-wrap gap-2">
          {CATEGORIES.map((cat) => (
            <Button
              key={cat.key}
              variant="ghost"
              size="sm"
              aria-pressed={category === cat.key}
              onClick={() => setCategory(cat.key)}
              className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition ${
                category === cat.key
                  ? 'bg-primary text-white'
                  : 'bg-white text-fg hover:bg-surface-muted'
              }`}
            >
              <span>{cat.icon}</span>
              {cat.label}
            </Button>
          ))}
        </div>

        {/* Grid */}
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((template) => (
            <div
              key={template.id}
              className="group rounded-lg border bg-white p-5 shadow-sm transition hover:border-info-border hover:shadow-md"
            >
              <div className="mb-3 flex items-start justify-between">
                <span className="text-3xl">{template.icon}</span>
                <span className="flex items-center gap-1 text-sm text-fg-muted">
                  ⭐ {template.stars}
                </span>
              </div>
              <h3 className="mb-1 text-lg font-semibold text-fg">{template.name}</h3>
              <p className="mb-3 text-sm text-fg-muted">{template.description}</p>
              <div className="mb-4 flex items-center gap-2 text-xs text-fg-muted">
                <span className="rounded bg-surface-muted px-2 py-0.5">{template.language}</span>
                <span className="rounded bg-surface-muted px-2 py-0.5">{template.framework}</span>
              </div>
              <div className="mb-4 flex flex-wrap gap-1">
                {template.tags.map((tag) => (
                  <span
                    key={tag}
                    className="rounded-full bg-info-bg px-2 py-0.5 text-xs text-info-color"
                  >
                    {tag}
                  </span>
                ))}
              </div>
              <Button className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-white opacity-0 transition group-hover:opacity-100 hover:bg-info-bg">
                Use Template
              </Button>
            </div>
          ))}
        </div>

        {filtered.length === 0 && (
          <div className="py-16 text-center text-fg-muted">
            <p className="text-lg">No templates match your search.</p>
            <Button
              variant="ghost"
              onClick={() => { setSearch(''); setCategory('all'); }}
              className="mt-2 text-info-color hover:underline"
            >
              Clear filters
            </Button>
          </div>
        )}
      </div>
    </div>
  );
};

export default TemplateGalleryPage;
