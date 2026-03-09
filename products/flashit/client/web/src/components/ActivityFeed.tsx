/**
 * Activity Feed Component (Web)
 * Display user activity timeline
 *
 * @doc.type component
 * @doc.purpose Activity feed and timeline visualization
 * @doc.layer product
 * @doc.pattern ActivityFeed
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

type ActivityType =
  | 'moment_created'
  | 'moment_updated'
  | 'moment_deleted'
  | 'moment_shared'
  | 'tag_added'
  | 'template_used'
  | 'search_performed'
  | 'summary_generated'
  | 'collaboration_started'
  | 'comment_added';

interface Activity {
  id: string;
  userId: string;
  type: ActivityType;
  timestamp: Date;
  metadata: Record<string, unknown>;
  description: string;
  icon?: string;
  color?: string;
}

interface ActivityGroup {
  date: string;
  activities: Activity[];
  count: number;
}

interface ActivityStats {
  totalActivities: number;
  momentsCreated: number;
  momentsUpdated: number;
  templatesUsed: number;
  searchesPerformed: number;
  summariesGenerated: number;
  collaborations: number;
}

// ============================================================================
// API Functions
// ============================================================================

async function fetchActivities(userId: string): Promise<ActivityGroup[]> {
  const response = await fetch(`/api/activities/${userId}`);
  const data = await response.json();
  if (!data.success) throw new Error(data.error);
  // Convert timestamp strings to Date objects
  return data.data.map((group: ActivityGroup) => ({
    ...group,
    activities: group.activities.map((activity) => ({
      ...activity,
      timestamp: new Date(activity.timestamp),
    })),
  }));
}

async function fetchActivityStats(userId: string): Promise<ActivityStats> {
  const response = await fetch(`/api/activities/${userId}/stats`);
  const data = await response.json();
  if (!data.success) throw new Error(data.error);
  return data.data;
}

// ============================================================================
// Component
// ============================================================================

interface ActivityFeedProps {
  userId: string;
  showStats?: boolean;
}

export default function ActivityFeed({ userId, showStats = true }: ActivityFeedProps) {
  const [selectedType, setSelectedType] = useState<ActivityType | 'all'>('all');

  // Fetch activities
  const { data: activityGroups, isLoading: loadingActivities } = useQuery({
    queryKey: ['activities', userId],
    queryFn: () => fetchActivities(userId),
  });

  // Fetch stats
  const { data: stats, isLoading: loadingStats } = useQuery({
    queryKey: ['activity-stats', userId],
    queryFn: () => fetchActivityStats(userId),
    enabled: showStats,
  });

  const formatDate = (dateStr: string): string => {
    const date = new Date(dateStr);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    if (date.toDateString() === today.toDateString()) {
      return 'Today';
    } else if (date.toDateString() === yesterday.toDateString()) {
      return 'Yesterday';
    } else {
      return date.toLocaleDateString('en-US', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      });
    }
  };

  const formatTime = (date: Date): string => {
    return date.toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
    });
  };

  const filterActivities = (groups: ActivityGroup[]): ActivityGroup[] => {
    if (selectedType === 'all') return groups;

    return groups
      .map((group) => ({
        ...group,
        activities: group.activities.filter((a) => a.type === selectedType),
        count: group.activities.filter((a) => a.type === selectedType).length,
      }))
      .filter((group) => group.count > 0);
  };

  if (loadingActivities || loadingStats) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500" />
      </div>
    );
  }

  const filteredGroups = activityGroups ? filterActivities(activityGroups) : [];

  return (
    <div className="max-w-4xl mx-auto p-6">
      {/* Stats Section */}
      {showStats && stats && (
        <div className="mb-8 grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <div className="text-2xl font-bold text-green-700">{stats.momentsCreated}</div>
            <div className="text-sm text-green-600">Moments Created</div>
          </div>
          <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
            <div className="text-2xl font-bold text-blue-700">{stats.momentsUpdated}</div>
            <div className="text-sm text-blue-600">Moments Updated</div>
          </div>
          <div className="bg-purple-50 p-4 rounded-lg border border-purple-200">
            <div className="text-2xl font-bold text-purple-700">{stats.templatesUsed}</div>
            <div className="text-sm text-purple-600">Templates Used</div>
          </div>
          <div className="bg-orange-50 p-4 rounded-lg border border-orange-200">
            <div className="text-2xl font-bold text-orange-700">{stats.searchesPerformed}</div>
            <div className="text-sm text-orange-600">Searches</div>
          </div>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="mb-6 flex flex-wrap gap-2">
        <button
          className={`px-4 py-2 rounded-full ${
            selectedType === 'all'
              ? 'bg-blue-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
          onClick={() => setSelectedType('all')}
        >
          All
        </button>
        <button
          className={`px-4 py-2 rounded-full ${
            selectedType === 'moment_created'
              ? 'bg-green-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
          onClick={() => setSelectedType('moment_created')}
        >
          ✨ Created
        </button>
        <button
          className={`px-4 py-2 rounded-full ${
            selectedType === 'moment_updated'
              ? 'bg-blue-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
          onClick={() => setSelectedType('moment_updated')}
        >
          ✏️ Updated
        </button>
        <button
          className={`px-4 py-2 rounded-full ${
            selectedType === 'template_used'
              ? 'bg-pink-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
          onClick={() => setSelectedType('template_used')}
        >
          📝 Templates
        </button>
        <button
          className={`px-4 py-2 rounded-full ${
            selectedType === 'collaboration_started'
              ? 'bg-orange-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
          onClick={() => setSelectedType('collaboration_started')}
        >
          🤝 Collaboration
        </button>
      </div>

      {/* Activity Timeline */}
      <div className="space-y-8">
        {filteredGroups.length === 0 ? (
          <div className="text-center py-12 text-gray-400">
            <div className="text-6xl mb-4">📭</div>
            <div className="text-xl">No activities yet</div>
          </div>
        ) : (
          filteredGroups.map((group) => (
            <div key={group.date} className="relative">
              {/* Date Header */}
              <div className="sticky top-0 bg-white z-10 pb-2 mb-4">
                <div className="flex items-center gap-3">
                  <div className="text-lg font-semibold">{formatDate(group.date)}</div>
                  <div className="flex-1 h-px bg-gray-200" />
                  <div className="text-sm text-gray-500">{group.count} activities</div>
                </div>
              </div>

              {/* Activities */}
              <div className="space-y-4">
                {group.activities.map((activity) => (
                  <div
                    key={activity.id}
                    className="flex gap-4 p-4 bg-white border rounded-lg hover:shadow-md transition-shadow"
                  >
                    {/* Icon */}
                    <div
                      className="flex-shrink-0 w-12 h-12 rounded-full flex items-center justify-center text-2xl"
                      style={{ backgroundColor: `${activity.color}20` }}
                    >
                      {activity.icon}
                    </div>

                    {/* Content */}
                    <div className="flex-1">
                      <div className="text-gray-900">{activity.description}</div>
                      <div className="text-sm text-gray-500 mt-1">
                        {formatTime(activity.timestamp)}
                      </div>
                      {/* Metadata Preview */}
                      {Object.keys(activity.metadata).length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-2">
                          {Object.entries(activity.metadata).map(([key, value]) => (
                            <span
                              key={key}
                              className="inline-block px-2 py-1 text-xs bg-gray-100 rounded"
                            >
                              {key}: {String(value)}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Type Badge */}
                    <div
                      className="flex-shrink-0 px-3 py-1 rounded-full text-xs font-medium"
                      style={{
                        backgroundColor: `${activity.color}20`,
                        color: activity.color,
                      }}
                    >
                      {activity.type.replace(/_/g, ' ')}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
