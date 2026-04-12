/**
 * @fileoverview OperationStatus - Displays the running/completed/failed/paused status
 * of an operation with a label and optional correlation ID.
 *
 * @doc.type component
 * @doc.purpose Shows the current state of an AI or system operation with visual status indicator.
 * @doc.category atom
 * @doc.tags ai, visibility, status
 */

import * as React from 'react';
import type { AIVisibilityContract } from '@ghatana/platform-events';
import { Spinner } from './Spinner';

export type OperationState = 'idle' | 'running' | 'completed' | 'failed' | 'paused';

export interface OperationStatusProps {
  /** Current state of the operation */
  readonly state: OperationState;
  /** Human-readable label describing the operation */
  readonly label: string;
  /** Optional correlation ID for tracing */
  readonly correlationId?: string;
  /** Optional callback when user dismisses the status */
  readonly onDismiss?: () => void;
  /** Additional CSS classes */
  readonly className?: string;
  /** Size variant */
  readonly size?: 'sm' | 'md' | 'lg';
  /** Whether to show the correlation ID */
  readonly showCorrelationId?: boolean;
}

const stateConfig: Record<
  OperationState,
  {
    readonly icon: React.ReactNode;
    readonly color: string;
    readonly bgColor: string;
    readonly label: string;
  }
> = {
  idle: {
    icon: (
      <div className="h-2 w-2 rounded-full bg-gray-400" aria-hidden="true" />
    ),
    color: 'text-gray-600',
    bgColor: 'bg-gray-100',
    label: 'Idle',
  },
  running: {
    icon: <Spinner size="sm" className="text-blue-500" aria-label="Loading" />,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    label: 'Running',
  },
  completed: {
    icon: (
      <svg
        className="h-4 w-4 text-green-500"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M5 13l4 4L19 7"
        />
      </svg>
    ),
    color: 'text-green-600',
    bgColor: 'bg-green-50',
    label: 'Completed',
  },
  failed: {
    icon: (
      <svg
        className="h-4 w-4 text-red-500"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M6 18L18 6M6 6l12 12"
        />
      </svg>
    ),
    color: 'text-red-600',
    bgColor: 'bg-red-50',
    label: 'Failed',
  },
  paused: {
    icon: (
      <svg
        className="h-4 w-4 text-yellow-500"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z"
        />
      </svg>
    ),
    color: 'text-yellow-600',
    bgColor: 'bg-yellow-50',
    label: 'Paused',
  },
};

const sizeConfig = {
  sm: 'text-xs px-2 py-1 gap-1.5',
  md: 'text-sm px-3 py-1.5 gap-2',
  lg: 'text-base px-4 py-2 gap-2.5',
};

/**
 * OperationStatus component - displays operation state with visual indicator.
 */
export const OperationStatus: React.FC<OperationStatusProps> = React.memo(({
  state,
  label,
  correlationId,
  onDismiss,
  className = '',
  size = 'md',
  showCorrelationId = false,
}) => {
  const config = stateConfig[state];
  const sizeClasses = sizeConfig[size];

  return (
    <div
      className={`inline-flex items-center rounded-md ${config.bgColor} ${sizeClasses} ${className}`}
      role="status"
      aria-live={state === 'running' ? 'polite' : 'off'}
      aria-label={`${config.label}: ${label}`}
    >
      <span className="flex-shrink-0">{config.icon}</span>
      <span className={`${config.color} font-medium`}>{label}</span>
      {showCorrelationId && correlationId && (
        <span
          className={`${config.color} opacity-60 font-mono text-xs ml-2`}
          title="Correlation ID"
        >
          {correlationId.slice(0, 8)}...
        </span>
      )}
      {onDismiss && (
        <button
          onClick={onDismiss}
          className={`ml-auto ${config.color} opacity-60 hover:opacity-100 focus:opacity-100 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-blue-500 rounded`}
          aria-label="Dismiss"
          type="button"
        >
          <svg
            className="h-4 w-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      )}
    </div>
  );
});

OperationStatus.displayName = 'OperationStatus';

/**
 * Creates an OperationStatus from an AIVisibilityContract.
 */
export function createOperationStatusFromContract(
  contract: AIVisibilityContract,
  options: Omit<OperationStatusProps, 'state' | 'label' | 'correlationId'> = {}
): OperationStatusProps {
  return {
    state: contract.operationState,
    label: contract.operationLabel,
    correlationId: contract.correlationId,
    ...options,
  };
}
