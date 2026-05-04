/**
 * GuidedPipelineFlow — goal-first guided pipeline creation flow.
 *
 * Default entry mode for new pipelines (`/build/pipelines/new`).
 * Presents a progressive checklist: describe goal → review suggested stages
 * → validate → review risk → run or schedule.
 *
 * The advanced canvas is available via the "Switch to advanced mode" action.
 * This component owns only the wizard UX; all API calls delegate to the
 * parent's handlers (requestSuggestions, handleValidate, etc.).
 *
 * @doc.type component
 * @doc.purpose Goal-first guided pipeline creation wizard
 * @doc.layer frontend
 * @doc.pattern Wizard
 */
import React, { useState, useCallback } from 'react';
import { CheckCircle, ChevronRight, Target, Layers, ShieldCheck, Play, Settings } from 'lucide-react';
import { Button, TextField, TextArea } from '@ghatana/design-system';
import type { StageKind } from '@/types/pipeline.types';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface GuidedStageSuggestion {
  label: string;
  kind: StageKind;
  description: string;
}

export interface GuidedPipelineFlowProps {
  /**
   * Called when the user submits a goal description.
   * The parent fetches suggestions and passes them back via `suggestions`.
   */
  onRequestSuggestions: (description: string, goal?: string) => Promise<void>;
  /** Suggestion results to display. Null = not yet fetched. */
  suggestions: readonly GuidedStageSuggestion[] | null;
  suggestionsLoading: boolean;
  /** Called when the user confirms the suggested stages and wants to apply them. */
  onApplySuggestions: () => void;
  /** Called when the user triggers validation. */
  onValidate: () => Promise<void>;
  validating: boolean;
  validationPassed: boolean | null;
  /** Called when the user triggers a run. */
  onRunNow: () => Promise<void>;
  running: boolean;
  /** Called when the user switches to the advanced canvas mode. */
  onSwitchToAdvanced: () => void;
  /** Whether the current user role can save/run pipelines. */
  canManagePipelines: boolean;
}

// ---------------------------------------------------------------------------
// Step definitions
// ---------------------------------------------------------------------------

type StepId = 'describe' | 'review' | 'validate' | 'run';

interface Step {
  id: StepId;
  label: string;
  description: string;
  icon: React.ReactElement;
}

const STEPS: readonly Step[] = [
  {
    id: 'describe',
    label: 'Describe goal',
    description: 'Tell us what the pipeline should accomplish.',
    icon: <Target className="h-4 w-4" aria-hidden="true" />,
  },
  {
    id: 'review',
    label: 'Review stages',
    description: 'Review and apply suggested pipeline stages.',
    icon: <Layers className="h-4 w-4" aria-hidden="true" />,
  },
  {
    id: 'validate',
    label: 'Validate',
    description: 'Check the pipeline for errors and risk cues.',
    icon: <ShieldCheck className="h-4 w-4" aria-hidden="true" />,
  },
  {
    id: 'run',
    label: 'Run',
    description: 'Run the pipeline or save as a draft.',
    icon: <Play className="h-4 w-4" aria-hidden="true" />,
  },
] as const;

const STEP_ORDER: Record<StepId, number> = {
  describe: 0,
  review: 1,
  validate: 2,
  run: 3,
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * Guided goal-first wizard for new pipelines.
 *
 * Keeps the canvas out of sight until the user explicitly opts into advanced mode.
 * Uses the same API hooks as the canvas builder; the parent applies suggestions.
 */
export function GuidedPipelineFlow({
  onRequestSuggestions,
  suggestions,
  suggestionsLoading,
  onApplySuggestions,
  onValidate,
  validating,
  validationPassed,
  onRunNow,
  running,
  onSwitchToAdvanced,
  canManagePipelines,
}: GuidedPipelineFlowProps): React.ReactElement {
  const [currentStep, setCurrentStep] = useState<StepId>('describe');
  const [description, setDescription] = useState('');
  const [goal, setGoal] = useState('');
  const [stagesApplied, setStagesApplied] = useState(false);

  const currentStepIndex = STEP_ORDER[currentStep];

  const handleDescribeSubmit = useCallback(async () => {
    if (!description.trim()) return;
    await onRequestSuggestions(description.trim(), goal.trim() || undefined);
    setCurrentStep('review');
  }, [description, goal, onRequestSuggestions]);

  const handleApply = useCallback(() => {
    onApplySuggestions();
    setStagesApplied(true);
    setCurrentStep('validate');
  }, [onApplySuggestions]);

  const handleValidate = useCallback(async () => {
    await onValidate();
    setCurrentStep('run');
  }, [onValidate]);

  const isStepComplete = useCallback(
    (id: StepId): boolean => {
      const idx = STEP_ORDER[id];
      if (idx < currentStepIndex) return true;
      if (id === 'describe') return description.trim().length > 0;
      if (id === 'review') return stagesApplied;
      if (id === 'validate') return validationPassed === true;
      return false;
    },
    [currentStepIndex, description, stagesApplied, validationPassed]
  );

  return (
    <div className="flex flex-col h-screen w-screen bg-gray-50 dark:bg-gray-950" data-testid="guided-pipeline-flow">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4 dark:border-gray-700 dark:bg-gray-900">
        <div>
          <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            New pipeline
          </h1>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            Follow the steps to create your pipeline.
          </p>
        </div>
        <Button
          variant="outlined"
          size="small"
          startIcon={<Settings className="h-3.5 w-3.5" aria-hidden="true" />}
          onClick={onSwitchToAdvanced}
        >
          Switch to advanced mode
        </Button>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Step sidebar */}
        <nav
          aria-label="Pipeline creation steps"
          className="hidden md:flex w-56 shrink-0 flex-col border-r border-gray-200 bg-white p-4 gap-1 dark:border-gray-700 dark:bg-gray-900"
        >
          {STEPS.map((step, idx) => {
            const isActive = step.id === currentStep;
            const complete = isStepComplete(step.id);
            const accessible = idx <= currentStepIndex;

            return (
              <Button
                key={step.id}
                variant="text"
                disabled={!accessible}
                onClick={() => accessible && setCurrentStep(step.id)}
                aria-current={isActive ? 'step' : undefined}
                className={[
                  'flex items-start gap-3 px-3 py-2.5 text-left text-sm',
                  isActive
                    ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300'
                    : accessible
                      ? 'text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-800'
                      : 'text-gray-400 cursor-not-allowed dark:text-gray-600',
                ].join(' ')}
              >
                <span className="mt-0.5 shrink-0">
                  {complete ? (
                    <CheckCircle className="h-4 w-4 text-green-500" aria-hidden="true" />
                  ) : (
                    step.icon
                  )}
                </span>
                <span>
                  <span className="block font-medium">{step.label}</span>
                  <span className="block text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                    {step.description}
                  </span>
                </span>
              </Button>
            );
          })}
        </nav>

        {/* Step content */}
        <main className="flex-1 overflow-y-auto p-6 md:p-10">
          {currentStep === 'describe' && (
            <DescribeStep
              description={description}
              onDescriptionChange={setDescription}
              goal={goal}
              onGoalChange={setGoal}
              onSubmit={handleDescribeSubmit}
              loading={suggestionsLoading}
            />
          )}
          {currentStep === 'review' && (
            <ReviewStep
              suggestions={suggestions}
              loading={suggestionsLoading}
              onApply={handleApply}
              onBack={() => setCurrentStep('describe')}
              onSwitchToAdvanced={onSwitchToAdvanced}
            />
          )}
          {currentStep === 'validate' && (
            <ValidateStep
              onValidate={handleValidate}
              validating={validating}
              validationPassed={validationPassed}
              onBack={() => setCurrentStep('review')}
            />
          )}
          {currentStep === 'run' && (
            <RunStep
              onRunNow={onRunNow}
              running={running}
              canManagePipelines={canManagePipelines}
              onSwitchToAdvanced={onSwitchToAdvanced}
            />
          )}
        </main>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Step sub-components
// ---------------------------------------------------------------------------

interface DescribeStepProps {
  description: string;
  onDescriptionChange: (v: string) => void;
  goal: string;
  onGoalChange: (v: string) => void;
  onSubmit: () => Promise<void>;
  loading: boolean;
}

function DescribeStep({
  description,
  onDescriptionChange,
  goal,
  onGoalChange,
  onSubmit,
  loading,
}: DescribeStepProps): React.ReactElement {
  return (
    <div className="max-w-xl">
      <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100 mb-1">
        Describe what this pipeline should do
      </h2>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">
        Be specific about the data source, transformation, and output. The system will suggest appropriate stages.
      </p>

      <label className="block mb-4">
        <span className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
          Description <span className="text-red-500" aria-hidden="true">*</span>
        </span>
        <TextArea
          value={description}
          onChange={(e) => onDescriptionChange(e.target.value)}
          rows={4}
          placeholder="e.g. Ingest customer events from Kafka, validate schema, enrich with CRM data, and publish to the analytics topic."
          required
          aria-required="true"
          fullWidth
        />
      </label>

      <label className="block mb-6">
        <span className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
          Goal <span className="text-xs font-normal text-gray-400">(optional)</span>
        </span>
        <TextField
          type="text"
          value={goal}
          onChange={(e) => onGoalChange(e.target.value)}
          placeholder="e.g. Reduce latency for real-time analytics"
          fullWidth
        />
      </label>

      <Button
        variant="contained"
        onClick={() => void onSubmit()}
        disabled={!description.trim() || loading}
        endIcon={!loading && <ChevronRight className="h-4 w-4" aria-hidden="true" />}
        loading={loading}
        loadingText="Generating suggestions…"
      >
        Generate stage suggestions
      </Button>
    </div>
  );
}

interface ReviewStepProps {
  suggestions: readonly GuidedStageSuggestion[] | null;
  loading: boolean;
  onApply: () => void;
  onBack: () => void;
  onSwitchToAdvanced: () => void;
}

function ReviewStep({
  suggestions,
  loading,
  onApply,
  onBack,
  onSwitchToAdvanced,
}: ReviewStepProps): React.ReactElement {
  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-16 gap-3">
        <span className="animate-spin h-6 w-6 border-2 border-indigo-600 border-t-transparent rounded-full" aria-hidden="true" />
        <p className="text-sm text-gray-500 dark:text-gray-400">Generating stage suggestions…</p>
      </div>
    );
  }

  if (!suggestions || suggestions.length === 0) {
    return (
      <div className="max-w-xl">
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
          No stages were suggested. Try refining your description.
        </p>
        <div className="flex gap-3">
          <Button variant="outlined" onClick={onBack}>
            Back
          </Button>
          <Button variant="contained" onClick={onSwitchToAdvanced}>
            Build manually
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-xl">
      <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100 mb-1">
        Review suggested stages
      </h2>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">
        These stages were suggested based on your description. Confirm to add them to the pipeline canvas.
      </p>

      <ol className="mb-6 space-y-2">
        {suggestions.map((stage, i) => (
          <li
            key={`${stage.kind}-${i}`}
            className="flex items-start gap-3 rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-800"
          >
            <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-xs font-semibold text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300">
              {i + 1}
            </span>
            <div>
              <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{stage.label}</p>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{stage.description}</p>
              <span className="mt-1 inline-block rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                {stage.kind}
              </span>
            </div>
          </li>
        ))}
      </ol>

      <div className="flex items-center gap-3">
        <Button variant="outlined" onClick={onBack}>
          Back
        </Button>
        <Button
          variant="contained"
          onClick={onApply}
          endIcon={<ChevronRight className="ml-1.5 inline h-4 w-4" aria-hidden="true" />}
        >
          Apply stages and continue
        </Button>
        <Button variant="text" onClick={onSwitchToAdvanced}>
          Edit in canvas
        </Button>
      </div>
    </div>
  );
}

interface ValidateStepProps {
  onValidate: () => Promise<void>;
  validating: boolean;
  validationPassed: boolean | null;
  onBack: () => void;
}

function ValidateStep({ onValidate, validating, validationPassed, onBack }: ValidateStepProps): React.ReactElement {
  return (
    <div className="max-w-xl">
      <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100 mb-1">
        Validate the pipeline
      </h2>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">
        Checks for schema errors, missing connectors, governance gaps, and risk cues before running.
      </p>

      {validationPassed === true && (
        <div className="mb-6 flex items-center gap-2 rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800 dark:border-green-700 dark:bg-green-900/20 dark:text-green-300">
          <CheckCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
          Pipeline passed validation.
        </div>
      )}

      {validationPassed === false && (
        <div className="mb-6 flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-700 dark:bg-red-900/20 dark:text-red-300">
          Validation found errors. Review the validation panel and fix before running.
        </div>
      )}

      <div className="flex items-center gap-3">
        <Button variant="outlined" onClick={onBack}>
          Back
        </Button>
        <Button
          variant="contained"
          onClick={() => void onValidate()}
          disabled={validating}
          startIcon={!validating && <ShieldCheck className="h-4 w-4" aria-hidden="true" />}
          loading={validating}
          loadingText="Validating…"
        >
          {validationPassed === true ? 'Re-validate' : 'Validate pipeline'}
        </Button>
      </div>
    </div>
  );
}

interface RunStepProps {
  onRunNow: () => Promise<void>;
  running: boolean;
  canManagePipelines: boolean;
  onSwitchToAdvanced: () => void;
}

function RunStep({ onRunNow, running, canManagePipelines, onSwitchToAdvanced }: RunStepProps): React.ReactElement {
  return (
    <div className="max-w-xl">
      <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100 mb-1">
        Run the pipeline
      </h2>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">
        Save as a draft to schedule later, or run now to execute immediately.
      </p>

      {!canManagePipelines && (
        <div className="mb-6 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-700 dark:bg-amber-900/20 dark:text-amber-300">
          Read-only access: running pipelines requires an operator or admin role.
        </div>
      )}

      <div className="flex items-center gap-3">
        <Button variant="outlined" onClick={onSwitchToAdvanced}>
          Open in canvas
        </Button>
        <Button
          variant="contained"
          onClick={() => void onRunNow()}
          disabled={running || !canManagePipelines}
          startIcon={!running && <Play className="h-4 w-4" aria-hidden="true" />}
          loading={running}
          loadingText="Triggering run…"
        >
          Run now
        </Button>
      </div>
    </div>
  );
}
