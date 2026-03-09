/**
 * @file AlertNotification Component
 *
 * Toast-style notifications for real-time metric changes and alerts.
 * Provides auto-dismiss, manual close, sound alerts, animations, and stack management.
 *
 * @module ui/components/plugins/AlertNotification
 * @version 1.0.0
 */

import clsx from 'clsx';
import React, { useCallback, useEffect, useState } from 'react';

export type AlertSeverity = 'info' | 'warning' | 'error' | 'success';

export interface AlertNotificationProps {
  /**
   * Unique identifier for this notification.
   */
  id: string;

  /**
   * Alert severity level.
   */
  severity: AlertSeverity;

  /**
   * Main message title.
   */
  title: string;

  /**
   * Optional detailed message.
   */
  message?: string;

  /**
   * Metric name that triggered the alert.
   */
  metric?: string;

  /**
   * Previous metric value.
   */
  previousValue?: number;

  /**
   * Current metric value.
   */
  currentValue?: number;

  /**
   * Threshold value if applicable.
   */
  threshold?: number;

  /**
   * Auto-dismiss delay in milliseconds. Default: 5000ms.
   * Set to 0 to disable auto-dismiss.
   */
  autoDismissMs?: number;

  /**
   * Whether to play sound on notification. Default: true.
   */
  playSound?: boolean;

  /**
   * Callback when notification is closed.
   */
  onClose?: (id: string) => void;

  /**
   * Optional action button label.
   */
  actionLabel?: string;

  /**
   * Callback for action button click.
   */
  onAction?: () => void;

  /**
   * Z-index for stacking. Default: 50.
   */
  zIndex?: number;

  /**
   * Animation position: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left'.
   */
  position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';
}

/**
 * Gets styling for severity level.
 */
const getSeverityStyles = (severity: AlertSeverity) => {
  const styles: Record<AlertSeverity, { bg: string; border: string; text: string; icon: string }> = {
    info: {
      bg: 'bg-blue-50',
      border: 'border-blue-200',
      text: 'text-blue-700',
      icon: 'ℹ️',
    },
    warning: {
      bg: 'bg-amber-50',
      border: 'border-amber-200',
      text: 'text-amber-700',
      icon: '⚠️',
    },
    error: {
      bg: 'bg-red-50',
      border: 'border-red-200',
      text: 'text-red-700',
      icon: '🚨',
    },
    success: {
      bg: 'bg-green-50',
      border: 'border-green-200',
      text: 'text-green-700',
      icon: '✓',
    },
  };

  return styles[severity];
};

/**
 * Plays a notification sound.
 */
const playNotificationSound = (severity: AlertSeverity) => {
  try {
    // Create audio context for sound generation
    const AudioContextClass = window.AudioContext || (window as Record<string, unknown>).webkitAudioContext;
    if (!AudioContextClass) return; // AudioContext not available
    
    const audioContext = new (AudioContextClass as typeof AudioContext)();
    
    // Define frequency and duration based on severity
    const frequencies: Record<AlertSeverity, number> = {
      info: 800,      // Medium pitch
      warning: 1000,  // Higher pitch
      error: 1200,    // Highest pitch
      success: 600,   // Low pitch
    };

    const durations: Record<AlertSeverity, number> = {
      info: 200,
      warning: 150,
      error: 300,
      success: 100,
    };

    const frequency = frequencies[severity];
    const duration = durations[severity];

    const oscillator = audioContext.createOscillator();
    const gainNode = audioContext.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(audioContext.destination);

    oscillator.frequency.value = frequency;
    oscillator.type = 'sine';

    gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + duration / 1000);

    oscillator.start(audioContext.currentTime);
    oscillator.stop(audioContext.currentTime + duration / 1000);
  } catch (error) {
    console.debug('Sound notification failed (may be expected in some environments):', error);
  }
};

/**
 * Alert Notification Component
 *
 * Toast-style notification that appears, optionally plays a sound,
 * auto-dismisses after specified time, and supports manual close.
 *
 * Usage:
 * ```tsx
 * <AlertNotification
 *   id="cpu-high-1"
 *   severity="warning"
 *   title="High CPU Usage"
 *   message="CPU usage exceeded 80%"
 *   metric="cpu"
 *   currentValue={85}
 *   threshold={80}
 *   onClose={(id) => console.log('Closed:', id)}
 *   onAction={() => navigate('/plugin-settings')}
 *   actionLabel="Adjust Settings"
 * />
 * ```
 */
export const AlertNotification: React.FC<AlertNotificationProps> = ({
  id,
  severity,
  title,
  message,
  metric,
  previousValue,
  currentValue,
  threshold,
  autoDismissMs = 5000,
  playSound = true,
  onClose,
  actionLabel,
  onAction,
  zIndex = 50,
  position = 'top-right',
}) => {
  const [isVisible, setIsVisible] = useState(true);
  const [isExiting, setIsExiting] = useState(false);

  // Play sound and set up auto-dismiss on mount
  useEffect(() => {
    if (playSound) {
      playNotificationSound(severity);
    }

    if (autoDismissMs > 0) {
      const timer = setTimeout(() => {
        handleClose();
      }, autoDismissMs);

      return () => clearTimeout(timer);
    }
  }, [severity, playSound, autoDismissMs]);

  const handleClose = useCallback(() => {
    setIsExiting(true);
    setTimeout(() => {
      setIsVisible(false);
      onClose?.(id);
    }, 300); // Match animation duration
  }, [id, onClose]);

  if (!isVisible) {
    return null;
  }

  const styles = getSeverityStyles(severity);

  // Position classes
  const positionClasses: Record<string, string> = {
    'top-right': 'top-4 right-4',
    'top-left': 'top-4 left-4',
    'bottom-right': 'bottom-4 right-4',
    'bottom-left': 'bottom-4 left-4',
  };

  // Animation classes
  const animationClasses = isExiting
    ? 'animate-fade-out'
    : 'animate-fade-in';

  return (
    <div
      className={clsx(
        'fixed',
        positionClasses[position],
        'max-w-sm',
        'pointer-events-auto',
        animationClasses,
        'z-' + zIndex
      )}
      style={{ zIndex }}
      role="alert"
      aria-live="polite"
      aria-atomic="true"
    >
      <div
        className={clsx(
          'rounded-lg border shadow-lg p-4',
          styles.bg,
          styles.border,
          'bg-white border border-gray-200 shadow-lg'
        )}
      >
        {/* Header: Icon, Title, Close Button */}
        <div className="flex items-start justify-between mb-2">
          <div className="flex items-start gap-3 flex-1">
            <span className="text-lg flex-shrink-0 mt-0.5">{styles.icon}</span>
            <div className="flex-1 min-w-0">
              <h3 className="font-semibold text-sm text-gray-900">{title}</h3>
            </div>
          </div>
          <button
            onClick={handleClose}
            className="flex-shrink-0 ml-2 text-gray-400 hover:text-gray-600 transition-colors"
            aria-label="Close notification"
          >
            <span className="text-lg">×</span>
          </button>
        </div>

        {/* Message */}
        {message && (
          <p className="text-sm text-gray-600 ml-8 mb-3">
            {message}
          </p>
        )}

        {/* Metric Details */}
        {metric && (currentValue !== undefined || previousValue !== undefined) && (
          <div className="ml-8 mb-3 space-y-1">
            <div className="text-xs text-gray-600">
              <span className="font-medium">{metric}</span>
              {currentValue !== undefined && (
                <>
                  <span className="mx-1">→</span>
                  <span className="font-semibold text-gray-900">{currentValue.toFixed(1)}</span>
                  {previousValue !== undefined && (
                    <span className={clsx(
                      'ml-1',
                      currentValue > previousValue ? 'text-red-600' : 'text-green-600'
                    )}>
                      ({currentValue > previousValue ? '+' : ''}{(currentValue - previousValue).toFixed(1)})
                    </span>
                  )}
                </>
              )}
            </div>
            {threshold !== undefined && (
              <div className="text-xs text-gray-500">
                Threshold: {threshold.toFixed(1)}
              </div>
            )}
          </div>
        )}

        {/* Action Button */}
        {actionLabel && onAction && (
          <div className="ml-8">
            <button
              onClick={() => {
                onAction();
                handleClose();
              }}
              className={clsx(
                'text-xs font-medium px-3 py-1.5 rounded-md',
                'border transition-colors',
                'hover:opacity-80 active:scale-95'
              )}
              style={{
                borderColor: styles.text.replace('text-', '').split('-')[0],
                color: styles.text,
                backgroundColor: styles.bg,
              }}
            >
              {actionLabel}
            </button>
          </div>
        )}

        {/* CSS for animations */}
        <style>{`
          @keyframes fadeIn {
            from {
              opacity: 0;
              transform: translateY(-10px);
            }
            to {
              opacity: 1;
              transform: translateY(0);
            }
          }

          @keyframes fadeOut {
            from {
              opacity: 1;
              transform: translateY(0);
            }
            to {
              opacity: 0;
              transform: translateY(-10px);
            }
          }

          .animate-fade-in {
            animation: fadeIn 300ms ease-out;
          }

          .animate-fade-out {
            animation: fadeOut 300ms ease-out;
          }
        `}</style>
      </div>
    </div>
  );
};

/**
 * Context and Hook for managing multiple notifications
 */

interface NotificationItem {
  id: string;
  config: Omit<AlertNotificationProps, 'id' | 'onClose'>;
}

interface NotificationContextType {
  notifications: NotificationItem[];
  add: (config: Omit<AlertNotificationProps, 'id' | 'onClose'>) => string;
  remove: (id: string) => void;
  clear: () => void;
}

const NotificationContext = React.createContext<NotificationContextType | undefined>(undefined);

/**
 * Provider component for notification management.
 */
export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);

  const add = useCallback((config: Omit<AlertNotificationProps, 'id' | 'onClose'>) => {
    const id = `notif-${Date.now()}-${Math.random()}`;
    setNotifications((prev) => [...prev, { id, config }]);
    return id;
  }, []);

  const remove = useCallback((id: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  }, []);

  const clear = useCallback(() => {
    setNotifications([]);
  }, []);

  const value: NotificationContextType = {
    notifications,
    add,
    remove,
    clear,
  };

  return (
    <NotificationContext.Provider value={value}>
      {children}
      {/* Render all notifications */}
      <div className="fixed pointer-events-none top-0 left-0 right-0 bottom-0">
        {notifications.map((notif) => (
          <AlertNotification
            key={notif.id}
            id={notif.id}
            {...notif.config}
            onClose={remove}
          />
        ))}
      </div>
    </NotificationContext.Provider>
  );
};

/**
 * Hook for accessing notification context.
 */
export const useNotifications = () => {
  const context = React.useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within NotificationProvider');
  }
  return context;
};

/**
 * Convenience hook for adding notifications.
 */
export const useAlert = () => {
  const { add } = useNotifications();

  return useCallback(
    (config: Omit<AlertNotificationProps, 'id' | 'onClose'>) => add(config),
    [add]
  );
};

export default AlertNotification;
