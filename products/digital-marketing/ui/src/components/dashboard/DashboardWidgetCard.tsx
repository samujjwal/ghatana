import React from 'react';

export type DashboardWidgetState = 'ready' | 'loading' | 'error' | 'unavailable';

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
  return (
    <div data-testid={testId} className="bg-white border border-gray-200 rounded-lg p-4">
      <h2 className="text-sm font-semibold text-gray-900 mb-3">{title}</h2>

      {state === 'loading' && <div className="animate-pulse h-20 bg-gray-100 rounded" />}

      {state === 'error' && (
        <div className="text-sm text-red-600" role="alert" data-testid={stateMessageTestId}>
          {message ?? 'Failed to load data'}
        </div>
      )}

      {state === 'unavailable' && (
        <div className="text-sm text-gray-600" role="status" data-testid={stateMessageTestId}>
          {message ?? 'Data is currently unavailable'}
        </div>
      )}

      {state === 'ready' && children}

      {footer}
    </div>
  );
}
