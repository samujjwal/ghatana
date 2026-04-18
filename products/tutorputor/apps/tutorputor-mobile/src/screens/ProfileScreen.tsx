/**
 * Profile Screen
 *
 * User profile and account settings.
 *
 * @doc.type component
 * @doc.purpose User profile screen
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
import type { ProfileStackParamList } from '../navigation/types';
import { useQuery } from '@tanstack/react-query';

type Props = NativeStackScreenProps<ProfileStackParamList, 'ProfileMain'>;

interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
  totalPoints: number;
  currentStreak: number;
  level: number;
  joinedAt: string;
}

async function fetchUserProfile(): Promise<UserProfile> {
  const token = typeof localStorage !== 'undefined' ? localStorage.getItem('auth_token') : null;
  const tenantId = typeof localStorage !== 'undefined' ? localStorage.getItem('tenant_id') : 'default';

  const response = await fetch('/api/v1/user/profile', {
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'X-Tenant-ID': tenantId || 'default',
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error('Failed to fetch profile');
  }

  return response.json();
}

export function ProfileScreen({ navigation }: Props): React.ReactElement {
  const { data: profile } = useQuery({
    queryKey: ['profile'],
    queryFn: fetchUserProfile,
  });

  const menuItems = [
    { icon: '🏆', label: 'Achievements', screen: 'Achievements' as const },
    { icon: '💾', label: 'Downloads', screen: 'Downloads' as const },
    { icon: '⚙️', label: 'Settings', screen: 'Settings' as const },
  ];

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        {/* Profile Header */}
        <View style={styles.profileHeader}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>
              {profile?.displayName?.charAt(0).toUpperCase() || '👤'}
            </Text>
          </View>
          <Text style={styles.name}>{profile?.displayName || 'Guest User'}</Text>
          <Text style={styles.email}>{profile?.email || 'Sign in to sync'}</Text>
        </View>

        {/* Stats */}
        <View style={styles.statsContainer}>
          <View style={styles.statItem}>
            <Text style={styles.statValue}>{profile?.totalPoints || 0}</Text>
            <Text style={styles.statLabel}>Points</Text>
          </View>
          <View style={styles.statDivider} />
          <View style={styles.statItem}>
            <Text style={styles.statValue}>{profile?.currentStreak || 0}</Text>
            <Text style={styles.statLabel}>Day Streak</Text>
          </View>
          <View style={styles.statDivider} />
          <View style={styles.statItem}>
            <Text style={styles.statValue}>{profile?.level || 1}</Text>
            <Text style={styles.statLabel}>Level</Text>
          </View>
        </View>

        {/* Menu */}
        <View style={styles.menuContainer}>
          {menuItems.map((item) => (
            <TouchableOpacity
              key={item.screen}
              style={styles.menuItem}
              onPress={() => navigation.navigate(item.screen)}
            >
              <Text style={styles.menuIcon}>{item.icon}</Text>
              <Text style={styles.menuLabel}>{item.label}</Text>
              <Text style={styles.menuArrow}>→</Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* App Info */}
        <View style={styles.appInfo}>
          <Text style={styles.appInfoText}>TutorPutor Mobile v0.1.0</Text>
        </View>
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
  content: {
    padding: 20,
  },
  profileHeader: {
    alignItems: 'center',
    paddingVertical: 32,
  },
  avatar: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: '#4F46E5',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  avatarText: {
    fontSize: 40,
    color: '#FFFFFF',
    fontWeight: '600',
  },
  name: {
    fontSize: 24,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 4,
  },
  email: {
    fontSize: 14,
    color: '#6B7280',
  },
  statsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 20,
    marginBottom: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 28,
    fontWeight: '700',
    color: '#4F46E5',
  },
  statLabel: {
    fontSize: 13,
    color: '#6B7280',
    marginTop: 4,
  },
  statDivider: {
    width: 1,
    height: 40,
    backgroundColor: '#E5E7EB',
  },
  menuContainer: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  menuIcon: {
    fontSize: 20,
    marginRight: 12,
  },
  menuLabel: {
    flex: 1,
    fontSize: 16,
    color: '#1F2937',
  },
  menuArrow: {
    fontSize: 18,
    color: '#9CA3AF',
  },
  appInfo: {
    marginTop: 24,
    alignItems: 'center',
  },
  appInfoText: {
    fontSize: 12,
    color: '#9CA3AF',
  },
});
