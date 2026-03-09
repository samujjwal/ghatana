/**
 * Budget Allocation Table Component
 *
 * Editable table for department budget allocation with real-time validation.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState, useMemo } from 'react';

export interface BudgetCategory {
    headcount: number;
    infrastructure: number;
    tools: number;
    training: number;
    other: number;
}

export interface DepartmentBudget {
    id: string;
    departmentId: string;
    departmentName: string;
    currentAllocated: number;
    currentSpent: number;
    proposedAllocated: number;
    categories: BudgetCategory;
    justification: string;
    status: 'draft' | 'active' | 'frozen' | 'archived';
}

interface AllocationTableProps {
    budgets: DepartmentBudget[];
    onBudgetChange: (departmentId: string, field: string, value: number | string) => void;
    onCategoryChange: (departmentId: string, category: keyof BudgetCategory, value: number) => void;
    totalBudgetLimit: number;
    readonly?: boolean;
}

export const AllocationTable: React.FC<AllocationTableProps> = ({
    budgets,
    onBudgetChange,
    onCategoryChange,
    totalBudgetLimit,
    readonly = false,
}) => {
    const [expandedDepartments, setExpandedDepartments] = useState<Set<string>>(new Set());

    const toggleExpand = (departmentId: string) => {
        setExpandedDepartments(prev => {
            const next = new Set(prev);
            if (next.has(departmentId)) {
                next.delete(departmentId);
            } else {
                next.add(departmentId);
            }
            return next;
        });
    };

    // Calculate totals
    const totals = useMemo(() => {
        const currentTotal = budgets.reduce((sum, b) => sum + b.currentAllocated, 0);
        const proposedTotal = budgets.reduce((sum, b) => sum + b.proposedAllocated, 0);
        const spentTotal = budgets.reduce((sum, b) => sum + b.currentSpent, 0);
        const remaining = totalBudgetLimit - proposedTotal;
        const overBudget = proposedTotal > totalBudgetLimit;

        return {
            currentTotal,
            proposedTotal,
            spentTotal,
            remaining,
            overBudget,
            changeAmount: proposedTotal - currentTotal,
            changePercent: currentTotal > 0 ? ((proposedTotal - currentTotal) / currentTotal) * 100 : 0,
        };
    }, [budgets, totalBudgetLimit]);

    const formatCurrency = (amount: number): string => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0,
        }).format(amount);
    };

    const calculateCategoryTotal = (categories: BudgetCategory): number => {
        return Object.values(categories).reduce((sum, val) => sum + val, 0);
    };

    const handleProposedChange = (departmentId: string, value: string) => {
        const numValue = parseFloat(value) || 0;
        onBudgetChange(departmentId, 'proposedAllocated', numValue);
    };

    const handleCategoryChange = (
        departmentId: string,
        category: keyof BudgetCategory,
        value: string
    ) => {
        const numValue = parseFloat(value) || 0;
        onCategoryChange(departmentId, category, numValue);
    };

    const handleJustificationChange = (departmentId: string, value: string) => {
        onBudgetChange(departmentId, 'justification', value);
    };

    return (
        <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
            {/* Table Header */}
            <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                    Department Budget Allocation
                </h3>
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                    Budget Limit: {formatCurrency(totalBudgetLimit)} •{' '}
                    <span className={totals.overBudget ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'}>
                        {totals.overBudget ? 'Over Budget' : 'Within Budget'} by {formatCurrency(Math.abs(totals.remaining))}
                    </span>
                </p>
            </div>

            {/* Table */}
            <div className="overflow-x-auto">
                <table className="w-full">
                    <thead className="bg-gray-50 dark:bg-gray-700/50">
                        <tr>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                Department
                            </th>
                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                Current
                            </th>
                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                Spent
                            </th>
                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                Proposed
                            </th>
                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                Change %
                            </th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                Justification
                            </th>
                            <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                Details
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                        {budgets.map((budget) => {
                            const changePercent = budget.currentAllocated > 0
                                ? ((budget.proposedAllocated - budget.currentAllocated) / budget.currentAllocated) * 100
                                : 0;
                            const isExpanded = expandedDepartments.has(budget.departmentId);
                            const categoryTotal = calculateCategoryTotal(budget.categories);
                            const categoryMismatch = Math.abs(categoryTotal - budget.proposedAllocated) > 0.01;

                            return (
                                <React.Fragment key={budget.id}>
                                    <tr className="hover:bg-gray-50 dark:hover:bg-gray-700/30">
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="font-medium text-gray-900 dark:text-gray-100">
                                                {budget.departmentName}
                                            </div>
                                            <div className="text-xs text-gray-500 dark:text-gray-400">
                                                {budget.status}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-right text-gray-900 dark:text-gray-100">
                                            {formatCurrency(budget.currentAllocated)}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-right">
                                            <div className="text-gray-900 dark:text-gray-100">
                                                {formatCurrency(budget.currentSpent)}
                                            </div>
                                            <div className="text-xs text-gray-500 dark:text-gray-400">
                                                {((budget.currentSpent / budget.currentAllocated) * 100).toFixed(0)}%
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-right">
                                            {readonly ? (
                                                <span className="text-gray-900 dark:text-gray-100">
                                                    {formatCurrency(budget.proposedAllocated)}
                                                </span>
                                            ) : (
                                                <input
                                                    type="number"
                                                    min="0"
                                                    step="10000"
                                                    value={budget.proposedAllocated}
                                                    onChange={(e) => handleProposedChange(budget.departmentId, e.target.value)}
                                                    className="w-32 px-3 py-1 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 text-right focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                />
                                            )}
                                            {categoryMismatch && !readonly && (
                                                <div className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
                                                    ⚠️ Categories: {formatCurrency(categoryTotal)}
                                                </div>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-right">
                                            <span
                                                className={`font-medium ${changePercent > 0
                                                        ? 'text-green-600 dark:text-green-400'
                                                        : changePercent < 0
                                                            ? 'text-red-600 dark:text-red-400'
                                                            : 'text-gray-600 dark:text-gray-400'
                                                    }`}
                                            >
                                                {changePercent >= 0 ? '+' : ''}
                                                {changePercent.toFixed(1)}%
                                            </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            {readonly ? (
                                                <p className="text-sm text-gray-700 dark:text-gray-300">
                                                    {budget.justification || '—'}
                                                </p>
                                            ) : (
                                                <textarea
                                                    value={budget.justification}
                                                    onChange={(e) => handleJustificationChange(budget.departmentId, e.target.value)}
                                                    placeholder="Explain budget change..."
                                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                    rows={2}
                                                />
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <button
                                                onClick={() => toggleExpand(budget.departmentId)}
                                                className="text-blue-600 dark:text-blue-400 hover:underline text-sm"
                                            >
                                                {isExpanded ? '▼ Hide' : '▶ Show'}
                                            </button>
                                        </td>
                                    </tr>

                                    {/* Expanded Category Breakdown */}
                                    {isExpanded && (
                                        <tr>
                                            <td colSpan={7} className="px-6 py-4 bg-gray-50 dark:bg-gray-700/30">
                                                <div className="ml-8">
                                                    <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">
                                                        Budget Categories
                                                    </h4>
                                                    <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
                                                        {(Object.keys(budget.categories) as (keyof BudgetCategory)[]).map((category) => (
                                                            <div key={category} className="space-y-1">
                                                                <label className="text-xs text-gray-600 dark:text-gray-400 capitalize">
                                                                    {category}
                                                                </label>
                                                                {readonly ? (
                                                                    <div className="text-sm text-gray-900 dark:text-gray-100">
                                                                        {formatCurrency(budget.categories[category])}
                                                                    </div>
                                                                ) : (
                                                                    <input
                                                                        type="number"
                                                                        min="0"
                                                                        step="1000"
                                                                        value={budget.categories[category]}
                                                                        onChange={(e) =>
                                                                            handleCategoryChange(budget.departmentId, category, e.target.value)
                                                                        }
                                                                        className="w-full px-2 py-1 border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 text-sm"
                                                                    />
                                                                )}
                                                            </div>
                                                        ))}
                                                    </div>
                                                    <div className="mt-3 pt-3 border-t border-gray-200 dark:border-gray-600 flex justify-between text-sm">
                                                        <span className="text-gray-600 dark:text-gray-400">Category Total:</span>
                                                        <span className={`font-semibold ${categoryMismatch ? 'text-yellow-600 dark:text-yellow-400' : 'text-gray-900 dark:text-gray-100'}`}>
                                                            {formatCurrency(categoryTotal)}
                                                            {categoryMismatch && ' (mismatch)'}
                                                        </span>
                                                    </div>
                                                </div>
                                            </td>
                                        </tr>
                                    )}
                                </React.Fragment>
                            );
                        })}
                    </tbody>
                    {/* Table Footer with Totals */}
                    <tfoot className="bg-gray-100 dark:bg-gray-700">
                        <tr className="font-semibold">
                            <td className="px-6 py-4 text-gray-900 dark:text-gray-100">
                                TOTAL ({budgets.length} departments)
                            </td>
                            <td className="px-6 py-4 text-right text-gray-900 dark:text-gray-100">
                                {formatCurrency(totals.currentTotal)}
                            </td>
                            <td className="px-6 py-4 text-right text-gray-900 dark:text-gray-100">
                                {formatCurrency(totals.spentTotal)}
                            </td>
                            <td className="px-6 py-4 text-right">
                                <div className={totals.overBudget ? 'text-red-600 dark:text-red-400' : 'text-gray-900 dark:text-gray-100'}>
                                    {formatCurrency(totals.proposedTotal)}
                                </div>
                                {totals.overBudget && (
                                    <div className="text-xs text-red-600 dark:text-red-400">
                                        Over limit by {formatCurrency(Math.abs(totals.remaining))}
                                    </div>
                                )}
                            </td>
                            <td className="px-6 py-4 text-right">
                                <span
                                    className={`${totals.changePercent > 0
                                            ? 'text-green-600 dark:text-green-400'
                                            : totals.changePercent < 0
                                                ? 'text-red-600 dark:text-red-400'
                                                : 'text-gray-600 dark:text-gray-400'
                                        }`}
                                >
                                    {totals.changePercent >= 0 ? '+' : ''}
                                    {totals.changePercent.toFixed(1)}%
                                </span>
                            </td>
                            <td colSpan={2} className="px-6 py-4 text-right text-sm text-gray-600 dark:text-gray-400">
                                Budget Limit: {formatCurrency(totalBudgetLimit)}
                            </td>
                        </tr>
                    </tfoot>
                </table>
            </div>
        </div>
    );
};

export default AllocationTable;
