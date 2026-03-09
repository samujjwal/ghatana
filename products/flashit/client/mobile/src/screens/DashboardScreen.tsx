/**
 * Dashboard Screen for Flashit Mobile
 * Main landing page with stats and recent moments
 */

import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { RootStackParamList } from '../navigation';
import { useApi } from '../contexts/ApiContext';
import { mobileAtoms } from '../state/localAtoms';
import { formatDistanceToNow } from 'date-fns';

type Props = NativeStackScreenProps<RootStackParamList, 'Dashboard'>;

export default function DashboardScreen({ navigation }: Props) {
  const { apiClient } = useApi();
  const currentUser = useAtomValue(mobileAtoms.currentUserAtom);

  const { data: spheresData, isLoading: spheresLoading } = useQuery({
    queryKey: ['spheres'],
    queryFn: () => apiClient.getSpheres(),
  });

  const { data: momentsData, isLoading: momentsLoading } = useQuery({
    queryKey: ['moments', 'recent'],
    queryFn: () => apiClient.searchMoments({ limit: 5 }),
  });

  const isLoading = spheresLoading || momentsLoading;

  if (isLoading) {
    return (
      <View 
        style={styles.loadingContainer}
        accessible={true}
        accessibilityLabel="Loading dashboard"
        accessibilityState={{ busy: true }}
      >
        <ActivityIndicator 
          size="large" 
          color="#0ea5e9"
          accessibilityLabel="Loading, please wait"
        />
      </View>
    );
  }

  const totalMoments = momentsData?.totalCount || 0;
  const totalSpheres = spheresData?.spheres.length || 0;

  return (
    <ScrollView 
      style={styles.container}
      accessible={false}
      accessibilityLabel="Dashboard screen"
    >
      <View style={styles.content}>
        {/* Welcome */}
        <Text 
          style={styles.welcome}
          accessibilityRole="header"
          accessibilityLabel={`Welcome back, ${currentUser?.displayName || currentUser?.email}`}
        >
          Welcome back, {currentUser?.displayName || currentUser?.email}!
        </Text>

        {/* Stats */}
        <View 
          style={styles.statsContainer}
          accessible={false}
          accessibilityLabel="Statistics"
        >
          <View 
            style={styles.statCard}
            accessible={true}
            accessibilityRole="summary"
            accessibilityLabel={`${totalMoments} moments captured`}
          >
            <Text style={styles.statNumber}>{totalMoments}</Text>
            <Text style={styles.statLabel}>Moments</Text>
          </View>
          <View 
            style={styles.statCard}
            accessible={true}
            accessibilityRole="summary"
            accessibilityLabel={`${totalSpheres} spheres created`}
          >
            <Text style={styles.statNumber}>{totalSpheres}</Text>
            <Text style={styles.statLabel}>Spheres</Text>
          </View>
        </View>

        {/* Quick Actions */}
        <View 
          style={styles.actionsContainer}
          accessible={false}
          accessibilityLabel="Quick actions"
        >
          <TouchableOpacity
            style={styles.actionButton}
            onPress={() => navigation.navigate('Capture')}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Capture moment"
            accessibilityHint="Double tap to create a new moment"
          >
            <Text style={styles.actionButtonText}>✨ Capture Moment</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.actionButton, styles.actionButtonSecondary]}
            onPress={() => navigation.navigate('Moments')}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="View moments"
            accessibilityHint="Double tap to view all your moments"
          >
            <Text style={styles.actionButtonTextSecondary}>📝 View Moments</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.actionButton, styles.actionButtonTertiary]}
            onPress={() => navigation.navigate('MultimediaTest')}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Test multimedia features"
            accessibilityHint="Double tap to test audio, video, and image capture"
          >
            <Text style={styles.actionButtonTextTertiary}>🧪 Test Multimedia</Text>
          </TouchableOpacity>
        </View>

        {/* Recent Moments */}
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text 
              style={styles.sectionTitle}
              accessibilityRole="header"
              accessibilityLabel="Recent moments"
            >
              Recent Moments
            </Text>
            <TouchableOpacity 
              onPress={() => navigation.navigate('Moments')}
              accessible={true}
              accessibilityRole="link"
              accessibilityLabel="View all moments"
              accessibilityHint="Double tap to see all your moments"
            >
              <Text style={styles.sectionLink}>View all →</Text>
            </TouchableOpacity>
          </View>

          {momentsData?.moments && momentsData.moments.length > 0 ? (
            <View style={styles.momentsList}>
              {momentsData.moments.map((moment) => (
                <View key={moment.id} style={styles.momentCard}>
                  <View style={styles.momentHeader}>
                    <Text style={styles.momentSphere}>{moment.sphere.name}</Text>
                    <Text style={styles.momentTime}>
                      {formatDistanceToNow(new Date(moment.capturedAt), {
                        addSuffix: true,
                      })}
                    </Text>
                  </View>
                  <Text style={styles.momentText} numberOfLines={2}>
                    {moment.contentText}
                  </Text>
                  {moment.emotions && moment.emotions.length > 0 && (
                    <View style={styles.emotionTags}>
                      {moment.emotions.slice(0, 3).map((emotion) => (
                        <Text key={emotion} style={styles.emotionTag}>
                          {emotion}
                        </Text>
                      ))}
                    </View>
                  )}
                </View>
              ))}
            </View>
          ) : (
            <View style={styles.emptyState}>
              <Text style={styles.emptyStateText}>No moments yet</Text>
              <TouchableOpacity onPress={() => navigation.navigate('Capture')}>
                <Text style={styles.emptyStateLink}>Capture your first moment →</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  content: {
    padding: 16,
  },
  welcome: {
    fontSize: 24,
    fontWeight: '600',
    color: '#1e293b',
    marginBottom: 20,
  },
  statsContainer: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
    boxShadow: '0px 1px 2px rgba(0, 0, 0, 0.05)',
    elevation: 2,
  },
  statNumber: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#0ea5e9',
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 14,
    color: '#64748b',
  },
  actionsContainer: {
    gap: 12,
    marginBottom: 24,
  },
  actionButton: {
    backgroundColor: '#0ea5e9',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  actionButtonSecondary: {
    backgroundColor: '#fff',
    borderWidth: 2,
    borderColor: '#e2e8f0',
  },
  actionButtonTertiary: {
    backgroundColor: '#fef3c7',
    borderWidth: 2,
    borderColor: '#f59e0b',
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  actionButtonTextSecondary: {
    color: '#0ea5e9',
    fontSize: 16,
    fontWeight: '600',
  },
  actionButtonTextTertiary: {
    color: '#92400e',
    fontSize: 16,
    fontWeight: '600',
  },
  section: {
    marginBottom: 24,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1e293b',
  },
  sectionLink: {
    fontSize: 14,
    color: '#0ea5e9',
    fontWeight: '500',
  },
  momentsList: {
    gap: 12,
  },
  momentCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#0ea5e9',
    boxShadow: '0px 1px 2px rgba(0, 0, 0, 0.05)',
    elevation: 2,
  },
  momentHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  momentSphere: {
    fontSize: 12,
    fontWeight: '600',
    color: '#0ea5e9',
    backgroundColor: '#e0f2fe',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  momentTime: {
    fontSize: 11,
    color: '#94a3b8',
  },
  momentText: {
    fontSize: 14,
    color: '#475569',
    lineHeight: 20,
  },
  emotionTags: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginTop: 8,
  },
  emotionTag: {
    fontSize: 11,
    color: '#7c3aed',
    backgroundColor: '#f3e8ff',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  emptyState: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 32,
    alignItems: 'center',
  },
  emptyStateText: {
    fontSize: 16,
    color: '#94a3b8',
    marginBottom: 8,
  },
  emptyStateLink: {
    fontSize: 14,
    color: '#0ea5e9',
    fontWeight: '500',
  },
});

