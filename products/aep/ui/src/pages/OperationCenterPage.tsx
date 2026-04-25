/**
 * OperationCenterPage — dedicated operations and job lifecycle management page.
 *
 * @doc.type page
 * @doc.purpose Full-page view of active and historical operations with retry/audit controls
 * @doc.layer frontend
 */
import React from 'react';
import { OperationCenter } from '@/components/shared/OperationCenter';
import { PageState } from '@/components/shared/PageState';

export function OperationCenterPage(): React.ReactElement {
  return (
    <div className="mx-auto max-w-5xl px-4 py-6">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-white">Operation Center</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          Monitor active jobs, retry failures, and review historical operations.
        </p>
      </div>
      <OperationCenter
        maxRows={25}
        onRetry={(id) => {
          // eslint-disable-next-line no-console
          console.info('Retry operation', id);
        }}
        onCancel={(id) => {
          // eslint-disable-next-line no-console
          console.info('Cancel operation', id);
        }}
      />
    </div>
  );
}
