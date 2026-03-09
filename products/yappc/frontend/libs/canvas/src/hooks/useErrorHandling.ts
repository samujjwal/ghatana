import { useState, useCallback, useRef, useEffect } from 'react';

import type { Node, Edge } from '@xyflow/react';

// Error types
/**
 *
 */
export interface CanvasError {
  id: string;
  type: 'validation' | 'network' | 'runtime' | 'collaboration' | 'performance' | 'data';
  level: 'error' | 'warning' | 'info';
  message: string;
  details?: string;
  timestamp: Date;
  source: {
    component: string;
    function: string;
    nodeId?: string;
    edgeId?: string;
  };
  context?: Record<string, unknown>;
  stackTrace?: string;
  resolved: boolean;
  resolution?: {
    action: string;
    timestamp: Date;
    user: string;
  };
}

// Recovery strategies
/**
 *
 */
export interface RecoveryStrategy {
  id: string;
  name: string;
  description: string;
  automatic: boolean;
  conditions: (error: CanvasError, context: unknown) => boolean;
  recover: (error: CanvasError, context: unknown) => Promise<boolean>;
  rollback?: (context: unknown) => Promise<void>;
}

// Error handling hook interface
/**
 *
 */
export interface UseErrorHandlingReturn {
  errors: CanvasError[];
  hasErrors: boolean;
  hasWarnings: boolean;
  reportError: (error: Omit<CanvasError, 'id' | 'timestamp' | 'resolved'>) => void;
  resolveError: (errorId: string, resolution: CanvasError['resolution']) => void;
  clearErrors: (type?: CanvasError['type']) => void;
  retryOperation: (operationId: string) => Promise<void>;
  recoverFromError: (errorId: string, strategyId?: string) => Promise<boolean>;
  createRecoveryPoint: () => string;
  restoreFromRecoveryPoint: (pointId: string) => Promise<boolean>;
  exportErrorLogs: () => any;
  getErrorStats: () => {
    total: number;
    byType: Record<string, number>;
    byLevel: Record<string, number>;
    resolved: number;
    unresolved: number;
  };
}

// Recovery point interface
/**
 *
 */
interface RecoveryPoint {
  id: string;
  timestamp: Date;
  description: string;
  data: {
    nodes: Node[];
    edges: Edge[];
    canvas: unknown;
    user: unknown;
  };
  metadata: {
    version: string;
    user: string;
    automatic: boolean;
  };
}

// Default recovery strategies
const defaultRecoveryStrategies: RecoveryStrategy[] = [
  {
    id: 'retry-network',
    name: 'Retry Network Operation',
    description: 'Automatically retry failed network operations',
    automatic: true,
    conditions: (error, context) =>
      error.type === 'network' && context.retryCount < 3,
    recover: async (error, context) => {
      try {
        // Retry the failed operation
        if (context.originalOperation) {
          await context.originalOperation();
          return true;
        }
        return false;
      } catch (e) {
        return false;
      }
    }
  },
  {
    id: 'fallback-offline',
    name: 'Offline Fallback',
    description: 'Switch to offline mode when network fails',
    automatic: true,
    conditions: (error, context) =>
      error.type === 'network' && error.message.includes('connection'),
    recover: async (error, context) => {
      // Enable offline mode
      context.setOfflineMode?.(true);
      return true;
    }
  },
  {
    id: 'reset-node',
    name: 'Reset Node',
    description: 'Reset a problematic node to default state',
    automatic: false,
    conditions: (error, context) =>
      error.type === 'validation' && error.source.nodeId,
    recover: async (error, context) => {
      if (error.source.nodeId && context.resetNode) {
        context.resetNode(error.source.nodeId);
        return true;
      }
      return false;
    }
  },
  {
    id: 'rollback-operation',
    name: 'Rollback Operation',
    description: 'Rollback the last operation that caused the error',
    automatic: false,
    conditions: (error, context) =>
      error.type === 'runtime' && context.canRollback,
    recover: async (error, context) => {
      try {
        await context.rollbackLastOperation?.();
        return true;
      } catch (e) {
        return false;
      }
    }
  }
];

/**
 * Hook for comprehensive error handling with recovery strategies and error tracking
 * 
 * Provides robust error management features including:
 * - Multi-level error classification (error, warning, info)
 * - Error categorization by type (validation, network, runtime, collaboration, performance, data)
 * - Automatic and manual recovery strategies
 * - Recovery point creation and restoration (undo/redo)
 * - Error resolution tracking and audit trail
 * - Operation retry mechanism
 * - Error statistics and reporting
 * - Export error logs for debugging
 * - Stack trace capture and context preservation
 * 
 * Implements production-ready error handling for canvas operations with rollback capabilities.
 * 
 * @param initialStrategies - Array of recovery strategies to use (defaults to built-in strategies)
 * @returns Error handling state and operations
 * 
 * @example
 * ```tsx
 * function ErrorBoundaryCanvas() {
 *   const {
 *     errors,
 *     hasErrors,
 *     hasWarnings,
 *     reportError,
 *     resolveError,
 *     clearErrors,
 *     retryOperation,
 *     recoverFromError,
 *     createRecoveryPoint,
 *     restoreFromRecoveryPoint,
 *     getErrorStats
 *   } = useErrorHandling();
 *   
 *   const performRiskyOperation = async () => {
 *     const recoveryPoint = createRecoveryPoint();
 *     
 *     try {
 *       await updateCanvas();
 *     } catch (err) {
 *       reportError({
 *         type: 'runtime',
 *         level: 'error',
 *         message: 'Canvas update failed',
 *         details: err.message,
 *         source: {
 *           component: 'Canvas',
 *           function: 'performRiskyOperation'
 *         },
 *         context: { recoveryPoint }
 *       });
 *       
 *       // Try automatic recovery
 *       const recovered = await recoverFromError(errorId);
 *       if (!recovered) {
 *         // Manual rollback
 *         await restoreFromRecoveryPoint(recoveryPoint);
 *       }
 *     }
 *   };
 *   
 *   const stats = getErrorStats();
 *   
 *   return (
 *     <div>
 *       {hasErrors && (
 *         <ErrorPanel
 *           errors={errors.filter(e => e.level === 'error')}
 *           onResolve={resolveError}
 *           onRetry={retryOperation}
 *         />
 *       )}
 *       {hasWarnings && (
 *         <WarningBanner
 *           warnings={errors.filter(e => e.level === 'warning')}
 *           onDismiss={(id) => resolveError(id, { action: 'dismissed', timestamp: new Date(), user: 'current' })}
 *         />
 *       )}
 *       <button onClick={() => clearErrors()}>Clear All Errors</button>
 *       <ErrorStats stats={stats} />
 *     </div>
 *   );
 * }
 * ```
 */
export const useErrorHandling = (
  initialStrategies: RecoveryStrategy[] = defaultRecoveryStrategies
): UseErrorHandlingReturn => {
  const [errors, setErrors] = useState<CanvasError[]>([]);
  const [recoveryPoints, setRecoveryPoints] = useState<RecoveryPoint[]>([]);
  const [recoveryStrategies] = useState<RecoveryStrategy[]>(initialStrategies);

  const retryOperations = useRef<Map<string, () => Promise<void>>>(new Map());
  const errorContext = useRef<unknown>({});

  // Calculate derived states
  const hasErrors = errors.some(e => e.level === 'error' && !e.resolved);
  const hasWarnings = errors.some(e => e.level === 'warning' && !e.resolved);

  // Report a new error
  const reportError = useCallback((error: Omit<CanvasError, 'id' | 'timestamp' | 'resolved'>) => {
    const newError: CanvasError = {
      ...error,
      id: `error-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      timestamp: new Date(),
      resolved: false
    };

    setErrors(prev => [...prev, newError]);

    // Try automatic recovery
    setTimeout(async () => {
      const applicableStrategies = recoveryStrategies.filter(
        strategy => strategy.automatic && strategy.conditions(newError, errorContext.current)
      );

      for (const strategy of applicableStrategies) {
        try {
          const recovered = await strategy.recover(newError, errorContext.current);
          if (recovered) {
            resolveError(newError.id, {
              action: `Auto-recovered using ${strategy.name}`,
              timestamp: new Date(),
              user: 'system'
            });
            break;
          }
        } catch (recoveryError) {
          console.error('Recovery strategy failed:', recoveryError);
        }
      }
    }, 100);

    // Log error for debugging
    console.error('Canvas Error:', newError);

    return newError.id;
  }, [recoveryStrategies]);

  // Resolve an error
  const resolveError = useCallback((errorId: string, resolution: CanvasError['resolution']) => {
    setErrors(prev => prev.map(error =>
      error.id === errorId
        ? { ...error, resolved: true, resolution }
        : error
    ));
  }, []);

  // Clear errors
  const clearErrors = useCallback((type?: CanvasError['type']) => {
    if (type) {
      setErrors(prev => prev.filter(error => error.type !== type));
    } else {
      setErrors([]);
    }
  }, []);

  // Retry a failed operation
  const retryOperation = useCallback(async (operationId: string) => {
    const operation = retryOperations.current.get(operationId);
    if (operation) {
      try {
        await operation();
        // Clear related errors
        setErrors(prev => prev.map(error =>
          error.context?.operationId === operationId
            ? {
              ...error, resolved: true, resolution: {
                action: 'Retried successfully',
                timestamp: new Date(),
                user: 'user'
              }
            }
            : error
        ));
      } catch (error) {
        reportError({
          type: 'runtime',
          level: 'error',
          message: 'Retry operation failed',
          source: { component: 'ErrorHandler', function: 'retryOperation' },
          context: { operationId, originalError: error }
        });
      }
    }
  }, [reportError]);

  // Recover from a specific error
  const recoverFromError = useCallback(async (errorId: string, strategyId?: string): Promise<boolean> => {
    const error = errors.find(e => e.id === errorId);
    if (!error) return false;

    let strategiesToTry = recoveryStrategies;
    if (strategyId) {
      const specificStrategy = recoveryStrategies.find(s => s.id === strategyId);
      strategiesToTry = specificStrategy ? [specificStrategy] : [];
    } else {
      strategiesToTry = recoveryStrategies.filter(s => s.conditions(error, errorContext.current));
    }

    for (const strategy of strategiesToTry) {
      try {
        const recovered = await strategy.recover(error, errorContext.current);
        if (recovered) {
          resolveError(errorId, {
            action: `Recovered using ${strategy.name}`,
            timestamp: new Date(),
            user: 'user'
          });
          return true;
        }
      } catch (recoveryError) {
        console.error(`Recovery strategy ${strategy.name} failed:`, recoveryError);
      }
    }

    return false;
  }, [errors, recoveryStrategies, resolveError]);

  // Create a recovery point
  const createRecoveryPoint = useCallback((description: string = 'Manual recovery point'): string => {
    const recoveryPoint: RecoveryPoint = {
      id: `recovery-${Date.now()}`,
      timestamp: new Date(),
      description,
      data: {
        nodes: errorContext.current.nodes || [],
        edges: errorContext.current.edges || [],
        canvas: errorContext.current.canvas || {},
        user: errorContext.current.user || {}
      },
      metadata: {
        version: '1.0.0',
        user: errorContext.current.userId || 'unknown',
        automatic: false
      }
    };

    setRecoveryPoints(prev => [...prev, recoveryPoint].slice(-10)); // Keep last 10 points
    return recoveryPoint.id;
  }, []);

  // Restore from recovery point
  const restoreFromRecoveryPoint = useCallback(async (pointId: string): Promise<boolean> => {
    const recoveryPoint = recoveryPoints.find(p => p.id === pointId);
    if (!recoveryPoint) return false;

    try {
      // Restore the canvas state
      if (errorContext.current.restoreCanvas) {
        await errorContext.current.restoreCanvas(recoveryPoint.data);
      }

      // Clear current errors
      clearErrors();

      reportError({
        type: 'runtime',
        level: 'info',
        message: `Restored from recovery point: ${recoveryPoint.description}`,
        source: { component: 'ErrorHandler', function: 'restoreFromRecoveryPoint' },
        context: { recoveryPointId: pointId }
      });

      return true;
    } catch (error) {
      reportError({
        type: 'runtime',
        level: 'error',
        message: 'Failed to restore from recovery point',
        source: { component: 'ErrorHandler', function: 'restoreFromRecoveryPoint' },
        context: { recoveryPointId: pointId, error }
      });
      return false;
    }
  }, [recoveryPoints, clearErrors, reportError]);

  // Export error logs
  const exportErrorLogs = useCallback(() => {
    return {
      errors: errors.map(error => ({
        ...error,
        timestamp: error.timestamp.toISOString(),
        resolution: error.resolution ? {
          ...error.resolution,
          timestamp: error.resolution.timestamp.toISOString()
        } : undefined
      })),
      recoveryPoints: recoveryPoints.map(point => ({
        ...point,
        timestamp: point.timestamp.toISOString(),
        // Exclude actual data from export for size reasons
        data: { summary: 'Data excluded from export' }
      })),
      exportTimestamp: new Date().toISOString(),
      version: '1.0.0'
    };
  }, [errors, recoveryPoints]);

  // Get error statistics
  const getErrorStats = useCallback(() => {
    const stats = {
      total: errors.length,
      byType: {} as Record<string, number>,
      byLevel: {} as Record<string, number>,
      resolved: errors.filter(e => e.resolved).length,
      unresolved: errors.filter(e => !e.resolved).length
    };

    errors.forEach(error => {
      stats.byType[error.type] = (stats.byType[error.type] || 0) + 1;
      stats.byLevel[error.level] = (stats.byLevel[error.level] || 0) + 1;
    });

    return stats;
  }, [errors]);

  // Auto-create recovery points on major errors
  useEffect(() => {
    const majorErrors = errors.filter(e =>
      e.level === 'error' &&
      ['runtime', 'data'].includes(e.type) &&
      !e.resolved
    );

    if (majorErrors.length > 0 && recoveryPoints.length === 0) {
      createRecoveryPoint('Auto-created before major error');
    }
  }, [errors, recoveryPoints.length, createRecoveryPoint]);

  // Update error context
  const updateErrorContext = useCallback((newContext: unknown) => {
    errorContext.current = { ...errorContext.current, ...newContext };
  }, []);

  // Register retry operation
  const registerRetryOperation = useCallback((operationId: string, operation: () => Promise<void>) => {
    retryOperations.current.set(operationId, operation);
  }, []);

  return {
    errors,
    hasErrors,
    hasWarnings,
    reportError,
    resolveError,
    clearErrors,
    retryOperation,
    recoverFromError,
    createRecoveryPoint,
    restoreFromRecoveryPoint,
    exportErrorLogs,
    getErrorStats,
    // Additional methods for internal use
    updateErrorContext,
    registerRetryOperation
  } as UseErrorHandlingReturn & {
    updateErrorContext: (context: unknown) => void;
    registerRetryOperation: (id: string, op: () => Promise<void>) => void;
  };
};