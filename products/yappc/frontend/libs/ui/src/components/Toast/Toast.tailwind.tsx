import * as React from 'react';
import { createPortal } from 'react-dom';

import { cn } from '../../utils/cn';

/**
 * Toast severity variants
 */
export type ToastSeverity = 'info' | 'success' | 'warning' | 'error';

/**
 * Toast position on screen
 */
export type ToastPosition =
  | 'top-left'
  | 'top-center'
  | 'top-right'
  | 'bottom-left'
  | 'bottom-center'
  | 'bottom-right';

/**
 * Toast item data
 */
export interface ToastData {
  id: string;
  message: React.ReactNode;
  severity?: ToastSeverity;
  duration?: number;
  action?: React.ReactNode;
}

/**
 * Toast component props
 */
export interface ToastProps {
  /**
   * Toast message content
   */
  message: React.ReactNode;
  /**
   * Severity level
   * @default 'info'
   */
  severity?: ToastSeverity;
  /**
   * Open state
   */
  open: boolean;
  /**
   * Callback when toast should close
   */
  onClose: () => void;
  /**
   * Auto-hide duration in milliseconds (0 = no auto-hide)
   * @default 6000
   */
  duration?: number;
  /**
   * Position on screen
   * @default 'bottom-left'
   */
  position?: ToastPosition;
  /**
   * Optional action button/component
   */
  action?: React.ReactNode;
  /**
   * Custom className
   */
  className?: string;
}

/**
 * Toast context for managing multiple toasts
 */
interface ToastContextValue {
  toasts: ToastData[];
  addToast: (toast: Omit<ToastData, 'id'>) => void;
  removeToast: (id: string) => void;
}

const ToastContext = React.createContext<ToastContextValue | undefined>(undefined);

/**
 * Severity color configurations
 */
const severityConfig: Record<ToastSeverity, {
  bg: string;
  border: string;
  text: string;
  icon: React.ReactNode;
}> = {
  info: {
    bg: 'bg-blue-50 dark:bg-blue-900/20',
    border: 'border-l-4 border-blue-500',
    text: 'text-blue-700 dark:text-blue-400',
    icon: (
      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  success: {
    bg: 'bg-green-50 dark:bg-green-900/20',
    border: 'border-l-4 border-green-500',
    text: 'text-green-700 dark:text-green-400',
    icon: (
      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  warning: {
    bg: 'bg-orange-50 dark:bg-orange-900/20',
    border: 'border-l-4 border-orange-500',
    text: 'text-orange-700 dark:text-orange-400',
    icon: (
      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
      </svg>
    ),
  },
  error: {
    bg: 'bg-red-50 dark:bg-red-900/20',
    border: 'border-l-4 border-red-500',
    text: 'text-red-700 dark:text-red-400',
    icon: (
      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
};

/**
 * ToastProvider - Context provider for toast management
 *
 * Wrap your app with this to enable toast notifications
 *
 * @example
 * ```tsx
 * function App() {
 *   return (
 *     <ToastProvider>
 *       <YourApp />
 *     </ToastProvider>
 *   );
 * }
 * ```
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = React.useState<ToastData[]>([]);

  const addToast = React.useCallback((toast: Omit<ToastData, 'id'>) => {
    const id = Math.random().toString(36).substr(2, 9);
    const newToast = { ...toast, id };
    
    setToasts((prev) => [...prev, newToast]);

    // Auto-dismiss if duration is set
    const duration = toast.duration ?? 6000;
    if (duration > 0) {
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, duration);
    }
  }, []);

  const removeToast = React.useCallback((id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ toasts, addToast, removeToast }}>
      {children}
      <ToastContainer toasts={toasts} onRemove={removeToast} />
    </ToastContext.Provider>
  );
}

/**
 * useToast - Hook to show toast notifications
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { addToast } = useToast();
 *
 *   const handleClick = () => {
 *     addToast({
 *       message: 'Operation successful',
 *       severity: 'success'
 *     });
 *   };
 *
 *   return <button onClick={handleClick}>Show Toast</button>;
 * }
 * ```
 */
export function useToast() {
  const context = React.useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return context;
}

/**
 * Toast container for rendering stacked toasts
 */
interface ToastContainerProps {
  toasts: ToastData[];
  onRemove: (id: string) => void;
}

/**
 *
 */
function ToastContainer({ toasts, onRemove }: ToastContainerProps) {
  if (toasts.length === 0) return null;

  return createPortal(
    <div className="fixed top-4 right-4 z-50 flex flex-col gap-3 max-w-md">
      {toasts.map((toast) => (
        <ToastItem
          key={toast.id}
          toast={toast}
          onClose={() => onRemove(toast.id)}
        />
      ))}
    </div>,
    document.body
  );
}

/**
 * Individual toast item
 */
interface ToastItemProps {
  toast: ToastData;
  onClose: () => void;
}

/**
 *
 */
function ToastItem({ toast, onClose }: ToastItemProps) {
  const severity = toast.severity ?? 'info';
  const config = severityConfig[severity];

  return (
    <div
      role="alert"
      aria-live="polite"
      className={cn(
        'flex items-start gap-3 p-4 rounded-lg shadow-lg border',
        'animate-in slide-in-from-right-full fade-in-0 duration-300',
        config.bg,
        config.border,
        config.text
      )}
    >
      {/* Icon */}
      <div className="flex-shrink-0 mt-0.5">
        {config.icon}
      </div>

      {/* Message */}
      <div className="flex-1 text-sm leading-relaxed">
        {toast.message}
      </div>

      {/* Action */}
      {toast.action && (
        <div className="flex-shrink-0">
          {toast.action}
        </div>
      )}

      {/* Close button */}
      <button
        type="button"
        onClick={onClose}
        className={cn(
          'flex-shrink-0 p-1 rounded transition-colors',
          'hover:bg-black/10 dark:hover:bg-white/10'
        )}
        aria-label="Close notification"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}

/**
 * Toast component for single toast (controlled)
 *
 * For most cases, use ToastProvider + useToast instead
 *
 * @example
 * ```tsx
 * const [open, setOpen] = useState(false);
 *
 * <Toast
 *   open={open}
 *   onClose={() => setOpen(false)}
 *   message="Changes saved"
 *   severity="success"
 * />
 * ```
 */
export const Toast = React.forwardRef<HTMLDivElement, ToastProps>(
  (
    {
      message,
      severity = 'info',
      open,
      onClose,
      duration = 6000,
      position = 'bottom-left',
      action,
      className,
    },
    ref
  ) => {
    // Auto-hide timer
    React.useEffect(() => {
      if (open && duration > 0) {
        const timer = setTimeout(onClose, duration);
        return () => clearTimeout(timer);
      }
    }, [open, duration, onClose]);

    if (!open) return null;

    const config = severityConfig[severity];

    // Position classes
    const positionClasses: Record<ToastPosition, string> = {
      'top-left': 'top-4 left-4',
      'top-center': 'top-4 left-1/2 -translate-x-1/2',
      'top-right': 'top-4 right-4',
      'bottom-left': 'bottom-4 left-4',
      'bottom-center': 'bottom-4 left-1/2 -translate-x-1/2',
      'bottom-right': 'bottom-4 right-4',
    };

    const toastContent = (
      <div
        ref={ref}
        role="alert"
        aria-live="polite"
        className={cn(
          'fixed z-50 flex items-start gap-3 p-4 rounded-lg shadow-lg border min-w-[300px] max-w-md',
          'animate-in fade-in-0 slide-in-from-bottom-2 duration-300',
          config.bg,
          config.border,
          config.text,
          positionClasses[position],
          className
        )}
      >
        {/* Icon */}
        <div className="flex-shrink-0 mt-0.5">
          {config.icon}
        </div>

        {/* Message */}
        <div className="flex-1 text-sm leading-relaxed">
          {message}
        </div>

        {/* Action */}
        {action && (
          <div className="flex-shrink-0">
            {action}
          </div>
        )}

        {/* Close button */}
        <button
          type="button"
          onClick={onClose}
          className={cn(
            'flex-shrink-0 p-1 rounded transition-colors',
            'hover:bg-black/10 dark:hover:bg-white/10'
          )}
          aria-label="Close notification"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    );

    return createPortal(toastContent, document.body);
  }
);

Toast.displayName = 'Toast';

/**
 * Snackbar - Alias for Toast (Material UI compatibility)
 */
export const Snackbar = Toast;
