/**
 * Error Boundary Component for Flashit Mobile
 * Catches and handles React errors gracefully
 *
 * @doc.type component
 * @doc.purpose Error boundary with recovery and logging
 * @doc.layer product
 * @doc.pattern ErrorBoundary
 */

import React, { Component, ReactNode, ErrorInfo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import * as Updates from 'expo-updates';
import AsyncStorage from '@react-native-async-storage/async-storage';

// ============================================================================
// Types & Interfaces
// ============================================================================

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: (error: Error, reset: () => void) => ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  resetKeys?: unknown[];
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  errorCount: number;
}

interface ErrorLog {
  timestamp: number;
  message: string;
  stack?: string;
  componentStack?: string;
  errorCount: number;
}

// ============================================================================
// Constants
// ============================================================================

const ERROR_LOG_KEY = '@ghatana/flashit-error_logs';
const MAX_ERROR_LOGS = 50;
const MAX_ERROR_RESET_ATTEMPTS = 3;

// ============================================================================
// Error Boundary Component
// ============================================================================

/**
 * ErrorBoundary catches errors in child components
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorCount: 0,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return {
      hasError: true,
      error,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    const { onError } = this.props;
    const { errorCount } = this.state;

    // Update state with error info
    this.setState({
      errorInfo,
      errorCount: errorCount + 1,
    });

    // Log error
    this.logError(error, errorInfo);

    // Call custom error handler
    if (onError) {
      onError(error, errorInfo);
    }

    // If too many errors, force reload
    if (errorCount >= MAX_ERROR_RESET_ATTEMPTS) {
      this.handleForceReload();
    }
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps): void {
    const { resetKeys } = this.props;
    const { hasError } = this.state;

    // Reset error state if resetKeys changed
    if (
      hasError &&
      resetKeys &&
      prevProps.resetKeys &&
      !this.areKeysEqual(resetKeys, prevProps.resetKeys)
    ) {
      this.reset();
    }
  }

  private areKeysEqual(keys1: unknown[], keys2: unknown[]): boolean {
    return JSON.stringify(keys1) === JSON.stringify(keys2);
  }

  private async logError(error: Error, errorInfo: ErrorInfo): Promise<void> {
    try {
      const errorLog: ErrorLog = {
        timestamp: Date.now(),
        message: error.message,
        stack: error.stack,
        componentStack: errorInfo.componentStack,
        errorCount: this.state.errorCount,
      };

      // Get existing logs
      const logsJson = await AsyncStorage.getItem(ERROR_LOG_KEY);
      const logs: ErrorLog[] = logsJson ? JSON.parse(logsJson) : [];

      // Add new log
      logs.unshift(errorLog);

      // Keep only recent logs
      if (logs.length > MAX_ERROR_LOGS) {
        logs.splice(MAX_ERROR_LOGS);
      }

      // Save logs
      await AsyncStorage.setItem(ERROR_LOG_KEY, JSON.stringify(logs));
    } catch (e) {
      console.error('Failed to log error:', e);
    }
  }

  private reset = (): void => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      errorCount: 0,
    });
  };

  private handleReload = async (): Promise<void> => {
    try {
      // Check for updates first (only in production builds, not Expo Go)
      if (!__DEV__ && Updates.isEnabled) {
        const update = await Updates.checkForUpdateAsync();
        if (update.isAvailable) {
          await Updates.fetchUpdateAsync();
          await Updates.reloadAsync();
        } else {
          await Updates.reloadAsync();
        }
      } else {
        // In development or Expo Go, just reload
        Updates.reloadAsync();
      }
    } catch (e) {
      console.error('Failed to reload app:', e);
      // Fallback to just resetting state
      this.reset();
    }
  };

  private handleForceReload = (): void => {
    // In production, reload the app
    if (!__DEV__) {
      Updates.reloadAsync();
    }
  };

  private handleClearData = async (): Promise<void> => {
    try {
      // Clear error logs
      await AsyncStorage.removeItem(ERROR_LOG_KEY);

      // Reset state
      this.reset();

      // Reload app
      await this.handleReload();
    } catch (e) {
      console.error('Failed to clear data:', e);
    }
  };

  render(): ReactNode {
    const { children, fallback } = this.props;
    const { hasError, error, errorInfo } = this.state;

    if (hasError && error) {
      // Use custom fallback if provided
      if (fallback) {
        return fallback(error, this.reset);
      }

      // Default error UI
      return (
        <View style={styles.container}>
          <View style={styles.header}>
            <Text style={styles.title}>Something went wrong</Text>
            <Text style={styles.subtitle}>
              We're sorry for the inconvenience. The app encountered an error.
            </Text>
          </View>

          <ScrollView style={styles.errorDetails}>
            <View style={styles.errorSection}>
              <Text style={styles.errorLabel}>Error Message:</Text>
              <Text style={styles.errorText}>{error.message}</Text>
            </View>

            {__DEV__ && error.stack && (
              <View style={styles.errorSection}>
                <Text style={styles.errorLabel}>Stack Trace:</Text>
                <Text style={styles.errorText}>{error.stack}</Text>
              </View>
            )}

            {__DEV__ && errorInfo?.componentStack && (
              <View style={styles.errorSection}>
                <Text style={styles.errorLabel}>Component Stack:</Text>
                <Text style={styles.errorText}>{errorInfo.componentStack}</Text>
              </View>
            )}
          </ScrollView>

          <View style={styles.actions}>
            <TouchableOpacity style={styles.primaryButton} onPress={this.reset}>
              <Text style={styles.primaryButtonText}>Try Again</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.secondaryButton} onPress={this.handleReload}>
              <Text style={styles.secondaryButtonText}>Reload App</Text>
            </TouchableOpacity>

            {this.state.errorCount >= 2 && (
              <TouchableOpacity
                style={styles.dangerButton}
                onPress={this.handleClearData}
              >
                <Text style={styles.dangerButtonText}>Clear Data & Reset</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      );
    }

    return children;
  }
}

// ============================================================================
// Styles
// ============================================================================

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    backgroundColor: '#fff',
    padding: 24,
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#d32f2f',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    lineHeight: 24,
  },
  errorDetails: {
    flex: 1,
    padding: 16,
  },
  errorSection: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
  },
  errorLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  errorText: {
    fontSize: 12,
    color: '#666',
    fontFamily: 'monospace',
    lineHeight: 18,
  },
  actions: {
    padding: 16,
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderTopColor: '#e0e0e0',
  },
  primaryButton: {
    backgroundColor: '#2196f3',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 12,
  },
  primaryButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  secondaryButton: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2196f3',
    marginBottom: 12,
  },
  secondaryButtonText: {
    color: '#2196f3',
    fontSize: 16,
    fontWeight: '600',
  },
  dangerButton: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#d32f2f',
  },
  dangerButtonText: {
    color: '#d32f2f',
    fontSize: 16,
    fontWeight: '600',
  },
});

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get error logs from storage
 */
export async function getErrorLogs(): Promise<ErrorLog[]> {
  try {
    const logsJson = await AsyncStorage.getItem(ERROR_LOG_KEY);
    return logsJson ? JSON.parse(logsJson) : [];
  } catch (e) {
    console.error('Failed to get error logs:', e);
    return [];
  }
}

/**
 * Clear error logs
 */
export async function clearErrorLogs(): Promise<void> {
  try {
    await AsyncStorage.removeItem(ERROR_LOG_KEY);
  } catch (e) {
    console.error('Failed to clear error logs:', e);
  }
}

export default ErrorBoundary;
