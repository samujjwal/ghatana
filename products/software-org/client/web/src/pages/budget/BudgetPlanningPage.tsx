/**
 * Budget Planning Page
 *
 * Main page for annual/quarterly budget planning with department allocations.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { BudgetSummaryCards, type BudgetMetrics } from '../../components/budget/BudgetSummaryCards';
import { AllocationTable, type DepartmentBudget, type BudgetCategory } from '../../components/budget/AllocationTable';

interface BudgetPlan {
    id: string;
    year: number;
    quarter?: string;
    totalBudgetLimit: number;
    status: 'draft' | 'submitted' | 'approved' | 'active' | 'archived';
    budgets: DepartmentBudget[];
    createdAt: string;
    updatedAt: string;
}

export const BudgetPlanningPage: React.FC = () => {
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    // State
    const [selectedYear, setSelectedYear] = useState<number>(2025);
    const [selectedQuarter, setSelectedQuarter] = useState<string | undefined>('Q1');
    const [totalBudgetLimit, setTotalBudgetLimit] = useState<number>(5000000);
    const [localBudgets, setLocalBudgets] = useState<DepartmentBudget[]>([]);

    // Fetch existing budget plan
    const {
        data: budgetPlan,
        isLoading,
        error,
    } = useQuery<BudgetPlan>({
        queryKey: ['/api/v1/budgets', selectedYear, selectedQuarter],
        queryFn: async () => {
            const params = new URLSearchParams({
                year: selectedYear.toString(),
                ...(selectedQuarter && { quarter: selectedQuarter }),
            });
            const response = await fetch(`/api/v1/budgets?${params}`);
            if (!response.ok) {
                if (response.status === 404) {
                    // No plan exists, return empty
                    return null;
                }
                throw new Error('Failed to fetch budget plan');
            }
            return response.json();
        },
    });

    // Initialize local budgets when data loads
    React.useEffect(() => {
        if (budgetPlan?.budgets) {
            setLocalBudgets(budgetPlan.budgets);
            if (budgetPlan.totalBudgetLimit) {
                setTotalBudgetLimit(budgetPlan.totalBudgetLimit);
            }
        } else if (!budgetPlan && !isLoading) {
            // Initialize with mock data if no plan exists
            setLocalBudgets([
                {
                    id: 'temp-1',
                    departmentId: 'dept-eng',
                    departmentName: 'Engineering',
                    currentAllocated: 2000000,
                    currentSpent: 1500000,
                    proposedAllocated: 2500000,
                    categories: {
                        headcount: 1800000,
                        infrastructure: 400000,
                        tools: 200000,
                        training: 80000,
                        other: 20000,
                    },
                    justification: 'Growing team, increased cloud costs',
                    status: 'draft',
                },
                {
                    id: 'temp-2',
                    departmentId: 'dept-ops',
                    departmentName: 'Operations',
                    currentAllocated: 1500000,
                    currentSpent: 1200000,
                    proposedAllocated: 1600000,
                    categories: {
                        headcount: 1000000,
                        infrastructure: 400000,
                        tools: 150000,
                        training: 30000,
                        other: 20000,
                    },
                    justification: 'Maintaining current operations',
                    status: 'draft',
                },
                {
                    id: 'temp-3',
                    departmentId: 'dept-sales',
                    departmentName: 'Sales',
                    currentAllocated: 1000000,
                    currentSpent: 850000,
                    proposedAllocated: 1200000,
                    categories: {
                        headcount: 900000,
                        infrastructure: 100000,
                        tools: 150000,
                        training: 40000,
                        other: 10000,
                    },
                    justification: 'Expanding sales team for new markets',
                    status: 'draft',
                },
            ]);
        }
    }, [budgetPlan, isLoading]);

    // Save draft mutation
    const saveDraftMutation = useMutation({
        mutationFn: async (data: { budgets: DepartmentBudget[]; totalBudgetLimit: number }) => {
            const response = await fetch('/api/v1/budgets', {
                method: budgetPlan?.id ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    id: budgetPlan?.id,
                    year: selectedYear,
                    quarter: selectedQuarter,
                    totalBudgetLimit: data.totalBudgetLimit,
                    budgets: data.budgets,
                    status: 'draft',
                }),
            });
            if (!response.ok) throw new Error('Failed to save budget');
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['/api/v1/budgets'] });
            alert('Budget plan saved as draft');
        },
        onError: (error: Error) => {
            alert(`Failed to save: ${error.message}`);
        },
    });

    // Submit for approval mutation
    const submitMutation = useMutation({
        mutationFn: async () => {
            // First save as draft
            let planId = budgetPlan?.id;
            if (!planId) {
                const saveResponse = await fetch('/api/v1/budgets', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        year: selectedYear,
                        quarter: selectedQuarter,
                        totalBudgetLimit,
                        budgets: localBudgets,
                        status: 'draft',
                    }),
                });
                if (!saveResponse.ok) throw new Error('Failed to create budget plan');
                const savedPlan = await saveResponse.json();
                planId = savedPlan.id;
            }

            // Submit to approval workflow
            const approvalResponse = await fetch('/api/v1/approvals', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    type: 'budget',
                    title: `Budget Plan ${selectedYear} ${selectedQuarter || ''}`,
                    data: {
                        budgetPlanId: planId,
                        year: selectedYear,
                        quarter: selectedQuarter,
                        totalBudgetLimit,
                        budgets: localBudgets,
                    },
                    priority: 'high',
                    dueDate: new Date(selectedYear, 0, 31).toISOString(), // Jan 31
                }),
            });

            if (!approvalResponse.ok) throw new Error('Failed to submit for approval');
            return approvalResponse.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['/api/v1/budgets'] });
            queryClient.invalidateQueries({ queryKey: ['/api/v1/approvals'] });
            alert('Budget plan submitted for approval');
            navigate('/approvals');
        },
        onError: (error: Error) => {
            alert(`Failed to submit: ${error.message}`);
        },
    });

    // Calculate metrics
    const metrics: BudgetMetrics = useMemo(() => {
        const totalAllocated = localBudgets.reduce((sum, b) => sum + b.proposedAllocated, 0);
        const totalSpent = localBudgets.reduce((sum, b) => sum + b.currentSpent, 0);
        const totalForecasted = localBudgets.reduce((sum, b) => sum + b.currentAllocated, 0);
        const variance = totalForecasted - totalSpent;
        const variancePercent = totalSpent > 0 ? (variance / totalSpent) * 100 : 0;
        const remainingBudget = totalBudgetLimit - totalAllocated;

        return {
            totalAllocated,
            totalSpent,
            totalForecasted,
            variance,
            variancePercent,
            remainingBudget,
            departmentCount: localBudgets.length,
        };
    }, [localBudgets, totalBudgetLimit]);

    // Handlers
    const handleBudgetChange = (departmentId: string, field: string, value: number | string) => {
        setLocalBudgets(prev =>
            prev.map(b =>
                b.departmentId === departmentId ? { ...b, [field]: value } : b
            )
        );
    };

    const handleCategoryChange = (
        departmentId: string,
        category: keyof BudgetCategory,
        value: number
    ) => {
        setLocalBudgets(prev =>
            prev.map(b =>
                b.departmentId === departmentId
                    ? { ...b, categories: { ...b.categories, [category]: value } }
                    : b
            )
        );
    };

    const handleSaveDraft = () => {
        saveDraftMutation.mutate({ budgets: localBudgets, totalBudgetLimit });
    };

    const handleSubmit = () => {
        // Validation
        const totalProposed = localBudgets.reduce((sum, b) => sum + b.proposedAllocated, 0);
        if (totalProposed > totalBudgetLimit) {
            alert(`Total proposed (${formatCurrency(totalProposed)}) exceeds budget limit (${formatCurrency(totalBudgetLimit)})`);
            return;
        }

        const missingJustifications = localBudgets.filter(
            b => Math.abs(b.proposedAllocated - b.currentAllocated) > 0.01 && !b.justification
        );
        if (missingJustifications.length > 0) {
            alert('Please provide justifications for all budget changes');
            return;
        }

        if (confirm('Submit budget plan for approval?')) {
            submitMutation.mutate();
        }
    };

    const formatCurrency = (amount: number): string => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0,
        }).format(amount);
    };

    const isReadonly = budgetPlan?.status === 'approved' || budgetPlan?.status === 'active';

    if (isLoading) {
        return (
            <div className="p-6 text-center">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
                <p className="text-gray-600 dark:text-gray-400 mt-4">Loading budget plan...</p>
            </div>
        );
    }

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="mb-8">
                <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100">
                    💰 Budget Planning
                </h1>
                <p className="text-gray-600 dark:text-gray-400 mt-2">
                    Plan and allocate budgets across departments
                </p>
            </div>

            {/* Period Selector */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 mb-6">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Year
                        </label>
                        <select
                            value={selectedYear}
                            onChange={(e) => setSelectedYear(parseInt(e.target.value))}
                            disabled={isReadonly}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option value={2024}>2024</option>
                            <option value={2025}>2025</option>
                            <option value={2026}>2026</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Quarter (Optional)
                        </label>
                        <select
                            value={selectedQuarter || ''}
                            onChange={(e) => setSelectedQuarter(e.target.value || undefined)}
                            disabled={isReadonly}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option value="">Annual</option>
                            <option value="Q1">Q1</option>
                            <option value="Q2">Q2</option>
                            <option value="Q3">Q3</option>
                            <option value="Q4">Q4</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Total Budget Limit
                        </label>
                        <input
                            type="number"
                            min="0"
                            step="100000"
                            value={totalBudgetLimit}
                            onChange={(e) => setTotalBudgetLimit(parseFloat(e.target.value) || 0)}
                            disabled={isReadonly}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                            Status
                        </label>
                        <div className="px-3 py-2 bg-gray-100 dark:bg-gray-700 rounded-md">
                            <span
                                className={`px-2 py-1 rounded-full text-xs font-medium ${budgetPlan?.status === 'approved' || budgetPlan?.status === 'active'
                                        ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-200'
                                        : budgetPlan?.status === 'submitted'
                                            ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-200'
                                            : 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200'
                                    }`}
                            >
                                {budgetPlan?.status || 'draft'}
                            </span>
                        </div>
                    </div>
                </div>
            </div>

            {/* Summary Cards */}
            <BudgetSummaryCards
                metrics={metrics}
                year={selectedYear}
                quarter={selectedQuarter}
            />

            {/* Allocation Table */}
            <AllocationTable
                budgets={localBudgets}
                onBudgetChange={handleBudgetChange}
                onCategoryChange={handleCategoryChange}
                totalBudgetLimit={totalBudgetLimit}
                readonly={isReadonly}
            />

            {/* Actions */}
            {!isReadonly && (
                <div className="mt-8 flex justify-end gap-4">
                    <button
                        onClick={() => navigate('/dashboard')}
                        className="px-6 py-3 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSaveDraft}
                        disabled={saveDraftMutation.isPending}
                        className="px-6 py-3 bg-gray-600 text-white rounded-lg hover:bg-gray-700 transition-colors disabled:opacity-50"
                    >
                        {saveDraftMutation.isPending ? 'Saving...' : 'Save Draft'}
                    </button>
                    <button
                        onClick={handleSubmit}
                        disabled={submitMutation.isPending || metrics.remainingBudget < 0}
                        className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 flex-1 max-w-xs"
                    >
                        {submitMutation.isPending ? 'Submitting...' : 'Submit for Approval'}
                    </button>
                </div>
            )}

            {isReadonly && (
                <div className="mt-8 p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
                    <p className="text-blue-800 dark:text-blue-200">
                        ℹ️ This budget plan is <strong>{budgetPlan?.status}</strong> and cannot be edited.
                    </p>
                </div>
            )}
        </div>
    );
};

export default BudgetPlanningPage;
