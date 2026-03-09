/**
 * Memory Expansion Screen for Flashit Mobile
 * Request and view AI-powered memory expansions of moments
 *
 * @doc.type screen
 * @doc.purpose Display and request AI memory expansions
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
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useApi } from '../contexts/ApiContext';
import { formatDistanceToNow } from 'date-fns';

interface MemoryExpansion {
  id: string;
  momentId: string;
  momentContent: string;
  expandedContent: string;
  type: 'deep_analysis' | 'context_enrichment' | 'connection_mapping' | 'future_implications';
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  completedAt?: string;
}

interface Moment {
  id: string;
  content: string;
  createdAt: string;
  sphereName?: string;
}

type ExpansionType = MemoryExpansion['type'];

const EXPANSION_TYPES: Array<{ type: ExpansionType; label: string; icon: keyof typeof Ionicons.glyphMap; description: string }> = [
  { type: 'deep_analysis', label: 'Deep Analysis', icon: 'telescope-outline', description: 'Discover hidden insights and deeper meaning' },
  { type: 'context_enrichment', label: 'Context Enrichment', icon: 'layers-outline', description: 'Add relevant context and background' },
  { type: 'connection_mapping', label: 'Connection Mapping', icon: 'git-network-outline', description: 'Find links to other moments and themes' },
  { type: 'future_implications', label: 'Future Implications', icon: 'bulb-outline', description: 'Explore potential outcomes and next steps' },
];

export default function MemoryExpansionScreen() {
  const { apiClient } = useApi();
  const queryClient = useQueryClient();
  const [refreshing, setRefreshing] = useState(false);
  const [selectedMomentId, setSelectedMomentId] = useState<string | null>(null);
  const [expandView, setExpandView] = useState<'expansions' | 'request'>('expansions');

  // Fetch existing expansions
  const { data: expansions, isLoading: expansionsLoading } = useQuery<MemoryExpansion[]>({
    queryKey: ['memory-expansions'],
    queryFn: async () => {
      // Placeholder — replace with actual /api/memory-expansion endpoint
      return [];
    },
  });

  // Fetch recent moments for expansion selection
  const { data: recentMoments, isLoading: momentsLoading } = useQuery({
    queryKey: ['moments', 'for-expansion'],
    queryFn: () => apiClient.getMoments({ limit: 20 }),
    enabled: expandView === 'request',
  });

  const requestExpansion = useMutation({
    mutationFn: async ({ momentId, type }: { momentId: string; type: ExpansionType }) => {
      // Placeholder — replace with actual API call
      Alert.alert('Expansion Requested', `A ${type.replace('_', ' ')} expansion has been queued for processing.`);
      return {};
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['memory-expansions'] });
      setSelectedMomentId(null);
      setExpandView('expansions');
    },
  });

  const onRefresh = async () => {
    setRefreshing(true);
    await queryClient.invalidateQueries({ queryKey: ['memory-expansions'] });
    setRefreshing(false);
  };

  const handleRequestExpansion = (type: ExpansionType) => {
    if (!selectedMomentId) {
      Alert.alert('Select a Moment', 'Please select a moment to expand first.');
      return;
    }
    requestExpansion.mutate({ momentId: selectedMomentId, type });
  };

  const moments = recentMoments?.moments || [];

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      showsVerticalScrollIndicator={false}
    >
      {/* Toggle */}
      <View style={styles.toggleRow}>
        <TouchableOpacity
          style={[styles.toggleBtn, expandView === 'expansions' && styles.toggleBtnActive]}
          onPress={() => setExpandView('expansions')}
        >
          <Ionicons name="layers-outline" size={16} color={expandView === 'expansions' ? '#6366f1' : '#94a3b8'} />
          <Text style={[styles.toggleText, expandView === 'expansions' && styles.toggleTextActive]}>
            My Expansions
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.toggleBtn, expandView === 'request' && styles.toggleBtnActive]}
          onPress={() => setExpandView('request')}
        >
          <Ionicons name="add-circle-outline" size={16} color={expandView === 'request' ? '#6366f1' : '#94a3b8'} />
          <Text style={[styles.toggleText, expandView === 'request' && styles.toggleTextActive]}>
            New Expansion
          </Text>
        </TouchableOpacity>
      </View>

      {/* Expansions List */}
      {expandView === 'expansions' && (
        <View style={styles.section}>
          {expansionsLoading ? (
            <View style={styles.centered}>
              <ActivityIndicator size="large" color="#6366f1" />
            </View>
          ) : (expansions || []).length === 0 ? (
            <View style={styles.emptyState}>
              <Ionicons name="expand-outline" size={48} color="#cbd5e1" />
              <Text style={styles.emptyTitle}>No memory expansions yet</Text>
              <Text style={styles.emptySubtitle}>
                Select a moment and request an AI-powered expansion to discover deeper insights
              </Text>
              <TouchableOpacity
                style={styles.ctaButton}
                onPress={() => setExpandView('request')}
              >
                <Text style={styles.ctaText}>Request Your First Expansion</Text>
              </TouchableOpacity>
            </View>
          ) : (
            (expansions || []).map((expansion) => (
              <View key={expansion.id} style={styles.expansionCard}>
                <View style={styles.expansionHeader}>
                  <View style={[styles.statusBadge, { backgroundColor: getStatusColor(expansion.status) }]}>
                    <Text style={styles.statusText}>{expansion.status}</Text>
                  </View>
                  <Text style={styles.expansionType}>
                    {expansion.type.replace(/_/g, ' ')}
                  </Text>
                </View>
                <Text style={styles.momentPreview} numberOfLines={2}>
                  {expansion.momentContent}
                </Text>
                {expansion.status === 'COMPLETED' && expansion.expandedContent && (
                  <View style={styles.expandedSection}>
                    <Text style={styles.expandedLabel}>Expanded Insight</Text>
                    <Text style={styles.expandedContent}>{expansion.expandedContent}</Text>
                  </View>
                )}
                <Text style={styles.dateText}>
                  {formatDistanceToNow(new Date(expansion.createdAt), { addSuffix: true })}
                </Text>
              </View>
            ))
          )}
        </View>
      )}

      {/* Request New Expansion */}
      {expandView === 'request' && (
        <View style={styles.section}>
          {/* Step 1: Select Moment */}
          <Text style={styles.stepTitle}>1. Select a Moment</Text>
          {momentsLoading ? (
            <ActivityIndicator size="small" color="#6366f1" />
          ) : moments.length === 0 ? (
            <Text style={styles.noMomentsText}>No moments found. Capture some moments first!</Text>
          ) : (
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.momentList}
            >
              {moments.map((moment: any) => (
                <TouchableOpacity
                  key={moment.id}
                  style={[
                    styles.momentCard,
                    selectedMomentId === moment.id && styles.momentCardSelected,
                  ]}
                  onPress={() => setSelectedMomentId(moment.id)}
                >
                  <Text style={styles.momentContent} numberOfLines={3}>
                    {moment.content}
                  </Text>
                  <Text style={styles.momentDate}>
                    {formatDistanceToNow(new Date(moment.createdAt), { addSuffix: true })}
                  </Text>
                  {selectedMomentId === moment.id && (
                    <Ionicons
                      name="checkmark-circle"
                      size={20}
                      color="#6366f1"
                      style={styles.checkIcon}
                    />
                  )}
                </TouchableOpacity>
              ))}
            </ScrollView>
          )}

          {/* Step 2: Choose Expansion Type */}
          <Text style={[styles.stepTitle, { marginTop: 24 }]}>2. Choose Expansion Type</Text>
          {EXPANSION_TYPES.map((et) => (
            <TouchableOpacity
              key={et.type}
              style={styles.typeCard}
              onPress={() => handleRequestExpansion(et.type)}
              disabled={!selectedMomentId || requestExpansion.isPending}
              accessibilityLabel={`Request ${et.label} expansion`}
            >
              <Ionicons name={et.icon} size={24} color={selectedMomentId ? '#6366f1' : '#cbd5e1'} />
              <View style={styles.typeInfo}>
                <Text style={[styles.typeLabel, !selectedMomentId && styles.typeDisabled]}>
                  {et.label}
                </Text>
                <Text style={styles.typeDesc}>{et.description}</Text>
              </View>
              <Ionicons
                name="chevron-forward"
                size={18}
                color={selectedMomentId ? '#94a3b8' : '#e2e8f0'}
              />
            </TouchableOpacity>
          ))}

          {requestExpansion.isPending && (
            <View style={styles.processingBanner}>
              <ActivityIndicator size="small" color="#6366f1" />
              <Text style={styles.processingText}>Requesting expansion...</Text>
            </View>
          )}
        </View>
      )}
    </ScrollView>
  );
}

function getStatusColor(status: string): string {
  switch (status) {
    case 'COMPLETED': return '#10b981';
    case 'PROCESSING': return '#f59e0b';
    case 'PENDING': return '#94a3b8';
    case 'FAILED': return '#ef4444';
    default: return '#94a3b8';
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  centered: {
    paddingVertical: 48,
    alignItems: 'center',
  },
  toggleRow: {
    flexDirection: 'row',
    margin: 16,
    marginBottom: 0,
    gap: 8,
  },
  toggleBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 10,
    borderRadius: 10,
    backgroundColor: '#f1f5f9',
  },
  toggleBtnActive: {
    backgroundColor: '#ede9fe',
  },
  toggleText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#94a3b8',
  },
  toggleTextActive: {
    color: '#6366f1',
    fontWeight: '600',
  },
  section: {
    padding: 16,
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
    paddingHorizontal: 16,
    lineHeight: 20,
  },
  ctaButton: {
    marginTop: 20,
    backgroundColor: '#6366f1',
    borderRadius: 10,
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  ctaText: {
    color: '#ffffff',
    fontWeight: '600',
    fontSize: 15,
  },
  expansionCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  expansionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  statusText: {
    fontSize: 11,
    color: '#ffffff',
    fontWeight: '600',
  },
  expansionType: {
    fontSize: 12,
    color: '#6366f1',
    fontWeight: '500',
    textTransform: 'capitalize',
  },
  momentPreview: {
    fontSize: 14,
    color: '#64748b',
    lineHeight: 20,
    marginBottom: 8,
  },
  expandedSection: {
    backgroundColor: '#faf5ff',
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
  },
  expandedLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#6366f1',
    marginBottom: 4,
  },
  expandedContent: {
    fontSize: 14,
    color: '#334155',
    lineHeight: 20,
  },
  dateText: {
    fontSize: 12,
    color: '#94a3b8',
  },
  stepTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#1e293b',
    marginBottom: 12,
  },
  noMomentsText: {
    fontSize: 14,
    color: '#94a3b8',
    textAlign: 'center',
    paddingVertical: 16,
  },
  momentList: {
    paddingBottom: 8,
    gap: 10,
  },
  momentCard: {
    width: 180,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 12,
    borderWidth: 1.5,
    borderColor: '#e2e8f0',
  },
  momentCardSelected: {
    borderColor: '#6366f1',
    backgroundColor: '#faf5ff',
  },
  momentContent: {
    fontSize: 13,
    color: '#334155',
    lineHeight: 18,
    marginBottom: 8,
  },
  momentDate: {
    fontSize: 11,
    color: '#94a3b8',
  },
  checkIcon: {
    position: 'absolute',
    top: 8,
    right: 8,
  },
  typeCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  typeInfo: {
    flex: 1,
  },
  typeLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1e293b',
  },
  typeDisabled: {
    color: '#cbd5e1',
  },
  typeDesc: {
    fontSize: 12,
    color: '#94a3b8',
    marginTop: 2,
  },
  processingBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 14,
    backgroundColor: '#ede9fe',
    borderRadius: 10,
    marginTop: 16,
  },
  processingText: {
    fontSize: 14,
    color: '#6366f1',
    fontWeight: '500',
  },
});
