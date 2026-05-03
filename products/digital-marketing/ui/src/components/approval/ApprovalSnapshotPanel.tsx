/**
 * Approval snapshot panel — displays captured fields of the approved item.
 *
 * @doc.type component
 * @doc.purpose Render content snapshot and diff for approval review
 * @doc.layer frontend
 */
import React from 'react';
import type { ApprovalSnapshot } from '@/types/approval';

interface ApprovalSnapshotPanelProps {
  snapshot: ApprovalSnapshot;
}

export const ApprovalSnapshotPanel: React.FC<ApprovalSnapshotPanelProps> = ({
  snapshot,
}) => (
  <section
    aria-labelledby="snapshot-heading"
    data-testid="approval-snapshot-panel"
    className="border rounded p-4"
  >
    <h3
      id="snapshot-heading"
      className="text-sm font-semibold text-gray-700 mb-3"
    >
      Content Snapshot
      <span className="ml-2 text-xs font-normal text-gray-400">
        captured {new Date(snapshot.capturedAt).toLocaleString()}
      </span>
    </h3>

    {snapshot.fields.length === 0 ? (
      <p className="text-sm text-gray-400">No snapshot fields recorded.</p>
    ) : (
      <dl className="divide-y text-sm">
        {snapshot.fields.map((f) => (
          <div key={f.key} className="flex py-2 gap-4">
            <dt className="w-1/3 font-medium text-gray-600 truncate">{f.key}</dt>
            <dd
              data-testid={`snapshot-field-${f.key}`}
              className="w-2/3 text-gray-800 break-all"
            >
              {typeof f.value === 'object'
                ? JSON.stringify(f.value, null, 2)
                : String(f.value)}
            </dd>
          </div>
        ))}
      </dl>
    )}
  </section>
);
