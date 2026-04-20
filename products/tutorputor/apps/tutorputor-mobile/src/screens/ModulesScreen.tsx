/**
 * Modules Screen
 *
 * Browse and discover learning modules.
 *
 * @doc.type component
 * @doc.purpose Module browsing and discovery
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
  ActivityIndicator,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { LearnStackParamList } from '../navigation/types';
import { useQuery } from '@tanstack/react-query';
import { createSessionHeaders } from '../storage/NativeSessionStorage';

type Props = NativeStackScreenProps<LearnStackParamList, 'Modules'>;

interface Module {
  id: string;
  title: string;
  description: string;
  domain: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  estimatedTimeMinutes: number;
  tags: string[];
}

async function fetchModules(): Promise<Module[]> {
  const response = await fetch('/api/v1/modules', {
    headers: createSessionHeaders({ 'Content-Type': 'application/json' }),
  });

  if (!response.ok) {
    throw new Error('Failed to fetch modules');
  }

  const data = await response.json();
  return data.items || [];
}

export function ModulesScreen({ navigation }: Props): React.ReactElement {
  const { data: modules, isLoading, error } = useQuery({
    queryKey: ['modules'],
    queryFn: fetchModules,
  });

  const renderModule = ({ item }: { item: Module }) => (
    <TouchableOpacity
      style={styles.moduleCard}
      onPress={() => navigation.navigate('ModuleDetail', { moduleId: item.id })}
    >
      <View style={styles.cardHeader}>
        <View style={[styles.domainBadge, { backgroundColor: getDomainColor(item.domain) }]}>
          <Text style={styles.domainText}>{item.domain}</Text>
        </View>
        <View style={[styles.difficultyBadge, { backgroundColor: getDifficultyColor(item.difficulty) }]}>
          <Text style={styles.difficultyText}>{item.difficulty}</Text>
        </View>
      </View>
      
      <Text style={styles.moduleTitle}>{item.title}</Text>
      <Text style={styles.moduleDescription} numberOfLines={2}>{item.description}</Text>
      
      <View style={styles.cardFooter}>
        <Text style={styles.durationText}>⏱️ {item.estimatedTimeMinutes} min</Text>
        {item.tags.length > 0 && (
          <Text style={styles.tagText}>{item.tags[0]}</Text>
        )}
      </View>
    </TouchableOpacity>
  );

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#4F46E5" />
          <Text style={styles.loadingText}>Loading modules...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (error) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Failed to load modules</Text>
          <TouchableOpacity onPress={() => navigation.replace('Modules')}>
            <Text style={styles.retryText}>Retry</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={modules}
        renderItem={renderModule}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>No modules available</Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

function getDomainColor(domain: string): string {
  const colors: Record<string, string> = {
    physics: '#EEF2FF',
    chemistry: '#ECFDF5',
    biology: '#FEF3C7',
    mathematics: '#FCE7F3',
    engineering: '#DBEAFE',
  };
  return colors[domain.toLowerCase()] || '#F3F4F6';
}

function getDifficultyColor(difficulty: string): string {
  const colors: Record<string, string> = {
    beginner: '#D1FAE5',
    intermediate: '#FEF3C7',
    advanced: '#FEE2E2',
  };
  return colors[difficulty] || '#F3F4F6';
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
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
  errorText: {
    fontSize: 16,
    color: '#DC2626',
    marginBottom: 12,
  },
  retryText: {
    fontSize: 14,
    color: '#4F46E5',
    fontWeight: '600',
  },
  listContent: {
    padding: 16,
  },
  moduleCard: {
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
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  domainBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  domainText: {
    fontSize: 11,
    color: '#374151',
    fontWeight: '500',
    textTransform: 'uppercase',
  },
  difficultyBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  difficultyText: {
    fontSize: 11,
    color: '#374151',
    fontWeight: '500',
    textTransform: 'capitalize',
  },
  moduleTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 8,
  },
  moduleDescription: {
    fontSize: 14,
    color: '#6B7280',
    marginBottom: 12,
    lineHeight: 20,
  },
  cardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  durationText: {
    fontSize: 13,
    color: '#6B7280',
  },
  tagText: {
    fontSize: 12,
    color: '#4F46E5',
    backgroundColor: '#EEF2FF',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  emptyContainer: {
    padding: 24,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 14,
    color: '#6B7280',
  },
});
