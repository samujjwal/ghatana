/**
 * Spotlight Ring Component
 *
 * Displays the top high-salience items from the Global Workspace.
 * Part of Journey 1: The Morning Briefing (System Consciousness)
 *
 * @doc.type component
 * @doc.purpose Display Global Workspace spotlight items
 * @doc.layer frontend
 */

import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, Zap, TrendingUp, Activity } from 'lucide-react';
import BaseCard from '../cards/BaseCard';
import StatusBadge from '../common/StatusBadge';

interface SalienceScore {
  score: number;
  breakdown: {
    recency: number;
    novelty: number;
    impact: number;
    urgency: number;
  };
}

interface SpotlightItem {
  id: string;
  tenantId: string;
  summary: string;
  salienceScore: SalienceScore;
  emergency: boolean;
  priority: number;
  category: string;
  spotlightedAt: string;
  expiresAt: string;
  accessCount: number;
  tags: string[];
  metadata: Record<string, any>;
}

interface SpotlightRingProps {
  maxItems?: number;
  autoRefresh?: boolean;
  refreshInterval?: number;
}

// Mock data for development
const mockSpotlightItems: SpotlightItem[] = [
  {
    id: '1',
    tenantId: 'demo',
    summary: 'Query optimization available for customer_events table',
    salienceScore: { score: 0.9, breakdown: { recency: 0.8, novelty: 0.7, impact: 0.9, urgency: 0.6 } },
    emergency: false,
    priority: 1,
    category: 'optimization',
    spotlightedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
    accessCount: 0,
    tags: ['performance', 'optimization'],
    metadata: {},
  },
  {
    id: '2',
    tenantId: 'demo',
    summary: 'Data freshness alert: orders table hasn\'t updated in 6h',
    salienceScore: { score: 0.7, breakdown: { recency: 0.9, novelty: 0.5, impact: 0.6, urgency: 0.8 } },
    emergency: false,
    priority: 2,
    category: 'warning',
    spotlightedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
    accessCount: 0,
    tags: ['data-quality', 'freshness'],
    metadata: {},
  },
];

const fetchSpotlightItems = async (): Promise<SpotlightItem[]> => {
  // Mock implementation for development
  return new Promise((resolve) => {
    setTimeout(() => resolve(mockSpotlightItems), 500);
  });
};

export function SpotlightRing({
  maxItems = 5,
  autoRefresh = true,
  refreshInterval = 30000,
}: SpotlightRingProps) {
  const { data: items = [], isLoading, error, refetch } = useQuery({
    queryKey: ['spotlight-items'],
    queryFn: fetchSpotlightItems,
    refetchInterval: autoRefresh ? refreshInterval : false,
  });

  const topItems = items.slice(0, maxItems);

  const getSalienceColor = (score: number): string => {
    if (score >= 0.9) return 'text-red-500';
    if (score >= 0.7) return 'text-orange-500';
    if (score >= 0.5) return 'text-yellow-500';
    return 'text-blue-500';
  };

  const getCategoryIcon = (category: string) => {
    switch (category?.toLowerCase()) {
      case 'anomaly':
      case 'alert':
        return <AlertTriangle className="h-5 w-5" />;
      case 'performance':
        return <Activity className="h-5 w-5" />;
      case 'trend':
        return <TrendingUp className="h-5 w-5" />;
      default:
        return <Zap className="h-5 w-5" />;
    }
  };

  if (isLoading) {
    return (
      <BaseCard>
        <div className="animate-pulse">
          <div className="h-8 bg-gray-200 rounded w-1/3 mb-4"></div>
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-20 bg-gray-200 rounded"></div>
            ))}
          </div>
        </div>
      </BaseCard>
    );
  }

  if (error) {
    return (
      <BaseCard>
        <div className="text-red-600">
          <AlertTriangle className="h-6 w-6 mb-2" />
          <p>Failed to load spotlight items</p>
        </div>
      </BaseCard>
    );
  }

  return (
    <BaseCard className="relative">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-2">
          <Zap className="h-6 w-6 text-yellow-500" />
          <h2 className="text-xl font-bold text-gray-900">Global Spotlight</h2>
        </div>
        {autoRefresh && (
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <Activity className="h-4 w-4 animate-pulse" />
            <span>Live</span>
          </div>
        )}
      </div>

      {topItems.length === 0 ? (
        <div className="text-center py-8 text-gray-500">
          <p className="text-lg">✨ All Clear</p>
          <p className="text-sm mt-2">No high-salience items detected</p>
        </div>
      ) : (
        <div className="space-y-4">
          {topItems.map((item: SpotlightItem, index: number) => (
            <div
              key={item.id}
              className={`
                relative p-4 rounded-lg border-2 transition-all hover:shadow-md
                ${item.emergency
                  ? 'border-red-500 bg-red-50'
                  : 'border-gray-200 bg-white'
                }
              `}
            >
              {/* Priority Badge */}
              <div className="absolute -top-2 -left-2 bg-white rounded-full border-2 border-gray-200 w-8 h-8 flex items-center justify-center font-bold text-sm">
                #{index + 1}
              </div>

              {/* Emergency Indicator */}
              {item.emergency && (
                <div className="absolute -top-2 -right-2">
                  <span className="relative flex h-6 w-6">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-6 w-6 bg-red-500"></span>
                  </span>
                </div>
              )}

              <div className="flex items-start gap-3">
                {/* Category Icon */}
                <div className={`${getSalienceColor(item.salienceScore.score)} mt-1`}>
                  {getCategoryIcon(item.category)}
                </div>

                {/* Content */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <StatusBadge
                      status={item.category}
                      variant={item.emergency ? 'danger' : 'warning'}
                    />
                    <span className="text-xs text-gray-500">
                      {new Date(item.spotlightedAt).toLocaleTimeString()}
                    </span>
                  </div>

                  <p className="text-sm font-medium text-gray-900 mb-2">
                    {item.summary}
                  </p>

                  {/* Salience Score Breakdown */}
                  <div className="flex items-center gap-3 text-xs text-gray-600">
                    <div className="flex items-center gap-1">
                      <span className="font-semibold">Salience:</span>
                      <span className={`font-bold ${getSalienceColor(item.salienceScore.score)}`}>
                        {(item.salienceScore.score * 100).toFixed(0)}%
                      </span>
                    </div>
                    <div className="h-3 w-px bg-gray-300"></div>
                    <div className="flex gap-2">
                      <span title="Recency">R: {(item.salienceScore.breakdown.recency * 100).toFixed(0)}</span>
                      <span title="Novelty">N: {(item.salienceScore.breakdown.novelty * 100).toFixed(0)}</span>
                      <span title="Impact">I: {(item.salienceScore.breakdown.impact * 100).toFixed(0)}</span>
                      <span title="Urgency">U: {(item.salienceScore.breakdown.urgency * 100).toFixed(0)}</span>
                    </div>
                  </div>

                  {/* Tags */}
                  {item.tags && item.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2">
                      {item.tags.slice(0, 3).map((tag: string) => (
                        <span
                          key={tag}
                          className="inline-block px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-700"
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Footer Stats */}
      {items && items.length > 0 && (
        <div className="mt-6 pt-4 border-t border-gray-200 flex justify-between text-sm text-gray-600">
          <span>{items.length} items in spotlight</span>
          <button
            onClick={() => refetch()}
            className="text-blue-600 hover:text-blue-700 font-medium"
          >
            Refresh
          </button>
        </div>
      )}
    </BaseCard>
  );
}

export default SpotlightRing;

