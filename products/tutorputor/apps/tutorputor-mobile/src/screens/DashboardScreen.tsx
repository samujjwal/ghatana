/**
 * Dashboard Screen (Simplified)
 *
 * Simplified dashboard with single primary CTA and
 * AI-suggested content. Reduces cognitive load.
 *
 * @doc.type component
 * @doc.purpose Simplified student dashboard
 * @doc.layer product
 * @doc.pattern Screen
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  ActivityIndicator,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { LearnStackParamList } from '../navigation/types';
import { useNetworkStatus } from '../hooks/useOffline';
import { useDashboard } from '../hooks/useDashboard';

import { ContinueLearningCard } from '../components/dashboard/ContinueLearningCard';
import { StartSomethingNewSection } from '../components/dashboard/StartSomethingNewSection';
import { QuickActions } from '../components/dashboard/QuickActions';
import { AIHelpButton } from '../components/dashboard/AIHelpButton';

type Props = NativeStackScreenProps<LearnStackParamList, 'Dashboard'>;

export function DashboardScreen({ navigation }: Props): React.ReactElement {
  const { isConnected } = useNetworkStatus();
  const { data: dashboard, isLoading, error } = useDashboard();

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#4F46E5" />
          <Text style={styles.loadingText}>Loading your learning...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (error || !dashboard) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.errorContainer}>
          <Text style={styles.errorIcon}>⚠️</Text>
          <Text style={styles.errorTitle}>Could not load dashboard</Text>
          <Text style={styles.errorMessage}>
            {error?.message || 'Please try again later'}
          </Text>
          <TouchableOpacity
            style={styles.retryButton}
            onPress={() => navigation.replace('Dashboard')}
          >
            <Text style={styles.retryButtonText}>Retry</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const hasActiveEnrollments = dashboard.currentEnrollments?.length > 0;
  const topEnrollment = hasActiveEnrollments ? dashboard.currentEnrollments[0] : null;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.contentContainer}>
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Hello, {dashboard.user?.displayName?.split(' ')[0] || 'Learner'}!</Text>
            <Text style={styles.subtitle}>
              {hasActiveEnrollments
                ? 'Ready to continue your journey?'
                : 'What would you like to learn today?'}
            </Text>
          </View>
          {!isConnected && (
            <View style={styles.offlineBadge}>
              <Text style={styles.offlineText}>Offline</Text>
            </View>
          )}
        </View>

        {/* Primary CTA: Continue Learning */}
        {hasActiveEnrollments && topEnrollment && (
          <ContinueLearningCard
            enrollment={topEnrollment}
            onPress={() => navigation.navigate('ModuleDetail', { moduleId: topEnrollment.moduleId })}
            onSeeAll={() => navigation.navigate('Enrollments')}
          />
        )}

        {/* Secondary: Start Something New */}
        <StartSomethingNewSection
          recommendations={dashboard.recommendedModules}
          isLoading={isLoading}
          onExplore={() => navigation.navigate('Modules')}
          onModulePress={(moduleId: string) => navigation.navigate('ModuleDetail', { moduleId })}
        />

        {/* Quick Actions (Progressive Disclosure) */}
        <QuickActions
          onBrowseModules={() => navigation.navigate('Modules')}
          onViewEnrollments={() => navigation.navigate('Enrollments')}
          onViewAchievements={() => navigation.navigate('AITutor')}
        />

        {/* Stats Summary */}
        {dashboard.stats && (
          <View style={styles.statsContainer}>
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{dashboard.stats.totalEnrollments}</Text>
              <Text style={styles.statLabel}>Enrolled</Text>
            </View>
            <View style={styles.statDivider} />
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{dashboard.stats.completedModules}</Text>
              <Text style={styles.statLabel}>Completed</Text>
            </View>
            <View style={styles.statDivider} />
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{Math.round(dashboard.stats.averageProgress)}%</Text>
              <Text style={styles.statLabel}>Avg Progress</Text>
            </View>
          </View>
        )}
      </ScrollView>

      {/* Floating AI Help Button */}
      <AIHelpButton
        onPress={() => navigation.navigate('AITutor')}
        context={topEnrollment?.moduleId}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  scrollView: {
    flex: 1,
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 100,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 24,
  },
  greeting: {
    fontSize: 24,
    fontWeight: '700',
    color: '#1F2937',
  },
  subtitle: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 4,
  },
  offlineBadge: {
    backgroundColor: '#FEF3C7',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  offlineText: {
    fontSize: 12,
    color: '#92400E',
    fontWeight: '500',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
    color: '#6B7280',
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  errorIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 8,
  },
  errorMessage: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    marginBottom: 24,
  },
  retryButton: {
    backgroundColor: '#4F46E5',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 14,
  },
  statsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginTop: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 24,
    fontWeight: '700',
    color: '#4F46E5',
  },
  statLabel: {
    fontSize: 12,
    color: '#6B7280',
    marginTop: 4,
  },
  statDivider: {
    width: 1,
    height: 40,
    backgroundColor: '#E5E7EB',
  },
});
