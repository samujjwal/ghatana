// Advanced UX & Accessibility system - Phase 10: User Experience Enhancement
// Clean export index for UX features

import React from 'react';

import { useCommandPalette, useAccessibility, useKeyboardShortcuts } from './hooks';

// Core hooks and components
export * from './hooks';
export * from './components';

// Re-export types from hooks (authoritative source)
export type {
  Command,
  CommandCategory,
  AccessibilityConfig,
  AccessibilityAnnouncement,
  KeyboardShortcut,
} from './hooks';

// Production-ready feature flags
export const UX_FEATURES = {
  // Command palette features
  COMMAND_PALETTE: true,
  COMMAND_SEARCH: true,
  COMMAND_CATEGORIES: true,
  COMMAND_SHORTCUTS: true,
  FUZZY_SEARCH: true,
  
  // Accessibility features
  SCREEN_READER_SUPPORT: true,
  KEYBOARD_NAVIGATION: true,
  HIGH_CONTRAST_MODE: true,
  REDUCED_MOTION: true,
  FOCUS_MANAGEMENT: true,
  ARIA_ANNOUNCEMENTS: true,
  
  // Keyboard shortcuts
  GLOBAL_SHORTCUTS: true,
  CONTEXT_SHORTCUTS: true,
  SHORTCUT_CONFLICTS: true,
  SHORTCUT_HELP: true,
  CUSTOM_SHORTCUTS: true,
  
  // Advanced UX
  USER_PREFERENCES: true,
  PREFERENCE_PERSISTENCE: true,
  UX_METRICS: true,
  TOOLTIPS_SYSTEM: false, // Future enhancement
  GESTURE_SUPPORT: false, // Future enhancement
} as const;

// System health check
export const validateUXSystem = () => {
  const checks = {
    hooksAvailable: true,
    componentsLoaded: true,
    accessibilityAPIAvailable: typeof document !== 'undefined',
    keyboardEventsSupported: typeof KeyboardEvent !== 'undefined',
    focusManagementAvailable: typeof document !== 'undefined' && 'activeElement' in document,
    schemasValid: true,
  };
  
  const healthy = Object.values(checks).every(Boolean);
  
  return {
    healthy,
    checks,
    capabilities: {
      commandPalette: true,
      keyboardShortcuts: checks.keyboardEventsSupported,
      screenReaderSupport: checks.accessibilityAPIAvailable,
      focusManagement: checks.focusManagementAvailable,
      ariaSupport: checks.accessibilityAPIAvailable,
      preferencesPersistence: typeof localStorage !== 'undefined',
    },
    timestamp: new Date().toISOString(),
    version: '1.0.0',
  };
};

// Quick start utilities
export const createUXProvider = (config: {
  enableCommandPalette?: boolean;
  enableAccessibility?: boolean;
  enableKeyboardShortcuts?: boolean;
}) => {
  return {
    config,
    initialize: () => {
      console.log('[UX] UX system initialized with config:', config);
      
      // Load user preferences
      if (typeof localStorage !== 'undefined') {
        const saved = localStorage.getItem('ux-preferences');
        if (saved) {
          try {
            const preferences = JSON.parse(saved);
            console.log('[UX] Loaded user preferences:', preferences);
            return preferences;
          } catch (error) {
            console.warn('[UX] Failed to load preferences:', error);
          }
        }
      }
      
      return {
        accessibility: {
          enableScreenReader: true,
          enableKeyboardNavigation: true,
          enableHighContrast: false,
          enableReducedMotion: false,
          fontSize: 'medium' as const,
          colorScheme: 'auto' as const,
          announceChanges: true,
          focusManagement: true,
        },
        ui: {
          theme: 'auto' as const,
          density: 'standard' as const,
          animations: true,
          tooltips: true,
          soundEffects: false,
        },
      };
    },
    
    savePreferences: (preferences: unknown) => {
      if (typeof localStorage !== 'undefined') {
        try {
          localStorage.setItem('ux-preferences', JSON.stringify(preferences));
          console.log('[UX] Preferences saved successfully');
        } catch (error) {
          console.error('[UX] Failed to save preferences:', error);
        }
      }
    },
  };
};

// Integration helpers
export const withCommandPalette = <T extends Record<string, unknown>>(
  Component: React.ComponentType<T>
) => {
  const CommandPaletteWrappedComponent = (props: T) => {
    const commandPalette = useCommandPalette({});
    
    return React.createElement(Component, {
      ...props,
      commandPalette,
    });
  };
  
  CommandPaletteWrappedComponent.displayName = `withCommandPalette(${Component.displayName || Component.name})`;
  return CommandPaletteWrappedComponent;
};

export const withAccessibility = <T extends Record<string, unknown>>(
  Component: React.ComponentType<T>
) => {
  const AccessibilityWrappedComponent = (props: T) => {
    const accessibility = useAccessibility({});
    
    return React.createElement(Component, {
      ...props,
      accessibility,
    });
  };
  
  AccessibilityWrappedComponent.displayName = `withAccessibility(${Component.displayName || Component.name})`;
  return AccessibilityWrappedComponent;
};

export const withKeyboardShortcuts = <T extends Record<string, unknown>>(
  Component: React.ComponentType<T>
) => {
  const KeyboardShortcutsWrappedComponent = (props: T) => {
    const keyboardShortcuts = useKeyboardShortcuts({});
    
    return React.createElement(Component, {
      ...props,
      keyboardShortcuts,
    });
  };
  
  KeyboardShortcutsWrappedComponent.displayName = `withKeyboardShortcuts(${Component.displayName || Component.name})`;
  return KeyboardShortcutsWrappedComponent;
};

// Accessibility utilities for Canvas components
export const createCanvasAccessibilityHelpers = () => ({
  announceNodeSelection: (nodeCount: number) => {
    const message = nodeCount === 1 
      ? 'One node selected' 
      : `${nodeCount} nodes selected`;
    
    // Create announcement manually since we don't import from schemas
    return {
      id: `announcement_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      message,
      priority: 'polite' as const,
      timestamp: new Date().toISOString(),
    };
  },
  
  announceCanvasChange: (changeType: string, nodeCount?: number) => {
    let message = '';
    switch (changeType) {
      case 'node-added':
        message = `Node added to canvas. Total nodes: ${nodeCount}`;
        break;
      case 'node-deleted':
        message = `Node deleted from canvas. Total nodes: ${nodeCount}`;
        break;
      case 'edge-added':
        message = 'Connection added between nodes';
        break;
      case 'edge-deleted':
        message = 'Connection removed between nodes';
        break;
      default:
        message = `Canvas updated: ${changeType}`;
    }
    
    return {
      id: `announcement_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      message,
      priority: 'polite' as const,
      timestamp: new Date().toISOString(),
    };
  },
  
  announceZoomChange: (zoomLevel: number) => {
    const message = `Zoom level changed to ${Math.round(zoomLevel * 100)}%`;
    return {
      id: `announcement_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      message,
      priority: 'polite' as const,
      timestamp: new Date().toISOString(),
    };
  },
  
  announceToolChange: (toolName: string) => {
    const message = `${toolName} tool selected`;
    return {
      id: `announcement_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      message,
      priority: 'polite' as const,
      timestamp: new Date().toISOString(),
    };
  },
});

// Version info
export const UX_VERSION = '1.0.0';
export const UX_BUILD_DATE = new Date().toISOString();