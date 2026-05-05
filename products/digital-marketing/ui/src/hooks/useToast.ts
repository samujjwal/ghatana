/**
 * Toast notification hook for surfacing mutation errors and success states.
 *
 * <p>P1-030: Provides toast notifications for mutation feedback with
 * actionable error messages and correlation ID support for diagnostics.</p>
 *
 * @doc.type hook
 * @doc.purpose Display toast notifications for user feedback
 * @doc.layer frontend
 */

import { useState, useCallback } from 'react';

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  correlationId?: string;
  duration?: number;
}

export interface UseToastReturn {
  toasts: Toast[];
  showSuccess: (message: string) => void;
  showError: (message: string, correlationId?: string) => void;
  showWarning: (message: string) => void;
  showInfo: (message: string) => void;
  dismissToast: (id: string) => void;
  clearAll: () => void;
}

export function useToast(): UseToastReturn {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const generateId = () => `toast-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

  const addToast = useCallback((toast: Omit<Toast, 'id'>) => {
    const id = generateId();
    const newToast: Toast = { ...toast, id };
    setToasts((prev) => [...prev, newToast]);

    // Auto-dismiss after duration (default 5s for errors, 3s for success)
    const duration = toast.duration ?? (toast.type === 'error' ? 5000 : 3000);
    setTimeout(() => {
      dismissToast(id);
    }, duration);

    return id;
  }, []);

  const showSuccess = useCallback((message: string) => {
    addToast({ type: 'success', message });
  }, [addToast]);

  const showError = useCallback((message: string, correlationId?: string) => {
    addToast({
      type: 'error',
      message,
      correlationId,
      duration: 8000, // Keep error toasts longer
    });
  }, [addToast]);

  const showWarning = useCallback((message: string) => {
    addToast({ type: 'warning', message });
  }, [addToast]);

  const showInfo = useCallback((message: string) => {
    addToast({ type: 'info', message });
  }, [addToast]);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const clearAll = useCallback(() => {
    setToasts([]);
  }, []);

  return {
    toasts,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    dismissToast,
    clearAll,
  };
}
