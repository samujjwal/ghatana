/**
 * OperationCenterPage — dedicated operations and job lifecycle management page.
 *
 * @doc.type page
 * @doc.purpose Full-page view of active and historical operations with retry/audit controls
 * @doc.layer frontend
 */
import React from 'react';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { OperationCenter } from '@/components/shared/OperationCenter';
import { PageState } from '@/components/shared/PageState';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { retryOperation, cancelOperation } from '@/api/aep.api';
import { toast } from 'sonner';

export function OperationCenterPage(): React.ReactElement {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  const retryMutation = useMutation({
    mutationFn: (operationId: string) => retryOperation(operationId, tenantId),
    onSuccess: (result) => {
      if (result.retried) {
        toast.success(`Operation ${result.operationId} retried successfully`);
        queryClient.invalidateQueries({ queryKey: ['aep', 'operations', tenantId] });
      }
    },
    onError: (error) => {
      toast.error(`Failed to retry operation: ${error instanceof Error ? error.message : 'Unknown error'}`);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (operationId: string) => cancelOperation(operationId, tenantId),
    onSuccess: (result) => {
      if (result.cancelled) {
        toast.success(`Operation ${result.operationId} cancelled successfully`);
        queryClient.invalidateQueries({ queryKey: ['aep', 'operations', tenantId] });
      }
    },
    onError: (error) => {
      toast.error(`Failed to cancel operation: ${error instanceof Error ? error.message : 'Unknown error'}`);
    },
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-white">Operation Center</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          Monitor active jobs, retry failures, and review historical operations.
        </p>
      </div>
      <OperationCenter
        tenantId={tenantId}
        maxRows={50}
        onRetry={(id) => retryMutation.mutate(id)}
        onCancel={(id) => cancelMutation.mutate(id)}
      />
    </div>
  );
}
