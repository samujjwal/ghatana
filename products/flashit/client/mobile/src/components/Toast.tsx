/**
 * Toast Notification Component for Mobile
 * 
 * Provides accessible toast notifications for user feedback.
 * Supports success, error, info, and warning message types.
 * 
 * @doc.type component
 * @doc.purpose Display temporary notification messages
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  TouchableOpacity,
  Platform,
} from 'react-native';
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
export const showToast = (
  type: ToastType,
  message: string,
  duration: number = 3000
) => {
  const id = Date.now().toString();
  const toastMessage: ToastMessage = { id, type, message, duration };
  
  // This will be set by the ToastProvider
  if (typeof window !== 'undefined' && (window as any).__showToast) {
    (window as any).__showToast(toastMessage);
  }
};

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toast, setToast] = useAtom(toastAtom);
  const fadeAnim = new Animated.Value(0);
  const translateY = new Animated.Value(-100);

  // Register global toast function
  useEffect(() => {
    (window as any).__showToast = setToast;
    return () => {
      delete (window as any).__showToast;
    };
  }, [setToast]);

  useEffect(() => {
    if (toast) {
      // Show animation
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 300,
          useNativeDriver: true,
        }),
        Animated.spring(translateY, {
          toValue: 0,
          tension: 65,
          friction: 8,
          useNativeDriver: true,
        }),
      ]).start();

      // Auto-hide after duration
      const timer = setTimeout(() => {
        hideToast();
      }, toast.duration || 3000);

      return () => clearTimeout(timer);
    }
  }, [toast]);

  const hideToast = () => {
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 0,
        duration: 200,
        useNativeDriver: true,
      }),
      Animated.timing(translateY, {
        toValue: -100,
        duration: 200,
        useNativeDriver: true,
      }),
    ]).start(() => {
      setToast(null);
    });
  };

  const getToastStyle = () => {
    switch (toast?.type) {
      case 'success':
        return styles.success;
      case 'error':
        return styles.error;
      case 'warning':
        return styles.warning;
      case 'info':
      default:
        return styles.info;
    }
  };

  const getAccessibilityLabel = () => {
    const type = toast?.type || 'info';
    return `${type} notification: ${toast?.message}`;
  };

  return (
    <>
      {children}
      {toast && (
        <Animated.View
          style={[
            styles.container,
            {
              opacity: fadeAnim,
              transform: [{ translateY }],
            },
          ]}
          accessible={true}
          accessibilityRole=\"alert\"
          accessibilityLabel={getAccessibilityLabel()}
          accessibilityLiveRegion=\"polite\"
        >
          <TouchableOpacity
            style={[styles.toast, getToastStyle()]}
            onPress={hideToast}
            accessible={true}
            accessibilityRole=\"button\"
            accessibilityLabel={`Dismiss ${toast.type} notification`}
            accessibilityHint=\"Double tap to dismiss\"
          >
            <Text style={styles.message}>{toast.message}</Text>
          </TouchableOpacity>
        </Animated.View>
      )}
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: Platform.OS === 'ios' ? 50 : 20,
    left: 20,
    right: 20,
    zIndex: 9999,
    elevation: 999,
  },
  toast: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 5,
  },
  message: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '500',
  },
  success: {
    backgroundColor: '#10b981',
  },
  error: {
    backgroundColor: '#ef4444',
  },
  warning: {
    backgroundColor: '#f59e0b',
  },
  info: {
    backgroundColor: '#3b82f6',
  },
});
