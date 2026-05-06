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
import { flashitMobileColors, flashitMobileShadows } from '@/styles/designTokens';

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
    queryFn: () => apiClient.getMoments({ limit: 5 }),
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
          color={flashitMobileColors.sky500}
          accessibilityLabel="Loading, please wait"
        />
      </View>
    );
  }

  const totalMoments = momentsData?.totalCount || 0;
  const totalSpheres = spheresData?.length || 0;

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
                    <Text style={styles.momentSphere}>{moment.sphere?.name ?? 'Unknown sphere'}</Text>
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
    backgroundColor: flashitMobileColors.slate50,
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
    color: flashitMobileColors.slate800,
    marginBottom: 20,
  },
  statsContainer: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  statCard: {
    flex: 1,
    backgroundColor: flashitMobileColors.white,
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
    boxShadow: flashitMobileShadows.soft,
    elevation: 2,
  },
  statNumber: {
    fontSize: 32,
    fontWeight: 'bold',
    color: flashitMobileColors.sky500,
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 14,
    color: flashitMobileColors.slate500,
  },
  actionsContainer: {
    gap: 12,
    marginBottom: 24,
  },
  actionButton: {
    backgroundColor: flashitMobileColors.sky500,
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  actionButtonSecondary: {
    backgroundColor: flashitMobileColors.white,
    borderWidth: 2,
    borderColor: flashitMobileColors.slate200,
  },
  actionButtonTertiary: {
    backgroundColor: flashitMobileColors.yellow100,
    borderWidth: 2,
    borderColor: flashitMobileColors.yellow500,
  },
  actionButtonText: {
    color: flashitMobileColors.white,
    fontSize: 16,
    fontWeight: '600',
  },
  actionButtonTextSecondary: {
    color: flashitMobileColors.sky500,
    fontSize: 16,
    fontWeight: '600',
  },
  actionButtonTextTertiary: {
    color: flashitMobileColors.yellow700,
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
    color: flashitMobileColors.slate800,
  },
  sectionLink: {
    fontSize: 14,
    color: flashitMobileColors.sky500,
    fontWeight: '500',
  },
  momentsList: {
    gap: 12,
  },
  momentCard: {
    backgroundColor: flashitMobileColors.white,
    borderRadius: 12,
    padding: 16,
    borderLeftWidth: 4,
    borderLeftColor: flashitMobileColors.sky500,
    boxShadow: flashitMobileShadows.soft,
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
    color: flashitMobileColors.sky500,
    backgroundColor: flashitMobileColors.sky100,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  momentTime: {
    fontSize: 11,
    color: flashitMobileColors.slate400,
  },
  momentText: {
    fontSize: 14,
    color: flashitMobileColors.slate600,
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
    color: flashitMobileColors.purple500,
    backgroundColor: flashitMobileColors.purple50,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  emptyState: {
    backgroundColor: flashitMobileColors.white,
    borderRadius: 12,
    padding: 32,
    alignItems: 'center',
  },
  emptyStateText: {
    fontSize: 16,
    color: flashitMobileColors.slate400,
    marginBottom: 8,
  },
  emptyStateLink: {
    fontSize: 14,
    color: flashitMobileColors.sky500,
    fontWeight: '500',
  },
});

