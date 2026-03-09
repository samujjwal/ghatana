/**
 * Cost & Optimization Page
 *
 * Implements Journey 7: Query Optimization & Cost Control
 * Shows cost breakdown, query optimization suggestions, and tier management.
 *
 * @doc.type page
 * @doc.purpose Cost analysis and query optimization dashboard
 * @doc.layer frontend
 */

import React from 'react';
import { DollarSign, TrendingUp, Zap, Database } from 'lucide-react';
import { CostExplorer } from '../components/cost/CostExplorer';
import { DashboardKPI } from '../components/cards/DashboardCard';
import { useQuery } from '@tanstack/react-query';
import { costService } from '../api/cost.service';

export function CostOptimizationPage() {
  const { data: costData } = useQuery({
    queryKey: ['cost-analysis', '30d'],
    queryFn: () => costService.getCostAnalysis('30d'),
  });

  const { data: mvSuggestions } = useQuery({
    queryKey: ['mv-suggestions'],
    queryFn: () => costService.getMaterializedViewSuggestions(),
  });

  const { data: hotnessMetrics } = useQuery({
    queryKey: ['hotness-metrics'],
    queryFn: () => costService.getHotnessMetrics(),
  });

  const totalSavingsPotential =
    mvSuggestions?.reduce((acc, s) => acc + s.estimatedSavings, 0) || 0;

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-gradient-to-br from-green-500 to-blue-500 rounded-lg">
              <DollarSign className="h-8 w-8 text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-gray-900">
                Cost & Optimization
              </h1>
              <p className="text-sm text-gray-600 mt-1">
                Analyze costs and optimize query performance
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* KPI Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <DashboardKPI
            title="Total Cost (30d)"
            value={`$${(costData?.total || 0).toFixed(2)}`}
            icon={<DollarSign className="h-6 w-6" />}
            trend={{ value: 8, direction: 'up' }}
            color="green"
          />
          <DashboardKPI
            title="Savings Potential"
            value={`$${totalSavingsPotential.toFixed(2)}`}
            icon={<TrendingUp className="h-6 w-6" />}
            trend={{ value: 15, direction: 'up' }}
            color="blue"
          />
          <DashboardKPI
            title="Optimization Ops"
            value={mvSuggestions?.length || 0}
            subtitle="Suggestions"
            icon={<Zap className="h-6 w-6" />}
            trend={{ value: 3, direction: 'up' }}
            color="yellow"
          />
          <DashboardKPI
            title="Hot Datasets"
            value={
              hotnessMetrics?.filter((m) => m.tier === 'HOT').length || 0
            }
            icon={<Database className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="orange"
          />
        </div>

        {/* Cost Explorer */}
        <div className="mb-8">
          <CostExplorer period="30d" />
        </div>

        {/* Optimization Suggestions */}
        {mvSuggestions && mvSuggestions.length > 0 && (
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">
              Materialized View Suggestions
            </h2>
            <div className="space-y-3">
              {mvSuggestions.map((suggestion, index) => (
                <div
                  key={index}
                  className="flex items-start gap-3 p-4 bg-blue-50 border border-blue-200 rounded-lg"
                >
                  <Zap className="h-5 w-5 text-blue-600 mt-0.5" />
                  <div className="flex-1">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium text-gray-900">
                        {suggestion.name}
                      </span>
                      <span className="text-sm font-bold text-green-600">
                        Save ${suggestion.estimatedSavings.toFixed(2)}/day
                      </span>
                    </div>
                    <p className="text-sm text-gray-600 mt-1">
                      Pattern: {suggestion.pattern} • Frequency: {suggestion.frequency}x/day
                    </p>
                    <p className="text-xs text-gray-500 mt-1">
                      Refresh: {suggestion.refreshStrategy}
                    </p>
                  </div>
                  <button className="px-3 py-1 bg-blue-600 text-white text-sm font-medium rounded hover:bg-blue-700 transition-colors">
                    Create
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default CostOptimizationPage;

