import React from 'react';

export type DashboardWidgetState =
  | 'ready'
  | 'loading'
  | 'error'
  | 'unavailable'
  | 'partial'
  | 'stale'
  | 'unauthorized'
  | 'connector-disabled';

interface DashboardWidgetCardProps {
  testId: string;
  title: string;
  state?: DashboardWidgetState;
  message?: string;
  stateMessageTestId?: string;
  children?: React.ReactNode;
  footer?: React.ReactNode;
}

export function DashboardWidgetCard({
  testId,
  title,
  state = 'ready',
  message,
  stateMessageTestId,
  children,
  footer,
}: DashboardWidgetCardProps): React.ReactElement {
  const isStateMessage = [
    'error',
    'unavailable',
    'partial',
    'stale',
    'unauthorized',
    'connector-disabled',
  ].includes(state);

  const stateToneClass: Record<Exclude<DashboardWidgetState, 'ready' | 'loading'>, string> = {
    error: 'text-red-600',
    unavailable: 'text-gray-600',
    partial: 'text-yellow-700',
    stale: 'text-orange-700',
    unauthorized: 'text-red-700',
    'connector-disabled': 'text-gray-700',
  };

  return (
    <div data-testid={testId} className="bg-white border border-gray-200 rounded-lg p-4">
      <h2 className="text-sm font-semibold text-gray-900 mb-3">{title}</h2>

      {state === 'loading' && (
        <div className="animate-pulse h-20 bg-gray-100 rounded" data-testid={stateMessageTestId} />
      )}

      {isStateMessage && (
        <div
          className={`text-sm ${stateToneClass[state as Exclude<DashboardWidgetState, 'ready' | 'loading'>]}`}
          role={state === 'error' || state === 'unauthorized' ? 'alert' : 'status'}
          data-testid={stateMessageTestId}
        >
          {message ?? defaultStateMessage(state)}
        </div>
      )}

      {state === 'ready' && children}

      {footer}
    </div>
  );
}

function defaultStateMessage(state: DashboardWidgetState): string {
  switch (state) {
    case 'error':
      return 'Failed to load data';
    case 'partial':
      return 'Partial data available';
    case 'stale':
      return 'Data is stale';
    case 'unauthorized':
      return 'Not authorized to view this data';
    case 'connector-disabled':
      return 'Connector is disabled';
    case 'unavailable':
      return 'Data is currently unavailable';
    default:
      return '';
  }
}
