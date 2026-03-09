/**
 * Telemetry Integration for DCMAAR Extension
 * 
 * This module integrates telemetry and observability features into existing
 * extension components, adding performance tracking, error monitoring, and
 * user interaction analytics.
 */

import React, { useEffect, useRef } from 'react';
import { telemetryManager } from './services/TelemetryManager';
import { errorMonitor } from './services/ErrorMonitor';

// ================================================================================================
// Performance Tracking Hook
// ================================================================================================

export const usePerformanceTracking = (componentName: string) => {
  const renderStartTime = useRef<number>(performance.now());
  const mountTime = useRef<number | null>(null);

  useEffect(() => {
    // Track component mount
    mountTime.current = performance.now();
    const mountDuration = mountTime.current - renderStartTime.current;
    
    telemetryManager.trackPerformance(`${componentName}.mount`, {
      duration: mountDuration,
      timestamp: mountTime.current
    });

    // Track component unmount
    return () => {
      if (mountTime.current) {
        const unmountTime = performance.now();
        const lifetimeDuration = unmountTime - mountTime.current;
        
        telemetryManager.trackPerformance(`${componentName}.lifetime`, {
          duration: lifetimeDuration,
          mountDuration: mountTime.current - renderStartTime.current,
          timestamp: unmountTime
        });
      }
    };
  }, [componentName]);

  // Function to track specific performance events
  const trackEvent = (eventName: string, metrics: Record<string, number>) => {
    telemetryManager.trackPerformance(`${componentName}.${eventName}`, metrics);
  };

  return { trackEvent };
};

// ================================================================================================
// User Interaction Tracking Hook
// ================================================================================================

export const useInteractionTracking = (componentName: string) => {
  const trackClick = (elementId: string, additionalData?: Record<string, any>) => {
    telemetryManager.trackInteraction(elementId, 'click', {
      component: componentName,
      timestamp: Date.now(),
      ...additionalData
    });
  };

  const trackHover = (elementId: string, duration?: number) => {
    telemetryManager.trackInteraction(elementId, 'hover', {
      component: componentName,
      duration,
      timestamp: Date.now()
    });
  };

  const trackScroll = (elementId: string, scrollPosition: number) => {
    telemetryManager.trackInteraction(elementId, 'scroll', {
      component: componentName,
      scrollPosition,
      timestamp: Date.now()
    });
  };

  const trackFormSubmit = (formId: string, formData: Record<string, any>) => {
    telemetryManager.trackInteraction(formId, 'submit', {
      component: componentName,
      formFields: Object.keys(formData).length,
      timestamp: Date.now()
    });
  };

  return {
    trackClick,
    trackHover,
    trackScroll,
    trackFormSubmit
  };
};

// ================================================================================================
// Enhanced Dashboard Page Integration
// ================================================================================================

export const useDashboardTelemetry = () => {
  const { trackEvent: trackPerformance } = usePerformanceTracking('DashboardPage');
  const { trackClick, trackHover } = useInteractionTracking('DashboardPage');

  useEffect(() => {
    // Initialize telemetry and error monitoring
    const initializeTelemetry = async () => {
      try {
        await telemetryManager.initialize();
        await errorMonitor.initialize();
        
        // Track dashboard page view
        telemetryManager.trackBusinessEvent('dashboard.page_view', {
          timestamp: Date.now(),
          referrer: document.referrer,
          viewport: {
            width: window.innerWidth,
            height: window.innerHeight
          }
        });

      } catch (error) {
        console.error('Failed to initialize telemetry:', error);
      }
    };

    initializeTelemetry();
  }, []);

  // Dashboard-specific tracking functions
  const trackConnectionRefresh = () => {
    trackClick('connection-refresh', { action: 'refresh_connection' });
  };

  const trackConnectionReconnect = async () => {
    const start = performance.now();
    trackClick('connection-reconnect', { action: 'reconnect_attempt' });
    
    try {
      // Track reconnect attempt and measure duration
      const end = performance.now();
      trackPerformance('connection.reconnect', {
        duration: end - start,
        success: 1
      });
    } catch (error) {
      const end = performance.now();
      trackPerformance('connection.reconnect', {
        duration: end - start,
        success: 0
      });
      
      errorMonitor.reportError(error as Error, 'network', 'medium', 'connection-reconnect');
    }
  };

  const trackCardInteraction = (cardType: string, action: string, data?: Record<string, any>) => {
    trackClick(`card-${cardType}`, {
      action,
      cardType,
      ...data
    });

    telemetryManager.trackBusinessEvent('dashboard.card_interaction', {
      cardType,
      action,
      timestamp: Date.now(),
      ...data
    });
  };

  const trackTimeFilterChange = (newFilter: string, previousFilter: string) => {
    trackClick('time-filter', {
      action: 'filter_change',
      newFilter,
      previousFilter
    });

    telemetryManager.trackBusinessEvent('dashboard.time_filter_changed', {
      newFilter,
      previousFilter,
      timestamp: Date.now()
    });
  };

  const trackDataRefresh = (dataType: string, success: boolean, duration?: number) => {
    trackPerformance(`data.refresh.${dataType}`, {
      duration: duration || 0,
      success: success ? 1 : 0
    });

    telemetryManager.trackBusinessEvent('dashboard.data_refresh', {
      dataType,
      success,
      duration,
      timestamp: Date.now()
    });
  };

  const trackNavigationToDetails = (detailsType: string) => {
    trackClick('navigation-details', {
      action: 'navigate_to_details',
      detailsType
    });

    telemetryManager.trackBusinessEvent('dashboard.navigation', {
      destination: 'details',
      detailsType,
      timestamp: Date.now()
    });
  };

  return {
    trackConnectionRefresh,
    trackConnectionReconnect,
    trackCardInteraction,
    trackTimeFilterChange,
    trackDataRefresh,
    trackNavigationToDetails,
    trackClick,
    trackHover,
    trackPerformance
  };
};

// ================================================================================================
// Settings Integration Hook
// ================================================================================================

export const useSettingsTelemetry = () => {
  const { trackClick } = useInteractionTracking('SettingsPage');

  const trackSettingChange = (settingName: string, oldValue: any, newValue: any) => {
    trackClick(`setting-${settingName}`, {
      action: 'setting_changed',
      settingName,
      oldValue: typeof oldValue,
      newValue: typeof newValue
    });

    telemetryManager.trackBusinessEvent('settings.changed', {
      settingName,
      oldValue: typeof oldValue,
      newValue: typeof newValue,
      timestamp: Date.now()
    });
  };

  const trackDataExport = (exportType: string, recordCount: number, format: string) => {
    trackClick('data-export', {
      action: 'export_data',
      exportType,
      recordCount,
      format
    });

    telemetryManager.trackBusinessEvent('settings.data_export', {
      exportType,
      recordCount,
      format,
      timestamp: Date.now()
    });
  };

  const trackDataClear = (dataType: string, recordCount: number) => {
    trackClick('data-clear', {
      action: 'clear_data',
      dataType,
      recordCount
    });

    telemetryManager.trackBusinessEvent('settings.data_clear', {
      dataType,
      recordCount,
      timestamp: Date.now()
    });
  };

  return {
    trackSettingChange,
    trackDataExport,
    trackDataClear
  };
};

// ================================================================================================
// Onboarding Tour Integration
// ================================================================================================

export const useOnboardingTelemetry = () => {
  const { trackClick } = useInteractionTracking('OnboardingTour');

  const trackTourStart = () => {
    trackClick('tour-start', { action: 'tour_started' });
    
    telemetryManager.trackBusinessEvent('onboarding.tour_started', {
      timestamp: Date.now(),
      userAgent: navigator.userAgent
    });
  };

  const trackTourStep = (stepNumber: number, stepName: string, action: 'next' | 'previous' | 'skip') => {
    trackClick(`tour-step-${stepNumber}`, {
      action: `step_${action}`,
      stepNumber,
      stepName
    });

    telemetryManager.trackBusinessEvent('onboarding.step_action', {
      stepNumber,
      stepName,
      action,
      timestamp: Date.now()
    });
  };

  const trackTourComplete = (totalSteps: number, completionTime: number) => {
    trackClick('tour-complete', {
      action: 'tour_completed',
      totalSteps,
      completionTime
    });

    telemetryManager.trackBusinessEvent('onboarding.tour_completed', {
      totalSteps,
      completionTime,
      completionRate: 100,
      timestamp: Date.now()
    });
  };

  const trackTourSkip = (currentStep: number, totalSteps: number) => {
    trackClick('tour-skip', {
      action: 'tour_skipped',
      currentStep,
      totalSteps
    });

    telemetryManager.trackBusinessEvent('onboarding.tour_skipped', {
      currentStep,
      totalSteps,
      completionRate: (currentStep / totalSteps) * 100,
      timestamp: Date.now()
    });
  };

  return {
    trackTourStart,
    trackTourStep,
    trackTourComplete,
    trackTourSkip
  };
};

// ================================================================================================
// Global Error Handler Setup
// ================================================================================================

export const initializeGlobalTelemetry = () => {
  // Initialize telemetry services
  telemetryManager.initialize().catch(console.error);
  errorMonitor.initialize().catch(console.error);

  // Set up global error handlers
  window.addEventListener('error', (event) => {
    errorMonitor.reportError(
      new Error(event.message),
      'javascript',
      'medium',
      'global-error-handler',
      {
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno
      }
    );
  });

  window.addEventListener('unhandledrejection', (event) => {
    const error = event.reason instanceof Error ? event.reason : new Error(String(event.reason));
    errorMonitor.reportError(error, 'javascript', 'high', 'unhandled-promise-rejection');
  });

  // Track page visibility changes
  document.addEventListener('visibilitychange', () => {
    telemetryManager.trackBusinessEvent('page.visibility_change', {
      visibilityState: document.visibilityState,
      timestamp: Date.now()
    });
  });

  // Track performance metrics periodically
  setInterval(async () => {
    try {
      const memory = (performance as any).memory;
      if (memory) {
        telemetryManager.trackPerformance('system.memory', {
          used: memory.usedJSHeapSize,
          total: memory.totalJSHeapSize,
          limit: memory.jsHeapSizeLimit
        });
      }

      // Check for performance issues
      if (memory && memory.usedJSHeapSize > 100 * 1024 * 1024) { // 100MB
        errorMonitor.reportPerformanceIssue(
          'memory.usage',
          memory.usedJSHeapSize,
          100 * 1024 * 1024,
          { component: 'global', type: 'memory_pressure' }
        );
      }

    } catch (error) {
      console.warn('Failed to collect system metrics:', error);
    }
  }, 30000); // Every 30 seconds

  console.log('[Telemetry] Global telemetry and error monitoring initialized');
};

// ================================================================================================
// Export All Integration Functions
// ================================================================================================

export {
  telemetryManager,
  errorMonitor
};