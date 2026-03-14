/**
 * Simulation Preview Panel
 * 
 * Real-time preview of simulation with validation feedback.
 * Displays simulation playback with step highlighting and error indicators.
 * 
 * @doc.type component
 * @doc.purpose Preview simulation manifests with validation
 * @doc.layer product
 * @doc.pattern Container
 */

import { useState, useCallback, useMemo, useEffect, useRef } from "react";
import { Button, Badge, Tooltip, Slider } from "@ghatana/design-system";
import type {
  SimulationManifest,
  SimulationStep,
  SimEntity,
} from "../hooks/useSimulationTimeline";

// =============================================================================
// Local Types
// =============================================================================

interface ManifestValidationResult {
  isValid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

interface ValidationError {
  id?: string;
  type: string;
  code?: string;
  message: string;
  stepIndex?: number;
  actionIndex?: number;
  entityId?: string;
  path?: string;
  severity?: "error" | "warning" | "info";
}

interface ValidationWarning {
  id?: string;
  type: string;
  code?: string;
  message: string;
  stepIndex?: number;
  path?: string;
}

// Helper type for entity properties access
type EntityProperties = {
  value?: string | number;
  label?: string;
  mass?: number;
  symbol?: string;
  formula?: string;
  [key: string]: unknown;
};

// =============================================================================
// Types
// =============================================================================

export interface SimulationPreviewPanelProps {
  /**
   * The manifest to preview.
   */
  manifest: SimulationManifest | null;

  /**
   * Validation result, if available.
   */
  validationResult?: ManifestValidationResult;

  /**
   * Current step index (external control).
   */
  currentStepIndex?: number;

  /**
   * Callback when step changes.
   */
  onStepChange?: (stepIndex: number) => void;

  /**
   * Callback when an error is clicked.
   */
  onErrorClick?: (error: ValidationError) => void;

  /**
   * Enable auto-play on load.
   */
  autoPlay?: boolean;

  /**
   * Playback speed (1 = normal).
   */
  playbackSpeed?: number;

  /**
   * Show compact mode.
   */
  compact?: boolean;

  /**
   * Custom class name.
   */
  className?: string;
}

interface PlaybackState {
  isPlaying: boolean;
  currentStep: number;
  totalSteps: number;
  speed: number;
  progress: number; // 0-1 within current step
}

// =============================================================================
// Validation Helpers
// =============================================================================

function validateManifestLocally(manifest: SimulationManifest): ValidationError[] {
  const errors: ValidationError[] = [];

  // Check required fields
  if (!manifest.id) {
    errors.push({
      type: "error",
      code: "MISSING_ID",
      message: "Manifest is missing an ID",
      path: "id",
    });
  }

  if (!manifest.title?.trim()) {
    errors.push({
      type: "error",
      code: "MISSING_TITLE",
      message: "Manifest is missing a title",
      path: "title",
    });
  }

  if (!manifest.domain) {
    errors.push({
      type: "error",
      code: "MISSING_DOMAIN",
      message: "Manifest is missing a domain",
      path: "domain",
    });
  }

  // Check steps
  if (!manifest.steps || manifest.steps.length === 0) {
    errors.push({
      type: "warning",
      code: "NO_STEPS",
      message: "Manifest has no steps defined",
      path: "steps",
    });
  } else {
    manifest.steps.forEach((step, stepIndex) => {
      // Check step has ID
      if (!step.id) {
        errors.push({
          type: "error",
          code: "STEP_MISSING_ID",
          message: `Step ${stepIndex + 1} is missing an ID`,
          stepIndex,
          path: `steps[${stepIndex}].id`,
        });
      }

      // Check actions reference valid entities
      step.actions?.forEach((action, actionIndex) => {
        if (action.entityId) {
          const entityExists =
            manifest.initialEntities?.some((e) => e.id === action.entityId) ||
            manifest.steps
              .slice(0, stepIndex)
              .some((s) =>
                s.actions?.some(
                  (a) => a.type === "CREATE_ENTITY" && a.entityId === action.entityId
                )
              );

          if (!entityExists && action.type !== "CREATE_ENTITY") {
            errors.push({
              type: "warning",
              code: "UNKNOWN_ENTITY",
              message: `Action references unknown entity: ${action.entityId}`,
              stepIndex,
              actionIndex,
              entityId: action.entityId as string,
              path: `steps[${stepIndex}].actions[${actionIndex}].entityId`,
            });
          }
        }
      });

      // Check for empty narration
      if (!step.narration?.trim()) {
        errors.push({
          type: "info",
          code: "EMPTY_NARRATION",
          message: `Step ${stepIndex + 1} has no narration`,
          stepIndex,
          path: `steps[${stepIndex}].narration`,
        });
      }
    });
  }

  // Check entities
  if (manifest.initialEntities) {
    const entityIds = new Set<string>();
    manifest.initialEntities.forEach((entity, index) => {
      if (!entity.id) {
        errors.push({
          type: "error",
          code: "ENTITY_MISSING_ID",
          message: `Entity ${index + 1} is missing an ID`,
          path: `initialEntities[${index}].id`,
        });
      } else if (entityIds.has(entity.id as string)) {
        errors.push({
          type: "error",
          code: "DUPLICATE_ENTITY_ID",
          message: `Duplicate entity ID: ${entity.id}`,
          entityId: entity.id as string,
          path: `initialEntities[${index}].id`,
        });
      } else {
        entityIds.add(entity.id as string);
      }
    });
  }

  return errors;
}

// =============================================================================
// Entity Preview Renderer
// =============================================================================

interface EntityPreviewProps {
  entities: SimEntity[];
  currentStep: SimulationStep | null;
  domain: string;
}

const EntityPreview = ({ entities, currentStep, domain }: EntityPreviewProps) => {
  // Get highlighted entity IDs from current step
  const highlightedIds = useMemo(() => {
    if (!currentStep?.actions) return new Set<string>();
    return new Set(
      currentStep.actions
        .filter((a) => a.entityId)
        .map((a) => a.entityId as string)
    );
  }, [currentStep]);

  // Simple visual representation based on domain
  const renderEntity = (entity: SimEntity) => {
    const isHighlighted = highlightedIds.has(entity.id as string);
    const baseStyle = `
      p-2 rounded border-2 transition-all duration-300
      ${isHighlighted 
        ? "border-yellow-500 bg-yellow-50 dark:bg-yellow-900/30 scale-105 shadow-lg" 
        : "border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800"
      }
    `;

    // Cast properties with explicit helper
    const props = entity.properties as EntityProperties | undefined;

    // Domain-specific rendering
    switch (domain) {
      case "CS_DISCRETE":
        return (
          <div key={entity.id as string} className={`${baseStyle} min-w-[40px] text-center`}>
            <span className="font-mono text-sm">
              {String(props?.value ?? props?.label ?? "?")}
            </span>
          </div>
        );

      case "PHYSICS":
        return (
          <div key={entity.id as string} className={`${baseStyle} flex items-center gap-2`}>
            <div
              className="w-6 h-6 rounded-full bg-blue-500"
              style={{
                transform: `scale(${Math.min(2, (Number(props?.mass) || 1) / 5 + 0.5)})`,
              }}
            />
            <span className="text-xs">{entity.type}</span>
          </div>
        );

      case "CHEMISTRY":
        return (
          <div key={entity.id as string} className={`${baseStyle} flex items-center gap-1`}>
            <span className="text-lg">{String(props?.symbol ?? "⚛️")}</span>
            <span className="text-xs">{String(props?.formula ?? entity.type)}</span>
          </div>
        );

      default:
        return (
          <div key={entity.id as string} className={baseStyle}>
            <span className="text-xs font-medium">{entity.type}</span>
            <span className="text-xs text-gray-500 block">{entity.id as string}</span>
          </div>
        );
    }
  };

  if (entities.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400 text-sm">
        No entities defined
      </div>
    );
  }

  return (
    <div className="flex flex-wrap gap-2 p-2">
      {entities.map((entity) => renderEntity(entity))}
    </div>
  );
};

// =============================================================================
// Step Timeline Mini
// =============================================================================

interface StepTimelineMiniProps {
  steps: SimulationStep[];
  currentIndex: number;
  errors: ValidationError[];
  onStepClick: (index: number) => void;
}

const StepTimelineMini = ({
  steps,
  currentIndex,
  errors,
  onStepClick,
}: StepTimelineMiniProps) => {
  // Map step indices to error counts
  const stepErrors = useMemo(() => {
    const map = new Map<number, { errors: number; warnings: number }>();
    errors.forEach((error) => {
      if (error.stepIndex !== undefined) {
        const current = map.get(error.stepIndex) || { errors: 0, warnings: 0 };
        if (error.type === "error") {
          current.errors++;
        } else if (error.type === "warning") {
          current.warnings++;
        }
        map.set(error.stepIndex, current);
      }
    });
    return map;
  }, [errors]);

  return (
    <div className="flex items-center gap-1 overflow-x-auto pb-2">
      {steps.map((step, index) => {
        const stepError = stepErrors.get(index);
        const isCurrent = index === currentIndex;
        const isPast = index < currentIndex;

        return (
          <Tooltip key={step.id as string} content={step.narration || `Step ${index + 1}`}>
            <button
              onClick={() => onStepClick(index)}
              className={`
                flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center
                text-xs font-medium transition-all
                ${isCurrent 
                  ? "bg-blue-500 text-white ring-2 ring-blue-300" 
                  : isPast 
                    ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400" 
                    : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400"
                }
                ${stepError?.errors ? "ring-2 ring-red-500" : ""}
                ${stepError?.warnings && !stepError.errors ? "ring-2 ring-yellow-500" : ""}
                hover:scale-110
              `}
            >
              {stepError?.errors ? "!" : index + 1}
            </button>
          </Tooltip>
        );
      })}
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const SimulationPreviewPanel = ({
  manifest,
  validationResult,
  currentStepIndex: externalStepIndex,
  onStepChange,
  onErrorClick,
  autoPlay = false,
  playbackSpeed = 1,
  compact: _compact = false,
  className = "",
}: SimulationPreviewPanelProps) => {
  // Note: _compact is reserved for future compact mode UI
  void _compact;
  
  // State
  const [playback, setPlayback] = useState<PlaybackState>({
    isPlaying: false,
    currentStep: 0,
    totalSteps: 0,
    speed: playbackSpeed,
    progress: 0,
  });
  const [showErrors, setShowErrors] = useState(true);

  const playbackRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Local validation
  const localErrors = useMemo(() => {
    if (!manifest) return [];
    return validateManifestLocally(manifest);
  }, [manifest]);

  // Combined errors from validation result and local validation
  const allErrors = useMemo(() => {
    const errors = [...localErrors];
    if (validationResult?.errors) {
      validationResult.errors.forEach((e) => {
        errors.push({
          type: "error",
          code: e.code || "VALIDATION_ERROR",
          message: e.message,
          path: e.path,
        });
      });
    }
    if (validationResult?.warnings) {
      validationResult.warnings.forEach((w) => {
        errors.push({
          type: "warning",
          code: w.code || "VALIDATION_WARNING",
          message: w.message,
          path: w.path,
        });
      });
    }
    return errors;
  }, [localErrors, validationResult]);

  // Error summary
  const errorSummary = useMemo(() => {
    return {
      errors: allErrors.filter((e) => e.type === "error").length,
      warnings: allErrors.filter((e) => e.type === "warning").length,
      info: allErrors.filter((e) => e.type === "info").length,
    };
  }, [allErrors]);

  // Current step
  const currentStepIndex = externalStepIndex ?? playback.currentStep;
  const currentStep = manifest?.steps?.[currentStepIndex] ?? null;
  const totalSteps = manifest?.steps?.length ?? 0;

  // Sync with external step index
  useEffect(() => {
    if (externalStepIndex !== undefined) {
      setPlayback((prev) => ({ ...prev, currentStep: externalStepIndex }));
    }
  }, [externalStepIndex]);

  // Sync total steps
  useEffect(() => {
    setPlayback((prev) => ({
      ...prev,
      totalSteps,
      currentStep: Math.min(prev.currentStep, Math.max(0, totalSteps - 1)),
    }));
  }, [totalSteps]);

  // Playback control
  useEffect(() => {
    if (playback.isPlaying && totalSteps > 0) {
      playbackRef.current = setInterval(() => {
        setPlayback((prev) => {
          const nextStep = prev.currentStep + 1;
          if (nextStep >= totalSteps) {
            return { ...prev, isPlaying: false, currentStep: 0 };
          }
          onStepChange?.(nextStep);
          return { ...prev, currentStep: nextStep };
        });
      }, 2000 / playback.speed);
    }

    return () => {
      if (playbackRef.current) {
        clearInterval(playbackRef.current);
      }
    };
  }, [playback.isPlaying, playback.speed, totalSteps, onStepChange]);

  // Auto-play on mount
  useEffect(() => {
    if (autoPlay && manifest && totalSteps > 0) {
      setPlayback((prev) => ({ ...prev, isPlaying: true }));
    }
  }, [autoPlay, manifest, totalSteps]);

  // Handlers
  const handlePlayPause = useCallback(() => {
    setPlayback((prev) => ({ ...prev, isPlaying: !prev.isPlaying }));
  }, []);

  const handleStepChange = useCallback((index: number) => {
    setPlayback((prev) => ({ ...prev, currentStep: index, isPlaying: false }));
    onStepChange?.(index);
  }, [onStepChange]);

  const handlePrevStep = useCallback(() => {
    if (currentStepIndex > 0) {
      handleStepChange(currentStepIndex - 1);
    }
  }, [currentStepIndex, handleStepChange]);

  const handleNextStep = useCallback(() => {
    if (currentStepIndex < totalSteps - 1) {
      handleStepChange(currentStepIndex + 1);
    }
  }, [currentStepIndex, totalSteps, handleStepChange]);

  const handleSpeedChange = useCallback((speed: number) => {
    setPlayback((prev) => ({ ...prev, speed }));
  }, []);

  // No manifest state
  if (!manifest) {
    return (
      <div className={`flex items-center justify-center h-full bg-gray-50 dark:bg-gray-900 rounded-lg ${className}`}>
        <div className="text-center text-gray-400">
          <div className="text-4xl mb-2">🎬</div>
          <p className="text-sm">No simulation to preview</p>
          <p className="text-xs mt-1">Generate or load a manifest to see the preview</p>
        </div>
      </div>
    );
  }

  return (
    <div className={`flex flex-col h-full bg-white dark:bg-gray-800 rounded-lg overflow-hidden ${className}`}>
      {/* Header */}
      <div className="flex-shrink-0 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {manifest.title}
            </h3>
            <div className="flex items-center gap-2 mt-1">
              <Badge tone="secondary">
                {manifest.domain}
              </Badge>
              <span className="text-xs text-gray-500">
                {totalSteps} steps
              </span>
            </div>
          </div>

          {/* Error Summary */}
          <div className="flex items-center gap-2">
            {errorSummary.errors > 0 && (
              <Badge tone="danger">
                {errorSummary.errors} errors
              </Badge>
            )}
            {errorSummary.warnings > 0 && (
              <Badge tone="warning">
                {errorSummary.warnings} warnings
              </Badge>
            )}
            {errorSummary.errors === 0 && errorSummary.warnings === 0 && (
              <Badge tone="success">
                ✓ Valid
              </Badge>
            )}
          </div>
        </div>
      </div>

      {/* Preview Area */}
      <div className="flex-1 overflow-hidden flex flex-col">
        {/* Entity visualization */}
        <div className="flex-1 overflow-auto p-4 bg-gray-50 dark:bg-gray-900">
          <EntityPreview
            entities={manifest.initialEntities || []}
            currentStep={currentStep}
            domain={manifest.domain}
          />
        </div>

        {/* Current step info */}
        {currentStep && (
          <div className="flex-shrink-0 px-4 py-2 bg-blue-50 dark:bg-blue-900/20 border-t border-blue-100 dark:border-blue-900">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs font-medium text-blue-600 dark:text-blue-400">
                Step {currentStepIndex + 1}
              </span>
              <span className="text-xs text-gray-500">
                {currentStep.actions?.length || 0} actions
              </span>
            </div>
            <p className="text-sm text-gray-700 dark:text-gray-300">
              {currentStep.narration || "No narration"}
            </p>
          </div>
        )}
      </div>

      {/* Timeline */}
      <div className="flex-shrink-0 px-4 py-2 border-t border-gray-200 dark:border-gray-700">
        <StepTimelineMini
          steps={manifest.steps || []}
          currentIndex={currentStepIndex}
          errors={allErrors}
          onStepClick={handleStepChange}
        />
      </div>

      {/* Controls */}
      <div className="flex-shrink-0 px-4 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
        <div className="flex items-center justify-between">
          {/* Playback Controls */}
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={handlePrevStep}
              disabled={currentStepIndex === 0}
            >
              ⏮
            </Button>
            <Button
              size="sm"
              onClick={handlePlayPause}
              disabled={totalSteps === 0}
            >
              {playback.isPlaying ? "⏸" : "▶"}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={handleNextStep}
              disabled={currentStepIndex >= totalSteps - 1}
            >
              ⏭
            </Button>
          </div>

          {/* Speed Control */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500">Speed:</span>
            <div className="w-20">
              <Slider
                min={0.5}
                max={3}
                value={playback.speed}
                onChange={(e) => handleSpeedChange(Number(e.target.value))}
              />
            </div>
            <span className="text-xs font-mono w-8">{playback.speed}x</span>
          </div>

          {/* Step Counter */}
          <div className="text-xs text-gray-500">
            {currentStepIndex + 1} / {totalSteps}
          </div>
        </div>
      </div>

      {/* Error Panel (Collapsible) */}
      {allErrors.length > 0 && showErrors && (
        <div className="flex-shrink-0 max-h-40 overflow-auto border-t border-gray-200 dark:border-gray-700">
          <div className="px-4 py-2 bg-gray-50 dark:bg-gray-900 sticky top-0 flex items-center justify-between">
            <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
              Validation Issues
            </span>
            <button
              onClick={() => setShowErrors(false)}
              className="text-xs text-gray-400 hover:text-gray-600"
            >
              Hide
            </button>
          </div>
          <div className="divide-y divide-gray-100 dark:divide-gray-800">
            {allErrors.slice(0, 10).map((error, index) => (
              <button
                key={index}
                onClick={() => onErrorClick?.(error)}
                className="w-full px-4 py-2 text-left hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
              >
                <div className="flex items-start gap-2">
                  <span
                    className={`
                      text-xs font-medium
                      ${error.type === "error" ? "text-red-600" : ""}
                      ${error.type === "warning" ? "text-yellow-600" : ""}
                      ${error.type === "info" ? "text-blue-600" : ""}
                    `}
                  >
                    {error.type === "error" && "❌"}
                    {error.type === "warning" && "⚠️"}
                    {error.type === "info" && "ℹ️"}
                  </span>
                  <div className="flex-1">
                    <p className="text-xs text-gray-700 dark:text-gray-300">
                      {error.message}
                    </p>
                    {error.path && (
                      <p className="text-xs text-gray-400 font-mono">
                        {error.path}
                      </p>
                    )}
                  </div>
                </div>
              </button>
            ))}
            {allErrors.length > 10 && (
              <div className="px-4 py-2 text-xs text-gray-400 text-center">
                +{allErrors.length - 10} more issues
              </div>
            )}
          </div>
        </div>
      )}

      {/* Show errors button if hidden */}
      {allErrors.length > 0 && !showErrors && (
        <button
          onClick={() => setShowErrors(true)}
          className="flex-shrink-0 px-4 py-2 text-xs text-center text-gray-500 hover:text-gray-700 border-t border-gray-200 dark:border-gray-700"
        >
          Show {allErrors.length} validation issues
        </button>
      )}
    </div>
  );
};

export default SimulationPreviewPanel;
