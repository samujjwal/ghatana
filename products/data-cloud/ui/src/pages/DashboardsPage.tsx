/**
 * Dashboards & Metrics Page
 *
 * Configure and view dashboards that reuse shared metrics and chart primitives.
 * See spec: docs/web-page-specs/01_dashboard_page.md
 *
 * @doc.type page
 * @doc.purpose Dashboards and metrics visualization
 * @doc.layer frontend
 */

import React from 'react';
import { BarChart2, Plus, TrendingUp } from 'lucide-react';
import { BaseCard } from '../components/cards/BaseCard';
import { EmptyState } from '../components/common/EmptyState';

/**
 * Dashboards Page Component
 *
 * @returns JSX element
 */
export function DashboardsPage(): React.ReactElement {
  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        <BaseCard className="mb-6">
          <div className="flex items-center justify-between">
            <div className="space-y-2">
              <h1 className="text-2xl font-semibold text-slate-900">Dashboards & Metrics</h1>
              <p className="text-sm text-slate-600">
                Configure and view dashboards that reuse shared metrics and chart primitives.
              </p>
            </div>
            <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
              <Plus className="h-4 w-4" />
              New Dashboard
            </button>
          </div>
        </BaseCard>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {/* Example Dashboard Cards */}
          <BaseCard className="hover:shadow-lg transition-shadow cursor-pointer">
            <div className="flex items-start gap-4">
              <div className="p-3 bg-blue-100 rounded-lg">
                <BarChart2 className="h-6 w-6 text-blue-600" />
              </div>
              <div className="flex-1">
                <h3 className="text-lg font-semibold text-slate-900">System Overview</h3>
                <p className="text-sm text-slate-600 mt-1">
                  Key metrics and health indicators
                </p>
                <div className="flex items-center gap-2 mt-3 text-sm text-green-600">
                  <TrendingUp className="h-4 w-4" />
                  <span>All systems operational</span>
                </div>
              </div>
            </div>
          </BaseCard>

          {/* Empty State for more dashboards */}
          <BaseCard className="col-span-1 md:col-span-2 lg:col-span-2">
            <EmptyState
              icon={<BarChart2 className="h-12 w-12" />}
              title="Create your first dashboard"
              description="Build custom dashboards with metrics and visualizations tailored to your needs."
            />
          </BaseCard>
        </div>
      </div>
    </div>
  );
}

