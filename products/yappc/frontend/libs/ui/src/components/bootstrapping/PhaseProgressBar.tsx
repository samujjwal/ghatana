/**
 * Phase Progress Bar Component
 *
 * @description Visual progress indicator for bootstrapping phases with
 * step indicators, completion status, and phase transitions.
 *
 * @doc.type component
 * @doc.purpose Phase progress visualization
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Check,
  MessageSquare,
  Search,
  Settings2,
  CheckCircle2,
  Rocket,
  Circle,
  Loader2,
} from 'lucide-react';
import { useAtomValue } from 'jotai';

import { cn } from '@ghatana/ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

import type { BootstrapPhase } from '@ghatana/yappc-canvas';
import {
  currentPhaseAtom,
  confidenceScoreAtom,
  questionsAnsweredAtom,
  totalQuestionsAtom,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

export interface PhaseConfig {
  id: BootstrapPhase;
  label: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
}

export interface PhaseProgressBarProps {
  /** Current active phase (if not using atoms) */
  currentPhase?: BootstrapPhase;
  /** Override confidence score */
  confidenceScore?: number;
  /** Questions answered count */
  questionsAnswered?: number;
  /** Total questions count */
  totalQuestions?: number;
  /** Called when a phase step is clicked */
  onPhaseClick?: (phase: BootstrapPhase) => void;
  /** Allow clicking completed phases to go back */
  allowNavigation?: boolean;
  /** Display orientation */
  orientation?: 'horizontal' | 'vertical';
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Show phase labels */
  showLabels?: boolean;
  /** Show confidence score badge */
  showConfidence?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Phase Configuration
// =============================================================================

const PHASES: PhaseConfig[] = [
  {
    id: 'enter',
    label: 'Enter',
    description: 'Provide your initial project idea or upload existing documents',
    icon: MessageSquare,
  },
  {
    id: 'explore',
    label: 'Explore',
    description: 'AI explores requirements through interactive questions',
    icon: Search,
  },
  {
    id: 'refine',
    label: 'Refine',
    description: 'Fine-tune features, priorities, and technical decisions',
    icon: Settings2,
  },
  {
    id: 'validate',
    label: 'Validate',
    description: 'Review and validate the generated project blueprint',
    icon: CheckCircle2,
  },
  {
    id: 'complete',
    label: 'Complete',
    description: 'Project blueprint is ready for initialization',
    icon: Rocket,
  },
];

const PHASE_ORDER: Record<BootstrapPhase, number> = {
  enter: 0,
  explore: 1,
  refine: 2,
  validate: 3,
  complete: 4,
};

// =============================================================================
// Phase Step Component
// =============================================================================

interface PhaseStepProps {
  phase: PhaseConfig;
  status: 'completed' | 'current' | 'upcoming';
  index: number;
  totalSteps: number;
  onClick?: () => void;
  allowNavigation: boolean;
  orientation: 'horizontal' | 'vertical';
  size: 'sm' | 'md' | 'lg';
  showLabels: boolean;
}

const PhaseStep: React.FC<PhaseStepProps> = ({
  phase,
  status,
  index,
  totalSteps,
  onClick,
  allowNavigation,
  orientation,
  size,
  showLabels,
}) => {
  const Icon = phase.icon;
  const isClickable = allowNavigation && status === 'completed';
  const isLast = index === totalSteps - 1;

  const sizeConfig = {
    sm: {
      iconSize: 'h-6 w-6',
      stepSize: 'h-8 w-8',
      fontSize: 'text-xs',
      connectorThickness: 'h-0.5',
    },
    md: {
      iconSize: 'h-5 w-5',
      stepSize: 'h-10 w-10',
      fontSize: 'text-sm',
      connectorThickness: 'h-0.5',
    },
    lg: {
      iconSize: 'h-6 w-6',
      stepSize: 'h-12 w-12',
      fontSize: 'text-base',
      connectorThickness: 'h-1',
    },
  }[size];

  const statusStyles = {
    completed: {
      bg: 'bg-success-500',
      border: 'border-success-500',
      text: 'text-white',
      icon: Check,
    },
    current: {
      bg: 'bg-primary-500',
      border: 'border-primary-500',
      text: 'text-white',
      icon: null,
    },
    upcoming: {
      bg: 'bg-zinc-800',
      border: 'border-zinc-600',
      text: 'text-zinc-400',
      icon: null,
    },
  }[status];

  const IconToRender = statusStyles.icon || Icon;

  return (
    <div
      className={cn(
        'flex items-center',
        orientation === 'horizontal' ? 'flex-col' : 'flex-row',
        !isLast && orientation === 'horizontal' && 'flex-1'
      )}
    >
      <Tooltip>
        <TooltipTrigger asChild>
          <motion.button
            type="button"
            onClick={isClickable ? onClick : undefined}
            disabled={!isClickable}
            className={cn(
              'relative flex items-center justify-center rounded-full border-2 transition-all duration-200',
              sizeConfig.stepSize,
              statusStyles.bg,
              statusStyles.border,
              statusStyles.text,
              isClickable && 'cursor-pointer hover:scale-110 hover:shadow-lg',
              !isClickable && 'cursor-default'
            )}
            whileHover={isClickable ? { scale: 1.1 } : undefined}
            whileTap={isClickable ? { scale: 0.95 } : undefined}
          >
            {status === 'current' ? (
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', stiffness: 300, damping: 20 }}
              >
                <Icon className={sizeConfig.iconSize} />
              </motion.div>
            ) : (
              <IconToRender className={sizeConfig.iconSize} />
            )}

            {/* Pulse animation for current phase */}
            {status === 'current' && (
              <motion.div
                className="absolute inset-0 rounded-full border-2 border-primary-400"
                initial={{ scale: 1, opacity: 1 }}
                animate={{ scale: 1.5, opacity: 0 }}
                transition={{ duration: 1.5, repeat: Infinity }}
              />
            )}
          </motion.button>
        </TooltipTrigger>
        <TooltipContent side={orientation === 'horizontal' ? 'bottom' : 'right'}>
          <p className="font-medium">{phase.label}</p>
          <p className="text-xs text-zinc-400">{phase.description}</p>
        </TooltipContent>
      </Tooltip>

      {/* Label */}
      {showLabels && (
        <span
          className={cn(
            'font-medium transition-colors duration-200',
            sizeConfig.fontSize,
            orientation === 'horizontal' ? 'mt-2' : 'ml-3',
            status === 'completed' && 'text-success-500',
            status === 'current' && 'text-primary-400',
            status === 'upcoming' && 'text-zinc-500'
          )}
        >
          {phase.label}
        </span>
      )}

      {/* Connector */}
      {!isLast && orientation === 'horizontal' && (
        <div className="flex-1 px-2">
          <div className={cn('w-full rounded-full bg-zinc-700', sizeConfig.connectorThickness)}>
            <motion.div
              className={cn('rounded-full bg-success-500', sizeConfig.connectorThickness)}
              initial={{ width: '0%' }}
              animate={{
                width: status === 'completed' ? '100%' : status === 'current' ? '50%' : '0%',
              }}
              transition={{ duration: 0.5, ease: 'easeOut' }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Confidence Badge Component
// =============================================================================

interface ConfidenceBadgeProps {
  score: number;
  size: 'sm' | 'md' | 'lg';
}

const ConfidenceBadge: React.FC<ConfidenceBadgeProps> = ({ score, size }) => {
  const sizeClasses = {
    sm: 'px-2 py-0.5 text-xs',
    md: 'px-3 py-1 text-sm',
    lg: 'px-4 py-1.5 text-base',
  }[size];

  const colorClass = useMemo(() => {
    if (score >= 80) return 'bg-success-500/20 text-success-400 border-success-500/30';
    if (score >= 60) return 'bg-warning-500/20 text-warning-400 border-warning-500/30';
    return 'bg-zinc-700/50 text-zinc-400 border-zinc-600';
  }, [score]);

  return (
    <motion.div
      className={cn('rounded-full border font-medium', sizeClasses, colorClass)}
      initial={{ scale: 0.8, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ type: 'spring', stiffness: 300, damping: 20 }}
    >
      {score}% confidence
    </motion.div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const PhaseProgressBar: React.FC<PhaseProgressBarProps> = ({
  currentPhase: currentPhaseProp,
  confidenceScore: confidenceScoreProp,
  questionsAnswered: questionsAnsweredProp,
  totalQuestions: totalQuestionsProp,
  onPhaseClick,
  allowNavigation = false,
  orientation = 'horizontal',
  size = 'md',
  showLabels = true,
  showConfidence = true,
  className,
}) => {
  // Use atoms if props not provided
  const atomPhase = useAtomValue(currentPhaseAtom);
  const atomConfidence = useAtomValue(confidenceScoreAtom);
  const atomAnswered = useAtomValue(questionsAnsweredAtom);
  const atomTotal = useAtomValue(totalQuestionsAtom);

  const currentPhase = currentPhaseProp ?? atomPhase;
  const confidenceScore = confidenceScoreProp ?? atomConfidence;
  const questionsAnswered = questionsAnsweredProp ?? atomAnswered;
  const totalQuestions = totalQuestionsProp ?? atomTotal;

  const currentPhaseIndex = PHASE_ORDER[currentPhase];

  const getPhaseStatus = (phaseIndex: number): 'completed' | 'current' | 'upcoming' => {
    if (phaseIndex < currentPhaseIndex) return 'completed';
    if (phaseIndex === currentPhaseIndex) return 'current';
    return 'upcoming';
  };

  return (
    <div
      className={cn(
        'flex w-full items-center',
        orientation === 'vertical' && 'flex-col items-start',
        className
      )}
    >
      {/* Progress Steps */}
      <div
        className={cn(
          'flex flex-1',
          orientation === 'horizontal' ? 'w-full items-center' : 'flex-col gap-4'
        )}
      >
        {PHASES.map((phase, index) => (
          <PhaseStep
            key={phase.id}
            phase={phase}
            status={getPhaseStatus(index)}
            index={index}
            totalSteps={PHASES.length}
            onClick={() => onPhaseClick?.(phase.id)}
            allowNavigation={allowNavigation}
            orientation={orientation}
            size={size}
            showLabels={showLabels}
          />
        ))}
      </div>

      {/* Confidence Badge */}
      {showConfidence && (
        <div
          className={cn(
            'flex items-center gap-3',
            orientation === 'horizontal' ? 'ml-6' : 'mt-4'
          )}
        >
          <ConfidenceBadge score={confidenceScore} size={size} />

          {/* Questions Progress */}
          {totalQuestions > 0 && (
            <span className="text-sm text-zinc-400">
              {questionsAnswered}/{totalQuestions} questions
            </span>
          )}
        </div>
      )}
    </div>
  );
};

PhaseProgressBar.displayName = 'PhaseProgressBar';

export default PhaseProgressBar;
