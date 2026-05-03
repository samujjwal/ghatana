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
import type { ApprovalRequest } from '@/types/approval';

interface ApprovalQueueTableProps {
  workspaceId: string;
  approvals: ApprovalRequest[];
}

function riskLabel(level: number): { text: string; className: string } {
  if (level >= 4) return { text: 'High', className: 'text-red-700 bg-red-100' };
  if (level >= 2) return { text: 'Medium', className: 'text-yellow-700 bg-yellow-100' };
  return { text: 'Low', className: 'text-green-700 bg-green-100' };
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
          <th className="py-2 pr-4">Description</th>
          <th className="py-2 pr-4">Risk</th>
          <th className="py-2 pr-4">Required Role</th>
          <th className="py-2 pr-4">Submitted</th>
          <th className="py-2" />
        </tr>
      </thead>
      <tbody>
        {approvals.map((a) => {
          const risk = riskLabel(a.riskLevel);
          return (
            <tr
              key={a.requestId}
              data-testid={`approval-row-${a.requestId}`}
              className="border-b hover:bg-gray-50"
            >
              <td className="py-2 pr-4 font-mono text-xs">{a.targetType}</td>
              <td className="py-2 pr-4 max-w-xs truncate">{a.description}</td>
              <td className="py-2 pr-4">
                <span
                  data-testid={`risk-badge-${a.requestId}`}
                  className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${risk.className}`}
                >
                  {risk.text}
                </span>
              </td>
              <td className="py-2 pr-4 text-xs text-gray-600">
                {a.requiredApproverRole}
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
          );
        })}
      </tbody>
    </table>
  );
};
