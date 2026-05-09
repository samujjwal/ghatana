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
import { Link } from 'react-router-dom';
import type { ApprovalRecordResponse } from '@/types/approval';
import { Badge, Table, TableHead, TableBody, TableRow, TableCell } from '@ghatana/design-system';

interface ApprovalQueueTableProps {
  workspaceId: string;
  approvals: ApprovalRecordResponse[];
}

function statusBadgeClass(status: string): 'success' | 'danger' | 'neutral' | 'warning' {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'CANCELLED') return 'neutral';
  return 'warning';
}

function riskBadgeTone(riskLevel: number): 'success' | 'warning' | 'danger' {
  if (riskLevel >= 4) return 'danger';
  if (riskLevel >= 2) return 'warning';
  return 'success';
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
    <Table data-testid="approval-queue-table" size="small">
      <TableHead>
        <TableRow className="border-b text-left text-gray-600">
          <TableCell component="th" className="py-2 pr-4">Type</TableCell>
          <TableCell component="th" className="py-2 pr-4">Target</TableCell>
          <TableCell component="th" className="py-2 pr-4">Risk</TableCell>
          <TableCell component="th" className="py-2 pr-4">Status</TableCell>
          <TableCell component="th" className="py-2 pr-4">Submitted By</TableCell>
          <TableCell component="th" className="py-2 pr-4">Submitted At</TableCell>
          <TableCell component="th" className="py-2" />
        </TableRow>
      </TableHead>
      <TableBody>
        {approvals.map((a) => (
            <TableRow
              key={a.requestId}
              data-testid={`approval-row-${a.requestId}`}
              className="border-b hover:bg-gray-50"
            >
              <TableCell className="py-2 pr-4 font-mono text-xs">{a.targetType ?? 'Unknown'}</TableCell>
              <TableCell className="py-2 pr-4 max-w-xs truncate text-xs text-gray-700">{a.targetId ?? 'N/A'}</TableCell>
              <TableCell className="py-2 pr-4">
                <Badge tone={riskBadgeTone(a.riskLevel)} variant="soft">
                  {a.riskLevel}
                </Badge>
              </TableCell>
              <TableCell className="py-2 pr-4">
                <Badge
                  data-testid={`status-badge-${a.requestId}`}
                  tone={statusBadgeClass(a.status)}
                  variant="soft"
                >
                  {a.status}
                </Badge>
              </TableCell>
              <TableCell className="py-2 pr-4 text-xs text-gray-600">
                {a.submittedBy}
              </TableCell>
              <TableCell className="py-2 pr-4 text-xs text-gray-500">
                {new Date(a.submittedAt).toLocaleDateString()}
              </TableCell>
              <TableCell className="py-2">
                <Link
                  to={`/workspaces/${workspaceId}/approvals/${a.requestId}`}
                  data-testid={`review-link-${a.requestId}`}
                  className="text-blue-600 hover:underline text-xs"
                >
                  Review
                </Link>
              </TableCell>
            </TableRow>
          ))}
      </TableBody>
    </Table>
  );
};
