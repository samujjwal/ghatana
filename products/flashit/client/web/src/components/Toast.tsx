/**
 * Toast Notification Component for Web
 * 
 * Provides accessible toast notifications for user feedback.
 * Supports success, error, info, and warning message types with animations.
 * 
 * WCAG 2.1 Compliant:
 * - Uses role="alert" for screen readers
 * - aria-live="polite" for non-intrusive announcements
 * - Keyboard dismissible (Escape key)
 */

import { useEffect, useState } from 'react';
import { atom, useAtom } from 'jotai';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface ToastMessage {
  id: string;
  type: ToastType;
  message: string;
  duration?: number;
}

// Global toast state
export const toastAtom = atom<ToastMessage | null>(null);

// Helper function to show toast
let setToastFn: ((toast: ToastMessage | null) => void) | null = null;

export const showToast = (
  type: ToastType,
  message: string,
  duration: number = 3000
) => {
  if (setToastFn) {
    const id = Date.now().toString();
    setToastFn({ id, type, message, duration });
  }
};

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toast, setToast] = useAtom(toastAtom);
  const [isVisible, setIsVisible] = useState(false);

  // Register setter
  useEffect(() => {
    setToastFn = setToast;
    return () => {
      setToastFn = null;
    };
  }, [setToast]);

  // Show/hide animation
  useEffect(() => {
    if (toast) {
      setIsVisible(true);
      const timer = setTimeout(() => {
        hideToast();
      }, toast.duration || 3000);

      return () => clearTimeout(timer);
    }
  }, [toast]);

  // Keyboard support (Escape to dismiss)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && toast) {
        hideToast();
      }
    };

    if (toast) {
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }
  }, [toast]);

  const hideToast = () => {
    setIsVisible(false);
    setTimeout(() => setToast(null), 300); // Wait for fade out
  };

  if (!toast) return <>{children}</>;

  const getToastClasses = () => {
    const baseClasses = 'fixed top-4 right-4 z-50 px-4 py-3 rounded-lg shadow-lg transition-all duration-300 transform';
    const visibilityClasses = isVisible
      ? 'translate-x-0 opacity-100'
      : 'translate-x-full opacity-0';

    const typeClasses = {
      success: 'bg-green-500 text-white',
      error: 'bg-red-500 text-white',
      warning: 'bg-yellow-500 text-gray-900',
      info: 'bg-blue-500 text-white',
    };

    return `${baseClasses} ${visibilityClasses} ${typeClasses[toast.type]}`;
  };

  const getAccessibilityLabel = () => {
    return `${toast.type} notification: ${toast.message}`;
  };

  return (
    <>
      {children}
      <div
        className={getToastClasses()}
        role="alert"
        aria-live="polite"
        aria-label={getAccessibilityLabel()}
      >
        <div className="flex items-center justify-between gap-4">
          <p className="text-sm font-medium">{toast.message}</p>
          <button
            onClick={hideToast}
            className="text-current hover:opacity-75 focus:outline-none focus:ring-2 focus:ring-current focus:ring-offset-2 rounded"
            aria-label="Dismiss notification"
          >
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>
      </div>
    </>
  );
}
