/**
 * Reflection Screen for Flashit Mobile
 * AI-powered insights, patterns, connections, and periodic reflections
 *
 * @doc.type screen
 * @doc.purpose Display AI reflection insights and pattern analysis
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
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useApi } from '../contexts/ApiContext';
import { formatDistanceToNow } from 'date-fns';

type ReflectionTab = 'insights' | 'patterns' | 'connections' | 'weekly' | 'monthly';

interface Insight {
  id: string;
  type: string;
  title: string;
  description: string;
  confidence: number;
  createdAt: string;
}

interface Pattern {
  id: string;
  name: string;
  description: string;
  frequency: number;
  trend: 'up' | 'down' | 'stable';
}

interface Connection {
  id: string;
  sourceTitle: string;
  targetTitle: string;
  relationship: string;
  strength: number;
}

interface PeriodicReflection {
  id: string;
  period: string;
  summary: string;
  highlights: string[];
  themes: string[];
  sentiment: number;
  createdAt: string;
}

export default function ReflectionScreen() {
  const { apiClient } = useApi();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<ReflectionTab>('insights');
  const [refreshing, setRefreshing] = useState(false);

  // Fetch all reflection data
  const { data: insights, isLoading: insightsLoading } = useQuery<Insight[]>({
    queryKey: ['reflection', 'insights'],
    queryFn: () => apiClient.search({ type: 'reflection_insights' }).then((r: any) => r.insights || []),
    placeholderData: [],
  });

  const { data: patterns, isLoading: patternsLoading } = useQuery<Pattern[]>({
    queryKey: ['reflection', 'patterns'],
    queryFn: async () => {
      // Placeholder — replace with actual /api/reflection/patterns
      return [];
    },
  });

  const { data: connections, isLoading: connectionsLoading } = useQuery<Connection[]>({
    queryKey: ['reflection', 'connections'],
    queryFn: async () => {
      // Placeholder — replace with actual /api/reflection/connections
      return [];
    },
  });

  const { data: weeklyReflection, isLoading: weeklyLoading } = useQuery<PeriodicReflection | null>({
    queryKey: ['reflection', 'weekly'],
    queryFn: async () => null,
  });

  const { data: monthlyReflection, isLoading: monthlyLoading } = useQuery<PeriodicReflection | null>({
    queryKey: ['reflection', 'monthly'],
    queryFn: async () => null,
  });

  const onRefresh = async () => {
    setRefreshing(true);
    await queryClient.invalidateQueries({ queryKey: ['reflection'] });
    setRefreshing(false);
  };

  const isLoading = {
    insights: insightsLoading,
    patterns: patternsLoading,
    connections: connectionsLoading,
    weekly: weeklyLoading,
    monthly: monthlyLoading,
  }[activeTab];

  const renderTab = (tab: ReflectionTab, label: string, icon: keyof typeof Ionicons.glyphMap) => (
    <TouchableOpacity
      key={tab}
      style={[styles.tab, activeTab === tab && styles.tabActive]}
      onPress={() => setActiveTab(tab)}
      accessibilityRole="tab"
      accessibilityState={{ selected: activeTab === tab }}
    >
      <Ionicons name={icon} size={14} color={activeTab === tab ? '#6366f1' : '#94a3b8'} />
      <Text style={[styles.tabText, activeTab === tab && styles.tabTextActive]}>{label}</Text>
    </TouchableOpacity>
  );

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      showsVerticalScrollIndicator={false}
    >
      {/* Tabs */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.tabScroll}
        contentContainerStyle={styles.tabRow}
      >
        {renderTab('insights', 'Insights', 'sparkles-outline')}
        {renderTab('patterns', 'Patterns', 'analytics-outline')}
        {renderTab('connections', 'Connections', 'git-network-outline')}
        {renderTab('weekly', 'Weekly', 'calendar-outline')}
        {renderTab('monthly', 'Monthly', 'today-outline')}
      </ScrollView>

      {isLoading ? (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color="#6366f1" />
          <Text style={styles.loadingText}>Analyzing your moments...</Text>
        </View>
      ) : (
        <View style={styles.content}>
          {/* Insights Tab */}
          {activeTab === 'insights' && (
            <>
              {(insights || []).length === 0 ? (
                <EmptyState
                  icon="sparkles-outline"
                  title="No insights yet"
                  subtitle="Capture more moments to unlock AI-powered insights about your patterns and growth"
                />
              ) : (
                (insights || []).map((insight) => (
                  <View key={insight.id} style={styles.insightCard}>
                    <View style={styles.insightHeader}>
                      <View style={[styles.insightBadge, { backgroundColor: getInsightColor(insight.type) }]}>
                        <Text style={styles.insightBadgeText}>{insight.type.replace('_', ' ')}</Text>
                      </View>
                      <Text style={styles.confidenceText}>
                        {Math.round(insight.confidence * 100)}%
                      </Text>
                    </View>
                    <Text style={styles.insightTitle}>{insight.title}</Text>
                    <Text style={styles.insightDesc}>{insight.description}</Text>
                    <Text style={styles.insightDate}>
                      {formatDistanceToNow(new Date(insight.createdAt), { addSuffix: true })}
                    </Text>
                  </View>
                ))
              )}
            </>
          )}

          {/* Patterns Tab */}
          {activeTab === 'patterns' && (
            <>
              {(patterns || []).length === 0 ? (
                <EmptyState
                  icon="analytics-outline"
                  title="No patterns detected"
                  subtitle="As you capture more moments, AI will identify recurring themes and behaviors"
                />
              ) : (
                (patterns || []).map((pattern) => (
                  <View key={pattern.id} style={styles.patternCard}>
                    <View style={styles.patternHeader}>
                      <Text style={styles.patternName}>{pattern.name}</Text>
                      <View style={styles.trendBadge}>
                        <Ionicons
                          name={pattern.trend === 'up' ? 'trending-up' : pattern.trend === 'down' ? 'trending-down' : 'remove-outline'}
                          size={14}
                          color={pattern.trend === 'up' ? '#10b981' : pattern.trend === 'down' ? '#ef4444' : '#64748b'}
                        />
                      </View>
                    </View>
                    <Text style={styles.patternDesc}>{pattern.description}</Text>
                    <Text style={styles.patternFreq}>Observed {pattern.frequency} times</Text>
                  </View>
                ))
              )}
            </>
          )}

          {/* Connections Tab */}
          {activeTab === 'connections' && (
            <>
              {(connections || []).length === 0 ? (
                <EmptyState
                  icon="git-network-outline"
                  title="No connections found"
                  subtitle="AI will discover relationships between your moments as your collection grows"
                />
              ) : (
                (connections || []).map((conn) => (
                  <View key={conn.id} style={styles.connectionCard}>
                    <View style={styles.connectionNodes}>
                      <Text style={styles.connectionNode}>{conn.sourceTitle}</Text>
                      <View style={styles.connectionLine}>
                        <Ionicons name="arrow-forward" size={14} color="#94a3b8" />
                      </View>
                      <Text style={styles.connectionNode}>{conn.targetTitle}</Text>
                    </View>
                    <Text style={styles.connectionRel}>{conn.relationship}</Text>
                    <View style={styles.strengthBar}>
                      <View style={[styles.strengthFill, { width: `${conn.strength * 100}%` }]} />
                    </View>
                  </View>
                ))
              )}
            </>
          )}

          {/* Weekly Tab */}
          {activeTab === 'weekly' && (
            <>
              {!weeklyReflection ? (
                <EmptyState
                  icon="calendar-outline"
                  title="No weekly reflection yet"
                  subtitle="Weekly reflections are generated every Sunday based on your captured moments"
                />
              ) : (
                <ReflectionCard reflection={weeklyReflection} />
              )}
            </>
          )}

          {/* Monthly Tab */}
          {activeTab === 'monthly' && (
            <>
              {!monthlyReflection ? (
                <EmptyState
                  icon="today-outline"
                  title="No monthly reflection yet"
                  subtitle="Monthly reflections are generated at the end of each month"
                />
              ) : (
                <ReflectionCard reflection={monthlyReflection} />
              )}
            </>
          )}
        </View>
      )}
    </ScrollView>
  );
}

function EmptyState({ icon, title, subtitle }: { icon: keyof typeof Ionicons.glyphMap; title: string; subtitle: string }) {
  return (
    <View style={styles.emptyState}>
      <Ionicons name={icon} size={48} color="#cbd5e1" />
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.emptySubtitle}>{subtitle}</Text>
    </View>
  );
}

function ReflectionCard({ reflection }: { reflection: PeriodicReflection }) {
  return (
    <View style={styles.reflectionCard}>
      <Text style={styles.reflectionPeriod}>{reflection.period}</Text>
      <Text style={styles.reflectionSummary}>{reflection.summary}</Text>

      {reflection.highlights.length > 0 && (
        <View style={styles.reflectionSection}>
          <Text style={styles.reflectionLabel}>Highlights</Text>
          {reflection.highlights.map((h, idx) => (
            <View key={idx} style={styles.highlightRow}>
              <Ionicons name="star" size={14} color="#f59e0b" />
              <Text style={styles.highlightText}>{h}</Text>
            </View>
          ))}
        </View>
      )}

      {reflection.themes.length > 0 && (
        <View style={styles.reflectionSection}>
          <Text style={styles.reflectionLabel}>Themes</Text>
          <View style={styles.themesRow}>
            {reflection.themes.map((t) => (
              <View key={t} style={styles.themeBadge}>
                <Text style={styles.themeText}>{t}</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      <View style={styles.sentimentRow}>
        <Text style={styles.sentimentLabel}>Overall Sentiment</Text>
        <Text style={[styles.sentimentValue, { color: reflection.sentiment >= 0 ? '#10b981' : '#ef4444' }]}>
          {reflection.sentiment >= 0 ? '+' : ''}{(reflection.sentiment * 100).toFixed(0)}%
        </Text>
      </View>
    </View>
  );
}

function getInsightColor(type: string): string {
  switch (type) {
    case 'vocabulary_growth': return '#10b981';
    case 'sentiment_shift': return '#f59e0b';
    case 'topic_evolution': return '#6366f1';
    case 'expression_change': return '#0ea5e9';
    default: return '#94a3b8';
  }
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
    paddingVertical: 64,
  },
  loadingText: {
    marginTop: 12,
    color: '#64748b',
    fontSize: 14,
  },
  tabScroll: {
    maxHeight: 48,
  },
  tabRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
    gap: 8,
  },
  tab: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: '#f1f5f9',
  },
  tabActive: {
    backgroundColor: '#ede9fe',
  },
  tabText: {
    fontSize: 12,
    fontWeight: '500',
    color: '#94a3b8',
  },
  tabTextActive: {
    color: '#6366f1',
    fontWeight: '600',
  },
  content: {
    padding: 16,
  },
  insightCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  insightHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  insightBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  insightBadgeText: {
    fontSize: 11,
    color: '#ffffff',
    fontWeight: '600',
    textTransform: 'capitalize',
  },
  confidenceText: {
    fontSize: 12,
    color: '#94a3b8',
    fontWeight: '600',
  },
  insightTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1e293b',
    marginBottom: 4,
  },
  insightDesc: {
    fontSize: 14,
    color: '#64748b',
    lineHeight: 20,
    marginBottom: 8,
  },
  insightDate: {
    fontSize: 12,
    color: '#94a3b8',
  },
  patternCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  patternHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  patternName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1e293b',
  },
  trendBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#f8fafc',
    alignItems: 'center',
    justifyContent: 'center',
  },
  patternDesc: {
    fontSize: 13,
    color: '#64748b',
    marginTop: 6,
    lineHeight: 18,
  },
  patternFreq: {
    fontSize: 12,
    color: '#94a3b8',
    marginTop: 8,
  },
  connectionCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  connectionNodes: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 8,
  },
  connectionNode: {
    flex: 1,
    fontSize: 13,
    fontWeight: '500',
    color: '#1e293b',
    backgroundColor: '#f1f5f9',
    padding: 8,
    borderRadius: 6,
    textAlign: 'center',
  },
  connectionLine: {
    width: 24,
    alignItems: 'center',
  },
  connectionRel: {
    fontSize: 12,
    color: '#6366f1',
    fontWeight: '500',
    marginBottom: 8,
  },
  strengthBar: {
    height: 4,
    backgroundColor: '#f1f5f9',
    borderRadius: 2,
    overflow: 'hidden',
  },
  strengthFill: {
    height: '100%',
    backgroundColor: '#6366f1',
    borderRadius: 2,
  },
  reflectionCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  reflectionPeriod: {
    fontSize: 13,
    color: '#6366f1',
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 8,
  },
  reflectionSummary: {
    fontSize: 15,
    color: '#334155',
    lineHeight: 22,
    marginBottom: 16,
  },
  reflectionSection: {
    marginBottom: 16,
  },
  reflectionLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1e293b',
    marginBottom: 8,
  },
  highlightRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 6,
  },
  highlightText: {
    flex: 1,
    fontSize: 14,
    color: '#334155',
  },
  themesRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  themeBadge: {
    backgroundColor: '#ede9fe',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  themeText: {
    fontSize: 12,
    color: '#6366f1',
    fontWeight: '500',
  },
  sentimentRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
  },
  sentimentLabel: {
    fontSize: 13,
    color: '#64748b',
  },
  sentimentValue: {
    fontSize: 16,
    fontWeight: '700',
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 48,
  },
  emptyTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: '#334155',
    marginTop: 16,
  },
  emptySubtitle: {
    fontSize: 14,
    color: '#94a3b8',
    marginTop: 8,
    textAlign: 'center',
    paddingHorizontal: 24,
    lineHeight: 20,
  },
});
