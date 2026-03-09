/**
 * Dataset Explorer Page
 *
 * Global list of datasets with search, filters, and sorting across all Data Cloud datasets.
 * See spec: docs/web-page-specs/11_dataset_explorer_list_page.md
 *
 * @doc.type page
 * @doc.purpose Dataset catalog list view with search and filters
 * @doc.layer frontend
 */

import React from 'react';
import { Link } from 'react-router';
import { Database, Filter, Search } from 'lucide-react';
import { BaseCard } from '../components/cards/BaseCard';
import { EmptyState } from '../components/common/EmptyState';

/**
 * Dataset Explorer Page Component
 *
 * @returns JSX element
 */
export function DatasetExplorerPage(): React.ReactElement {
  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        <BaseCard className="mb-6">
          <div className="space-y-2">
            <h1 className="text-2xl font-semibold text-slate-900">Dataset Explorer</h1>
            <p className="text-sm text-slate-600">
              Browse and inspect datasets registered in the Data Cloud catalog.
            </p>
          </div>
        </BaseCard>

        <div className="space-y-6">
          {/* Search and Filters */}
          <BaseCard>
            <div className="space-y-4">
              <div className="flex gap-4">
                <div className="flex-1 relative">
                  <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Search datasets by name, description, or column..."
                    className="w-full pl-10 pr-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                </div>
                <button className="flex items-center gap-2 px-4 py-2 border border-slate-300 rounded-lg hover:bg-slate-50">
                  <Filter className="h-4 w-4" />
                  Filters
                </button>
              </div>
            </div>
          </BaseCard>

          {/* Empty State - to be replaced with actual dataset list */}
          <BaseCard>
            <EmptyState
              icon={<Database className="h-12 w-12" />}
              title="No datasets found"
              description="Start by connecting data sources or creating collections."
              action={
                <Link
                  to="/collections/new"
                  className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
                >
                  Create Collection
                </Link>
              }
            />
          </BaseCard>
        </div>
      </div>
    </div>
  );
}

