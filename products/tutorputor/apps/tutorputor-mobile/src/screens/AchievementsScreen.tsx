/**
 * Achievements Screen
 *
 * User badges and achievements display.
 *
 * @doc.type component
 * @doc.purpose User achievements and badges
 * @doc.layer product
 * @doc.pattern Screen
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  SafeAreaView,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { ProfileStackParamList } from '../navigation/types';
import { useQuery } from '@tanstack/react-query';

type Props = NativeStackScreenProps<ProfileStackParamList, 'Achievements'>;

interface Achievement {
  id: string;
  badge: {
    name: string;
    icon: string;
    description: string;
    rarity: 'common' | 'rare' | 'epic' | 'legendary';
  };
  earnedAt: string;
  category: string;
}

async function fetchAchievements(): Promise<Achievement[]> {
  const token = typeof localStorage !== 'undefined' ? localStorage.getItem('auth_token') : null;
  const tenantId = typeof localStorage !== 'undefined' ? localStorage.getItem('tenant_id') : 'default';

  const response = await fetch('/api/v1/gamification/achievements', {
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'X-Tenant-ID': tenantId || 'default',
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error('Failed to fetch achievements');
  }

  const data = await response.json();
  return data.achievements || [];
}

export function AchievementsScreen({ navigation }: Props): React.ReactElement {
  const { data: achievements } = useQuery({
    queryKey: ['achievements'],
    queryFn: fetchAchievements,
  });

  const renderAchievement = ({ item }: { item: Achievement }) => (
    <View style={styles.achievementCard}>
      <View style={[styles.iconContainer, { backgroundColor: getRarityColor(item.badge.rarity) }]}>
        <Text style={styles.achievementIcon}>{item.badge.icon}</Text>
      </View>
      <View style={styles.achievementInfo}>
        <Text style={styles.achievementName}>{item.badge.name}</Text>
        <Text style={styles.achievementDescription}>{item.badge.description}</Text>
        <View style={styles.achievementMeta}>
          <View style={[styles.rarityBadge, { backgroundColor: getRarityColor(item.badge.rarity) }]}>
            <Text style={styles.rarityText}>{item.badge.rarity}</Text>
          </View>
          <Text style={styles.earnedDate}>
            Earned {new Date(item.earnedAt).toLocaleDateString()}
          </Text>
        </View>
      </View>
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={achievements}
        renderItem={renderAchievement}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>🏆</Text>
            <Text style={styles.emptyTitle}>No achievements yet</Text>
            <Text style={styles.emptyText}>Complete modules and challenges to earn badges!</Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

function getRarityColor(rarity: string): string {
  const colors: Record<string, string> = {
    common: '#F3F4F6',
    rare: '#DBEAFE',
    epic: '#E9D5FF',
    legendary: '#FEF3C7',
  };
  return colors[rarity] || '#F3F4F6';
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  listContent: {
    padding: 16,
  },
  achievementCard: {
    flexDirection: 'row',
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
  iconContainer: {
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  achievementIcon: {
    fontSize: 28,
  },
  achievementInfo: {
    flex: 1,
  },
  achievementName: {
    fontSize: 17,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 4,
  },
  achievementDescription: {
    fontSize: 14,
    color: '#6B7280',
    marginBottom: 8,
    lineHeight: 20,
  },
  achievementMeta: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  rarityBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
    marginRight: 8,
  },
  rarityText: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'uppercase',
    color: '#374151',
  },
  earnedDate: {
    fontSize: 12,
    color: '#9CA3AF',
  },
  emptyContainer: {
    alignItems: 'center',
    padding: 40,
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
  },
});
