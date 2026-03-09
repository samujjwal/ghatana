/**
 * Canvas URL Integration Component
 *
 * Integrates URL parameters with Canvas state, providing automatic
 * synchronization of task focus, persona selection, and viewport state.
 *
 * @doc.type component
 * @doc.purpose URL parameter integration for Canvas
 * @doc.layer integration
 * @doc.pattern Integration Layer
 */

import React, { useEffect, useCallback } from 'react';
import { Box, Alert } from '@ghatana/ui';
import { Snackbar } from '@ghatana/ui';
import { useCanvasURLState } from '../../hooks/useCanvasURLState';
import { useTaskFocusedCanvas } from '../../hooks/useTaskFocusedCanvas';
import { PersonaAdaptiveCanvas } from './PersonaAdaptiveCanvas';
import { usePersona } from '../../context/PersonaContext';
import type { PersonaType } from '../../context/PersonaContext';

/**
 * Component props
 */
export interface CanvasURLIntegrationProps {
  /** Canvas content */
  children: React.ReactNode;
  /** Project identifier */
  projectId: string;
  /** Callback when URL state is applied */
  onURLStateApplied?: (state: {
    taskId?: string;
    persona?: PersonaType;
    nodeId?: string;
  }) => void;
  /** Callback when URL state fails to apply */
  onURLStateError?: (error: string) => void;
  /** Whether to show notifications */
  showNotifications?: boolean;
}

/**
 * Canvas URL Integration Component
 *
 * Wraps Canvas with URL parameter integration, automatically applying
 * task focus, persona selection, and viewport state from URL.
 *
 * @example
 * ```tsx
 * <CanvasURLIntegration projectId="alpha">
 *   <YourCanvasComponent />
 * </CanvasURLIntegration>
 * ```
 */
export const CanvasURLIntegration: React.FC<CanvasURLIntegrationProps> = ({
  children,
  projectId,
  onURLStateApplied,
  onURLStateError,
  showNotifications = true,
}) => {
  // Stabilize callbacks to prevent infinite re-render loops
  const handleStateChange = useCallback((state: unknown) => {
    console.log('[Canvas URL] State changed:', state);
  }, []);

  const handleValidationError = useCallback((validationErrors: string[]) => {
    console.error('[Canvas URL] Validation errors:', validationErrors);
    if (onURLStateError) {
      onURLStateError(validationErrors.join(', '));
    }
  }, [onURLStateError]);

  const { urlState, isValid, errors } = useCanvasURLState({
    syncToURL: true,
    onStateChange: handleStateChange,
    onValidationError: handleValidationError,
  });

  const { focusedTask, focusTask, clearFocus } =
    useTaskFocusedCanvas(projectId);
  const { setPrimaryPersona } = usePersona();

  const [notification, setNotification] = React.useState<{
    open: boolean;
    message: string;
    severity: 'success' | 'error' | 'info';
  }>({
    open: false,
    message: '',
    severity: 'info',
  });

  /**
   * Apply URL state to Canvas
   */
  const applyURLState = useCallback(() => {
    if (!isValid) {
      console.warn('[Canvas URL] Invalid URL state:', errors);
      return;
    }

    let applied = false;
    const appliedState: {
      taskId?: string;
      persona?: PersonaType;
      nodeId?: string;
    } = {};

    // Apply task focus
    if (urlState.taskId && urlState.taskId !== focusedTask?.id) {
      focusTask(urlState.taskId);
      appliedState.taskId = urlState.taskId;
      applied = true;

      if (showNotifications) {
        setNotification({
          open: true,
          message: `Focused on task: ${urlState.taskId}`,
          severity: 'info',
        });
      }
    }

    // Apply persona
    if (urlState.persona) {
      setPrimaryPersona(urlState.persona);
      appliedState.persona = urlState.persona;
      applied = true;

      if (showNotifications) {
        setNotification({
          open: true,
          message: `Switched to ${urlState.persona} persona`,
          severity: 'info',
        });
      }
    }

    // Apply node focus
    if (urlState.nodeId) {
      appliedState.nodeId = urlState.nodeId;
      applied = true;
    }

    // Notify parent
    if (applied && onURLStateApplied) {
      onURLStateApplied(appliedState);
    }

    // Log telemetry
    if (applied) {
      console.log('[Canvas URL] Applied state:', appliedState);
    }
  }, [
    urlState,
    isValid,
    errors,
    focusedTask,
    focusTask,
    setPrimaryPersona,
    onURLStateApplied,
    showNotifications,
  ]);

  /**
   * Apply URL state on mount and when URL changes
   */
  useEffect(() => {
    applyURLState();
  }, [applyURLState]);

  /**
   * Clear focus when task is removed from URL
   */
  useEffect(() => {
    if (!urlState.taskId && focusedTask) {
      clearFocus();
    }
  }, [urlState.taskId, focusedTask, clearFocus]);

  /**
   * Handle notification close
   */
  const handleNotificationClose = useCallback(() => {
    setNotification((prev) => ({ ...prev, open: false }));
  }, []);

  return (
    <>
      {/* Validation Errors */}
      {!isValid && errors.length > 0 && (
        <Box
          className="absolute top-[16px] left-[50%] z-[10000] max-w-[600px]" >
          <Alert severity="error" onClose={() => { }}>
            <strong>Invalid URL Parameters:</strong>
            <ul style={{ margin: '8px 0 0 0', paddingLeft: '20px', transform: 'translateX(-50%)' }}>
              {errors.map((error, index) => (
                <li key={index}>{error}</li>
              ))}
            </ul>
          </Alert>
        </Box>
      )}

      {/* Persona-Adaptive Canvas Wrapper */}
      <PersonaAdaptiveCanvas
        projectId={projectId}
        showPersonaIndicator={!!urlState.persona}
        onPersonaChange={(persona) => {
          console.log('[Canvas URL] Persona changed:', persona);
        }}
      >
        {children}
      </PersonaAdaptiveCanvas>

      {/* Notifications */}
      {showNotifications && (
        <Snackbar
          open={notification.open}
          autoHideDuration={3000}
          onClose={handleNotificationClose}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        >
          <Alert
            onClose={handleNotificationClose}
            severity={notification.severity}
            className="w-full"
          >
            {notification.message}
          </Alert>
        </Snackbar>
      )}

      {/* Debug Info (Development Only) */}
      {process.env.NODE_ENV === 'development' && (
        <Box
          className="fixed p-4 rounded-lg text-xs bottom-[16px] right-[16px] z-[9999] bg-white dark:bg-gray-900 shadow-md font-mono max-w-[300px] opacity-[0.8]"
        >
          <Box className="font-bold mb-2">Canvas URL State</Box>
          <Box className="flex flex-col gap-1">
            <Box>
              <strong>Valid:</strong> {isValid ? '✅' : '❌'}
            </Box>
            {urlState.taskId && (
              <Box>
                <strong>Task:</strong> {urlState.taskId}
              </Box>
            )}
            {urlState.persona && (
              <Box>
                <strong>Persona:</strong> {urlState.persona}
              </Box>
            )}
            {urlState.nodeId && (
              <Box>
                <strong>Node:</strong> {urlState.nodeId}
              </Box>
            )}
            {urlState.viewMode && (
              <Box>
                <strong>View:</strong> {urlState.viewMode}
              </Box>
            )}
            {urlState.zoom && (
              <Box>
                <strong>Zoom:</strong> {urlState.zoom.toFixed(2)}
              </Box>
            )}
            {focusedTask && (
              <Box
                className="mt-2 pt-2 border-t border-solid border-gray-200 dark:border-gray-700"
              >
                <strong>Focused Task:</strong>
                <Box className="text-[11px] mt-1">{focusedTask.name}</Box>
              </Box>
            )}
          </Box>
        </Box>
      )}
    </>
  );
};

/**
 * Higher-order component to wrap Canvas with URL integration
 *
 * @example
 * ```tsx
 * const EnhancedCanvas = withCanvasURLIntegration(MyCanvasComponent);
 * ```
 */
export function withCanvasURLIntegration<P extends object>(
  Component: React.ComponentType<P>
) {
  return function CanvasWithURLIntegration(props: P & { projectId: string }) {
    return (
      <CanvasURLIntegration projectId={props.projectId}>
        <Component {...props} />
      </CanvasURLIntegration>
    );
  };
}
