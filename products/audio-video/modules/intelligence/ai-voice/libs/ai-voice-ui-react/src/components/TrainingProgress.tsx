/**
 * @ghatana/ai-voice-ui-react - TrainingProgress
 * 
 * Training progress indicator component.
 * 
 * @doc.type component
 * @doc.purpose Training progress display
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import type { TrainingStatus } from '../types';

export interface TrainingProgressProps {
  /** Training status */
  status: TrainingStatus;
  
  /** Progress percentage (0-100) */
  progress: number;
  
  /** Model name being trained */
  modelName?: string;
  
  /** Error message if failed */
  error?: string;
  
  /** Cancel handler */
  onCancel?: () => void;
  
  /** Additional CSS classes */
  className?: string;
}

const statusLabels: Record<TrainingStatus, string> = {
  pending: 'Preparing...',
  preprocessing: 'Preprocessing samples...',
  extracting: 'Extracting features...',
  training: 'Training model...',
  completed: 'Training complete!',
  failed: 'Training failed',
};

const statusColors: Record<TrainingStatus, string> = {
  pending: 'bg-gray-500',
  preprocessing: 'bg-blue-500',
  extracting: 'bg-purple-500',
  training: 'bg-blue-500',
  completed: 'bg-green-500',
  failed: 'bg-red-500',
};

/**
 * Training progress indicator component.
 */
export const TrainingProgress: React.FC<TrainingProgressProps> = ({
  status,
  progress,
  modelName,
  error,
  onCancel,
  className,
}) => {
  const isActive = status !== 'completed' && status !== 'failed';
  
  return (
    <div className={twMerge(clsx('bg-gray-800 rounded-lg p-4'), className)}>
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <div>
          <h3 className="text-lg font-medium text-white">
            {modelName ? `Training: ${modelName}` : 'Training Voice Model'}
          </h3>
          <p className="text-sm text-gray-400">{statusLabels[status]}</p>
        </div>
        
        {isActive && onCancel && (
          <button
            onClick={onCancel}
            className="px-3 py-1.5 text-sm rounded-lg bg-red-600/20 text-red-400 hover:bg-red-600/30 transition-all"
          >
            Cancel
          </button>
        )}
      </div>

      {/* Progress bar */}
      <div className="mb-2">
        <div className="flex justify-between text-sm text-gray-400 mb-1">
          <span>{statusLabels[status]}</span>
          <span>{Math.round(progress)}%</span>
        </div>
        <div className="h-2 bg-gray-700 rounded-full overflow-hidden">
          <div
            className={clsx(
              'h-full transition-all duration-300',
              statusColors[status],
              isActive && 'animate-pulse'
            )}
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      {/* Steps indicator */}
      <div className="flex justify-between mt-4">
        {(['preprocessing', 'extracting', 'training', 'completed'] as TrainingStatus[]).map((step, i) => {
          const stepIndex = ['preprocessing', 'extracting', 'training', 'completed'].indexOf(status);
          const currentIndex = ['preprocessing', 'extracting', 'training', 'completed'].indexOf(step);
          const isComplete = currentIndex < stepIndex || status === 'completed';
          const isCurrent = step === status;
          
          return (
            <div key={step} className="flex flex-col items-center">
              <div
                className={clsx(
                  'w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium',
                  isComplete ? 'bg-green-600 text-white' :
                  isCurrent ? 'bg-blue-600 text-white' :
                  'bg-gray-700 text-gray-400'
                )}
              >
                {isComplete ? '✓' : i + 1}
              </div>
              <span className="text-xs text-gray-500 mt-1 capitalize">
                {step === 'completed' ? 'Done' : step}
              </span>
            </div>
          );
        })}
      </div>

      {/* Error message */}
      {error && (
        <div className="mt-4 p-3 bg-red-600/20 border border-red-600 rounded-lg">
          <p className="text-sm text-red-400">{error}</p>
        </div>
      )}

      {/* Success message */}
      {status === 'completed' && (
        <div className="mt-4 p-3 bg-green-600/20 border border-green-600 rounded-lg">
          <p className="text-sm text-green-400">
            Voice model trained successfully! You can now use it for voice conversion.
          </p>
        </div>
      )}
    </div>
  );
};
