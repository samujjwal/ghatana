/**
 * Enrollments Screen
 *
 * Shows all active learning enrollments.
 *
 * @doc.type component
 * @doc.purpose User enrollments list
 * @doc.layer product
 * @doc.pattern Screen
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  SafeAreaView,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { LearnStackParamList } from '../navigation/types';
import { useQuery } from '@tanstack/react-query';

type Props = NativeStackScreenProps<LearnStackParamList, 'Enrollments'>;

interface Enrollment {
  id: string;
  moduleId: string;
  status: 'active' | 'completed' | 'paused';
  progressPercent: number;
  moduleTitle: string;
  lastAccessedAt?: string;
}

async function fetchEnrollments(): Promise<Enrollment[]> {
  const token = typeof localStorage !== 'undefined' ? localStorage.getItem('auth_token') : null;
  const tenantId = typeof localStorage !== 'undefined' ? localStorage.getItem('tenant_id') : 'default';

  const response = await fetch('/api/v1/enrollments', {
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'X-Tenant-ID': tenantId || 'default',
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error('Failed to fetch enrollments');
  }

  const data = await response.json();
  return data.enrollments || [];
}

export function EnrollmentsScreen({ navigation }: Props): React.ReactElement {
  const { data: enrollments } = useQuery({
    queryKey: ['enrollments'],
    queryFn: fetchEnrollments,
  });

  const activeEnrollments = enrollments?.filter(e => e.status === 'active') || [];
  const completedEnrollments = enrollments?.filter(e => e.status === 'completed') || [];

  const renderEnrollment = ({ item }: { item: Enrollment }) => (
    <TouchableOpacity
      style={styles.enrollmentCard}
      onPress={() => navigation.navigate('ModuleDetail', { moduleId: item.moduleId })}
    >
      <View style={styles.enrollmentHeader}>
        <Text style={styles.moduleTitle} numberOfLines={2}>{item.moduleTitle}</Text>
        <View style={[styles.statusBadge, item.status === 'completed' ? styles.completedBadge : styles.activeBadge]}>
          <Text style={styles.statusText}>{item.status}</Text>
        </View>
      </View>

      <View style={styles.progressSection}>
        <View style={styles.progressBar}>
          <View style={[styles.progressFill, { width: `${item.progressPercent}%` }]} />
        </View>
        <Text style={styles.progressText}>{Math.round(item.progressPercent)}%</Text>
      </View>

      {item.lastAccessedAt && (
        <Text style={styles.lastAccessed}>
          Last accessed: {new Date(item.lastAccessedAt).toLocaleDateString()}
        </Text>
      )}
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={[...activeEnrollments, ...completedEnrollments]}
        renderItem={renderEnrollment}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>📚</Text>
            <Text style={styles.emptyTitle}>No enrollments yet</Text>
            <Text style={styles.emptyText}>Browse modules and start learning!</Text>
            <TouchableOpacity
              style={styles.browseButton}
              onPress={() => navigation.navigate('Modules')}
            >
              <Text style={styles.browseButtonText}>Browse Modules</Text>
            </TouchableOpacity>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  listContent: {
    padding: 16,
  },
  enrollmentCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  enrollmentHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  moduleTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1F2937',
    flex: 1,
    marginRight: 8,
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  activeBadge: {
    backgroundColor: '#EEF2FF',
  },
  completedBadge: {
    backgroundColor: '#D1FAE5',
  },
  statusText: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  progressSection: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  progressBar: {
    flex: 1,
    height: 6,
    backgroundColor: '#E5E7EB',
    borderRadius: 3,
    marginRight: 8,
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#4F46E5',
    borderRadius: 3,
  },
  progressText: {
    fontSize: 12,
    color: '#6B7280',
    width: 35,
  },
  lastAccessed: {
    fontSize: 12,
    color: '#9CA3AF',
  },
  emptyContainer: {
    alignItems: 'center',
    padding: 40,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    marginBottom: 24,
  },
  browseButton: {
    backgroundColor: '#4F46E5',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  browseButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 14,
  },
});
