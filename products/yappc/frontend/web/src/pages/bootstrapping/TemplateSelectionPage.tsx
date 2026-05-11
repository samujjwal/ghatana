// @ts-nocheck
/**
 * Template Selection Page
 *
 * @description Allows users to browse and select from predefined project templates
 * with different tech stacks and configurations.
 *
 * @doc.type page
 * @doc.purpose Template browsing and selection
 * @doc.layer page
 * @doc.phase bootstrapping
 */

import React, { useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search,
  ArrowRight,
  ArrowLeft,
  CheckCircle2,
  Clock,
  Star,
  Layers,
  Zap,
  Globe,
  Smartphone,
  Server,
  Code2,
  Palette,
  ShoppingCart,
  MessageSquare,
  BarChart3,
  FileCode2,
  Sparkles,
  Users,
  Rocket,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { Button } from '@ghatana/design-system';
import { Input } from '@ghatana/design-system';

import { selectedTemplateAtom } from '../../state/atoms';
import { ROUTES } from '../../router/paths';
import { useTranslation } from '@ghatana/i18n';

// =============================================================================
// Types
// =============================================================================

interface ProjectTemplate {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  icon: React.ElementType;
  techStack: string[];
  estimatedTime: string;
  popularity: number;
  features: string[];
  complexity: 'beginner' | 'intermediate' | 'advanced';
  preview?: string;
}

type TemplateCategory =
  | 'web-app'
  | 'mobile'
  | 'api'
  | 'e-commerce'
  | 'dashboard'
  | 'saas'
  | 'ai-ml'
  | 'social';

// =============================================================================
// Template Data
// =============================================================================

const CATEGORIES: { id: TemplateCategory; label: string; icon: React.ElementType }[] = [
  { id: 'web-app', label: 'Web Apps', icon: Globe },
  { id: 'mobile', label: 'Mobile', icon: Smartphone },
  { id: 'api', label: 'APIs', icon: Server },
  { id: 'e-commerce', label: 'E-Commerce', icon: ShoppingCart },
  { id: 'dashboard', label: 'Dashboards', icon: BarChart3 },
  { id: 'saas', label: 'SaaS', icon: Layers },
  { id: 'ai-ml', label: 'AI/ML', icon: Sparkles },
  { id: 'social', label: 'Social', icon: MessageSquare },
];

const TEMPLATES: ProjectTemplate[] = [
  {
    id: 'next-saas-starter',
    name: 'Next.js SaaS Starter',
    description:
      'Full-featured SaaS boilerplate with authentication, subscriptions, and dashboard.',
    category: 'saas',
    icon: Rocket,
    techStack: ['Next.js', 'TypeScript', 'Prisma', 'Stripe', 'Tailwind'],
    estimatedTime: '2-4 weeks',
    popularity: 98,
    complexity: 'intermediate',
    features: [
      'User authentication',
      'Subscription billing',
      'Admin dashboard',
      'Email notifications',
    ],
  },
  {
    id: 'react-dashboard',
    name: 'React Admin Dashboard',
    description:
      'Comprehensive admin dashboard with charts, tables, and data management.',
    category: 'dashboard',
    icon: BarChart3,
    techStack: ['React', 'TypeScript', 'Recharts', 'TanStack Query', 'Tailwind'],
    estimatedTime: '1-2 weeks',
    popularity: 95,
    complexity: 'beginner',
    features: [
      'Interactive charts',
      'Data tables',
      'Dark mode',
      'Responsive layout',
    ],
  },
  {
    id: 'ecommerce-platform',
    name: 'E-Commerce Platform',
    description:
      'Full e-commerce solution with product catalog, cart, and checkout flow.',
    category: 'e-commerce',
    icon: ShoppingCart,
    techStack: ['Next.js', 'TypeScript', 'Shopify API', 'Stripe', 'Tailwind'],
    estimatedTime: '3-5 weeks',
    popularity: 92,
    complexity: 'advanced',
    features: [
      'Product catalog',
      'Shopping cart',
      'Checkout flow',
      'Order management',
    ],
  },
  {
    id: 'rest-api',
    name: 'REST API Starter',
    description:
      'Deployment-oriented REST API starter with authentication, validation, and documentation.',
    category: 'api',
    icon: Server,
    techStack: ['Node.js', 'Express', 'TypeScript', 'Prisma', 'PostgreSQL'],
    estimatedTime: '1-2 weeks',
    popularity: 90,
    complexity: 'beginner',
    features: [
      'JWT authentication',
      'Input validation',
      'OpenAPI docs',
      'Rate limiting',
    ],
  },
  {
    id: 'mobile-expo',
    name: 'Expo Mobile App',
    description:
      'Cross-platform mobile app with navigation, state management, and UI kit.',
    category: 'mobile',
    icon: Smartphone,
    techStack: ['React Native', 'Expo', 'TypeScript', 'Zustand', 'NativeWind'],
    estimatedTime: '2-3 weeks',
    popularity: 88,
    complexity: 'intermediate',
    features: [
      'Cross-platform',
      'Push notifications',
      'Offline support',
      'Biometric auth',
    ],
  },
  {
    id: 'ai-chat-app',
    name: 'AI Chat Application',
    description:
      'GPT-powered chat application with streaming, context memory, and plugins.',
    category: 'ai-ml',
    icon: Sparkles,
    techStack: ['Next.js', 'OpenAI', 'LangChain', 'TypeScript', 'Vercel AI SDK'],
    estimatedTime: '2-3 weeks',
    popularity: 96,
    complexity: 'intermediate',
    features: [
      'Streaming responses',
      'Context memory',
      'Plugin system',
      'Multi-model support',
    ],
  },
  {
    id: 'social-platform',
    name: 'Social Network Starter',
    description:
      'Social platform with profiles, feeds, messaging, and real-time updates.',
    category: 'social',
    icon: Users,
    techStack: ['Next.js', 'TypeScript', 'Socket.io', 'Prisma', 'Redis'],
    estimatedTime: '4-6 weeks',
    popularity: 85,
    complexity: 'advanced',
    features: [
      'User profiles',
      'News feed',
      'Real-time chat',
      'Notifications',
    ],
  },
  {
    id: 'portfolio-site',
    name: 'Portfolio Website',
    description:
      'Modern portfolio site with animations, blog, and contact form.',
    category: 'web-app',
    icon: Palette,
    techStack: ['Next.js', 'TypeScript', 'Framer Motion', 'MDX', 'Tailwind'],
    estimatedTime: '3-5 days',
    popularity: 82,
    complexity: 'beginner',
    features: [
      'Animations',
      'Blog system',
      'Contact form',
      'SEO optimized',
    ],
  },
];

const COMPLEXITY_COLORS: Record<ProjectTemplate['complexity'], string> = {
  beginner: 'bg-success-500/10 text-success-400 border-success-500/30',
  intermediate: 'bg-warning-500/10 text-warning-400 border-warning-500/30',
  advanced: 'bg-error-500/10 text-error-400 border-error-500/30',
};

// =============================================================================
// Template Card Component
// =============================================================================

interface TemplateCardProps {
  template: ProjectTemplate;
  isSelected: boolean;
  onSelect: (template: ProjectTemplate) => void;
}

const TemplateCard: React.FC<TemplateCardProps> = ({
  template,
  isSelected,
  onSelect,
}) => {
  const Icon = template.icon;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      whileHover={{ y: -4 }}
      onClick={() => onSelect(template)}
      className={cn(
        'group relative cursor-pointer rounded-xl border p-5 transition-all duration-200',
        isSelected
          ? 'border-primary-500 bg-primary-500/10 ring-1 ring-primary-500'
          : 'border-border bg-surface/50 hover:border-border hover:bg-surface'
      )}
    >
      {/* Selection Indicator */}
      {isSelected && (
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          className="absolute right-3 top-3"
        >
          <CheckCircle2 className="h-5 w-5 text-primary-400" />
        </motion.div>
      )}

      {/* Header */}
      <div className="flex items-start gap-4">
        <div
          className={cn(
            'flex h-12 w-12 items-center justify-center rounded-lg',
            isSelected ? 'bg-primary-500/20' : 'bg-surface group-hover:bg-surface-muted'
          )}
        >
          <Icon
            className={cn(
              'h-6 w-6',
              isSelected ? 'text-primary-400' : 'text-fg-muted'
            )}
          />
        </div>
        <div className="flex-1 overflow-hidden">
          <h3 className="font-semibold text-fg-muted">{template.name}</h3>
          <p className="mt-1 line-clamp-2 text-sm text-fg-muted">
            {template.description}
          </p>
        </div>
      </div>

      {/* Tech Stack */}
      <div className="mt-4 flex flex-wrap gap-1.5">
        {template.techStack.slice(0, 4).map((tech) => (
          <span
            key={tech}
            className="rounded-md border border-border bg-surface/50 px-2 py-0.5 text-xs text-fg-muted"
          >
            {tech}
          </span>
        ))}
        {template.techStack.length > 4 && (
          <span className="rounded-md border border-border bg-surface/50 px-2 py-0.5 text-xs text-fg-muted">
            +{template.techStack.length - 4}
          </span>
        )}
      </div>

      {/* Footer */}
      <div className="mt-4 flex items-center justify-between border-t border-border pt-4">
        <div className="flex items-center gap-3 text-xs text-fg-muted">
          <span className="flex items-center gap-1">
            <Clock className="h-3 w-3" />
            {template.estimatedTime}
          </span>
          <span className="flex items-center gap-1">
            <Star className="h-3 w-3 fill-current text-warning-color" />
            {template.popularity}%
          </span>
        </div>
        <span
          className={cn(
            'rounded-md border px-2 py-0.5 text-xs capitalize',
            COMPLEXITY_COLORS[template.complexity]
          )}
        >
          {template.complexity}
        </span>
      </div>
    </motion.div>
  );
};

// =============================================================================
// Template Detail Panel
// =============================================================================

interface TemplateDetailProps {
  template: ProjectTemplate;
  onUse: () => void;
}

const TemplateDetail: React.FC<TemplateDetailProps> = ({ template, onUse }) => {
  const Icon = template.icon;

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 20 }}
      className="rounded-xl border border-border bg-surface/50 p-6"
    >
      {/* Header */}
      <div className="flex items-start gap-4">
        <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-primary-500/10">
          <Icon className="h-7 w-7 text-primary-400" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-fg-muted">{template.name}</h2>
          <p className="mt-1 text-sm text-fg-muted">{template.description}</p>
        </div>
      </div>

      {/* Stats */}
      <div className="mt-6 grid grid-cols-3 gap-4">
        <div className="rounded-lg bg-surface/50 p-3 text-center">
          <Clock className="mx-auto h-5 w-5 text-fg-muted" />
          <p className="mt-1 text-sm font-medium text-fg-muted">
            {template.estimatedTime}
          </p>
          <p className="text-xs text-fg-muted">Est. Time</p>
        </div>
        <div className="rounded-lg bg-surface/50 p-3 text-center">
          <Star className="mx-auto h-5 w-5 text-warning-color" />
          <p className="mt-1 text-sm font-medium text-fg-muted">
            {template.popularity}%
          </p>
          <p className="text-xs text-fg-muted">Popularity</p>
        </div>
        <div className="rounded-lg bg-surface/50 p-3 text-center">
          <Zap className="mx-auto h-5 w-5 text-fg-muted" />
          <p className="mt-1 text-sm font-medium capitalize text-fg-muted">
            {template.complexity}
          </p>
          <p className="text-xs text-fg-muted">Complexity</p>
        </div>
      </div>

      {/* Tech Stack */}
      <div className="mt-6">
        <h3 className="mb-3 text-sm font-medium text-fg-muted">Tech Stack</h3>
        <div className="flex flex-wrap gap-2">
          {template.techStack.map((tech) => (
            <span
              key={tech}
              className="inline-flex items-center gap-1.5 rounded-md border border-border bg-surface/50 px-2.5 py-1 text-sm text-fg-muted"
            >
              <Code2 className="h-3 w-3" />
              {tech}
            </span>
          ))}
        </div>
      </div>

      {/* Features */}
      <div className="mt-6">
        <h3 className="mb-3 text-sm font-medium text-fg-muted">
          Included Features
        </h3>
        <div className="space-y-2">
          {template.features.map((feature) => (
            <div key={feature} className="flex items-center gap-2 text-sm">
              <CheckCircle2 className="h-4 w-4 text-success-400" />
              <span className="text-fg-muted">{feature}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Actions */}
      <div className="mt-6 flex gap-3">
        <Button onClick={onUse} className="flex-1">
          Use This Template
          <ArrowRight className="ml-2 h-4 w-4" />
        </Button>
      </div>
    </motion.div>
  );
};

// =============================================================================
// Main Page Component
// =============================================================================

const TemplateSelectionPage: React.FC = () => {
  const navigate = useNavigate();
  const setSelectedTemplate = useSetAtom(selectedTemplateAtom);
  const { t } = useTranslation('common');

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<TemplateCategory | null>(
    null
  );
  const [selectedTemplate, setSelectedTemplateLocal] =
    useState<ProjectTemplate | null>(null);

  // Filter templates
  const filteredTemplates = useMemo(() => {
    return TEMPLATES.filter((template) => {
      // Category filter
      if (selectedCategory && template.category !== selectedCategory) {
        return false;
      }
      // Search filter
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return (
          template.name.toLowerCase().includes(query) ||
          template.description.toLowerCase().includes(query) ||
          template.techStack.some((tech) => tech.toLowerCase().includes(query))
        );
      }
      return true;
    });
  }, [searchQuery, selectedCategory]);

  // Handle template selection
  const handleSelectTemplate = useCallback((template: ProjectTemplate) => {
    setSelectedTemplateLocal(template);
  }, []);

  // Handle use template
  const handleUseTemplate = useCallback(() => {
    if (!selectedTemplate) return;

    // Set template info for state (without icon as it's not serializable)
    setSelectedTemplate({
      id: selectedTemplate.id,
      name: selectedTemplate.name,
      description: selectedTemplate.description,
      category: selectedTemplate.category,
      techStack: selectedTemplate.techStack,
      estimatedTime: selectedTemplate.estimatedTime,
      popularity: selectedTemplate.popularity,
      features: selectedTemplate.features,
      complexity: selectedTemplate.complexity,
    } as unknown);

    navigate(ROUTES.TEMPLATES, {
      state: { templateId: selectedTemplate.id },
    });
  }, [selectedTemplate, setSelectedTemplate, navigate]);

  return (
    <div className="min-h-screen bg-surface">
      <div className="flex h-screen">
        {/* Main Content */}
        <div className="flex-1 overflow-auto p-6">
          {/* Header */}
          <div className="mb-6">
            <Button
              variant="ghost"
              onClick={() => navigate(-1)}
              className="mb-4 gap-2"
            >
              <ArrowLeft className="h-4 w-4" />
              Back
            </Button>
            <h1 className="text-2xl font-bold text-fg-muted">
              Choose a Template
            </h1>
            <p className="mt-1 text-sm text-fg-muted">
              Start with a pre-configured template to accelerate your development.
            </p>
          </div>

          {/* Search & Filters */}
          <div className="mb-6 flex flex-col gap-4 sm:flex-row">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-muted" />
              <Input
                placeholder={t('templateSelection.searchPlaceholder')}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-10"
              />
            </div>
          </div>

          {/* Category Pills */}
          <div className="mb-6 flex flex-wrap gap-2">
            <Button
              variant={selectedCategory === null ? 'solid' : 'outline'}
              size="sm"
              onClick={() => setSelectedCategory(null)}
            >
              All
            </Button>
            {CATEGORIES.map((cat) => {
              const Icon = cat.icon;
              return (
                <Button
                  key={cat.id}
                  variant={selectedCategory === cat.id ? 'solid' : 'outline'}
                  size="sm"
                  onClick={() => setSelectedCategory(cat.id)}
                  className="gap-1.5"
                >
                  <Icon className="h-3.5 w-3.5" />
                  {cat.label}
                </Button>
              );
            })}
          </div>

          {/* Templates Grid */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3">
            <AnimatePresence mode="popLayout">
              {filteredTemplates.map((template) => (
                <TemplateCard
                  key={template.id}
                  template={template}
                  isSelected={selectedTemplate?.id === template.id}
                  onSelect={handleSelectTemplate}
                />
              ))}
            </AnimatePresence>
          </div>

          {/* Empty State */}
          {filteredTemplates.length === 0 && (
            <div className="py-12 text-center">
              <FileCode2 className="mx-auto h-12 w-12 text-fg" />
              <h3 className="mt-4 text-lg font-medium text-fg-muted">
                No templates found
              </h3>
              <p className="mt-1 text-sm text-fg-muted">
                Try adjusting your search or filter criteria.
              </p>
              <Button
                variant="outline"
                className="mt-4"
                onClick={() => {
                  setSearchQuery('');
                  setSelectedCategory(null);
                }}
              >
                Clear filters
              </Button>
            </div>
          )}
        </div>

        {/* Detail Panel */}
        <div className="hidden w-96 border-l border-border bg-surface/30 p-6 lg:block">
          <AnimatePresence mode="wait">
            {selectedTemplate ? (
              <TemplateDetail
                key={selectedTemplate.id}
                template={selectedTemplate}
                onUse={handleUseTemplate}
              />
            ) : (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="flex h-full flex-col items-center justify-center text-center"
              >
                <div className="rounded-full bg-surface p-4">
                  <Layers className="h-8 w-8 text-fg-muted" />
                </div>
                <h3 className="mt-4 text-lg font-medium text-fg-muted">
                  Select a template
                </h3>
                <p className="mt-1 text-sm text-fg-muted">
                  Click on a template to see details and get started.
                </p>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
};

export default TemplateSelectionPage;
