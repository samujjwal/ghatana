/**
 * Activity Feed Component (Mobile)
 * Display user activity timeline
 *
 * @doc.type component
 * @doc.purpose Activity feed and timeline visualization
 * @doc.layer product
 * @doc.pattern ActivityFeed
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
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
  onActivityPress?: (activity: Activity) => void;
}

export default function ActivityFeed({
  userId,
  showStats = true,
  onActivityPress,
}: ActivityFeedProps) {
  const [selectedType, setSelectedType] = useState<ActivityType | 'all'>('all');

  // Fetch activities
  const {
    data: activityGroups,
    isLoading: loadingActivities,
    refetch: refetchActivities,
  } = useQuery({
    queryKey: ['activities', userId],
    queryFn: () => fetchActivities(userId),
  });

  // Fetch stats
  const { data: stats, isLoading: loadingStats } = useQuery({
    queryKey: ['activity-stats', userId],
    queryFn: () => fetchActivityStats(userId),
    enabled: showStats,
  });

  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = async () => {
    setRefreshing(true);
    await refetchActivities();
    setRefreshing(false);
  };

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
        month: 'short',
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
      <View style={styles.centered}>
        <ActivityIndicator size="large" color="#3b82f6" />
      </View>
    );
  }

  const filteredGroups = activityGroups ? filterActivities(activityGroups) : [];

  return (
    <ScrollView
      style={styles.container}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
      }
    >
      {/* Stats Section */}
      {showStats && stats && (
        <View style={styles.statsContainer}>
          <View style={styles.statsGrid}>
            <View style={[styles.statCard, styles.statGreen]}>
              <Text style={styles.statNumber}>{stats.momentsCreated}</Text>
              <Text style={styles.statLabel}>Moments</Text>
            </View>
            <View style={[styles.statCard, styles.statBlue]}>
              <Text style={styles.statNumber}>{stats.momentsUpdated}</Text>
              <Text style={styles.statLabel}>Updated</Text>
            </View>
            <View style={[styles.statCard, styles.statPurple]}>
              <Text style={styles.statNumber}>{stats.templatesUsed}</Text>
              <Text style={styles.statLabel}>Templates</Text>
            </View>
            <View style={[styles.statCard, styles.statOrange]}>
              <Text style={styles.statNumber}>{stats.searchesPerformed}</Text>
              <Text style={styles.statLabel}>Searches</Text>
            </View>
          </View>
        </View>
      )}

      {/* Filter Tabs */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.filtersContainer}
        contentContainerStyle={styles.filtersContent}
      >
        <TouchableOpacity
          style={[styles.filterTab, selectedType === 'all' && styles.filterTabActive]}
          onPress={() => setSelectedType('all')}
        >
          <Text style={[styles.filterText, selectedType === 'all' && styles.filterTextActive]}>
            All
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.filterTab,
            selectedType === 'moment_created' && styles.filterTabActive,
          ]}
          onPress={() => setSelectedType('moment_created')}
        >
          <Text
            style={[
              styles.filterText,
              selectedType === 'moment_created' && styles.filterTextActive,
            ]}
          >
            ✨ Created
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.filterTab,
            selectedType === 'moment_updated' && styles.filterTabActive,
          ]}
          onPress={() => setSelectedType('moment_updated')}
        >
          <Text
            style={[
              styles.filterText,
              selectedType === 'moment_updated' && styles.filterTextActive,
            ]}
          >
            ✏️ Updated
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.filterTab,
            selectedType === 'template_used' && styles.filterTabActive,
          ]}
          onPress={() => setSelectedType('template_used')}
        >
          <Text
            style={[
              styles.filterText,
              selectedType === 'template_used' && styles.filterTextActive,
            ]}
          >
            📝 Templates
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.filterTab,
            selectedType === 'collaboration_started' && styles.filterTabActive,
          ]}
          onPress={() => setSelectedType('collaboration_started')}
        >
          <Text
            style={[
              styles.filterText,
              selectedType === 'collaboration_started' && styles.filterTextActive,
            ]}
          >
            🤝 Collab
          </Text>
        </TouchableOpacity>
      </ScrollView>

      {/* Activity Timeline */}
      <View style={styles.timelineContainer}>
        {filteredGroups.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyIcon}>📭</Text>
            <Text style={styles.emptyText}>No activities yet</Text>
          </View>
        ) : (
          filteredGroups.map((group) => (
            <View key={group.date} style={styles.groupContainer}>
              {/* Date Header */}
              <View style={styles.dateHeader}>
                <Text style={styles.dateText}>{formatDate(group.date)}</Text>
                <View style={styles.dateLine} />
                <Text style={styles.dateCount}>{group.count}</Text>
              </View>

              {/* Activities */}
              {group.activities.map((activity) => (
                <TouchableOpacity
                  key={activity.id}
                  style={styles.activityCard}
                  onPress={() => onActivityPress?.(activity)}
                  activeOpacity={0.7}
                >
                  {/* Icon */}
                  <View
                    style={[
                      styles.activityIcon,
                      { backgroundColor: `${activity.color}30` },
                    ]}
                  >
                    <Text style={styles.activityIconText}>{activity.icon}</Text>
                  </View>

                  {/* Content */}
                  <View style={styles.activityContent}>
                    <Text style={styles.activityDescription}>
                      {activity.description}
                    </Text>
                    <Text style={styles.activityTime}>
                      {formatTime(activity.timestamp)}
                    </Text>
                  </View>

                  {/* Type Badge */}
                  <View
                    style={[
                      styles.activityBadge,
                      { backgroundColor: `${activity.color}20` },
                    ]}
                  >
                    <Text
                      style={[styles.activityBadgeText, { color: activity.color }]}
                    >
                      {activity.type.split('_')[0]}
                    </Text>
                  </View>
                </TouchableOpacity>
              ))}
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}

// ============================================================================
// Styles
// ============================================================================

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f9fafb',
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  statsContainer: {
    padding: 16,
  },
  statsGrid: {
    flexDirection: 'row',
    gap: 12,
  },
  statCard: {
    flex: 1,
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  statGreen: {
    backgroundColor: '#d1fae5',
  },
  statBlue: {
    backgroundColor: '#dbeafe',
  },
  statPurple: {
    backgroundColor: '#ede9fe',
  },
  statOrange: {
    backgroundColor: '#fed7aa',
  },
  statNumber: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 12,
    color: '#6b7280',
  },
  filtersContainer: {
    maxHeight: 50,
    marginBottom: 16,
  },
  filtersContent: {
    paddingHorizontal: 16,
    gap: 8,
  },
  filterTab: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#e5e7eb',
  },
  filterTabActive: {
    backgroundColor: '#3b82f6',
  },
  filterText: {
    fontSize: 14,
    color: '#374151',
  },
  filterTextActive: {
    color: '#fff',
    fontWeight: '600',
  },
  timelineContainer: {
    padding: 16,
  },
  emptyState: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  emptyText: {
    fontSize: 18,
    color: '#9ca3af',
  },
  groupContainer: {
    marginBottom: 32,
  },
  dateHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  dateText: {
    fontSize: 16,
    fontWeight: '600',
    marginRight: 12,
  },
  dateLine: {
    flex: 1,
    height: 1,
    backgroundColor: '#e5e7eb',
  },
  dateCount: {
    fontSize: 14,
    color: '#6b7280',
    marginLeft: 12,
  },
  activityCard: {
    flexDirection: 'row',
    padding: 16,
    marginBottom: 12,
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  activityIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  activityIconText: {
    fontSize: 24,
  },
  activityContent: {
    flex: 1,
  },
  activityDescription: {
    fontSize: 15,
    color: '#111827',
    marginBottom: 4,
  },
  activityTime: {
    fontSize: 13,
    color: '#6b7280',
  },
  activityBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    alignSelf: 'flex-start',
  },
  activityBadgeText: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'capitalize',
  },
});
