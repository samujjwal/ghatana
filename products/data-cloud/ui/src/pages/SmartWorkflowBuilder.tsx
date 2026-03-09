/**
 * Smart Workflow Builder Page
 *
 * Intent-based workflow creation with progressive disclosure.
 * Users describe what they want in natural language, AI generates the pipeline.
 *
 * Features:
 * - Natural language pipeline description
 * - AI-generated pipeline steps with confidence scores
 * - Simple list-based editing for basic workflows
 * - "Advanced mode" toggle to expand to full canvas
 *
 * @doc.type page
 * @doc.purpose Intent-based workflow builder
 * @doc.layer frontend
 */

import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router';
import {
  Sparkles,
  Play,
  Save,
  ArrowRight,
  ArrowLeft,
  ChevronRight,
  ChevronDown,
  Plus,
  Trash2,
  Edit2,
  RefreshCw,
  Database,
  Filter,
  Wand2,
  Upload,
  Download,
  Code,
  Settings,
  CheckCircle,
  AlertCircle,
  Loader2,
  Zap,
  LayoutGrid,
} from 'lucide-react';
import { cn } from '../lib/theme';
import { CommandBar, CommandBarTrigger, AmbientIntelligenceBar } from '../components/core';

/**
 * Pipeline step types
 */
type StepType = 'source' | 'transform' | 'destination' | 'condition';

/**
 * Pipeline step interface
 */
interface PipelineStep {
  id: string;
  type: StepType;
  name: string;
  description: string;
  aiGenerated: boolean;
  confidence: number;
  config?: Record<string, unknown>;
  status?: 'pending' | 'valid' | 'error';
  errorMessage?: string;
}

/**
 * Generated workflow
 */
interface GeneratedWorkflow {
  id: string;
  name: string;
  description: string;
  steps: PipelineStep[];
  aiConfidence: number;
}

/**
 * Step type configuration
 */
const STEP_TYPE_CONFIG: Record<
  StepType,
  { icon: React.ReactNode; color: string; label: string }
> = {
  source: {
    icon: <Upload className="h-4 w-4" />,
    color: 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400',
    label: 'Source',
  },
  transform: {
    icon: <Wand2 className="h-4 w-4" />,
    color: 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400',
    label: 'Transform',
  },
  destination: {
    icon: <Download className="h-4 w-4" />,
    color: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
    label: 'Destination',
  },
  condition: {
    icon: <Filter className="h-4 w-4" />,
    color: 'bg-amber-100 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400',
    label: 'Condition',
  },
};

/**
 * Example prompts for inspiration
 */
const EXAMPLE_PROMPTS = [
  'Load data from S3, clean email addresses, save to PostgreSQL',
  'Read customer events, aggregate by day, write to analytics table',
  'Ingest JSON files, validate schema, transform to parquet, store in data lake',
  'Filter orders by status, join with customers, export to CSV',
];

/**
 * Pipeline templates
 */
interface PipelineTemplate {
  id: string;
  name: string;
  description: string;
  category: 'etl' | 'analytics' | 'ml' | 'sync';
  prompt: string;
  popularity: number;
}

const PIPELINE_TEMPLATES: PipelineTemplate[] = [
  {
    id: 'etl-s3-postgres',
    name: 'S3 to PostgreSQL ETL',
    description: 'Extract from S3, transform, load to PostgreSQL',
    category: 'etl',
    prompt: 'Load CSV data from S3 bucket, clean and validate records, remove duplicates, save to PostgreSQL',
    popularity: 95,
  },
  {
    id: 'daily-aggregation',
    name: 'Daily Aggregation',
    description: 'Aggregate events by day for analytics',
    category: 'analytics',
    prompt: 'Read events from kafka, aggregate by day and user, calculate metrics, write to analytics warehouse',
    popularity: 88,
  },
  {
    id: 'data-sync',
    name: 'Database Sync',
    description: 'Sync data between two databases',
    category: 'sync',
    prompt: 'Read changed records from source MySQL, compare with target, upsert changes to destination PostgreSQL',
    popularity: 76,
  },
  {
    id: 'ml-feature',
    name: 'ML Feature Pipeline',
    description: 'Prepare features for machine learning',
    category: 'ml',
    prompt: 'Load user data, compute engagement features, normalize values, write to feature store',
    popularity: 72,
  },
];

const CATEGORY_COLORS: Record<string, string> = {
  etl: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  analytics: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  ml: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  sync: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
};

/**
 * Template Card Component
 */
function TemplateCard({
  template,
  onSelect,
}: {
  template: PipelineTemplate;
  onSelect: () => void;
}) {
  return (
    <button
      onClick={onSelect}
      className={cn(
        'flex flex-col items-start gap-2 p-4 text-left',
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        'rounded-xl',
        'hover:border-primary-300 dark:hover:border-primary-700',
        'hover:shadow-md',
        'transition-all duration-200',
        'group'
      )}
    >
      <div className="flex items-center justify-between w-full">
        <span className={cn('text-xs px-2 py-0.5 rounded', CATEGORY_COLORS[template.category])}>
          {template.category.toUpperCase()}
        </span>
        <span className="text-xs text-gray-400">{template.popularity}% popular</span>
      </div>
      <h3 className="font-medium text-gray-900 dark:text-gray-100 group-hover:text-primary-600 dark:group-hover:text-primary-400">
        {template.name}
      </h3>
      <p className="text-sm text-gray-500 line-clamp-2">
        {template.description}
      </p>
      <div className="flex items-center gap-1 mt-auto pt-2 text-xs text-primary-600 dark:text-primary-400 opacity-0 group-hover:opacity-100 transition-opacity">
        <Zap className="h-3 w-3" />
        Use template
      </div>
    </button>
  );
}

/**
 * Pipeline Step Card Component
 */
function StepCard({
  step,
  index,
  onEdit,
  onDelete,
  onMoveUp,
  onMoveDown,
  isFirst,
  isLast,
}: {
  step: PipelineStep;
  index: number;
  onEdit: () => void;
  onDelete: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
  isFirst: boolean;
  isLast: boolean;
}) {
  const [isExpanded, setIsExpanded] = useState(false);
  const config = STEP_TYPE_CONFIG[step.type];

  return (
    <div className="relative">
      {/* Connection line */}
      {!isLast && (
        <div className="absolute left-6 top-full w-0.5 h-4 bg-gray-200 dark:bg-gray-700" />
      )}

      <div
        className={cn(
          'bg-white dark:bg-gray-800',
          'border border-gray-200 dark:border-gray-700',
          'rounded-xl overflow-hidden',
          step.status === 'error' && 'border-red-300 dark:border-red-700'
        )}
      >
        {/* Header */}
        <div
          className={cn(
            'flex items-center gap-3 px-4 py-3 cursor-pointer',
            'hover:bg-gray-50 dark:hover:bg-gray-700/50',
            'transition-colors'
          )}
          onClick={() => setIsExpanded(!isExpanded)}
        >
          {/* Step number */}
          <div className="flex items-center justify-center w-6 h-6 rounded-full bg-gray-100 dark:bg-gray-700 text-xs font-medium text-gray-600 dark:text-gray-400">
            {index + 1}
          </div>

          {/* Type icon */}
          <div className={cn('p-2 rounded-lg', config.color)}>
            {config.icon}
          </div>

          {/* Name & Description */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="font-medium text-gray-900 dark:text-gray-100">
                {step.name}
              </span>
              {step.aiGenerated && (
                <span className="inline-flex items-center gap-1 px-1.5 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-xs rounded">
                  <Sparkles className="h-3 w-3" />
                  AI
                </span>
              )}
              <span className="text-xs px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-500 rounded">
                {config.label}
              </span>
            </div>
            <p className="text-sm text-gray-500 truncate">{step.description}</p>
          </div>

          {/* Confidence */}
          {step.aiGenerated && (
            <div className="flex items-center gap-1">
              <div
                className={cn(
                  'w-2 h-2 rounded-full',
                  step.confidence >= 0.8
                    ? 'bg-green-500'
                    : step.confidence >= 0.6
                      ? 'bg-amber-500'
                      : 'bg-red-500'
                )}
              />
              <span className="text-xs text-gray-400">
                {Math.round(step.confidence * 100)}%
              </span>
            </div>
          )}

          {/* Status */}
          {step.status === 'valid' && (
            <CheckCircle className="h-5 w-5 text-green-500" />
          )}
          {step.status === 'error' && (
            <AlertCircle className="h-5 w-5 text-red-500" />
          )}

          {/* Expand */}
          {isExpanded ? (
            <ChevronDown className="h-5 w-5 text-gray-400" />
          ) : (
            <ChevronRight className="h-5 w-5 text-gray-400" />
          )}
        </div>

        {/* Expanded Content */}
        {isExpanded && (
          <div className="px-4 py-3 border-t border-gray-100 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
            {step.errorMessage && (
              <div className="mb-3 p-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded text-sm text-red-600 dark:text-red-400">
                {step.errorMessage}
              </div>
            )}

            {/* Config placeholder */}
            <div className="text-sm text-gray-500 mb-3">
              Configuration details would appear here...
            </div>

            {/* Actions */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onMoveUp();
                  }}
                  disabled={isFirst}
                  className={cn(
                    'p-1.5 rounded hover:bg-gray-200 dark:hover:bg-gray-700',
                    isFirst && 'opacity-30 cursor-not-allowed'
                  )}
                >
                  <ArrowLeft className="h-4 w-4 text-gray-400 rotate-90" />
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onMoveDown();
                  }}
                  disabled={isLast}
                  className={cn(
                    'p-1.5 rounded hover:bg-gray-200 dark:hover:bg-gray-700',
                    isLast && 'opacity-30 cursor-not-allowed'
                  )}
                >
                  <ArrowRight className="h-4 w-4 text-gray-400 rotate-90" />
                </button>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onEdit();
                  }}
                  className="flex items-center gap-1 px-2 py-1 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                >
                  <Edit2 className="h-3 w-3" />
                  Edit
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete();
                  }}
                  className="flex items-center gap-1 px-2 py-1 text-sm text-red-600 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-900/30 rounded"
                >
                  <Trash2 className="h-3 w-3" />
                  Remove
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Smart Workflow Builder Page
 */
export function SmartWorkflowBuilder() {
  const navigate = useNavigate();
  const [prompt, setPrompt] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [workflow, setWorkflow] = useState<GeneratedWorkflow | null>(null);
  const [advancedMode, setAdvancedMode] = useState(false);

  // Generate workflow from prompt
  const handleGenerate = useCallback(async () => {
    if (!prompt.trim()) return;

    setIsGenerating(true);

    // Simulate AI generation
    await new Promise((resolve) => setTimeout(resolve, 1500));

    // Mock generated workflow
    const generatedWorkflow: GeneratedWorkflow = {
      id: Date.now().toString(),
      name: 'Generated Pipeline',
      description: prompt,
      aiConfidence: 0.87,
      steps: [
        {
          id: '1',
          type: 'source',
          name: 'Load from S3',
          description: 'Read data from S3 bucket',
          aiGenerated: true,
          confidence: 0.92,
          status: 'valid',
        },
        {
          id: '2',
          type: 'transform',
          name: 'Clean & Validate',
          description: 'Clean email addresses and validate format',
          aiGenerated: true,
          confidence: 0.85,
          status: 'valid',
        },
        {
          id: '3',
          type: 'transform',
          name: 'Filter Invalid',
          description: 'Remove records with invalid data',
          aiGenerated: true,
          confidence: 0.78,
          status: 'pending',
        },
        {
          id: '4',
          type: 'destination',
          name: 'Save to PostgreSQL',
          description: 'Write cleaned data to PostgreSQL database',
          aiGenerated: true,
          confidence: 0.91,
          status: 'valid',
        },
      ],
    };

    setWorkflow(generatedWorkflow);
    setIsGenerating(false);
  }, [prompt]);

  // Delete step
  const handleDeleteStep = useCallback(
    (stepId: string) => {
      if (!workflow) return;
      setWorkflow({
        ...workflow,
        steps: workflow.steps.filter((s) => s.id !== stepId),
      });
    },
    [workflow]
  );

  // Move step up
  const handleMoveUp = useCallback(
    (index: number) => {
      if (!workflow || index === 0) return;
      const newSteps = [...workflow.steps];
      [newSteps[index - 1], newSteps[index]] = [newSteps[index], newSteps[index - 1]];
      setWorkflow({ ...workflow, steps: newSteps });
    },
    [workflow]
  );

  // Move step down
  const handleMoveDown = useCallback(
    (index: number) => {
      if (!workflow || index === workflow.steps.length - 1) return;
      const newSteps = [...workflow.steps];
      [newSteps[index], newSteps[index + 1]] = [newSteps[index + 1], newSteps[index]];
      setWorkflow({ ...workflow, steps: newSteps });
    },
    [workflow]
  );

  // Add new step
  const handleAddStep = useCallback(() => {
    if (!workflow) return;
    const newStep: PipelineStep = {
      id: Date.now().toString(),
      type: 'transform',
      name: 'New Step',
      description: 'Configure this step',
      aiGenerated: false,
      confidence: 1,
      status: 'pending',
    };
    setWorkflow({
      ...workflow,
      steps: [...workflow.steps, newStep],
    });
  }, [workflow]);

  // Deploy workflow
  const handleDeploy = useCallback(() => {
    // In real app, would save and deploy
    alert('Workflow deployed successfully!');
    navigate('/pipelines');
  }, [navigate]);

  // Switch to advanced mode (full canvas)
  const handleAdvancedMode = useCallback(() => {
    if (workflow) {
      // Navigate to full workflow designer with current workflow
      navigate(`/workflows/${workflow.id}?mode=advanced`);
    }
  }, [workflow, navigate]);

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <header className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              Smart Workflow Builder
            </h1>
            <p className="text-sm text-gray-500 mt-0.5">
              Describe your pipeline in natural language, AI will build it
            </p>
          </div>
          <div className="flex items-center gap-3">
            <CommandBarTrigger />
            {workflow && (
              <>
                <button
                  onClick={handleAdvancedMode}
                  className={cn(
                    'flex items-center gap-2 px-3 py-2 rounded-lg',
                    'border border-gray-200 dark:border-gray-700',
                    'text-sm text-gray-600 dark:text-gray-400',
                    'hover:bg-gray-100 dark:hover:bg-gray-800',
                    'transition-colors'
                  )}
                >
                  <LayoutGrid className="h-4 w-4" />
                  Advanced Mode
                </button>
                <button
                  onClick={handleDeploy}
                  className={cn(
                    'flex items-center gap-2 px-4 py-2 rounded-lg',
                    'bg-primary-600 hover:bg-primary-700',
                    'text-white text-sm font-medium',
                    'transition-colors'
                  )}
                >
                  <Play className="h-4 w-4" />
                  Deploy
                </button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 overflow-y-auto p-6">
        {/* Input Section */}
        <div className="max-w-3xl mx-auto mb-8">
          <div
            className={cn(
              'bg-gradient-to-br from-purple-50 to-pink-50',
              'dark:from-purple-900/20 dark:to-pink-900/20',
              'border border-purple-200 dark:border-purple-800',
              'rounded-2xl p-6'
            )}
          >
            <div className="flex items-center gap-2 mb-4">
              <Sparkles className="h-5 w-5 text-purple-500" />
              <span className="text-sm font-medium text-purple-700 dark:text-purple-300">
                Describe your pipeline
              </span>
            </div>

            <div className="relative">
              <textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="e.g., Load data from S3, clean email addresses, save to PostgreSQL"
                rows={3}
                className={cn(
                  'w-full px-4 py-3 rounded-xl',
                  'bg-white dark:bg-gray-800',
                  'border border-purple-200 dark:border-purple-700',
                  'text-gray-900 dark:text-gray-100',
                  'placeholder-gray-400',
                  'focus:ring-2 focus:ring-purple-500 focus:border-transparent',
                  'outline-none resize-none'
                )}
              />
              <button
                onClick={handleGenerate}
                disabled={!prompt.trim() || isGenerating}
                className={cn(
                  'absolute right-3 bottom-3',
                  'flex items-center gap-2 px-4 py-2 rounded-lg',
                  'bg-gradient-to-r from-purple-600 to-pink-600',
                  'hover:from-purple-700 hover:to-pink-700',
                  'text-white text-sm font-medium',
                  'transition-colors',
                  'disabled:opacity-50 disabled:cursor-not-allowed'
                )}
              >
                {isGenerating ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Generating...
                  </>
                ) : (
                  <>
                    <Zap className="h-4 w-4" />
                    Generate Pipeline
                  </>
                )}
              </button>
            </div>

            {/* Example prompts */}
            <div className="mt-4">
              <p className="text-xs text-gray-500 mb-2">Try an example:</p>
              <div className="flex flex-wrap gap-2">
                {EXAMPLE_PROMPTS.map((example, index) => (
                  <button
                    key={index}
                    onClick={() => setPrompt(example)}
                    className={cn(
                      'px-3 py-1.5 rounded-full text-xs',
                      'bg-white dark:bg-gray-800',
                      'border border-gray-200 dark:border-gray-700',
                      'text-gray-600 dark:text-gray-400',
                      'hover:border-purple-300 dark:hover:border-purple-700',
                      'transition-colors'
                    )}
                  >
                    {example.length > 50 ? example.slice(0, 50) + '...' : example}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Generated Pipeline */}
        {workflow && (
          <div className="max-w-3xl mx-auto">
            {/* Pipeline Header */}
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  Generated Pipeline
                </h2>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-xs rounded-full">
                  <Sparkles className="h-3 w-3" />
                  {Math.round(workflow.aiConfidence * 100)}% confidence
                </span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleGenerate()}
                  className="flex items-center gap-1 px-3 py-1.5 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg"
                >
                  <RefreshCw className="h-4 w-4" />
                  Regenerate
                </button>
                <button
                  onClick={handleAddStep}
                  className="flex items-center gap-1 px-3 py-1.5 text-sm text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg"
                >
                  <Plus className="h-4 w-4" />
                  Add Step
                </button>
              </div>
            </div>

            {/* Pipeline Steps */}
            <div className="space-y-4">
              {workflow.steps.map((step, index) => (
                <StepCard
                  key={step.id}
                  step={step}
                  index={index}
                  onEdit={() => {}}
                  onDelete={() => handleDeleteStep(step.id)}
                  onMoveUp={() => handleMoveUp(index)}
                  onMoveDown={() => handleMoveDown(index)}
                  isFirst={index === 0}
                  isLast={index === workflow.steps.length - 1}
                />
              ))}
            </div>

            {/* Empty state */}
            {workflow.steps.length === 0 && (
              <div className="text-center py-12 bg-gray-50 dark:bg-gray-800/50 rounded-xl">
                <p className="text-gray-500">No steps in pipeline</p>
                <button
                  onClick={handleAddStep}
                  className="mt-2 text-primary-600 hover:underline text-sm"
                >
                  Add a step
                </button>
              </div>
            )}
          </div>
        )}

        {/* Empty state before generation */}
        {!workflow && !isGenerating && (
          <div className="max-w-4xl mx-auto">
            {/* Hero */}
            <div className="text-center mb-10">
              <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-gradient-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 mb-4">
                <Sparkles className="h-8 w-8 text-purple-500" />
              </div>
              <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
                Build pipelines with AI
              </h2>
              <p className="text-gray-500 max-w-md mx-auto">
                Describe what you want your data pipeline to do, or start from a template.
              </p>
            </div>

            {/* Template Gallery */}
            <div>
              <h3 className="text-sm font-medium text-gray-500 uppercase tracking-wide mb-4">
                Popular Templates
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {PIPELINE_TEMPLATES.map((template) => (
                  <TemplateCard
                    key={template.id}
                    template={template}
                    onSelect={() => {
                      setPrompt(template.prompt);
                      // Auto-generate after template selection
                      setTimeout(() => handleGenerate(), 100);
                    }}
                  />
                ))}
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Ambient Intelligence Bar */}
      <AmbientIntelligenceBar />

      {/* Command Bar Modal */}
      <CommandBar />
    </div>
  );
}

export default SmartWorkflowBuilder;

