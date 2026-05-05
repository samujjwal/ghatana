/**
 * Toast notification component for displaying user feedback.
 *
 * <p>P1-030: Displays toast notifications with actionable messages
 * and correlation ID support for diagnostics.</p>
 *
 * @doc.type component
 * @doc.purpose Display toast notifications
 * @doc.layer frontend
 */

import React from 'react';
import type { Toast } from '@/hooks/useToast';

interface ToastContainerProps {
  toasts: Toast[];
  onDismiss: (id: string) => void;
}

export function ToastContainer({ toasts, onDismiss }: ToastContainerProps): React.ReactElement {
  return (
    <div className="fixed top-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onDismiss={onDismiss} />
      ))}
    </div>
  );
}

interface ToastItemProps {
  toast: Toast;
  onDismiss: (id: string) => void;
}

function ToastItem({ toast, onDismiss }: ToastItemProps): React.ReactElement {
  const bgColors = {
    success: 'bg-green-50 border-green-200 text-green-800',
    error: 'bg-red-50 border-red-200 text-red-800',
    warning: 'bg-yellow-50 border-yellow-200 text-yellow-800',
    info: 'bg-blue-50 border-blue-200 text-blue-800',
  };

  const icons = {
    success: '✓',
    error: '✕',
    warning: '⚠',
    info: 'ℹ',
  };

  return (
    <div
      role="alert"
      className={`min-w-[300px] max-w-[400px] p-4 rounded-lg border shadow-lg ${bgColors[toast.type]} animate-in slide-in-from-right`}
      data-testid={`toast-${toast.type}`}
    >
      <div className="flex items-start gap-3">
        <span className="text-lg">{icons[toast.type]}</span>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium">{toast.message}</p>
          {toast.correlationId && (
            <p className="mt-1 text-xs opacity-70">
              Correlation ID: <code className="font-mono">{toast.correlationId}</code>
            </p>
          )}
        </div>
        <button
          onClick={() => onDismiss(toast.id)}
          className="text-sm opacity-50 hover:opacity-100"
          aria-label="Dismiss"
          data-testid="toast-dismiss"
        >
          ✕
        </button>
      </div>
    </div>
  );
}
