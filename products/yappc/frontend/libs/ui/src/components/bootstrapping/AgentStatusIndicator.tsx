/**
 * Agent Status Indicator Component
 *
 * @description Displays the current AI agent status with animated indicators
 * for thinking, typing, processing, and other states during bootstrapping.
 *
 * @doc.type component
 * @doc.purpose AI agent status display
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Bot,
  Brain,
  Loader2,
  CheckCircle2,
  AlertCircle,
  PauseCircle,
  Sparkles,
  MessageSquare,
  Search,
  FileText,
  Code2,
  Wand2,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';

// =============================================================================
// Types
// =============================================================================

export type AgentStatusType =
  | 'idle'
  | 'thinking'
  | 'typing'
  | 'searching'
  | 'analyzing'
  | 'generating'
  | 'processing'
  | 'completed'
  | 'error'
  | 'paused'
  | 'waiting';

export interface AgentStatusIndicatorProps {
  /** Current agent status */
  status: AgentStatusType;
  /** Custom status message */
  message?: string;
  /** Show detailed progress (0-100) */
  progress?: number;
  /** Processing step number */
  step?: number;
  /** Total steps */
  totalSteps?: number;
  /** Agent name/identifier */
  agentName?: string;
  /** Show avatar/icon */
  showAvatar?: boolean;
  /** Compact display mode */
  compact?: boolean;
  /** Show processing time */
  showDuration?: boolean;
  /** Processing start time */
  startTime?: Date;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Status Configuration
// =============================================================================

interface StatusConfig {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  color: string;
  bgColor: string;
  animate: boolean;
  pulseColor?: string;
}

const STATUS_CONFIG: Record<AgentStatusType, StatusConfig> = {
  idle: {
    icon: Bot,
    label: 'Ready',
    color: 'text-neutral-500 dark:text-neutral-400',
    bgColor: 'bg-neutral-100 dark:bg-neutral-800',
    animate: false,
  },
  thinking: {
    icon: Brain,
    label: 'Thinking',
    color: 'text-purple-600 dark:text-purple-400',
    bgColor: 'bg-purple-100 dark:bg-purple-900/30',
    animate: true,
    pulseColor: 'bg-purple-400',
  },
  typing: {
    icon: MessageSquare,
    label: 'Typing',
    color: 'text-blue-600 dark:text-blue-400',
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    animate: true,
    pulseColor: 'bg-blue-400',
  },
  searching: {
    icon: Search,
    label: 'Searching',
    color: 'text-amber-600 dark:text-amber-400',
    bgColor: 'bg-amber-100 dark:bg-amber-900/30',
    animate: true,
    pulseColor: 'bg-amber-400',
  },
  analyzing: {
    icon: FileText,
    label: 'Analyzing',
    color: 'text-cyan-600 dark:text-cyan-400',
    bgColor: 'bg-cyan-100 dark:bg-cyan-900/30',
    animate: true,
    pulseColor: 'bg-cyan-400',
  },
  generating: {
    icon: Wand2,
    label: 'Generating',
    color: 'text-indigo-600 dark:text-indigo-400',
    bgColor: 'bg-indigo-100 dark:bg-indigo-900/30',
    animate: true,
    pulseColor: 'bg-indigo-400',
  },
  processing: {
    icon: Loader2,
    label: 'Processing',
    color: 'text-primary-600 dark:text-primary-400',
    bgColor: 'bg-primary-100 dark:bg-primary-900/30',
    animate: true,
    pulseColor: 'bg-primary-400',
  },
  completed: {
    icon: CheckCircle2,
    label: 'Completed',
    color: 'text-success-600 dark:text-success-400',
    bgColor: 'bg-success-100 dark:bg-success-900/30',
    animate: false,
  },
  error: {
    icon: AlertCircle,
    label: 'Error',
    color: 'text-error-600 dark:text-error-400',
    bgColor: 'bg-error-100 dark:bg-error-900/30',
    animate: false,
  },
  paused: {
    icon: PauseCircle,
    label: 'Paused',
    color: 'text-warning-600 dark:text-warning-400',
    bgColor: 'bg-warning-100 dark:bg-warning-900/30',
    animate: false,
  },
  waiting: {
    icon: Sparkles,
    label: 'Waiting',
    color: 'text-neutral-600 dark:text-neutral-400',
    bgColor: 'bg-neutral-100 dark:bg-neutral-800',
    animate: true,
    pulseColor: 'bg-neutral-400',
  },
};

// =============================================================================
// Animated Dots
// =============================================================================

const ThinkingDots: React.FC<{ color?: string }> = ({ color = 'bg-current' }) => {
  return (
    <div className="flex items-center gap-1">
      {[0, 1, 2].map((i) => (
        <motion.div
          key={i}
          className={cn('h-1.5 w-1.5 rounded-full', color)}
          animate={{
            y: [0, -4, 0],
            opacity: [0.5, 1, 0.5],
          }}
          transition={{
            duration: 0.6,
            repeat: Infinity,
            delay: i * 0.15,
            ease: 'easeInOut',
          }}
        />
      ))}
    </div>
  );
};

// =============================================================================
// Typing Cursor
// =============================================================================

const TypingCursor: React.FC = () => {
  return (
    <motion.span
      className="ml-0.5 inline-block h-4 w-0.5 bg-current"
      animate={{ opacity: [1, 0] }}
      transition={{
        duration: 0.5,
        repeat: Infinity,
        repeatType: 'reverse',
      }}
    />
  );
};

// =============================================================================
// Progress Ring
// =============================================================================

interface ProgressRingProps {
  progress: number;
  size?: number;
  strokeWidth?: number;
  className?: string;
}

const ProgressRing: React.FC<ProgressRingProps> = ({
  progress,
  size = 32,
  strokeWidth = 3,
  className,
}) => {
  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const strokeDashoffset = circumference - (progress / 100) * circumference;

  return (
    <svg width={size} height={size} className={className}>
      {/* Background circle */}
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        className="text-neutral-200 dark:text-neutral-700"
      />
      {/* Progress circle */}
      <motion.circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeDasharray={circumference}
        initial={{ strokeDashoffset: circumference }}
        animate={{ strokeDashoffset }}
        transition={{ duration: 0.3, ease: 'easeOut' }}
        className="text-primary-500"
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
      />
    </svg>
  );
};

// =============================================================================
// Duration Display
// =============================================================================

const useDuration = (startTime?: Date) => {
  const [duration, setDuration] = React.useState(0);

  React.useEffect(() => {
    if (!startTime) return;

    const interval = setInterval(() => {
      setDuration(Math.floor((Date.now() - startTime.getTime()) / 1000));
    }, 1000);

    return () => clearInterval(interval);
  }, [startTime]);

  const formatted = useMemo(() => {
    const minutes = Math.floor(duration / 60);
    const seconds = duration % 60;
    return minutes > 0
      ? `${minutes}m ${seconds}s`
      : `${seconds}s`;
  }, [duration]);

  return formatted;
};

// =============================================================================
// Main Component
// =============================================================================

export const AgentStatusIndicator: React.FC<AgentStatusIndicatorProps> = ({
  status,
  message,
  progress,
  step,
  totalSteps,
  agentName = 'AI Assistant',
  showAvatar = true,
  compact = false,
  showDuration = false,
  startTime,
  className,
}) => {
  const config = STATUS_CONFIG[status];
  const Icon = config.icon;
  const duration = useDuration(showDuration && config.animate ? startTime : undefined);

  // Computed display message
  const displayMessage = useMemo(() => {
    if (message) return message;
    if (step && totalSteps) return `Step ${step} of ${totalSteps}`;
    return config.label;
  }, [message, step, totalSteps, config.label]);

  // Compact mode
  if (compact) {
    return (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className={cn(
          'inline-flex items-center gap-2 rounded-full px-3 py-1.5',
          config.bgColor,
          className
        )}
      >
        {/* Spinning icon for active states */}
        {config.animate ? (
          <motion.div
            animate={{ rotate: status === 'processing' || status === 'searching' ? 360 : 0 }}
            transition={{
              duration: 1,
              repeat: status === 'processing' || status === 'searching' ? Infinity : 0,
              ease: 'linear',
            }}
          >
            <Icon className={cn('h-4 w-4', config.color)} />
          </motion.div>
        ) : (
          <Icon className={cn('h-4 w-4', config.color)} />
        )}

        <span className={cn('text-sm font-medium', config.color)}>
          {displayMessage}
        </span>

        {/* Thinking dots */}
        {(status === 'thinking' || status === 'typing') && (
          <ThinkingDots color={config.pulseColor} />
        )}

        {/* Progress percentage */}
        {progress !== undefined && (
          <Badge variant="outline" className="text-xs">
            {Math.round(progress)}%
          </Badge>
        )}
      </motion.div>
    );
  }

  // Full mode
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      className={cn(
        'flex items-start gap-3 rounded-lg p-4',
        config.bgColor,
        className
      )}
    >
      {/* Avatar/Icon */}
      {showAvatar && (
        <div className="relative flex-shrink-0">
          {/* Pulse effect for active states */}
          {config.animate && config.pulseColor && (
            <motion.div
              className={cn(
                'absolute -inset-1 rounded-full opacity-30',
                config.pulseColor
              )}
              animate={{
                scale: [1, 1.2, 1],
                opacity: [0.3, 0.1, 0.3],
              }}
              transition={{
                duration: 1.5,
                repeat: Infinity,
                ease: 'easeInOut',
              }}
            />
          )}

          {/* Progress ring or icon container */}
          {progress !== undefined ? (
            <div className="relative">
              <ProgressRing progress={progress} size={40} strokeWidth={3} />
              <div className="absolute inset-0 flex items-center justify-center">
                <Icon className={cn('h-5 w-5', config.color)} />
              </div>
            </div>
          ) : (
            <div
              className={cn(
                'flex h-10 w-10 items-center justify-center rounded-full',
                'bg-white dark:bg-neutral-800'
              )}
            >
              {config.animate && (status === 'processing' || status === 'searching') ? (
                <motion.div
                  animate={{ rotate: 360 }}
                  transition={{
                    duration: 1,
                    repeat: Infinity,
                    ease: 'linear',
                  }}
                >
                  <Icon className={cn('h-5 w-5', config.color)} />
                </motion.div>
              ) : (
                <Icon className={cn('h-5 w-5', config.color)} />
              )}
            </div>
          )}
        </div>
      )}

      {/* Content */}
      <div className="flex-1 min-w-0">
        {/* Header */}
        <div className="flex items-center gap-2">
          <span className="font-medium text-neutral-900 dark:text-neutral-100">
            {agentName}
          </span>
          <Badge
            variant="outline"
            className={cn('text-xs', config.color)}
          >
            {config.label}
          </Badge>
          {showDuration && config.animate && (
            <span className="text-xs text-neutral-500">{duration}</span>
          )}
        </div>

        {/* Message */}
        <div className={cn('mt-1 flex items-center gap-1 text-sm', config.color)}>
          <span>{displayMessage}</span>
          {status === 'typing' && <TypingCursor />}
          {status === 'thinking' && <ThinkingDots color={config.pulseColor} />}
        </div>

        {/* Progress bar */}
        {progress !== undefined && (
          <div className="mt-2">
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-neutral-200 dark:bg-neutral-700">
              <motion.div
                className="h-full bg-primary-500 rounded-full"
                initial={{ width: 0 }}
                animate={{ width: `${progress}%` }}
                transition={{ duration: 0.3, ease: 'easeOut' }}
              />
            </div>
            <div className="mt-1 flex items-center justify-between text-xs text-neutral-500">
              <span>
                {step && totalSteps && `Step ${step}/${totalSteps}`}
              </span>
              <span>{Math.round(progress)}%</span>
            </div>
          </div>
        )}
      </div>
    </motion.div>
  );
};

export default AgentStatusIndicator;
