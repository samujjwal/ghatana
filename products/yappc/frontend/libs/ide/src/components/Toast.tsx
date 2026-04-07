/**
 * IDE Toast utilities.
 *
 * Re-exports core Toast components from @ghatana/ui and provides
 * the convenience {@link useToastNotifications} hook for IDE components.
 *
 * @doc.type module
 * @doc.purpose IDE toast notification helpers
 * @doc.layer ui
 */
import { useToast, type ToastData } from '@ghatana/design-system';

export { Toast, ToastProvider, useToast } from '@ghatana/design-system';
export type {
  ToastProps,
  ToastSeverity,
  ToastPosition,
  ToastData,
} from '@ghatana/design-system';

/**
 * Convenience hook providing severity-specific toast methods.
 *
 * @example
 * ```tsx
 * const { success, error, info, warning } = useToastNotifications();
 * success('File saved');
 * error('Upload failed');
 * ```
 */
export interface UseToastNotificationsReturn {
  success: (message: string, duration?: number) => void;
  error: (message: string, duration?: number) => void;
  warning: (message: string, duration?: number) => void;
  info: (message: string, duration?: number) => void;
}

export function useToastNotifications(): UseToastNotificationsReturn {
  const { addToast } = useToast();

  return {
    success: (message: string, duration?: number) =>
      addToast({
        severity: 'success' as ToastData['severity'],
        message,
        duration,
      }),
    error: (message: string, duration?: number) =>
      addToast({
        severity: 'error' as ToastData['severity'],
        message,
        duration,
      }),
    warning: (message: string, duration?: number) =>
      addToast({
        severity: 'warning' as ToastData['severity'],
        message,
        duration,
      }),
    info: (message: string, duration?: number) =>
      addToast({
        severity: 'info' as ToastData['severity'],
        message,
        duration,
      }),
  };
}

/**
 * Re-export ToastContainer — the provider already renders it, but
 * exposed here for IDE panels that need manual control.
 */
export { default as ToastContainer } from '@ghatana/design-system';
