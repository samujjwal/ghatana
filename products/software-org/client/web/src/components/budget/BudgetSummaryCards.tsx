/**
 * Budget Summary Cards Component
 *
 * Displays key budget metrics in card format.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';

export interface BudgetMetrics {
    totalAllocated: number;
    totalSpent: number;
    totalForecasted: number;
    variance: number;
    variancePercent: number;
    remainingBudget: number;
    departmentCount: number;
}

interface BudgetSummaryCardsProps {
    metrics: BudgetMetrics;
    year: number;
    quarter?: string;
}

export const BudgetSummaryCards: React.FC<BudgetSummaryCardsProps> = ({
    metrics,
    year,
    quarter,
}) => {
    const formatCurrency = (amount: number): string => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0,
        }).format(amount);
    };

    const getVarianceColor = (variance: number): string => {
        if (variance < 0) return 'text-red-600 dark:text-red-400';
        if (variance > 0) return 'text-green-600 dark:text-green-400';
        return 'text-gray-600 dark:text-gray-400';
    };

    const getBudgetHealthColor = (percent: number): string => {
        if (percent > 90) return 'text-red-600 dark:text-red-400';
        if (percent > 75) return 'text-yellow-600 dark:text-yellow-400';
        return 'text-green-600 dark:text-green-400';
    };

    const spendPercent = metrics.totalAllocated > 0
        ? (metrics.totalSpent / metrics.totalAllocated) * 100
        : 0;

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            {/* Total Allocated */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 transition-transform hover:scale-105">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-gray-600 dark:text-gray-400">Total Allocated</p>
                        <p className="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                            {formatCurrency(metrics.totalAllocated)}
                        </p>
                    </div>
                    <div className="text-5xl">💰</div>
                </div>
                <div className="mt-4 text-xs text-gray-500 dark:text-gray-400">
                    {metrics.departmentCount} departments • {year} {quarter || ''}
                </div>
            </div>

            {/* Total Spent */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 transition-transform hover:scale-105">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-gray-600 dark:text-gray-400">Total Spent</p>
                        <p className="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                            {formatCurrency(metrics.totalSpent)}
                        </p>
                    </div>
                    <div className="text-5xl">💸</div>
                </div>
                <div className="mt-4">
                    <div className="flex items-center justify-between text-xs mb-1">
                        <span className="text-gray-600 dark:text-gray-400">Budget Usage</span>
                        <span className={getBudgetHealthColor(spendPercent)}>
                            {spendPercent.toFixed(1)}%
                        </span>
                    </div>
                    <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                        <div
                            className={`h-2 rounded-full transition-all ${spendPercent > 90
                                    ? 'bg-red-500'
                                    : spendPercent > 75
                                        ? 'bg-yellow-500'
                                        : 'bg-green-500'
                                }`}
                            style={{ width: `${Math.min(spendPercent, 100)}%` }}
                        />
                    </div>
                </div>
            </div>

            {/* Remaining Budget */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 transition-transform hover:scale-105">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-gray-600 dark:text-gray-400">Remaining</p>
                        <p className="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                            {formatCurrency(metrics.remainingBudget)}
                        </p>
                    </div>
                    <div className="text-5xl">
                        {metrics.remainingBudget > 0 ? '✅' : '⚠️'}
                    </div>
                </div>
                <div className="mt-4 text-xs text-gray-500 dark:text-gray-400">
                    {((metrics.remainingBudget / metrics.totalAllocated) * 100).toFixed(1)}% available
                </div>
            </div>

            {/* Forecast Variance */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 transition-transform hover:scale-105">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-gray-600 dark:text-gray-400">Variance</p>
                        <p className={`text-3xl font-bold mt-2 ${getVarianceColor(metrics.variance)}`}>
                            {metrics.variance >= 0 ? '+' : ''}
                            {formatCurrency(metrics.variance)}
                        </p>
                    </div>
                    <div className="text-5xl">
                        {metrics.variance >= 0 ? '📈' : '📉'}
                    </div>
                </div>
                <div className="mt-4 text-xs text-gray-500 dark:text-gray-400">
                    Forecasted: {formatCurrency(metrics.totalForecasted)}
                </div>
                <div className={`text-xs mt-1 ${getVarianceColor(metrics.variance)}`}>
                    {metrics.variancePercent >= 0 ? '+' : ''}
                    {metrics.variancePercent.toFixed(1)}% vs forecast
                </div>
            </div>
        </div>
    );
};

export default BudgetSummaryCards;
