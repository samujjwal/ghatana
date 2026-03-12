/**
 * Brain Dashboard Page
 *
 * Implements Journey 1: The Morning Briefing (System Consciousness)
 * Shows the Global Workspace spotlight and autonomy timeline.
 *
 * @doc.type page
 * @doc.purpose Glass Box Brain dashboard for system consciousness
 * @doc.layer frontend
 */

import React from 'react';
import { Brain, Activity, Zap, TrendingUp } from 'lucide-react';
import { SpotlightRing } from '../components/brain/SpotlightRing';
import { AutonomyTimeline } from '../components/brain/AutonomyTimeline';
import { DashboardKPI } from '../components/cards/DashboardCard';
import { useQuery } from '@tanstack/react-query';
import { brainService, type BrainStats } from '../api/brain.service';

export function BrainDashboardPage() {
  const { data: stats } = useQuery<BrainStats>({
    queryKey: ['brain-stats'],
    queryFn: () => brainService.getBrainStats(),
    refetchInterval: 60_000,
  });

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-gradient-to-br from-purple-500 to-blue-500 rounded-lg">
              <Brain className="h-8 w-8 text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-gray-900">
                Data Cloud Brain
              </h1>
              <p className="text-sm text-gray-600 mt-1">
                Glass Box Intelligence: System Consciousness & Autonomy
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
            title="Spotlight Items"
            value={stats?.spotlightItemsCount || 0}
            icon={<Zap className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="yellow"
          />
          <DashboardKPI
            title="Autonomy Actions"
            value={stats?.autonomyActionsToday || 0}
            subtitle="Today"
            icon={<Activity className="h-6 w-6" />}
            trend={{ value: 12, direction: 'up' }}
            color="blue"
          />
          <DashboardKPI
            title="System Confidence"
            value={`${((stats?.averageConfidence || 0) * 100).toFixed(0)}%`}
            icon={<TrendingUp className="h-6 w-6" />}
            trend={{ value: 5, direction: 'up' }}
            color="green"
          />
          <DashboardKPI
            title="Active Subsystems"
            value={stats?.activeSubsystems || 0}
            icon={<Brain className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="purple"
          />
        </div>

        {/* Main Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left Column: Spotlight Ring */}
          <div className="lg:col-span-1">
            <SpotlightRing maxItems={5} autoRefresh={true} />
          </div>

          {/* Right Column: Autonomy Timeline */}
          <div className="lg:col-span-2">
            <AutonomyTimeline maxItems={10} showFilters={true} />
          </div>
        </div>

        {/* Information Banner */}
        <div className="mt-8 bg-blue-50 border border-blue-200 rounded-lg p-4">
          <div className="flex items-start gap-3">
            <Brain className="h-5 w-5 text-blue-600 mt-0.5" />
            <div className="flex-1">
              <h3 className="text-sm font-semibold text-blue-900 mb-1">
                About the Glass Box Brain
              </h3>
              <p className="text-sm text-blue-800">
                The Data Cloud Brain provides transparent visibility into the platform's cognitive state.
                The <strong>Global Spotlight</strong> shows high-salience items requiring attention,
                while the <strong>Autonomy Timeline</strong> tracks AI-driven actions and decisions.
                This "glass box" approach enables collaboration between human expertise and machine intelligence.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default BrainDashboardPage;

