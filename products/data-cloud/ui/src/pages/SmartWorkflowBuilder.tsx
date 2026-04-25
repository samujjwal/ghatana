/**
 * Workflow Builder Page
 *
 * Intent-based pipeline creation with progressive disclosure.
 * Users describe what they want in natural language, then continue in the
 * runtime-backed pipeline editor with real tool integrations.
 *
 * Features:
 * - Natural language intent input
 * - Workflow suggestions with confidence scores
 * - Interactive review and editing of generated workflows
 * - Integration with real workflow API (not mock)
 * - Capability-aware UI with fallback states
 *
 * @doc.type page
 * @doc.purpose Intent-based workflow creation
 * @doc.layer frontend
 */

import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { getCapabilitySignal, useCapabilityRegistry } from '../api/capabilities.service';
import { UnsupportedSurfaceBoundary } from '../components/common/UnsupportedSurfaceBoundary';
import { smartWorkflowGenerationBoundary } from '../components/common/unsupportedSurfaceRegistry';
import SessionBootstrap from '../lib/auth/session';
import { generateWorkflowDraft, type WorkflowDraft } from '../lib/api/ai';
import { workflowsApi, type WorkflowEdge, type WorkflowNode } from '../lib/api/workflows';
import {
  Sparkles,
  Play,
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
  CheckCircle,
  AlertCircle,
  Loader2,
  Zap,
  LayoutGrid,
} from 'lucide-react';
import { Button, IconButton } from '@ghatana/design-system';
import { cn } from '../lib/theme';
import {
  SMART_WORKFLOW_AI_ASSIST_DEGRADED_DETAIL,
  SMART_WORKFLOW_AI_ASSIST_DEGRADED_TITLE,
  SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_DETAIL,
  SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_TITLE,
} from '../lib/runtime-boundaries';
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
  reviewRequired: boolean;
  provenance: WorkflowDraft['provenance'];
  fallback: boolean;
}

function toPipelineStep(step: WorkflowDraft['steps'][number]): PipelineStep {
  return {
    id: step.id,
    type: step.type,
    name: step.name,
    description: step.description,
    aiGenerated: true,
    confidence: step.confidence,
    config: step.config,
    status: step.confidence >= 0.6 ? 'valid' : 'pending',
  };
}

function toWorkflowNode(step: PipelineStep, index: number): WorkflowNode {
  return {
    id: step.id,
    type: step.type,
    label: step.name,
    position: {
      x: 120 + index * 260,
      y: 180,
    },
    data: {
      description: step.description,
      config: step.config ?? {},
      aiGenerated: step.aiGenerated,
      confidence: step.confidence,
    },
  };
}

function toWorkflowEdges(steps: PipelineStep[]): WorkflowEdge[] {
  return steps.slice(1).map((step, index) => ({
    id: `edge-${steps[index].id}-${step.id}`,
    source: steps[index].id,
    target: step.id,
  }));
}

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return fallback;
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
                  Generated
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
 * Smart Workflow Builder page component.
 */
export function SmartWorkflowBuilder() {
  const navigate = useNavigate();
  const { data: capabilityRegistry } = useCapabilityRegistry();
  const aiAssistCapability = getCapabilitySignal(capabilityRegistry?.capabilities, ['ai.assist', 'ai_assist', 'assist']);
  const [prompt, setPrompt] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [showGenerationBoundary, setShowGenerationBoundary] = useState(false);
  const [workflow, setWorkflow] = useState<GeneratedWorkflow | null>(null);
  const [advancedMode, setAdvancedMode] = useState(false);
  const [generationError, setGenerationError] = useState<string | null>(null);
  const [deploymentError, setDeploymentError] = useState<string | null>(null);
  const [isDeploying, setIsDeploying] = useState(false);

  // Generate workflow from prompt
  const handleGenerate = useCallback(async () => {
    if (!prompt.trim()) return;

    if (aiAssistCapability?.status === 'unavailable') {
      setWorkflow(null);
      setShowGenerationBoundary(true);
      return;
    }

    setIsGenerating(true);
    setGenerationError(null);
    setDeploymentError(null);
    setShowGenerationBoundary(false);

    try {
      const tenantId = SessionBootstrap.requireTenantId();
      const response = await generateWorkflowDraft(tenantId, prompt.trim());
      setWorkflow({
        id: response.data.draft.workflowId,
        name: response.data.draft.name,
        description: response.data.draft.description,
        steps: response.data.draft.steps.map(toPipelineStep),
        aiConfidence: response.data.confidence,
        reviewRequired: response.data.draft.reviewRequired,
        provenance: response.data.draft.provenance,
        fallback: response.data.fallback,
      });
    } catch (error) {
      setWorkflow(null);
      const message = getErrorMessage(error, 'Workflow draft generation failed.');
      if (/unavailable|unsupported/i.test(message)) {
        setShowGenerationBoundary(true);
      } else {
        setGenerationError(message);
      }
    } finally {
      setIsGenerating(false);
    }
  }, [aiAssistCapability?.status, prompt]);

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
  const handleDeploy = useCallback(async () => {
    if (!workflow) {
      return;
    }

    setIsDeploying(true);
    setDeploymentError(null);

    try {
      const created = await workflowsApi.create({
        name: workflow.name,
        description: workflow.description,
        nodes: workflow.steps.map(toWorkflowNode),
        edges: toWorkflowEdges(workflow.steps),
        tags: ['builder-generated'],
        metadata: {
          aiConfidence: workflow.aiConfidence,
          fallback: workflow.fallback,
          reviewRequired: workflow.reviewRequired,
          prompt,
          provenance: workflow.provenance,
          reviewedAt: new Date().toISOString(),
        },
      });
      navigate(`/pipelines/${created.id}`);
    } catch (error) {
      setDeploymentError(getErrorMessage(error, 'Unable to save workflow draft to the runtime-backed pipeline registry.'));
    } finally {
      setIsDeploying(false);
    }
  }, [navigate, prompt, workflow]);

  // Switch to advanced mode (full canvas)
  const handleAdvancedMode = useCallback(() => {
    if (workflow) {
      // Navigate to full workflow designer with current workflow
      navigate(`/pipelines/${workflow.id}?mode=advanced`);
    }
  }, [workflow, navigate]);

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <header className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              Workflow Builder
            </h1>
            <p className="text-sm text-gray-500 mt-0.5">
              Capture pipeline intent here, then continue in the runtime-backed pipeline editor
            </p>
          </div>
          <div className="flex items-center gap-3">
            <CommandBarTrigger />
            {workflow && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  leadingIcon={<LayoutGrid className="h-4 w-4" />}
                  onClick={handleAdvancedMode}
                >
                  Advanced Mode
                </Button>
                <Button
                  variant="solid"
                  loading={isDeploying}
                  leadingIcon={isDeploying ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
                  onClick={() => { void handleDeploy(); }}
                  disabled={isDeploying}
                >
                  {isDeploying ? 'Saving…' : 'Deploy'}
                </Button>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <section className="flex-1 overflow-y-auto p-6">
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
                aria-label="Describe your pipeline intent"
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
              <Button
                variant="solid"
                className="absolute right-3 bottom-3"
                loading={isGenerating}
                leadingIcon={isGenerating ? undefined : <Zap className="h-4 w-4" />}
                onClick={handleGenerate}
                disabled={!prompt.trim() || isGenerating}
              >
                {isGenerating ? 'Generating...' : 'Generate Draft'}
              </Button>
            </div>

            {generationError && (
              <div className="mt-4 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-200">
                {generationError}
              </div>
            )}

            {showGenerationBoundary && (
              <div className="mt-4">
                <UnsupportedSurfaceBoundary
                  title={smartWorkflowGenerationBoundary.title}
                  summary={smartWorkflowGenerationBoundary.summary}
                  details={smartWorkflowGenerationBoundary.details}
                  state={smartWorkflowGenerationBoundary.state}
                />
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => navigate('/pipelines')}
                  >
                    Open Pipelines
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setShowGenerationBoundary(false)}
                  >
                    Dismiss
                  </Button>
                </div>
              </div>
            )}

            {aiAssistCapability?.status === 'unavailable' && (
              <div className="mt-4 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                <p className="font-medium">{SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_TITLE}</p>
                <p className="mt-1">
                  {aiAssistCapability.detail ?? SMART_WORKFLOW_AI_ASSIST_UNAVAILABLE_DETAIL}
                </p>
              </div>
            )}

            {aiAssistCapability?.status === 'degraded' && (
              <div className="mt-4 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                <p className="font-medium">{SMART_WORKFLOW_AI_ASSIST_DEGRADED_TITLE}</p>
                <p className="mt-1">
                  {aiAssistCapability.detail ?? SMART_WORKFLOW_AI_ASSIST_DEGRADED_DETAIL}
                </p>
              </div>
            )}

            {/* Example prompts */}
            <div className="mt-4">
              <p className="text-xs text-gray-500 mb-2">Try an example:</p>
              <div className="flex flex-wrap gap-2">
                {EXAMPLE_PROMPTS.map((example, index) => (
                  <button
                    key={index}
                    onClick={() => {
                      setPrompt(example);
                      setShowGenerationBoundary(false);
                    }}
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

        {/* Pipeline Draft */}
        {workflow && (
          <div className="max-w-3xl mx-auto">
            {/* Pipeline Header */}
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  Pipeline Draft
                </h2>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 text-xs rounded-full">
                  <Sparkles className="h-3 w-3" />
                  {Math.round(workflow.aiConfidence * 100)}% confidence
                </span>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  leadingIcon={<RefreshCw className="h-4 w-4" />}
                  onClick={() => { void handleGenerate(); }}
                >
                  Regenerate
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  leadingIcon={<Plus className="h-4 w-4" />}
                  onClick={handleAddStep}
                >
                  Add Step
                </Button>
              </div>
            </div>

            {(workflow.reviewRequired || workflow.aiConfidence < 0.75 || workflow.fallback) && (
              <div className="mb-4 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                <p className="font-medium">Review required before deployment</p>
                <p className="mt-1">
                  This draft was generated with {Math.round(workflow.aiConfidence * 100)}% confidence.
                  Confirm source, transform, and destination steps before saving it to the pipeline registry.
                </p>
              </div>
            )}

            <div className="mb-4 rounded-xl border border-gray-200 bg-white p-4 text-sm text-gray-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">
              <p className="font-medium text-gray-900 dark:text-gray-100">{workflow.name}</p>
              <p className="mt-1">{workflow.description}</p>
              <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                Generated {new Date(workflow.provenance.generatedAt).toLocaleString()} via {workflow.provenance.strategy}.
                Prompt summary: {workflow.provenance.promptSummary}
              </p>
            </div>

            {deploymentError && (
              <div className="mb-4 rounded-xl border border-red-300 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-200">
                {deploymentError}
              </div>
            )}

            {/* Pipeline Steps */}
            <div className="space-y-4">
              {workflow.steps.map((step, index) => (
                <StepCard
                  key={step.id}
                  step={step}
                  index={index}
                  onEdit={() => { }}
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
                <Button
                  variant="link"
                  size="sm"
                  className="mt-2"
                  onClick={handleAddStep}
                >
                  Add a step
                </Button>
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
                      setShowGenerationBoundary(false);
                    }}
                  />
                ))}
              </div>
            </div>
          </div>
        )}
      </section>

      {/* Ambient Intelligence Bar */}
      <AmbientIntelligenceBar />

      {/* Command Bar Modal */}
      <CommandBar />
    </div>
  );
}

export default SmartWorkflowBuilder;

