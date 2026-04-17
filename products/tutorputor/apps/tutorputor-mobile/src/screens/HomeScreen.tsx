/**
 * Home Screen
 *
 * Main dashboard for the mobile app
 *
 * @doc.type component
 * @doc.purpose Home screen/dashboard
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
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../navigation/types';
import { useNetworkStatus, useDownloadedModules } from '../hooks/useOffline';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

export function HomeScreen({ navigation }: Props): React.ReactElement {
  const { isConnected } = useNetworkStatus();
  const { modules, isLoading } = useDownloadedModules();

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView}>
        {/* Welcome Section */}
        <View style={styles.welcomeSection}>
          <Text style={styles.welcomeText}>Welcome to TutorPutor!</Text>
          <Text style={styles.subtitle}>
            Learn anywhere, even offline
          </Text>
        </View>

        {/* Quick Actions */}
        <View style={styles.actionsSection}>
          <TouchableOpacity
            style={styles.actionCard}
            onPress={() => navigation.navigate('Modules')}
          >
            <Text style={styles.actionIcon}>📚</Text>
            <Text style={styles.actionTitle}>Browse Modules</Text>
            <Text style={styles.actionSubtitle}>Explore all courses</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.actionCard}
            onPress={() => navigation.navigate('Downloads')}
          >
            <Text style={styles.actionIcon}>💾</Text>
            <Text style={styles.actionTitle}>Downloads</Text>
            <Text style={styles.actionSubtitle}>
              {isLoading ? 'Loading...' : `${modules.length} downloaded`}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.actionCard}
            onPress={() => navigation.navigate('Profile')}
          >
            <Text style={styles.actionIcon}>👤</Text>
            <Text style={styles.actionTitle}>My Profile</Text>
            <Text style={styles.actionSubtitle}>Track progress</Text>
          </TouchableOpacity>
        </View>

        {/* Network Status */}
        <View style={styles.statusSection}>
          <Text style={styles.statusLabel}>Network Status:</Text>
          <Text style={[styles.statusValue, isConnected ? styles.online : styles.offline]}>
            {isConnected ? '🟢 Online' : '🔴 Offline'}
          </Text>
        </View>

        {/* Downloaded Modules Summary */}
        {!isLoading && modules.length > 0 && (
          <View style={styles.modulesSection}>
            <Text style={styles.sectionTitle}>Downloaded for Offline</Text>
            {modules.slice(0, 3).map((module) => (
              <TouchableOpacity
                key={module.id}
                style={styles.moduleCard}
                onPress={() => navigation.navigate('ModuleDetail', { moduleId: module.id })}
              >
                <Text style={styles.moduleTitle}>{module.title}</Text>
                <Text style={styles.moduleMeta}>
                  {module.lessons?.length || 0} lessons • {module.category}
                </Text>
              </TouchableOpacity>
            ))}
            {modules.length > 3 && (
              <TouchableOpacity onPress={() => navigation.navigate('Downloads')}>
                <Text style={styles.seeAll}>See all {modules.length} modules →</Text>
              </TouchableOpacity>
            )}
          </View>
        )}
      </ScrollView>
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
  welcomeSection: {
    padding: 24,
    backgroundColor: '#4F46E5',
  },
  welcomeText: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  subtitle: {
    fontSize: 16,
    color: '#E0E7FF',
    marginTop: 8,
  },
  actionsSection: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    padding: 16,
    gap: 12,
  },
  actionCard: {
    flex: 1,
    minWidth: '45%',
    backgroundColor: '#FFFFFF',
    padding: 20,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    alignItems: 'center',
  },
  actionIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  actionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1F2937',
  },
  actionSubtitle: {
    fontSize: 12,
    color: '#6B7280',
    marginTop: 4,
  },
  statusSection: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    borderRadius: 8,
  },
  statusLabel: {
    fontSize: 14,
    color: '#6B7280',
    marginRight: 8,
  },
  statusValue: {
    fontSize: 14,
    fontWeight: '500',
  },
  online: {
    color: '#059669',
  },
  offline: {
    color: '#DC2626',
  },
  modulesSection: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 12,
  },
  moduleCard: {
    backgroundColor: '#FFFFFF',
    padding: 16,
    borderRadius: 8,
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  moduleTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#1F2937',
  },
  moduleMeta: {
    fontSize: 12,
    color: '#6B7280',
    marginTop: 4,
  },
  seeAll: {
    fontSize: 14,
    color: '#4F46E5',
    fontWeight: '500',
    marginTop: 8,
    textAlign: 'center',
  },
});
