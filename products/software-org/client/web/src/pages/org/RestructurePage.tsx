/**
 * Restructure Page Component
 *
 * Organization restructuring management and proposal system.
 * View active restructures, propose new changes, and track history.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RestructureProposalForm } from '../../components/org/RestructureProposalForm';

type ApprovalStatus = 'PENDING' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'COMPLETED';

interface RestructureChange {
  type: 'merge' | 'split' | 'reorganize' | 'rename';
  departmentId: string;
  targetDepartmentId?: string;
  newName?: string;
  newParentId?: string;
}

interface RestructureMetadata {
  impact?: { departments: number; employees: number; budget: number };
  changes?: RestructureChange[];
}

interface ApprovalApi {
  id: string;
  type: string;
  title?: string | null;
  description?: string | null;
  status: ApprovalStatus;
  metadata?: unknown;
}

const toRestructureMetadata = (value: unknown): RestructureMetadata | null => {
  if (!value || typeof value !== 'object') return null;
  return value as RestructureMetadata;
};

export const RestructurePage: React.FC = () => {
  const [showProposalForm, setShowProposalForm] = useState(false);
  const [activeTab, setActiveTab] = useState<'active' | 'history'>('active');

  // Fetch restructure approvals
  const { data: approvals = [], isLoading } = useQuery<ApprovalApi[]>({
    queryKey: ['/api/v1/approvals', { type: 'restructure' }],
    queryFn: async () => {
      const response = await fetch('/api/v1/approvals?type=restructure');
      if (!response.ok) throw new Error('Failed to fetch approvals');
      const json = (await response.json()) as { data?: ApprovalApi[] };
      return json.data ?? [];
    },
  });

  const activeRestructures = approvals.filter(
    a => a.status === 'PENDING' || a.status === 'IN_PROGRESS'
  );

  const completedRestructures = approvals.filter(
    a => a.status === 'APPROVED' || a.status === 'REJECTED' || a.status === 'COMPLETED'
  );

  if (showProposalForm) {
    return (
      <RestructureProposalForm
        onSuccess={() => setShowProposalForm(false)}
        onCancel={() => setShowProposalForm(false)}
      />
    );
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100">
            Organization Restructuring
          </h1>
          <p className="text-gray-600 dark:text-gray-400 mt-2">
            Plan and track organizational changes
          </p>
        </div>
        <button
          onClick={() => setShowProposalForm(true)}
          className="px-6 py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors font-medium"
        >
          + Propose Restructure
        </button>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 dark:border-gray-700 mb-6">
        <nav className="flex gap-8">
          <button
            onClick={() => setActiveTab('active')}
            className={`pb-4 px-1 border-b-2 font-medium transition-colors ${activeTab === 'active'
              ? 'border-blue-500 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
              }`}
          >
            Active Restructures
            {activeRestructures.length > 0 && (
              <span className="ml-2 px-2 py-1 text-xs bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 rounded-full">
                {activeRestructures.length}
              </span>
            )}
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`pb-4 px-1 border-b-2 font-medium transition-colors ${activeTab === 'history'
              ? 'border-blue-500 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
              }`}
          >
            History
          </button>
        </nav>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="text-center py-12">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
          <p className="text-gray-600 dark:text-gray-400 mt-4">Loading...</p>
        </div>
      ) : activeTab === 'active' ? (
        <div>
          {activeRestructures.length === 0 ? (
            <div className="text-center py-12">
              <div className="text-6xl mb-4">🏢</div>
              <h3 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
                No Active Restructures
              </h3>
              <p className="text-gray-600 dark:text-gray-400 mb-6">
                Click "Propose Restructure" to begin planning organizational changes
              </p>
            </div>
          ) : (
            <div className="space-y-6">
              {activeRestructures.map((approval) => (
                <RestructureCard key={approval.id} approval={approval} />
              ))}
            </div>
          )}
        </div>
      ) : (
        <div>
          {completedRestructures.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-gray-600 dark:text-gray-400">No completed restructures</p>
            </div>
          ) : (
            <div className="space-y-6">
              {completedRestructures.map((approval) => (
                <RestructureCard key={approval.id} approval={approval} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// Restructure card component
const RestructureCard: React.FC<{ approval: ApprovalApi }> = ({ approval }) => {
  const [expanded, setExpanded] = useState(false);
  const metadata = toRestructureMetadata(approval.metadata);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'APPROVED':
        return 'bg-green-100 text-green-800 dark:bg-green-900/50 dark:text-green-200';
      case 'REJECTED':
        return 'bg-red-100 text-red-800 dark:bg-red-900/50 dark:text-red-200';
      case 'IN_PROGRESS':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/50 dark:text-blue-200';
      default:
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/50 dark:text-yellow-200';
    }
  };

  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
      <div
        className="p-6 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="flex items-center justify-between">
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-2">
              <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                {approval.title ?? 'Restructure Proposal'}
              </h3>
              <span
                className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${getStatusColor(
                  approval.status
                )}`}
              >
                {approval.status}
              </span>
            </div>
            <p className="text-gray-600 dark:text-gray-400 text-sm line-clamp-2">
              {approval.description ?? ''}
            </p>
            {metadata?.impact && (
              <div className="flex items-center gap-6 mt-3 text-sm text-gray-600 dark:text-gray-400">
                <span>🏢 {metadata.impact.departments} depts</span>
                <span>👥 {metadata.impact.employees} people</span>
                <span>💵 ${(metadata.impact.budget / 1000000).toFixed(1)}M</span>
              </div>
            )}
          </div>
          <div className="text-gray-400 dark:text-gray-500">
            {expanded ? '▲' : '▼'}
          </div>
        </div>
      </div>

      {expanded && metadata?.changes && (
        <div className="px-6 pb-6 border-t border-gray-200 dark:border-gray-700 pt-4">
          <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">
            Proposed Changes ({metadata.changes.length})
          </h4>
          <div className="space-y-2 mb-4">
            {metadata.changes.map((change, index) => (
              <div
                key={index}
                className="p-3 bg-gray-50 dark:bg-gray-700 rounded-md text-sm"
              >
                <span className="font-medium text-gray-900 dark:text-gray-100">
                  {change.type.toUpperCase()}
                </span>
                <p className="text-gray-600 dark:text-gray-400 mt-1">
                  Department ID: {change.departmentId}
                  {change.newName && ` → ${change.newName}`}
                  {change.newParentId && ` → Parent: ${change.newParentId}`}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default RestructurePage;
