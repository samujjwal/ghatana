/**
 * Data Quality Dashboard Page
 *
 * Implements Journey 6: Data Quality & Validation
 * Shows quality metrics, PII detection, and validation rules.
 *
 * @doc.type page
 * @doc.purpose Data quality monitoring and validation dashboard
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { Shield, AlertTriangle, CheckCircle, Activity } from 'lucide-react';
import { QualityHeatmap } from '../components/quality/QualityHeatmap';
import { PIIDetectionPanel } from '../components/quality/PIIDetectionPanel';
import { DashboardKPI } from '../components/cards/DashboardCard';
import { useQuery } from '@tanstack/react-query';
import { qualityService } from '../api/quality.service';

export function DataQualityPage() {
  const [selectedDataset, setSelectedDataset] = useState<string | undefined>();

  const { data: metrics } = useQuery({
    queryKey: ['quality-metrics'],
    queryFn: () => qualityService.getQualityMetrics(),
  });

  const { data: anomalies } = useQuery({
    queryKey: ['quality-anomalies'],
    queryFn: () => qualityService.getAnomalies(),
  });

  const safeMetrics = metrics ?? [];
  const avgQuality = safeMetrics.reduce((acc, m) => acc + m.overallScore, 0) / (safeMetrics.length || 1);
  const totalIssues = safeMetrics.reduce((acc, m) => acc + m.issues.length, 0);
  const criticalIssues = safeMetrics.reduce(
    (acc, m) => acc + m.issues.filter((i) => i.severity === 'CRITICAL').length,
    0
  );

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-gradient-to-br from-blue-500 to-green-500 rounded-lg">
              <Shield className="h-8 w-8 text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-gray-900">Data Quality</h1>
              <p className="text-sm text-gray-600 mt-1">
                Monitor quality metrics, detect PII, and manage validation rules
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
            title="Avg Quality Score"
            value={`${((avgQuality || 0) * 100).toFixed(0)}%`}
            icon={<CheckCircle className="h-6 w-6" />}
            trend={{ value: 2, direction: 'up' }}
            color="green"
          />
          <DashboardKPI
            title="Total Issues"
            value={totalIssues}
            icon={<AlertTriangle className="h-6 w-6" />}
            trend={{ value: 5, direction: 'down' }}
            color="orange"
          />
          <DashboardKPI
            title="Critical Issues"
            value={criticalIssues}
            icon={<AlertTriangle className="h-6 w-6" />}
            trend={{ value: 1, direction: 'down' }}
            color="red"
          />
          <DashboardKPI
            title="Anomalies (24h)"
            value={anomalies?.length || 0}
            icon={<Activity className="h-6 w-6" />}
            trend={{ value: 0, direction: 'neutral' }}
            color="blue"
          />
        </div>

        {/* Main Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left Column: Quality Heatmap */}
          <div className="lg:col-span-2">
            <QualityHeatmap onDatasetClick={setSelectedDataset} />
          </div>

          {/* Right Column: PII Detection */}
          <div className="lg:col-span-1">
            <PIIDetectionPanel datasetId={selectedDataset} />
          </div>
        </div>

        {/* Anomalies Section */}
        {anomalies && anomalies.length > 0 && (
          <div className="mt-8">
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">
                Recent Anomalies
              </h2>
              <div className="space-y-3">
                {anomalies.slice(0, 5).map((anomaly) => (
                  <div
                    key={anomaly.id}
                    className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg"
                  >
                    <AlertTriangle
                      className={`h-5 w-5 mt-0.5 ${
                        anomaly.severity === 'HIGH'
                          ? 'text-red-500'
                          : anomaly.severity === 'MEDIUM'
                          ? 'text-orange-500'
                          : 'text-yellow-500'
                      }`}
                    />
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900">
                          {anomaly.type.replace(/_/g, ' ')}
                        </span>
                        <span
                          className={`px-2 py-0.5 rounded text-xs font-semibold ${
                            anomaly.severity === 'HIGH'
                              ? 'bg-red-100 text-red-700'
                              : anomaly.severity === 'MEDIUM'
                              ? 'bg-orange-100 text-orange-700'
                              : 'bg-yellow-100 text-yellow-700'
                          }`}
                        >
                          {anomaly.severity}
                        </span>
                      </div>
                      <p className="text-sm text-gray-600 mt-1">
                        {anomaly.description}
                      </p>
                      <p className="text-xs text-gray-500 mt-1">
                        {new Date(anomaly.timestamp).toLocaleString()}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default DataQualityPage;
