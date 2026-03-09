/**
 * Quality Heatmap Component
 *
 * Displays data quality metrics as a heatmap with drill-down capabilities.
 * Part of Journey 6: Data Quality & Validation
 *
 * @doc.type component
 * @doc.purpose Data quality heatmap visualization
 * @doc.layer frontend
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle, Info, TrendingDown } from 'lucide-react';
import { qualityService, QualityMetric } from '../../api/quality.service';
import BaseCard from '../cards/BaseCard';

interface QualityHeatmapProps {
  onDatasetClick?: (datasetId: string) => void;
}

export function QualityHeatmap({ onDatasetClick }: QualityHeatmapProps) {
  const { data: metrics, isLoading } = useQuery({
    queryKey: ['quality-metrics'],
    queryFn: () => qualityService.getQualityMetrics(),
    refetchInterval: 60000,
  });

  const getScoreColor = (score: number): string => {
    if (score >= 0.9) return 'bg-green-500';
    if (score >= 0.7) return 'bg-yellow-500';
    if (score >= 0.5) return 'bg-orange-500';
    return 'bg-red-500';
  };

  const getScoreTextColor = (score: number): string => {
    if (score >= 0.9) return 'text-green-700';
    if (score >= 0.7) return 'text-yellow-700';
    if (score >= 0.5) return 'text-orange-700';
    return 'text-red-700';
  };

  const getScoreIcon = (score: number) => {
    if (score >= 0.9) return <CheckCircle className="h-4 w-4 text-green-500" />;
    if (score >= 0.7) return <Info className="h-4 w-4 text-yellow-500" />;
    return <AlertTriangle className="h-4 w-4 text-red-500" />;
  };

  if (isLoading) {
    return (
      <BaseCard title="Data Quality Heatmap">
        <div className="animate-pulse space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-16 bg-gray-200 rounded"></div>
          ))}
        </div>
      </BaseCard>
    );
  }

  return (
    <BaseCard
      title="Data Quality Heatmap"
      subtitle="Quality scores across all datasets"
      actions={
        <button className="text-sm text-primary-600 hover:text-primary-700">
          Refresh
        </button>
      }
    >
      <div className="space-y-2">
        {/* Header Row */}
        <div className="grid grid-cols-12 gap-2 text-xs font-semibold text-gray-600 pb-2 border-b">
          <div className="col-span-4">Dataset</div>
          <div className="col-span-2 text-center">Complete</div>
          <div className="col-span-2 text-center">Accurate</div>
          <div className="col-span-2 text-center">Fresh</div>
          <div className="col-span-2 text-center">Overall</div>
        </div>

        {/* Data Rows */}
        {metrics?.map((metric) => (
          <div
            key={metric.datasetId}
            className="grid grid-cols-12 gap-2 items-center py-2 hover:bg-gray-50 rounded cursor-pointer transition-colors"
            onClick={() => onDatasetClick?.(metric.datasetId)}
          >
            <div className="col-span-4 flex items-center gap-2">
              {getScoreIcon(metric.overallScore)}
              <span className="text-sm font-medium text-gray-900 truncate">
                {metric.datasetName}
              </span>
            </div>

            <div className="col-span-2 flex justify-center">
              <ScoreCell score={metric.completeness} />
            </div>

            <div className="col-span-2 flex justify-center">
              <ScoreCell score={metric.accuracy} />
            </div>

            <div className="col-span-2 flex justify-center">
              <ScoreCell score={metric.freshness} />
            </div>

            <div className="col-span-2 flex justify-center">
              <div
                className={`px-3 py-1 rounded-full text-xs font-semibold ${getScoreTextColor(
                  metric.overallScore
                )} bg-opacity-10`}
                style={{
                  backgroundColor: `${getScoreColor(metric.overallScore)}20`,
                }}
              >
                {(metric.overallScore * 100).toFixed(0)}%
              </div>
            </div>
          </div>
        ))}

        {(!metrics || metrics.length === 0) && (
          <div className="text-center py-8 text-gray-500">
            <TrendingDown className="h-12 w-12 mx-auto mb-2 opacity-50" />
            <p>No quality metrics available</p>
          </div>
        )}
      </div>
    </BaseCard>
  );
}

interface ScoreCellProps {
  score: number;
}

function ScoreCell({ score }: ScoreCellProps) {
  const getColor = (s: number): string => {
    if (s >= 0.9) return 'bg-green-500';
    if (s >= 0.7) return 'bg-yellow-500';
    if (s >= 0.5) return 'bg-orange-500';
    return 'bg-red-500';
  };

  return (
    <div className="w-16 h-8 bg-gray-200 rounded overflow-hidden relative">
      <div
        className={`h-full ${getColor(score)} transition-all duration-300`}
        style={{ width: `${score * 100}%` }}
      />
      <span className="absolute inset-0 flex items-center justify-center text-xs font-semibold text-gray-700">
        {(score * 100).toFixed(0)}%
      </span>
    </div>
  );
}

export default QualityHeatmap;

