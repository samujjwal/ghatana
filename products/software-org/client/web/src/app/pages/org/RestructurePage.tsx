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
import { RestructureProposalForm } from '@/components/org/RestructureProposalForm';

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
        (approval) => approval.status === 'PENDING' || approval.status === 'IN_PROGRESS'
    );

    const completedRestructures = approvals.filter(
        (approval) =>
            approval.status === 'APPROVED' || approval.status === 'REJECTED' || approval.status === 'COMPLETED'
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
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100">Organization Restructure</h1>
                    <p className="text-gray-600 dark:text-gray-400 mt-2">
                        Manage organizational restructuring proposals and track changes
                    </p>
                </div>
                <button
                    onClick={() => setShowProposalForm(true)}
                    className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-medium transition-colors"
                >
                    New Proposal
                </button>
            </div>

            {/* Tabs */}
            <div className="border-b border-gray-200 dark:border-gray-700 mb-6">
                <nav className="flex space-x-8">
                    <button
                        onClick={() => setActiveTab('active')}
                        className={`py-2 px-1 border-b-2 font-medium text-sm transition-colors ${activeTab === 'active'
                                ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
                            }`}
                    >
                        Active Restructures ({activeRestructures.length})
                    </button>
                    <button
                        onClick={() => setActiveTab('history')}
                        className={`py-2 px-1 border-b-2 font-medium text-sm transition-colors ${activeTab === 'history'
                                ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
                            }`}
                    >
                        History ({completedRestructures.length})
                    </button>
                </nav>
            </div>

            {/* Content */}
            {isLoading ? (
                <div className="text-center py-12">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
                    <p className="text-gray-600 dark:text-gray-400 mt-4">Loading restructures...</p>
                </div>
            ) : activeTab === 'active' ? (
                <div className="space-y-6">
                    {activeRestructures.length === 0 ? (
                        <div className="text-center py-12 bg-gray-50 dark:bg-gray-800 rounded-lg">
                            <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100">No active restructures</h3>
                            <p className="text-gray-600 dark:text-gray-400 mt-2">Create a new proposal to get started</p>
                        </div>
                    ) : (
                        activeRestructures.map((restructure) => {
                            const metadata = toRestructureMetadata(restructure.metadata);
                            return (
                                <div key={restructure.id} className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1">
                                            <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                                                {restructure.title || 'Untitled Restructure'}
                                            </h3>
                                            <p className="text-gray-600 dark:text-gray-400 mt-1">{restructure.description || ''}</p>
                                            <div className="flex items-center space-x-4 mt-4">
                                                <span
                                                    className={`px-2 py-1 rounded-full text-xs font-medium ${restructure.status === 'PENDING'
                                                            ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-200'
                                                            : 'bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-200'
                                                        }`}
                                                >
                                                    {restructure.status}
                                                </span>
                                                {metadata?.impact ? (
                                                    <div className="text-sm text-gray-500 dark:text-gray-400">
                                                        Impact: {metadata.impact.departments} depts, {metadata.impact.employees} employees
                                                    </div>
                                                ) : null}
                                            </div>
                                        </div>
                                        <div className="flex space-x-2">
                                            <button className="text-blue-600 hover:text-blue-700 font-medium text-sm">View Details</button>
                                            <button className="text-gray-600 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 font-medium text-sm">
                                                Track Progress
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            );
                        })
                    )}
                </div>
            ) : (
                <div className="space-y-6">
                    {completedRestructures.length === 0 ? (
                        <div className="text-center py-12 bg-gray-50 dark:bg-gray-800 rounded-lg">
                            <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100">No restructure history</h3>
                            <p className="text-gray-600 dark:text-gray-400 mt-2">Completed restructures will appear here</p>
                        </div>
                    ) : (
                        completedRestructures.map((restructure) => {
                            const metadata = toRestructureMetadata(restructure.metadata);
                            return (
                                <div key={restructure.id} className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1">
                                            <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                                                {restructure.title || 'Untitled Restructure'}
                                            </h3>
                                            <p className="text-gray-600 dark:text-gray-400 mt-1">{restructure.description || ''}</p>
                                            <div className="flex items-center space-x-4 mt-4">
                                                <span
                                                    className={`px-2 py-1 rounded-full text-xs font-medium ${restructure.status === 'APPROVED'
                                                            ? 'bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-200'
                                                            : restructure.status === 'REJECTED'
                                                                ? 'bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-200'
                                                                : 'bg-gray-100 text-gray-800 dark:bg-gray-900/20 dark:text-gray-200'
                                                        }`}
                                                >
                                                    {restructure.status}
                                                </span>
                                                {metadata?.impact ? (
                                                    <div className="text-sm text-gray-500 dark:text-gray-400">
                                                        Impact: {metadata.impact.departments} depts, {metadata.impact.employees} employees
                                                    </div>
                                                ) : null}
                                            </div>
                                        </div>
                                        <button className="text-blue-600 hover:text-blue-700 font-medium text-sm">View Summary</button>
                                    </div>
                                </div>
                            );
                        })
                    )}
                </div>
            )}
        </div>
    );
};

export default RestructurePage;
