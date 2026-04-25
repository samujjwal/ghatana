/**
 * OperationsContext
 *
 * Global React context that tracks in-flight and recently completed background
 * mutations across all Data Cloud pages. Provides visibility into job status
 * without requiring individual pages to surface their own progress states.
 *
 * Usage: wrap the component tree with <OperationsProvider> and use
 * useOperations() inside any component that needs to track a mutation.
 *
 * @doc.type context
 * @doc.purpose Background job visibility across all DC pages
 * @doc.layer frontend
 * @doc.pattern Context Provider
 */

import React, { createContext, useCallback, useContext, useState } from 'react';

export type JobStatus = 'pending' | 'success' | 'failure';

export interface BackgroundJob {
  id: string;
  name: string;
  status: JobStatus;
  startedAt: string;
  completedAt?: string;
  detail?: string;
}

interface OperationsContextValue {
  jobs: BackgroundJob[];
  startJob: (name: string) => string;
  completeJob: (id: string, status: 'success' | 'failure', detail?: string) => void;
  dismissJob: (id: string) => void;
  dismissAllCompleted: () => void;
}

const OperationsContext = createContext<OperationsContextValue | null>(null);

let jobCounter = 0;

/**
 * Provider component. Mount once at the layout/app root.
 */
export function OperationsProvider({ children }: { children: React.ReactNode }): React.ReactElement {
  const [jobs, setJobs] = useState<BackgroundJob[]>([]);

  const startJob = useCallback((name: string): string => {
    jobCounter += 1;
    const id = `job-${jobCounter}`;
    setJobs((prev) => [
      ...prev,
      { id, name, status: 'pending', startedAt: new Date().toISOString() },
    ]);
    return id;
  }, []);

  const completeJob = useCallback(
    (id: string, status: 'success' | 'failure', detail?: string) => {
      setJobs((prev) =>
        prev.map((j) =>
          j.id === id
            ? { ...j, status, completedAt: new Date().toISOString(), ...(detail !== undefined ? { detail } : {}) }
            : j,
        ),
      );
    },
    [],
  );

  const dismissJob = useCallback((id: string) => {
    setJobs((prev) => prev.filter((j) => j.id !== id));
  }, []);

  const dismissAllCompleted = useCallback(() => {
    setJobs((prev) => prev.filter((j) => j.status === 'pending'));
  }, []);

  return (
    <OperationsContext.Provider value={{ jobs, startJob, completeJob, dismissJob, dismissAllCompleted }}>
      {children}
    </OperationsContext.Provider>
  );
}

/**
 * Returns the operations context. Must be used inside <OperationsProvider>.
 */
export function useOperations(): OperationsContextValue {
  const ctx = useContext(OperationsContext);
  if (!ctx) {
    throw new Error('useOperations must be used inside <OperationsProvider>');
  }
  return ctx;
}
