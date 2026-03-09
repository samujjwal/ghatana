import React, { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { DepartmentHierarchyViz } from './DepartmentHierarchyViz';
import type { Department } from './DepartmentHierarchyViz';
import { useAtom } from 'jotai';
import { currentUserAtom } from '../../atoms/user';

interface RestructureChange {
    type: 'merge' | 'split' | 'reorganize' | 'rename';
    departmentId: string;
    targetDepartmentId?: string;
    newName?: string;
    newParentId?: string;
}

interface RestructureProposalFormProps {
    onSuccess?: () => void;
    onCancel?: () => void;
}

export const RestructureProposalForm: React.FC<RestructureProposalFormProps> = ({
    onSuccess,
    onCancel,
}) => {
    const queryClient = useQueryClient();
    const [currentUser] = useAtom(currentUserAtom);
    const [changeType, setChangeType] = useState<RestructureChange['type']>('reorganize');
    const [selectedDept, setSelectedDept] = useState<string>('');
    const [targetDept, setTargetDept] = useState<string>('');
    const [newName, setNewName] = useState('');
    const [newParentId, setNewParentId] = useState<string>('');
    const [justification, setJustification] = useState('');
    const [proposedChanges, setProposedChanges] = useState<RestructureChange[]>([]);

    // Fetch departments
    const { data: departments = [] } = useQuery<Department[]>({
        queryKey: ['/api/v1/org/departments'],
        queryFn: async () => {
            const response = await fetch('/api/v1/org/departments');
            if (!response.ok) throw new Error('Failed to fetch departments');
            return response.json();
        },
    });

    interface SubmitApprovalPayload {
        type: string;
        title: string;
        description: string;
        metadata: unknown;
        requesterId: string;
        data: unknown;
        approvers: Array<{ userId: string; role: string; level: number }>;
    }

    // Create approval mutation
    const createApproval = useMutation({
        mutationFn: async (data: SubmitApprovalPayload) => {
            const response = await fetch('/api/v1/approvals', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data),
            });
            if (!response.ok) throw new Error('Failed to create approval');
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['/api/v1/approvals'] });
            onSuccess?.();
        },
    });

    // Calculate impact
    const impact = useMemo(() => {
        const affectedDepts = new Set<string>();
        const affectedTeams = new Set<string>();
        let affectedEmployees = 0;
        let budgetImpact = 0;

        proposedChanges.forEach(change => {
            const dept = departments.find(d => d.id === change.departmentId);
            if (!dept) return;

            affectedDepts.add(change.departmentId);
            if (change.targetDepartmentId) {
                affectedDepts.add(change.targetDepartmentId);
            }

            // Count employees and budget
            affectedEmployees += dept.headcount || 0;
            budgetImpact += dept.budget || 0;

            // Find child departments
            const findChildren = (parentId: string) => {
                departments.forEach(d => {
                    if (d.parentDepartmentId === parentId) {
                        affectedDepts.add(d.id);
                        affectedEmployees += d.headcount || 0;
                        budgetImpact += d.budget || 0;
                        findChildren(d.id);
                    }
                });
            };
            findChildren(dept.id);
        });

        return {
            departments: affectedDepts.size,
            teams: affectedTeams.size,
            employees: affectedEmployees,
            budget: budgetImpact,
        };
    }, [proposedChanges, departments]);

    const addChange = () => {
        if (!selectedDept) return;

        const change: RestructureChange = {
            type: changeType,
            departmentId: selectedDept,
            targetDepartmentId: targetDept || undefined,
            newName: newName || undefined,
            newParentId: newParentId || undefined,
        };

        setProposedChanges([...proposedChanges, change]);

        // Reset form
        setSelectedDept('');
        setTargetDept('');
        setNewName('');
        setNewParentId('');
    };

    const removeChange = (index: number) => {
        setProposedChanges(proposedChanges.filter((_, i) => i !== index));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!currentUser?.id) {
            alert('Please sign in to submit a proposal');
            return;
        }

        if (proposedChanges.length === 0) {
            alert('Please add at least one change');
            return;
        }

        if (!justification.trim()) {
            alert('Please provide justification for this restructure');
            return;
        }

        // Create approval request
        await createApproval.mutateAsync({
            type: 'restructure',
            title: `Organization Restructure - ${proposedChanges.length} change${proposedChanges.length > 1 ? 's' : ''}`,
            description: justification,
            metadata: {
                changes: proposedChanges,
                impact,
            },
            requesterId: currentUser.id,
            data: {
                changes: proposedChanges,
            },
            approvers: [
                {
                    userId: currentUser.id,
                    role: 'REQUESTER',
                    level: 0,
                },
            ],
        });
    };

    return (
        <div className="max-w-7xl mx-auto p-6">
            <div className="mb-8">
                <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100">
                    Propose Organization Restructure
                </h1>
                <p className="mt-2 text-gray-600 dark:text-gray-400">
                    Plan changes to the organization structure. All changes require executive approval.
                </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Left: Change builder */}
                <div className="lg:col-span-1">
                    <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                        <h2 className="text-xl font-semibold mb-4 text-gray-900 dark:text-gray-100">
                            Add Change
                        </h2>

                        <form onSubmit={(e) => { e.preventDefault(); addChange(); }} className="space-y-4">
                            {/* Change Type */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                    Change Type
                                </label>
                                <select
                                    value={changeType}
                                    onChange={(e) => setChangeType(e.target.value as RestructureChange['type'])}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                >
                                    <option value="reorganize">Reorganize (Move to new parent)</option>
                                    <option value="rename">Rename Department</option>
                                    <option value="merge">Merge Departments</option>
                                    <option value="split">Split Department</option>
                                </select>
                            </div>

                            {/* Department Selection */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                    Department
                                </label>
                                <select
                                    value={selectedDept}
                                    onChange={(e) => setSelectedDept(e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    required
                                >
                                    <option value="">Select department...</option>
                                    {departments.map(dept => (
                                        <option key={dept.id} value={dept.id}>
                                            {dept.name}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {/* Conditional fields based on change type */}
                            {changeType === 'reorganize' && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        New Parent Department
                                    </label>
                                    <select
                                        value={newParentId}
                                        onChange={(e) => setNewParentId(e.target.value)}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    >
                                        <option value="">Root (no parent)</option>
                                        {departments
                                            .filter(d => d.id !== selectedDept)
                                            .map(dept => (
                                                <option key={dept.id} value={dept.id}>
                                                    {dept.name}
                                                </option>
                                            ))}
                                    </select>
                                </div>
                            )}

                            {changeType === 'rename' && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        New Name
                                    </label>
                                    <input
                                        type="text"
                                        value={newName}
                                        onChange={(e) => setNewName(e.target.value)}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                        placeholder="Enter new department name"
                                    />
                                </div>
                            )}

                            {(changeType === 'merge' || changeType === 'split') && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        {changeType === 'merge' ? 'Merge Into' : 'Split Into'}
                                    </label>
                                    <select
                                        value={targetDept}
                                        onChange={(e) => setTargetDept(e.target.value)}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    >
                                        <option value="">Select department...</option>
                                        {departments
                                            .filter(d => d.id !== selectedDept)
                                            .map(dept => (
                                                <option key={dept.id} value={dept.id}>
                                                    {dept.name}
                                                </option>
                                            ))}
                                    </select>
                                </div>
                            )}

                            <button
                                type="submit"
                                className="w-full px-4 py-2 bg-blue-500 text-white rounded-md hover:bg-blue-600 transition-colors"
                            >
                                Add Change
                            </button>
                        </form>

                        {/* Proposed changes list */}
                        {proposedChanges.length > 0 && (
                            <div className="mt-6">
                                <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
                                    Proposed Changes ({proposedChanges.length})
                                </h3>
                                <div className="space-y-2">
                                    {proposedChanges.map((change, index) => {
                                        const dept = departments.find(d => d.id === change.departmentId);
                                        return (
                                            <div
                                                key={index}
                                                className="flex items-start justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-md text-sm"
                                            >
                                                <div>
                                                    <span className="font-medium text-gray-900 dark:text-gray-100">
                                                        {change.type.toUpperCase()}
                                                    </span>
                                                    <p className="text-gray-600 dark:text-gray-400 mt-1">
                                                        {dept?.name}
                                                        {change.newName && ` → ${change.newName}`}
                                                    </p>
                                                </div>
                                                <button
                                                    onClick={() => removeChange(index)}
                                                    className="text-red-500 hover:text-red-700"
                                                >
                                                    ✕
                                                </button>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* Impact preview */}
                        {proposedChanges.length > 0 && (
                            <div className="mt-6 p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-md">
                                <h3 className="text-sm font-semibold text-yellow-900 dark:text-yellow-100 mb-2">
                                    📊 Impact Analysis
                                </h3>
                                <div className="space-y-1 text-sm text-yellow-800 dark:text-yellow-200">
                                    <p>Departments affected: {impact.departments}</p>
                                    <p>Employees affected: {impact.employees}</p>
                                    <p>Budget impact: ${(impact.budget / 1000000).toFixed(1)}M</p>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Justification */}
                    {proposedChanges.length > 0 && (
                        <div className="mt-6 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                            <h2 className="text-xl font-semibold mb-4 text-gray-900 dark:text-gray-100">
                                Justification
                            </h2>
                            <textarea
                                value={justification}
                                onChange={(e) => setJustification(e.target.value)}
                                rows={6}
                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                placeholder="Explain why this restructure is needed..."
                                required
                            />

                            <div className="flex gap-3 mt-4">
                                <button
                                    onClick={handleSubmit}
                                    disabled={createApproval.isPending}
                                    className="flex-1 px-4 py-2 bg-green-500 text-white rounded-md hover:bg-green-600 transition-colors disabled:opacity-50"
                                >
                                    {createApproval.isPending ? 'Submitting...' : 'Submit for Approval'}
                                </button>
                                <button
                                    onClick={onCancel}
                                    className="px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-300 dark:hover:bg-gray-600"
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    )}
                </div>

                {/* Right: Visual preview */}
                <div className="lg:col-span-2">
                    <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                        <h2 className="text-xl font-semibold mb-4 text-gray-900 dark:text-gray-100">
                            Organization Preview
                        </h2>

                        <DepartmentHierarchyViz
                            departments={departments}
                            proposedChanges={proposedChanges}
                            editable={true}
                            showComparison={proposedChanges.length > 0}
                            onChangeProposed={(change) => {
                                setProposedChanges([...proposedChanges, change]);
                            }}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
};
