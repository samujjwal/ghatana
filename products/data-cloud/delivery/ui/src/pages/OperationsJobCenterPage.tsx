/**
 * Operations Job Center Page
 *
 * Consolidated view of in-flight and recently completed background jobs tracked
 * by OperationsContext. Provides a single place to audit operation outcomes
 * beyond the floating ActiveOperationsBar.
 *
 * @doc.type page
 * @doc.purpose Unified background operation visibility for operators/admins
 * @doc.layer frontend
 */

import React from 'react';
import { Link } from 'react-router';
import { CheckCircle2, Clock3, RefreshCw, XCircle } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { useOperations } from '../contexts/OperationsContext';
import { cn, cardStyles, textStyles } from '../lib/theme';

function statusTone(status: 'pending' | 'success' | 'failure'): string {
  if (status === 'success') return 'text-green-600 dark:text-green-300';
  if (status === 'failure') return 'text-red-600 dark:text-red-300';
  return 'text-blue-600 dark:text-blue-300';
}

function StatusIcon({ status }: { status: 'pending' | 'success' | 'failure' }): React.ReactElement {
  if (status === 'success') {
    return <CheckCircle2 className="h-4 w-4 text-green-500" />;
  }

  if (status === 'failure') {
    return <XCircle className="h-4 w-4 text-red-500" />;
  }

  return <RefreshCw className="h-4 w-4 animate-spin text-blue-500" />;
}

export function OperationsJobCenterPage(): React.ReactElement {
  const { jobs, dismissJob, dismissAllCompleted } = useOperations();

  const pendingJobs = jobs.filter((job) => job.status === 'pending');
  const completedJobs = jobs.filter((job) => job.status !== 'pending');

  return (
    <section className="space-y-6" aria-label="Operations job center" data-testid="operations-job-center">
      <header className={cn(cardStyles.base, cardStyles.padded)}>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className={textStyles.h1}>Operations Job Center</h1>
            <p className={textStyles.muted}>
              Session-scoped background jobs from governance, workflow, and operations actions.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={dismissAllCompleted} disabled={completedJobs.length === 0}>
              Dismiss Completed
            </Button>
            <Link to="/operations" className="text-sm text-blue-600 hover:underline dark:text-blue-400">
              Back to Operations Console
            </Link>
          </div>
        </div>
      </header>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <div className={cn(cardStyles.base, cardStyles.padded)}>
          <p className={textStyles.label}>Pending</p>
          <p className={textStyles.h2}>{pendingJobs.length}</p>
        </div>
        <div className={cn(cardStyles.base, cardStyles.padded)}>
          <p className={textStyles.label}>Completed</p>
          <p className={textStyles.h2}>{completedJobs.length}</p>
        </div>
        <div className={cn(cardStyles.base, cardStyles.padded)}>
          <p className={textStyles.label}>Failures</p>
          <p className={textStyles.h2}>{jobs.filter((job) => job.status === 'failure').length}</p>
        </div>
      </div>

      <div className={cn(cardStyles.base, cardStyles.padded)}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className={textStyles.h3}>Job Timeline</h2>
          <span className="text-xs text-gray-500 dark:text-gray-400">
            Ordered by creation time
          </span>
        </div>

        {jobs.length === 0 ? (
          <div className="rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <Clock3 className="mx-auto mb-2 h-5 w-5" />
            No background jobs recorded in this session yet.
          </div>
        ) : (
          <div className="space-y-2">
            {jobs.map((job) => (
              <div
                key={job.id}
                className="flex flex-wrap items-center gap-3 rounded-lg border border-gray-200 px-3 py-2 dark:border-gray-700"
              >
                <StatusIcon status={job.status} />
                <div className="min-w-52 flex-1">
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{job.name}</p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    Started {new Date(job.startedAt).toLocaleString()}
                    {job.completedAt ? ` • Completed ${new Date(job.completedAt).toLocaleString()}` : ''}
                  </p>
                  {job.detail ? <p className="text-xs text-gray-600 dark:text-gray-300">{job.detail}</p> : null}
                </div>
                <span className={cn('text-xs font-semibold uppercase tracking-wide', statusTone(job.status))}>
                  {job.status}
                </span>
                {job.status !== 'pending' ? (
                  <Button variant="ghost" size="sm" onClick={() => dismissJob(job.id)}>
                    Dismiss
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

export default OperationsJobCenterPage;
