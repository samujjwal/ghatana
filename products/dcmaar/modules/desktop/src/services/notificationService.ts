import { useCallback, useEffect, useState } from 'react';

// Mock Tauri functions for development
const invoke = async <T = any>(cmd: string, args?: unknown): Promise<T> => {
  console.log('Mock Tauri invoke:', cmd, args);
  
  // Return appropriate mock values based on command
  if (cmd === 'is_notification_supported') {
    return true as T;
  }
  
  return undefined as T;
};

const getVersion = async () => '0.1.0';

const isPermissionGranted = async () => true;

const requestNotificationPermission = async () => 'granted' as const;

/**
 * Notification urgency level
 */
export enum NotificationLevel {
  Info = 'info',
  Warning = 'warning',
  Error = 'error',
}

/**
 * Notification action button
 */
export interface NotificationAction {
  id: string;
  label: string;
  /**
   * Only supported on some platforms
   * @default false
   */
  isDestructive?: boolean;
  /**
   * Only supported on some platforms
   * @default false
   */
  requiresAuthentication?: boolean;
}

/**
 * Notification options
 */
export interface NotificationOptions {
  /** Notification title */
  title: string;
  /** Notification body text */
  body?: string;
  /** Notification level */
  level?: NotificationLevel;
  /** Action buttons */
  actions?: NotificationAction[];
  /** Sound to play (platform-dependent) */
  sound?: string;
  /** Time in milliseconds before the notification is automatically closed */
  timeout?: number;
  /**
   * A unique identifier for the notification
   * Used to replace or update existing notifications with the same ID
   */
  id?: string;
  /**
   * Only supported on some platforms
   * @default false
   */
  silent?: boolean;
  /**
   * Only supported on some platforms
   * @default false
   */
  sticky?: boolean;
  /**
   * Custom data associated with the notification
   */
  data?: Record<string, any>;
}

// Platform detection
const isWeb = typeof window !== 'undefined' && 'document' in window;
const isTauri = !isWeb && '__TAURI__' in window;

// Cache for notification permissions
let permissionCache: NotificationPermission | null = null;

/**
 * Shows a native notification
 * @param options Notification options
 * @returns Promise that resolves when the notification is shown
 */
export async function showNotification(options: NotificationOptions): Promise<void> {
  // Use web notifications if available and permission is granted
  if (isWeb && 'Notification' in window && Notification.permission === 'granted') {
    showWebNotification(options);
    return;
  }
  
  // Use Tauri notifications if available
  if (isTauri) {
    try {
      // Check if we're in a Tauri environment with notification support
      const supported = await invoke<boolean>('is_notification_supported');
      if (supported) {
        return invoke('show_notification', {
          options: {
            level: NotificationLevel.Info,
            actions: [],
            silent: false,
            sticky: false,
            ...options,
          },
        });
      }
    } catch (error) {
      console.error('Error showing Tauri notification:', error);
    }
  }
  
  // Fallback to console log if no notification system is available
  console.log(`[Notification] ${options.title}`, options.body || '');
  
  // If we have a body, log it as well
  if (options.body) {
    console.log(options.body);
  }
  
  // Log actions if any
  if (options.actions && options.actions.length > 0) {
    console.log('Actions:', options.actions.map(a => a.label).join(', '));
  }
}

/**
 * Shows a notification using the Web Notifications API
 */
async function showWebNotification(options: NotificationOptions) {
  // Request permission if not already granted
  if (Notification.permission !== 'granted') {
    const permission = await requestNotificationPermission();
    if (permission !== 'granted') {
      throw new Error('Notification permission not granted');
    }
  }
  
  // Create and show the notification
  const notification = new Notification(options.title, {
    body: options.body,
    // @ts-ignore - tag is not in the type definition but is supported
    tag: options.id || `notification-${Date.now()}`,
    // @ts-ignore - renotify is not in the type definition but is supported
    renotify: !!options.id,
    silent: options.silent,
    data: options.data,
  });
  
  // Handle notification click
  notification.onclick = () => {
    // Focus the window
    window.focus();
    
    // Emit an event that the notification was clicked
    const event = new CustomEvent('notification-clicked', {
      detail: {
        id: options.id,
        data: options.data,
      },
    });
    
    window.dispatchEvent(event);
  };
  
  // Handle notification close
  notification.onclose = () => {
    // Emit an event that the notification was closed
    const event = new CustomEvent('notification-closed', {
      detail: {
        id: options.id,
        data: options.data,
      },
    });
    
    window.dispatchEvent(event);
  };
  
  // Set a timeout to close the notification
  if (options.timeout && options.timeout > 0) {
    setTimeout(() => {
      notification.close();
    }, options.timeout);
  }
  
  return notification;
}

/**
 * Checks if notifications are supported on the current platform
 * @returns Promise that resolves to a boolean indicating if notifications are supported
 */
export async function isNotificationSupported(): Promise<boolean> {
  // Check if we're in a web environment with Notification API
  if (isWeb && 'Notification' in window) {
    return true;
  }
  
  // Check if we're in a Tauri environment
  if (isTauri) {
    try {
      return await invoke<boolean>('is_notification_supported');
    } catch (error) {
      console.error('Error checking notification support:', error);
      return false;
    }
  }
  
  return false;
}

/**
 * Checks if the application has permission to show notifications
 * @returns Promise that resolves to a boolean indicating if notifications are allowed
 */
export async function hasNotificationPermission(): Promise<boolean> {
  // Return cached permission if available
  if (permissionCache === 'granted') return true;
  if (permissionCache === 'denied') return false;
  
  // Check web notification permission
  if (isWeb && 'Notification' in window) {
    permissionCache = Notification.permission;
    return permissionCache === 'granted';
  }
  
  // Check Tauri notification permission
  if (isTauri) {
    try {
      const permission = await isPermissionGranted();
      permissionCache = permission ? 'granted' : 'denied';
      return permission;
    } catch (error) {
      console.error('Error checking notification permission:', error);
      return false;
    }
  }
  
  return false;
}

/**
 * Requests permission to show notifications
 * @returns Promise that resolves to the permission status
 */
export async function requestPermission(): Promise<NotificationPermission> {
  // Handle web notifications
  if (isWeb && 'Notification' in window) {
    try {
      const permission = await Notification.requestPermission();
      permissionCache = permission;
      return permission;
    } catch (error) {
      console.error('Error requesting notification permission:', error);
      return 'denied';
    }
  }
  
  // Handle Tauri notifications
  if (isTauri) {
    try {
      const permission = await requestNotificationPermission();
      permissionCache = permission ? 'granted' : 'denied';
      return permission ? 'granted' : 'denied';
    } catch (error) {
      console.error('Error requesting Tauri notification permission:', error);
      return 'denied';
    }
  }
  
  return 'denied';
}

/**
 * Hook for using notifications in React components
 * @returns Object with notification functions and support status
 */
export function useNotifications() {
  const [isSupported, setIsSupported] = useState<boolean | null>(null);
  const [permission, setPermission] = useState<NotificationPermission>('default');
  const [appVersion, setAppVersion] = useState<string>('');

  // Check notification support and permission when the hook is used
  useEffect(() => {
    const checkSupport = async () => {
      try {
        const supported = await isNotificationSupported();
        setIsSupported(supported);
        
        if (supported) {
          const hasPermission = await hasNotificationPermission();
          setPermission(hasPermission ? 'granted' : 'denied');
          
          // Get app version for debugging
          if (isTauri) {
            try {
              const version = await getVersion();
              setAppVersion(version);
            } catch (error) {
              console.error('Error getting app version:', error);
            }
          }
        }
      } catch (error) {
        console.error('Error checking notification support:', error);
        setIsSupported(false);
      }
    };
    
    checkSupport();
  }, []);

  /**
   * Show a notification
   */
  const notify = useCallback(async (options: NotificationOptions): Promise<void> => {
    if (isSupported === false) {
      console.warn('Notifications are not supported on this platform');
      return;
    }
    
    // Ensure we have permission
    let hasPermission = await hasNotificationPermission();
    if (!hasPermission) {
      const result = await requestPermission();
      hasPermission = result === 'granted';
      setPermission(result);
      
      if (!hasPermission) {
        console.warn('Notification permission denied');
        return;
      }
    }
    
    // Show the notification
    return showNotification(options);
  }, [isSupported]);

  /**
   * Show a success notification
   */
  const success = useCallback((title: string, body?: string, options: Partial<NotificationOptions> = {}) => {
    return notify({
      title,
      body,
      level: NotificationLevel.Info,
      ...options,
    });
  }, [notify]);

  /**
   * Show a warning notification
   */
  const warning = useCallback((title: string, body?: string, options: Partial<NotificationOptions> = {}) => {
    return notify({
      title,
      body,
      level: NotificationLevel.Warning,
      ...options,
    });
  }, [notify]);

  /**
   * Show an error notification
   */
  const error = useCallback((title: string, body?: string, options: Partial<NotificationOptions> = {}) => {
    return notify({
      title,
      body,
      level: NotificationLevel.Error,
      sticky: true, // Errors are sticky by default
      ...options,
    });
  }, [notify]);

  return {
    isSupported,
    permission,
    appVersion,
    requestPermission,
    show: notify,
    success,
    warning,
    error,
  };
}

// Types are already exported with their interface declarations above
