/**
 * Cost Explorer Component
 *
 * Displays cost breakdown by dataset, query, and user with optimization suggestions.
 * Part of Journey 7: Query Optimization & Cost Control
 *
 * @doc.type component
 * @doc.purpose Cost analysis dashboard
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  DollarSign,
  TrendingUp,
  TrendingDown,
  Database,
  Users,
  FileText,
} from 'lucide-react';
import { costService, CostBreakdown } from '../../api/cost.service';
import BaseCard from '../cards/BaseCard';

interface CostExplorerProps {
  period?: string;
}

type ViewMode = 'DATASET' | 'QUERY' | 'USER';

export function CostExplorer({ period = '30d' }: CostExplorerProps) {
  const [viewMode, setViewMode] = useState<ViewMode>('DATASET');

  const { data: costData, isLoading } = useQuery({
    queryKey: ['cost-analysis', period],
    queryFn: () => costService.getCostAnalysis(period),
    refetchInterval: 300000, // 5 minutes
  });

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: costData?.currency || 'USD',
      minimumFractionDigits: 2,
    }).format(amount);
  };

  if (isLoading) {
    return (
      <BaseCard title="Cost Explorer">
        <div className="animate-pulse space-y-4">
          <div className="h-24 bg-gray-200 rounded"></div>
          <div className="h-64 bg-gray-200 rounded"></div>
        </div>
      </BaseCard>
    );
  }

  if (!costData) {
    return (
      <BaseCard title="Cost Explorer">
        <div className="text-center py-8 text-gray-500">
          <DollarSign className="h-12 w-12 mx-auto mb-2 opacity-50" />
          <p>No cost data available</p>
        </div>
      </BaseCard>
    );
  }

  return (
    <div className="space-y-6">
      {/* Summary Card */}
      <BaseCard>
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-gray-600">Total Cost</p>
            <p className="text-3xl font-bold text-gray-900 mt-1">
              {formatCurrency(costData.total)}
            </p>
            <p className="text-xs text-gray-500 mt-1">Period: {costData.period}</p>
          </div>
          <div className="p-3 bg-gradient-to-br from-green-400 to-blue-500 rounded-lg">
            <DollarSign className="h-8 w-8 text-white" />
          </div>
        </div>
      </BaseCard>

      {/* View Mode Tabs */}
      <div className="flex gap-2 border-b border-gray-200">
        <button
          onClick={() => setViewMode('DATASET')}
          className={`flex items-center gap-2 px-4 py-2 font-medium text-sm transition-colors ${
            viewMode === 'DATASET'
              ? 'text-primary-600 border-b-2 border-primary-600'
              : 'text-gray-600 hover:text-gray-900'
          }`}
        >
          <Database className="h-4 w-4" />
          By Dataset
        </button>
        <button
          onClick={() => setViewMode('QUERY')}
          className={`flex items-center gap-2 px-4 py-2 font-medium text-sm transition-colors ${
            viewMode === 'QUERY'
              ? 'text-primary-600 border-b-2 border-primary-600'
              : 'text-gray-600 hover:text-gray-900'
          }`}
        >
          <FileText className="h-4 w-4" />
          By Query
        </button>
        <button
          onClick={() => setViewMode('USER')}
          className={`flex items-center gap-2 px-4 py-2 font-medium text-sm transition-colors ${
            viewMode === 'USER'
              ? 'text-primary-600 border-b-2 border-primary-600'
              : 'text-gray-600 hover:text-gray-900'
          }`}
        >
          <Users className="h-4 w-4" />
          By User
        </button>
      </div>

      {/* Content */}
      <BaseCard>
        {viewMode === 'DATASET' && (
          <CostByDataset items={costData.byDataset} formatCurrency={formatCurrency} />
        )}
        {viewMode === 'QUERY' && (
          <CostByQuery items={costData.byQuery} formatCurrency={formatCurrency} />
        )}
        {viewMode === 'USER' && (
          <CostByUser items={costData.byUser} formatCurrency={formatCurrency} />
        )}
      </BaseCard>
    </div>
  );
}

interface CostByDatasetProps {
  items: Array<{
    datasetId: string;
    datasetName: string;
    cost: number;
    percentage: number;
  }>;
  formatCurrency: (amount: number) => string;
}

function CostByDataset({ items, formatCurrency }: CostByDatasetProps) {
  return (
    <div className="space-y-3">
      <h3 className="text-lg font-semibold text-gray-900">Cost by Dataset</h3>
      {items.map((item) => (
        <div key={item.datasetId} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <Database className="h-4 w-4 text-gray-400" />
              <span className="text-sm font-medium text-gray-900">
                {item.datasetName}
              </span>
            </div>
            <div className="mt-2">
              <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-primary-500 transition-all duration-300"
                  style={{ width: `${item.percentage}%` }}
                />
              </div>
            </div>
          </div>
          <div className="ml-4 text-right">
            <div className="text-lg font-bold text-gray-900">
              {formatCurrency(item.cost)}
            </div>
            <div className="text-xs text-gray-500">{item.percentage.toFixed(1)}%</div>
          </div>
        </div>
      ))}
    </div>
  );
}

interface CostByQueryProps {
  items: Array<{
    queryId: string;
    queryHash: string;
    cost: number;
    executionCount: number;
    avgCost: number;
  }>;
  formatCurrency: (amount: number) => string;
}

function CostByQuery({ items, formatCurrency }: CostByQueryProps) {
  return (
    <div className="space-y-3">
      <h3 className="text-lg font-semibold text-gray-900">Top Expensive Queries</h3>
      {items.slice(0, 10).map((item, index) => (
        <div key={item.queryId} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
          <div className="flex-shrink-0 w-8 h-8 bg-primary-100 text-primary-600 rounded-full flex items-center justify-center font-bold text-sm">
            #{index + 1}
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-gray-900 font-mono truncate">
              {item.queryHash.substring(0, 16)}...
            </div>
            <div className="text-xs text-gray-500 mt-1">
              Executions: {item.executionCount} • Avg: {formatCurrency(item.avgCost)}
            </div>
          </div>
          <div className="text-lg font-bold text-gray-900">
            {formatCurrency(item.cost)}
          </div>
        </div>
      ))}
    </div>
  );
}

interface CostByUserProps {
  items: Array<{
    userId: string;
    userName: string;
    cost: number;
    queryCount: number;
  }>;
  formatCurrency: (amount: number) => string;
}

function CostByUser({ items, formatCurrency }: CostByUserProps) {
  return (
    <div className="space-y-3">
      <h3 className="text-lg font-semibold text-gray-900">Cost by User</h3>
      {items.map((item) => (
        <div key={item.userId} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-purple-400 to-pink-500 rounded-full flex items-center justify-center text-white font-bold">
              {item.userName.charAt(0).toUpperCase()}
            </div>
            <div>
              <div className="text-sm font-medium text-gray-900">{item.userName}</div>
              <div className="text-xs text-gray-500">{item.queryCount} queries</div>
            </div>
          </div>
          <div className="text-lg font-bold text-gray-900">
            {formatCurrency(item.cost)}
          </div>
        </div>
      ))}
    </div>
  );
}

export default CostExplorer;

