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

const SPHERE_COLORS = ['#0ea5e9', '#6366f1', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6'];

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

  // Compute analytics from available data
  const overview: OverviewStats = React.useMemo(() => {
    const moments = momentsData?.moments || [];
    const now = new Date();
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

    const momentsThisWeek = moments.filter(
      (m: { createdAt: string }) => new Date(m.createdAt) >= weekAgo
    ).length;
    const momentsThisMonth = moments.filter(
      (m: { createdAt: string }) => new Date(m.createdAt) >= monthAgo
    ).length;

    // Count spheres
    const sphereCounts: Record<string, number> = {};
    moments.forEach((m: { sphereId?: string }) => {
      if (m.sphereId) {
        sphereCounts[m.sphereId] = (sphereCounts[m.sphereId] || 0) + 1;
      }
    });
    const topSphereId = Object.entries(sphereCounts).sort(([, a], [, b]) => b - a)[0]?.[0];
    const spheres = Array.isArray(spheresData) ? spheresData : (spheresData as any)?.spheres || [];
    const topSphere = spheres.find((s: { id: string }) => s.id === topSphereId);

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
  }, [momentsData, spheresData]);

  const patterns: PatternData[] = React.useMemo(() => {
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const counts = new Array(7).fill(0);
    const moments = momentsData?.moments || [];
    moments.forEach((m: { createdAt: string }) => {
      const dayIndex = new Date(m.createdAt).getDay();
      counts[dayIndex]++;
    });
    return days.map((day, idx) => ({ dayOfWeek: day, count: counts[idx] }));
  }, [momentsData]);

  const sphereBreakdown: SphereBreakdown[] = React.useMemo(() => {
    const moments = momentsData?.moments || [];
    const spheres = Array.isArray(spheresData) ? spheresData : (spheresData as any)?.spheres || [];
    const sphereCounts: Record<string, number> = {};
    moments.forEach((m: { sphereId?: string }) => {
      if (m.sphereId) {
        sphereCounts[m.sphereId] = (sphereCounts[m.sphereId] || 0) + 1;
      }
    });
    const total = moments.length || 1;
    return Object.entries(sphereCounts)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 6)
      .map(([id, count], idx) => ({
        name: spheres.find((s: { id: string }) => s.id === id)?.name || 'Unknown',
        count,
        percentage: Math.round((count / total) * 100),
        color: SPHERE_COLORS[idx % SPHERE_COLORS.length],
      }));
  }, [momentsData, spheresData]);

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" color="#0ea5e9" />
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
      <Ionicons name={icon} size={16} color={activeTab === tab ? '#0ea5e9' : '#94a3b8'} />
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
            <StatCard label="Total Moments" value={overview.totalMoments.toString()} icon="document-text-outline" color="#0ea5e9" />
            <StatCard label="This Week" value={overview.momentsThisWeek.toString()} icon="calendar-outline" color="#6366f1" />
            <StatCard label="This Month" value={overview.momentsThisMonth.toString()} icon="trending-up-outline" color="#10b981" />
            <StatCard label="Daily Avg" value={overview.avgMomentsPerDay.toString()} icon="speedometer-outline" color="#f59e0b" />
            <StatCard label="Streak" value={`${overview.streakDays}d`} icon="flame-outline" color="#ef4444" />
            <StatCard label="Spheres" value={overview.totalSpheres.toString()} icon="grid-outline" color="#8b5cf6" />
          </View>
          {overview.topSphereName && (
            <View style={styles.infoCard}>
              <Ionicons name="trophy-outline" size={20} color="#f59e0b" />
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
            <Ionicons name="information-circle-outline" size={20} color="#0ea5e9" />
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

function computeStreak(moments: Array<{ createdAt: string }>): number {
  if (moments.length === 0) return 0;
  const dates = new Set(
    moments.map((m) => new Date(m.createdAt).toISOString().split('T')[0])
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
    backgroundColor: '#f8fafc',
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    color: '#64748b',
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
    backgroundColor: '#f1f5f9',
  },
  tabActive: {
    backgroundColor: '#e0f2fe',
  },
  tabLabel: {
    fontSize: 12,
    fontWeight: '500',
    color: '#94a3b8',
  },
  tabLabelActive: {
    color: '#0ea5e9',
    fontWeight: '600',
  },
  section: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1e293b',
    marginBottom: 16,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  statCard: {
    width: (screenWidth - 56) / 3,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  statValue: {
    fontSize: 22,
    fontWeight: '700',
    color: '#1e293b',
    marginTop: 8,
  },
  statLabel: {
    fontSize: 11,
    color: '#94a3b8',
    marginTop: 4,
    textAlign: 'center',
  },
  infoCard: {
    flexDirection: 'row',
    backgroundColor: '#f0f9ff',
    borderRadius: 12,
    padding: 14,
    gap: 10,
    alignItems: 'center',
    marginTop: 16,
  },
  infoText: {
    flex: 1,
    fontSize: 14,
    color: '#334155',
    lineHeight: 20,
  },
  infoBold: {
    fontWeight: '700',
    color: '#0ea5e9',
  },
  linkButton: {
    marginTop: 12,
    padding: 14,
    backgroundColor: '#0ea5e9',
    borderRadius: 10,
    alignItems: 'center',
  },
  linkButtonText: {
    color: '#ffffff',
    fontWeight: '600',
    fontSize: 15,
  },
  barsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'flex-end',
    height: 160,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  barColumn: {
    alignItems: 'center',
    flex: 1,
  },
  barCount: {
    fontSize: 11,
    color: '#64748b',
    marginBottom: 4,
    fontWeight: '600',
  },
  bar: {
    width: 28,
    backgroundColor: '#0ea5e9',
    borderRadius: 4,
  },
  barLabel: {
    fontSize: 11,
    color: '#94a3b8',
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
    color: '#334155',
    fontWeight: '500',
    width: 80,
  },
  sphereBarContainer: {
    flex: 1,
    height: 8,
    backgroundColor: '#f1f5f9',
    borderRadius: 4,
    overflow: 'hidden',
  },
  sphereBar: {
    height: '100%',
    borderRadius: 4,
  },
  sphereCount: {
    fontSize: 13,
    color: '#334155',
    fontWeight: '600',
    width: 30,
    textAlign: 'right',
  },
  spherePct: {
    fontSize: 12,
    color: '#94a3b8',
    width: 36,
    textAlign: 'right',
  },
  emptyText: {
    fontSize: 14,
    color: '#94a3b8',
    textAlign: 'center',
    marginTop: 24,
  },
});
