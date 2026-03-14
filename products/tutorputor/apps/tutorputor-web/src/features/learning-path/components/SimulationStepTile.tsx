/**
 * SimulationStepTile Component
 *
 * UI card showing simulation thumbnail, domain, difficulty, expected time,
 * and "Launch simulation" CTA. Used in learning paths and dashboards.
 *
 * @doc.type component
 * @doc.purpose Display simulation step as interactive tile in learning path
 * @doc.layer product
 * @doc.pattern Component
 */

import React from "react";
import { useNavigate } from "react-router-dom";
import { Button, Badge, Progress, Card } from "@ghatana/design-system";
import type { 
  SimulationLearningStep, 
  StepProgress, 
  SimulationDomain, 
  Difficulty,
  StepProgressStatus,
} from "../hooks/useSimulationSteps";

// =============================================================================
// Types
// =============================================================================

export interface SimulationStepTileProps {
  /** The simulation step to display */
  step: SimulationLearningStep;
  /** Progress on this step (if any) */
  progress?: StepProgress;
  /** Callback when launching the simulation */
  onLaunch?: (step: SimulationLearningStep) => void;
  /** Whether this step is locked (prerequisites not met) */
  isLocked?: boolean;
  /** Whether the tile is in compact mode */
  compact?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Domain Configuration
// =============================================================================

const DOMAIN_CONFIG: Record<
  SimulationDomain,
  { label: string; color: string; icon: string; bgGradient: string }
> = {
  CS_DISCRETE: {
    label: "Computer Science",
    color: "bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200",
    icon: "💻",
    bgGradient: "from-purple-500/10 to-purple-600/5",
  },
  PHYSICS: {
    label: "Physics",
    color: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
    icon: "⚛️",
    bgGradient: "from-blue-500/10 to-blue-600/5",
  },
  ECONOMICS: {
    label: "Economics",
    color: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
    icon: "📈",
    bgGradient: "from-green-500/10 to-green-600/5",
  },
  CHEMISTRY: {
    label: "Chemistry",
    color: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
    icon: "🧪",
    bgGradient: "from-yellow-500/10 to-yellow-600/5",
  },
  BIOLOGY: {
    label: "Biology",
    color: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200",
    icon: "🧬",
    bgGradient: "from-emerald-500/10 to-emerald-600/5",
  },
  MEDICINE: {
    label: "Medicine",
    color: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
    icon: "💊",
    bgGradient: "from-red-500/10 to-red-600/5",
  },
  ENGINEERING: {
    label: "Engineering",
    color: "bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200",
    icon: "⚙️",
    bgGradient: "from-orange-500/10 to-orange-600/5",
  },
  MATHEMATICS: {
    label: "Mathematics",
    color: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200",
    icon: "📐",
    bgGradient: "from-indigo-500/10 to-indigo-600/5",
  },
};

const DIFFICULTY_CONFIG: Record<Difficulty, { label: string; color: string }> = {
  INTRO: {
    label: "Beginner",
    color: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  },
  INTERMEDIATE: {
    label: "Intermediate",
    color: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
  },
  ADVANCED: {
    label: "Advanced",
    color: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  },
};

const STATUS_CONFIG: Record<StepProgressStatus, { label: string; color: string; buttonText: string; buttonVariant: "solid" | "outline" | "ghost" }> = {
  not_started: {
    label: "Not Started",
    color: "text-gray-500 dark:text-gray-400",
    buttonText: "Start",
    buttonVariant: "solid" as const,
  },
  in_progress: {
    label: "In Progress",
    color: "text-blue-500 dark:text-blue-400",
    buttonText: "Continue",
    buttonVariant: "outline" as const,
  },
  completed: {
    label: "Completed",
    color: "text-green-500 dark:text-green-400",
    buttonText: "Review",
    buttonVariant: "outline" as const,
  },
  skipped: {
    label: "Skipped",
    color: "text-gray-400 dark:text-gray-500",
    buttonText: "Try Again",
    buttonVariant: "ghost" as const,
  },
};

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Format duration in minutes to human-readable string.
 */
function formatDuration(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
}

/**
 * Get status from progress or default to not_started.
 */
function getStatus(progress?: StepProgress): StepProgressStatus {
  return progress?.status ?? "not_started";
}

// =============================================================================
// Component
// =============================================================================

export const SimulationStepTile: React.FC<SimulationStepTileProps> = ({
  step,
  progress,
  onLaunch,
  isLocked = false,
  compact = false,
  className = "",
}) => {
  const navigate = useNavigate();
  const domainConfig = DOMAIN_CONFIG[step.domain];
  const difficultyConfig = DIFFICULTY_CONFIG[step.difficulty];
  const status = getStatus(progress);
  const statusConfig = STATUS_CONFIG[status];

  const handleLaunch = () => {
    if (isLocked) return;

    if (onLaunch) {
      onLaunch(step);
    } else {
      // Default navigation to simulation player
      navigate(`/simulation/${step.simulationId}`, {
        state: {
          stepId: step.id,
          manifestId: step.manifestId,
        },
      });
    }
  };

  // Calculate progress percentage for in-progress steps
  const progressPercent =
    status === "completed"
      ? 100
      : status === "in_progress" && progress?.timeSpentSeconds
      ? Math.min(
          100,
          (progress.timeSpentSeconds / (step.estimatedTimeMinutes * 60)) * 100
        )
      : 0;

  if (compact) {
    return (
      <Card
        className={`
          relative overflow-hidden p-3 transition-all hover:shadow-md
          ${isLocked ? "opacity-60 cursor-not-allowed" : "cursor-pointer hover:scale-[1.02]"}
          ${className}
        `}
        onClick={!isLocked ? handleLaunch : undefined}
      >
        <div className="flex items-center gap-3">
          {/* Domain Icon */}
          <div
            className={`
              flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center
              bg-gradient-to-br ${domainConfig.bgGradient}
            `}
          >
            <span className="text-xl">{domainConfig.icon}</span>
          </div>

          {/* Content */}
          <div className="flex-1 min-w-0">
            <h4 className="font-medium text-sm text-gray-900 dark:text-gray-100 truncate">
              {step.metadata.title}
            </h4>
            <div className="flex items-center gap-2 mt-1">
              <span className={`text-xs ${statusConfig.color}`}>
                {statusConfig.label}
              </span>
              <span className="text-xs text-gray-400">•</span>
              <span className="text-xs text-gray-500 dark:text-gray-400">
                {formatDuration(step.estimatedTimeMinutes)}
              </span>
            </div>
          </div>

          {/* Status indicator */}
          {status === "completed" && (
            <div className="flex-shrink-0 text-green-500">
              <CheckCircleIcon />
            </div>
          )}
          {status === "in_progress" && (
            <div className="flex-shrink-0">
              <Progress value={progressPercent} size="sm" className="w-12" />
            </div>
          )}
          {isLocked && (
            <div className="flex-shrink-0 text-gray-400">
              <LockIcon />
            </div>
          )}
        </div>
      </Card>
    );
  }

  return (
    <Card
      className={`
        relative overflow-hidden transition-all hover:shadow-lg
        ${isLocked ? "opacity-60" : "hover:scale-[1.01]"}
        ${className}
      `}
    >
      {/* Gradient header */}
      <div
        className={`
          h-24 bg-gradient-to-br ${domainConfig.bgGradient}
          flex items-center justify-center
        `}
      >
        <span className="text-4xl">{domainConfig.icon}</span>
        {step.metadata.thumbnailUrl && (
          <img
            src={step.metadata.thumbnailUrl}
            alt={step.metadata.title}
            className="absolute inset-0 w-full h-24 object-cover opacity-20"
          />
        )}
      </div>

      {/* Content */}
      <div className="p-4">
        {/* Badges */}
        <div className="flex flex-wrap gap-2 mb-3">
          <Badge className={domainConfig.color}>{domainConfig.label}</Badge>
          <Badge className={difficultyConfig.color}>{difficultyConfig.label}</Badge>
        </div>

        {/* Title */}
        <h3 className="font-semibold text-lg text-gray-900 dark:text-gray-100 mb-2 line-clamp-2">
          {step.metadata.title}
        </h3>

        {/* Description */}
        {step.metadata.description && (
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-3 line-clamp-2">
            {step.metadata.description}
          </p>
        )}

        {/* Skills */}
        {step.skills.length > 0 && (
          <div className="flex flex-wrap gap-1 mb-3">
            {step.skills.slice(0, 3).map((skill) => (
              <span
                key={skill.skillId}
                className="text-xs px-2 py-0.5 rounded-full bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400"
              >
                {skill.name}
              </span>
            ))}
            {step.skills.length > 3 && (
              <span className="text-xs px-2 py-0.5 rounded-full bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400">
                +{step.skills.length - 3} more
              </span>
            )}
          </div>
        )}

        {/* Meta info */}
        <div className="flex items-center justify-between text-sm text-gray-500 dark:text-gray-400 mb-4">
          <div className="flex items-center gap-1">
            <ClockIcon />
            <span>{formatDuration(step.estimatedTimeMinutes)}</span>
          </div>
          <span className={statusConfig.color}>{statusConfig.label}</span>
        </div>

        {/* Progress bar (if in progress) */}
        {status === "in_progress" && (
          <div className="mb-4">
            <Progress value={progressPercent} />
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
              {Math.round(progressPercent)}% complete
            </p>
          </div>
        )}

        {/* Score (if completed) */}
        {status === "completed" && progress?.score !== undefined && (
          <div className="flex items-center gap-2 mb-4 p-2 rounded-lg bg-green-50 dark:bg-green-900/20">
            <CheckCircleIcon className="text-green-500" />
            <span className="text-sm text-green-700 dark:text-green-300">
              Score: {progress.score}%
            </span>
          </div>
        )}

        {/* Action button */}
        <Button
          onClick={handleLaunch}
          disabled={isLocked}
          variant={statusConfig.buttonVariant}
          className="w-full"
        >
          {isLocked ? (
            <>
              <LockIcon className="mr-2 h-4 w-4" />
              Prerequisites Required
            </>
          ) : (
            <>
              {status === "not_started" && <PlayIcon className="mr-2 h-4 w-4" />}
              {status === "in_progress" && <ArrowRightIcon className="mr-2 h-4 w-4" />}
              {status === "completed" && <RefreshIcon className="mr-2 h-4 w-4" />}
              {status === "skipped" && <RetryIcon className="mr-2 h-4 w-4" />}
              {statusConfig.buttonText}
            </>
          )}
        </Button>
      </div>

      {/* Locked overlay */}
      {isLocked && (
        <div className="absolute inset-0 bg-gray-900/10 dark:bg-gray-900/30 flex items-center justify-center">
          <div className="bg-white dark:bg-gray-800 rounded-lg p-3 shadow-lg">
            <LockIcon className="h-6 w-6 text-gray-400 mx-auto mb-1" />
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Complete prerequisites first
            </p>
          </div>
        </div>
      )}
    </Card>
  );
};

// =============================================================================
// Icon Components (inline for simplicity)
// =============================================================================

const ClockIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={`h-4 w-4 ${className}`}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
    />
  </svg>
);

const PlayIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={`h-4 w-4 ${className}`}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"
    />
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
    />
  </svg>
);

const ArrowRightIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={`h-4 w-4 ${className}`}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M13 7l5 5m0 0l-5 5m5-5H6"
    />
  </svg>
);

const RefreshIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={`h-4 w-4 ${className}`}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
    />
  </svg>
);

const RetryIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={`h-4 w-4 ${className}`}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
    />
  </svg>
);

const CheckCircleIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={`h-5 w-5 ${className}`}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
    />
  </svg>
);

const LockIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={`h-5 w-5 ${className}`}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
    />
  </svg>
);

export default SimulationStepTile;
