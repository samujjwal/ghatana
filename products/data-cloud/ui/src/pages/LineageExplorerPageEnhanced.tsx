/**
 * Enhanced Lineage Explorer Page
 *
 * Part of Journey 9: Lineage Explorer & Root Cause Analysis
 * Complete implementation with graph, impact analysis, and time-travel
 *
 * @doc.type page
 * @doc.purpose Interactive lineage exploration with root cause analysis
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { GitBranch, Clock, Database, AlertTriangle, Activity, CheckCircle } from 'lucide-react';
import { lineageService, LineageNode } from '../api/lineage.service';
import { LineageGraph } from '../components/lineage/LineageGraph';
import { DashboardKPI } from '../components/cards/DashboardCard';
import { BaseCard } from '../components/cards/BaseCard';

/** @deprecated Use DataExplorer instead. Routes now redirect /lineage → DataExplorer. */
export function LineageExplorerPage() {
  const [selectedDatasetId, setSelectedDatasetId] = useState('ds-001');
  const [selectedNode, setSelectedNode] = useState<LineageNode | null>(null);
  const [timeTravelDate, setTimeTravelDate] = useState<string | undefined>();

  // Fetch lineage data
  const { data: lineageData, isLoading } = useQuery({
    queryKey: ['lineage', selectedDatasetId, timeTravelDate],
    queryFn: () => lineageService.getLineage(selectedDatasetId, 'BOTH', 3),
  });

  // Fetch impact analysis
  const { data: impactData } = useQuery({
    queryKey: ['impact-analysis', selectedDatasetId],
    queryFn: () => lineageService.getImpactAnalysis(selectedDatasetId),
  });

  // Fetch execution logs
  const { data: executionLogs } = useQuery({
    queryKey: ['execution-logs', selectedDatasetId],
    queryFn: () => lineageService.getExecutionLogs(selectedDatasetId, 10),
  });

  const handleNodeClick = (node: LineageNode) => {
    setSelectedNode(node);
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-gradient-to-br from-blue-500 to-purple-500 rounded-lg">
              <GitBranch className="h-8 w-8 text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-gray-900">
                Lineage Explorer
              </h1>
              <p className="text-sm text-gray-600 mt-1">
                Trace data lineage and perform root cause analysis
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
            title="Affected Datasets"
            value={impactData?.affectedDatasets || 0}
            icon={<Database className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="blue"
          />
          <DashboardKPI
            title="Affected Queries"
            value={impactData?.affectedQueries || 0}
            icon={<Activity className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="purple"
          />
          <DashboardKPI
            title="Dashboards"
            value={impactData?.affectedDashboards || 0}
            icon={<CheckCircle className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="green"
          />
          <DashboardKPI
            title="Workflows"
            value={impactData?.affectedWorkflows || 0}
            icon={<AlertTriangle className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="orange"
          />
        </div>

        {/* Controls */}
        <div className="mb-6 flex items-center gap-4">
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Dataset
            </label>
            <select
              value={selectedDatasetId}
              onChange={(e) => setSelectedDatasetId(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
            >
              <option value="ds-001">Sales Summary</option>
              <option value="ds-002">Customer Events</option>
              <option value="ds-003">Transactions</option>
            </select>
          </div>

          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700 mb-1">
              <Clock className="h-4 w-4 inline mr-1" />
              Time Travel (Optional)
            </label>
            <input
              type="datetime-local"
              value={timeTravelDate || ''}
              onChange={(e) => setTimeTravelDate(e.target.value || undefined)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>

        {/* Lineage Graph */}
        <div className="mb-8">
          <BaseCard title="Lineage Graph" subtitle="Interactive data lineage visualization">
            {isLoading ? (
              <div className="h-[600px] flex items-center justify-center">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
                  <p className="text-gray-600">Loading lineage...</p>
                </div>
              </div>
            ) : lineageData ? (
              <LineageGraph
                nodes={lineageData.nodes}
                edges={lineageData.edges}
                rootNode={lineageData.rootNode}
                onNodeClick={handleNodeClick}
                height="600px"
              />
            ) : (
              <div className="h-[600px] flex items-center justify-center text-gray-500">
                No lineage data available
              </div>
            )}
          </BaseCard>
        </div>

        {/* Bottom Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Impact Analysis */}
          <BaseCard title="Impact Analysis" subtitle="Downstream dependencies">
            {impactData?.details && impactData.details.length > 0 ? (
              <div className="space-y-2">
                {impactData.details.slice(0, 10).map((detail) => (
                  <div
                    key={detail.id}
                    className="flex items-center justify-between p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <Database className="h-4 w-4 text-gray-400" />
                      <div>
                        <div className="text-sm font-medium text-gray-900">
                          {detail.name}
                        </div>
                        <div className="text-xs text-gray-500">
                          {detail.type} • Distance: {detail.distance}
                        </div>
                      </div>
                    </div>
                    <span
                      className={`px-2 py-1 rounded text-xs font-semibold ${
                        detail.impact === 'DIRECT'
                          ? 'bg-red-100 text-red-700'
                          : 'bg-yellow-100 text-yellow-700'
                      }`}
                    >
                      {detail.impact}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-8 text-gray-500">
                No impact data available
              </div>
            )}
          </BaseCard>

          {/* Execution Logs */}
          <BaseCard title="Execution Logs" subtitle="Recent executions">
            {executionLogs && executionLogs.length > 0 ? (
              <div className="space-y-2">
                {executionLogs.map((log) => (
                  <div
                    key={log.id}
                    className="p-3 bg-gray-50 rounded-lg"
                  >
                    <div className="flex items-center justify-between mb-2">
                      <span
                        className={`px-2 py-1 rounded text-xs font-semibold ${
                          log.status === 'SUCCESS'
                            ? 'bg-green-100 text-green-700'
                            : log.status === 'FAILED'
                            ? 'bg-red-100 text-red-700'
                            : 'bg-yellow-100 text-yellow-700'
                        }`}
                      >
                        {log.status}
                      </span>
                      <span className="text-xs text-gray-500">
                        {new Date(log.timestamp).toLocaleString()}
                      </span>
                    </div>
                    <div className="text-sm text-gray-700">
                      Duration: {(log.duration / 1000).toFixed(1)}s
                      {log.recordsProcessed && (
                        <> • Records: {log.recordsProcessed.toLocaleString()}</>
                      )}
                    </div>
                    {log.error && (
                      <div className="mt-2 text-xs text-red-600 bg-red-50 p-2 rounded">
                        {log.error}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-8 text-gray-500">
                No execution logs available
              </div>
            )}
          </BaseCard>
        </div>

        {/* Selected Node Details */}
        {selectedNode && (
          <div className="mt-8">
            <BaseCard title="Node Details" subtitle={selectedNode.name}>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <span className="text-sm font-medium text-gray-600">ID:</span>
                  <p className="text-sm text-gray-900 mt-1">{selectedNode.id}</p>
                </div>
                <div>
                  <span className="text-sm font-medium text-gray-600">Type:</span>
                  <p className="text-sm text-gray-900 mt-1">{selectedNode.type}</p>
                </div>
                {selectedNode.metadata && Object.keys(selectedNode.metadata).length > 0 && (
                  <div className="col-span-2">
                    <span className="text-sm font-medium text-gray-600">Metadata:</span>
                    <pre className="text-xs text-gray-900 mt-1 bg-gray-50 p-2 rounded">
                      {JSON.stringify(selectedNode.metadata, null, 2)}
                    </pre>
                  </div>
                )}
              </div>
            </BaseCard>
          </div>
        )}
      </div>
    </div>
  );
}

export default LineageExplorerPage;

