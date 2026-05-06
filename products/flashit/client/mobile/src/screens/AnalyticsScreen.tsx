/**
 * Analytics Screen for Flashit Mobile
 * Displays capture patterns, frequency, and usage analytics
 *
 * @doc.type screen
 * @doc.purpose Show user analytics and capture patterns
 * @doc.layer product
 * @doc.pattern MobileScreen
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Dimensions,
  ActivityIndicator,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { useApi } from '../contexts/ApiContext';
import { flashitMobileColors } from '@/styles/designTokens';

const { width: screenWidth } = Dimensions.get('window');

type AnalyticsTab = 'overview' | 'language' | 'patterns' | 'spheres';

interface OverviewStats {
  totalMoments: number;
  totalSpheres: number;
  momentsThisWeek: number;
  momentsThisMonth: number;
  avgMomentsPerDay: number;
  streakDays: number;
  topSphereId?: string;
  topSphereName?: string;
}

interface PatternData {
  dayOfWeek: string;
  count: number;
}

interface SphereBreakdown {
  name: string;
  count: number;
  percentage: number;
  color: string;
}

const SPHERE_COLORS = [flashitMobileColors.sky500, flashitMobileColors.indigo500, flashitMobileColors.yellow500, flashitMobileColors.green500, flashitMobileColors.red500, flashitMobileColors.purple500];

export default function AnalyticsScreen() {
  const { apiClient } = useApi();
  const [activeTab, setActiveTab] = useState<AnalyticsTab>('overview');

  const { data: spheresData, isLoading: spheresLoading } = useQuery({
    queryKey: ['spheres'],
    queryFn: () => apiClient.getSpheres(),
  });

  const { data: momentsData, isLoading: momentsLoading } = useQuery({
    queryKey: ['moments', 'analytics'],
    queryFn: () => apiClient.getMoments({ limit: 200 }),
  });

  const isLoading = spheresLoading || momentsLoading;
  const moments = momentsData?.moments || [];
  const spheres = spheresData || [];

  // Compute analytics from available data
  const overview: OverviewStats = React.useMemo(() => {
    const now = new Date();
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

    const momentsThisWeek = moments.filter(
      (m) => new Date(m.capturedAt) >= weekAgo
    ).length;
    const momentsThisMonth = moments.filter(
      (m) => new Date(m.capturedAt) >= monthAgo
    ).length;

    // Count spheres
    const sphereCounts: Record<string, number> = {};
    moments.forEach((m) => {
      if (m.sphereId) {
        sphereCounts[m.sphereId] = (sphereCounts[m.sphereId] || 0) + 1;
      }
    });
    const topSphereId = Object.entries(sphereCounts).sort(([, a], [, b]) => b - a)[0]?.[0];
    const topSphere = spheres.find((s) => s.id === topSphereId);

    return {
      totalMoments: momentsData?.totalCount || moments.length,
      totalSpheres: spheres.length,
      momentsThisWeek,
      momentsThisMonth,
      avgMomentsPerDay: momentsThisMonth > 0 ? Math.round((momentsThisMonth / 30) * 10) / 10 : 0,
      streakDays: computeStreak(moments),
      topSphereId,
      topSphereName: topSphere?.name,
    };
  }, [moments, momentsData?.totalCount, spheres]);

  const patterns: PatternData[] = React.useMemo(() => {
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const counts = new Array(7).fill(0);
    moments.forEach((m) => {
      const dayIndex = new Date(m.capturedAt).getDay();
      counts[dayIndex]++;
    });
    return days.map((day, idx) => ({ dayOfWeek: day, count: counts[idx] }));
  }, [moments]);

  const sphereBreakdown: SphereBreakdown[] = React.useMemo(() => {
    const sphereCounts: Record<string, number> = {};
    moments.forEach((m) => {
      if (m.sphereId) {
        sphereCounts[m.sphereId] = (sphereCounts[m.sphereId] || 0) + 1;
      }
    });
    const total = moments.length || 1;
    return Object.entries(sphereCounts)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 6)
      .map(([id, count], idx) => ({
        name: spheres.find((s) => s.id === id)?.name || 'Unknown',
        count,
        percentage: Math.round((count / total) * 100),
        color: SPHERE_COLORS[idx % SPHERE_COLORS.length],
      }));
  }, [moments, spheres]);

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" color={flashitMobileColors.sky500} />
        <Text style={styles.loadingText}>Loading analytics...</Text>
      </View>
    );
  }

  const renderTab = (tab: AnalyticsTab, label: string, icon: keyof typeof Ionicons.glyphMap) => (
    <TouchableOpacity
      key={tab}
      style={[styles.tab, activeTab === tab && styles.tabActive]}
      onPress={() => setActiveTab(tab)}
      accessibilityRole="tab"
      accessibilityState={{ selected: activeTab === tab }}
    >
      <Ionicons name={icon} size={16} color={activeTab === tab ? flashitMobileColors.sky500 : flashitMobileColors.slate400} />
      <Text style={[styles.tabLabel, activeTab === tab && styles.tabLabelActive]}>{label}</Text>
    </TouchableOpacity>
  );

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      {/* Tabs */}
      <View style={styles.tabRow}>
        {renderTab('overview', 'Overview', 'bar-chart-outline')}
        {renderTab('language', 'Language', 'language-outline')}
        {renderTab('patterns', 'Patterns', 'analytics-outline')}
        {renderTab('spheres', 'Spheres', 'grid-outline')}
      </View>

      {/* Overview Tab */}
      {activeTab === 'overview' && (
        <View style={styles.section}>
          <View style={styles.statsGrid}>
            <StatCard label="Total Moments" value={overview.totalMoments.toString()} icon="document-text-outline" color={flashitMobileColors.sky500} />
            <StatCard label="This Week" value={overview.momentsThisWeek.toString()} icon="calendar-outline" color={flashitMobileColors.indigo500} />
            <StatCard label="This Month" value={overview.momentsThisMonth.toString()} icon="trending-up-outline" color={flashitMobileColors.green500} />
            <StatCard label="Daily Avg" value={overview.avgMomentsPerDay.toString()} icon="speedometer-outline" color={flashitMobileColors.yellow500} />
            <StatCard label="Streak" value={`${overview.streakDays}d`} icon="flame-outline" color={flashitMobileColors.red500} />
            <StatCard label="Spheres" value={overview.totalSpheres.toString()} icon="grid-outline" color={flashitMobileColors.purple500} />
          </View>
          {overview.topSphereName && (
            <View style={styles.infoCard}>
              <Ionicons name="trophy-outline" size={20} color={flashitMobileColors.yellow500} />
              <Text style={styles.infoText}>
                Most active sphere: <Text style={styles.infoBold}>{overview.topSphereName}</Text>
              </Text>
            </View>
          )}
        </View>
      )}

      {/* Language Tab */}
      {activeTab === 'language' && (
        <View style={styles.section}>
          <View style={styles.infoCard}>
            <Ionicons name="information-circle-outline" size={20} color={flashitMobileColors.sky500} />
            <Text style={styles.infoText}>
              Language analytics are available on the Language Insights screen for detailed vocabulary
              and sentiment analysis.
            </Text>
          </View>
          <TouchableOpacity style={styles.linkButton}>
            <Text style={styles.linkButtonText}>Open Language Insights →</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Patterns Tab */}
      {activeTab === 'patterns' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Capture by Day of Week</Text>
          <View style={styles.barsContainer}>
            {patterns.map((p) => {
              const maxCount = Math.max(...patterns.map((x) => x.count), 1);
              const height = (p.count / maxCount) * 120;
              return (
                <View key={p.dayOfWeek} style={styles.barColumn}>
                  <Text style={styles.barCount}>{p.count}</Text>
                  <View style={[styles.bar, { height: Math.max(height, 4) }]} />
                  <Text style={styles.barLabel}>{p.dayOfWeek}</Text>
                </View>
              );
            })}
          </View>
        </View>
      )}

      {/* Spheres Tab */}
      {activeTab === 'spheres' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Sphere Breakdown</Text>
          {sphereBreakdown.length === 0 ? (
            <Text style={styles.emptyText}>No sphere data available yet</Text>
          ) : (
            sphereBreakdown.map((s) => (
              <View key={s.name} style={styles.sphereRow}>
                <View style={[styles.sphereDot, { backgroundColor: s.color }]} />
                <Text style={styles.sphereName}>{s.name}</Text>
                <View style={styles.sphereBarContainer}>
                  <View
                    style={[styles.sphereBar, { width: `${s.percentage}%`, backgroundColor: s.color }]}
                  />
                </View>
                <Text style={styles.sphereCount}>{s.count}</Text>
                <Text style={styles.spherePct}>{s.percentage}%</Text>
              </View>
            ))
          )}
        </View>
      )}
    </ScrollView>
  );
}

function StatCard({
  label,
  value,
  icon,
  color,
}: {
  label: string;
  value: string;
  icon: keyof typeof Ionicons.glyphMap;
  color: string;
}) {
  return (
    <View style={styles.statCard} accessibilityLabel={`${label}: ${value}`}>
      <Ionicons name={icon} size={24} color={color} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function computeStreak(moments: Array<{ capturedAt: string }>): number {
  if (moments.length === 0) return 0;
  const dates = new Set(
    moments.map((m) => new Date(m.capturedAt).toISOString().split('T')[0])
  );
  let streak = 0;
  const today = new Date();
  for (let i = 0; i < 365; i++) {
    const date = new Date(today.getTime() - i * 24 * 60 * 60 * 1000);
    const key = date.toISOString().split('T')[0];
    if (dates.has(key)) {
      streak++;
    } else if (i > 0) {
      break;
    }
  }
  return streak;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: flashitMobileColors.slate50,
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    color: flashitMobileColors.slate500,
    fontSize: 14,
  },
  tabRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
    gap: 8,
  },
  tab: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: flashitMobileColors.slate100,
  },
  tabActive: {
    backgroundColor: flashitMobileColors.sky100,
  },
  tabLabel: {
    fontSize: 12,
    fontWeight: '500',
    color: flashitMobileColors.slate400,
  },
  tabLabelActive: {
    color: flashitMobileColors.sky500,
    fontWeight: '600',
  },
  section: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: flashitMobileColors.slate800,
    marginBottom: 16,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  statCard: {
    width: (screenWidth - 56) / 3,
    backgroundColor: flashitMobileColors.whiteAlt,
    borderRadius: 12,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: flashitMobileColors.slate200,
  },
  statValue: {
    fontSize: 22,
    fontWeight: '700',
    color: flashitMobileColors.slate800,
    marginTop: 8,
  },
  statLabel: {
    fontSize: 11,
    color: flashitMobileColors.slate400,
    marginTop: 4,
    textAlign: 'center',
  },
  infoCard: {
    flexDirection: 'row',
    backgroundColor: flashitMobileColors.sky50,
    borderRadius: 12,
    padding: 14,
    gap: 10,
    alignItems: 'center',
    marginTop: 16,
  },
  infoText: {
    flex: 1,
    fontSize: 14,
    color: flashitMobileColors.slate700,
    lineHeight: 20,
  },
  infoBold: {
    fontWeight: '700',
    color: flashitMobileColors.sky500,
  },
  linkButton: {
    marginTop: 12,
    padding: 14,
    backgroundColor: flashitMobileColors.sky500,
    borderRadius: 10,
    alignItems: 'center',
  },
  linkButtonText: {
    color: flashitMobileColors.whiteAlt,
    fontWeight: '600',
    fontSize: 15,
  },
  barsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'flex-end',
    height: 160,
    backgroundColor: flashitMobileColors.whiteAlt,
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: flashitMobileColors.slate200,
  },
  barColumn: {
    alignItems: 'center',
    flex: 1,
  },
  barCount: {
    fontSize: 11,
    color: flashitMobileColors.slate500,
    marginBottom: 4,
    fontWeight: '600',
  },
  bar: {
    width: 28,
    backgroundColor: flashitMobileColors.sky500,
    borderRadius: 4,
  },
  barLabel: {
    fontSize: 11,
    color: flashitMobileColors.slate400,
    marginTop: 6,
  },
  sphereRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    gap: 8,
  },
  sphereDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  sphereName: {
    fontSize: 14,
    color: flashitMobileColors.slate700,
    fontWeight: '500',
    width: 80,
  },
  sphereBarContainer: {
    flex: 1,
    height: 8,
    backgroundColor: flashitMobileColors.slate100,
    borderRadius: 4,
    overflow: 'hidden',
  },
  sphereBar: {
    height: '100%',
    borderRadius: 4,
  },
  sphereCount: {
    fontSize: 13,
    color: flashitMobileColors.slate700,
    fontWeight: '600',
    width: 30,
    textAlign: 'right',
  },
  spherePct: {
    fontSize: 12,
    color: flashitMobileColors.slate400,
    width: 36,
    textAlign: 'right',
  },
  emptyText: {
    fontSize: 14,
    color: flashitMobileColors.slate400,
    textAlign: 'center',
    marginTop: 24,
  },
});
