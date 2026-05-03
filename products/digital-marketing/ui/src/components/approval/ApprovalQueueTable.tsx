/**
 * Approval queue table.
 *
 * Displays pending approvals with risk badge, type, urgency, and actions.
 *
 * @doc.type component
 * @doc.purpose Render sortable approval queue rows for the reviewer UI
 * @doc.layer frontend
 */
import React from 'react';
import { Link } from 'react-router';
import type { ApprovalRecordResponse } from '@/types/approval';

interface ApprovalQueueTableProps {
  workspaceId: string;
  approvals: ApprovalRecordResponse[];
}

function statusBadgeClass(status: string): string {
  if (status === 'APPROVED') return 'text-green-700 bg-green-100';
  if (status === 'REJECTED') return 'text-red-700 bg-red-100';
  if (status === 'CANCELLED') return 'text-gray-600 bg-gray-100';
  return 'text-yellow-700 bg-yellow-100';
}

export const ApprovalQueueTable: React.FC<ApprovalQueueTableProps> = ({
  workspaceId,
  approvals,
}) => {
  if (approvals.length === 0) {
    return (
      <p
        data-testid="approval-queue-empty"
        className="text-sm text-gray-500 py-8 text-center"
      >
        No pending approvals.
      </p>
    );
  }

  return (
    <table
      data-testid="approval-queue-table"
      className="w-full text-sm border-collapse"
    >
      <thead>
        <tr className="border-b text-left text-gray-600">
          <th className="py-2 pr-4">Type</th>
          <th className="py-2 pr-4">Target</th>
          <th className="py-2 pr-4">Risk</th>
          <th className="py-2 pr-4">Status</th>
          <th className="py-2 pr-4">Submitted By</th>
          <th className="py-2 pr-4">Submitted At</th>
          <th className="py-2" />
        </tr>
      </thead>
      <tbody>
        {approvals.map((a) => (
            <tr
              key={a.requestId}
              data-testid={`approval-row-${a.requestId}`}
              className="border-b hover:bg-gray-50"
            >
              <td className="py-2 pr-4 font-mono text-xs">{a.targetType ?? 'Unknown'}</td>
              <td className="py-2 pr-4 max-w-xs truncate text-xs text-gray-700">{a.targetId ?? 'N/A'}</td>
              <td className="py-2 pr-4">
                <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${
                  a.riskLevel >= 4 ? 'text-red-700 bg-red-100' :
                  a.riskLevel >= 3 ? 'text-orange-700 bg-orange-100' :
                  a.riskLevel >= 2 ? 'text-yellow-700 bg-yellow-100' :
                  'text-green-700 bg-green-100'
                }`}>
                  {a.riskLevel}
                </span>
              </td>
              <td className="py-2 pr-4">
                <span
                  data-testid={`status-badge-${a.requestId}`}
                  className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${statusBadgeClass(a.status)}`}
                >
                  {a.status}
                </span>
              </td>
              <td className="py-2 pr-4 text-xs text-gray-600">
                {a.submittedBy}
              </td>
              <td className="py-2 pr-4 text-xs text-gray-500">
                {new Date(a.submittedAt).toLocaleDateString()}
              </td>
              <td className="py-2">
                <Link
                  to={`/workspaces/${workspaceId}/approvals/${a.requestId}`}
                  data-testid={`review-link-${a.requestId}`}
                  className="text-blue-600 hover:underline text-xs"
                >
                  Review
                </Link>
              </td>
            </tr>
          ))}
      </tbody>
    </table>
  );
};
