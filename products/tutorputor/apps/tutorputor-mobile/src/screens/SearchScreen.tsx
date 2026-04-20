/**
 * Search Screen
 *
 * Content discovery and search interface.
 *
 * @doc.type component
 * @doc.purpose Search and content discovery
 * @doc.layer product
 * @doc.pattern Screen
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  FlatList,
  TouchableOpacity,
  SafeAreaView,
  ActivityIndicator,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { ExploreStackParamList } from '../navigation/types';
import { useQuery } from '@tanstack/react-query';
import { createSessionHeaders } from '../storage/NativeSessionStorage';

type Props = NativeStackScreenProps<ExploreStackParamList, 'Search'>;

interface SearchResult {
  id: string;
  title: string;
  description: string;
  assetType: 'module' | 'experience' | 'simulation';
  domain: string;
}

async function searchContent(query: string): Promise<SearchResult[]> {
  if (!query.trim()) return [];

  const response = await fetch(`/api/v1/search?q=${encodeURIComponent(query)}`, {
    headers: createSessionHeaders({ 'Content-Type': 'application/json' }),
  });

  if (!response.ok) {
    throw new Error('Search failed');
  }

  const data = await response.json();
  return data.results?.map((r: { asset: SearchResult }) => r.asset) || [];
}

export function SearchScreen({ navigation }: Props): React.ReactElement {
  const [query, setQuery] = useState('');
  const { data: results, isLoading } = useQuery({
    queryKey: ['search', query],
    queryFn: () => searchContent(query),
    enabled: query.length >= 2,
  });

  const renderResult = ({ item }: { item: SearchResult }) => (
    <TouchableOpacity
      style={styles.resultCard}
      onPress={() => {
        if (item.assetType === 'module') {
          navigation.navigate('ModuleDetail', { moduleId: item.id });
        }
      }}
    >
      <View style={styles.resultHeader}>
        <View style={[styles.typeBadge, { backgroundColor: getTypeColor(item.assetType) }]}>
          <Text style={styles.typeText}>{item.assetType}</Text>
        </View>
        <Text style={styles.domainText}>{item.domain}</Text>
      </View>
      <Text style={styles.resultTitle}>{item.title}</Text>
      <Text style={styles.resultDescription} numberOfLines={2}>{item.description}</Text>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container}>
      {/* Search Input */}
      <View style={styles.searchContainer}>
        <Text style={styles.searchIcon}>🔍</Text>
        <TextInput
          style={styles.searchInput}
          value={query}
          onChangeText={setQuery}
          placeholder="Search modules, topics..."
          placeholderTextColor="#9CA3AF"
          autoFocus
        />
        {query.length > 0 && (
          <TouchableOpacity onPress={() => setQuery('')}>
            <Text style={styles.clearIcon}>✕</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Results */}
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#4F46E5" />
        </View>
      ) : (
        <FlatList
          data={results}
          renderItem={renderResult}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            query.length >= 2 ? (
              <View style={styles.emptyContainer}>
                <Text style={styles.emptyText}>No results found for "{query}"</Text>
              </View>
            ) : (
              <View style={styles.emptyContainer}>
                <Text style={styles.emptyIcon}>🔍</Text>
                <Text style={styles.emptyTitle}>Start searching</Text>
                <Text style={styles.emptyText}>Find modules, topics, and learning resources</Text>
              </View>
            )
          }
        />
      )}
    </SafeAreaView>
  );
}

function getTypeColor(type: string): string {
  const colors: Record<string, string> = {
    module: '#EEF2FF',
    experience: '#ECFDF5',
    simulation: '#FEF3C7',
  };
  return colors[type] || '#F3F4F6';
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    margin: 16,
    paddingHorizontal: 16,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  searchIcon: {
    fontSize: 18,
    marginRight: 12,
  },
  searchInput: {
    flex: 1,
    height: 48,
    fontSize: 16,
    color: '#1F2937',
  },
  clearIcon: {
    fontSize: 16,
    color: '#9CA3AF',
    padding: 8,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  listContent: {
    padding: 16,
  },
  resultCard: {
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
  resultHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  typeBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  typeText: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'uppercase',
    color: '#374151',
  },
  domainText: {
    fontSize: 12,
    color: '#6B7280',
  },
  resultTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 4,
  },
  resultDescription: {
    fontSize: 14,
    color: '#6B7280',
    lineHeight: 20,
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
  },
});
