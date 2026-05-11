/**
 * Approval snapshot panel — displays captured fields of the approved item.
 *
 * @doc.type component
 * @doc.purpose Render content snapshot and diff for approval review
 * @doc.layer frontend
 */
import React from 'react';
import type { ApprovalSnapshot } from '@/types/approval';
import { formatDateTime } from '@/lib/i18n/format';

interface ApprovalSnapshotPanelProps {
  snapshot: ApprovalSnapshot;
}

export const ApprovalSnapshotPanel: React.FC<ApprovalSnapshotPanelProps> = ({
  snapshot,
}) => (
  <section
    aria-labelledby="snapshot-heading"
    data-testid="approval-snapshot-panel"
    className="border rounded p-4 space-y-2"
  >
    <h3
      id="snapshot-heading"
      className="text-sm font-semibold text-gray-700"
    >
      Approval Snapshot
      <span className="ml-2 text-xs font-normal text-gray-400">
        captured {formatDateTime(snapshot.snapshotAt)}
      </span>
    </h3>

    <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
      <div>
        <dt className="font-medium text-gray-600">Target Type</dt>
        <dd data-testid="snapshot-field-targetType" className="font-mono text-xs">{snapshot.targetType}</dd>
      </div>
      <div>
        <dt className="font-medium text-gray-600">Target ID</dt>
        <dd data-testid="snapshot-field-targetId">{snapshot.targetId}</dd>
      </div>
      <div>
        <dt className="font-medium text-gray-600">Risk Level</dt>
        <dd data-testid="snapshot-field-riskLevel">{snapshot.riskLevel}</dd>
      </div>
      <div>
        <dt className="font-medium text-gray-600">Required Role</dt>
        <dd data-testid="snapshot-field-requiredApproverRole">{snapshot.requiredApproverRole}</dd>
      </div>
      {snapshot.snapshotSummary && (
        <div className="col-span-2">
          <dt className="font-medium text-gray-600">Summary</dt>
          <dd data-testid="snapshot-field-summary" className="text-gray-700 text-xs">{snapshot.snapshotSummary}</dd>
        </div>
      )}
    </dl>
  </section>
);
