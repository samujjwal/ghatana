/**
 * Search Screen for Flashit Mobile
 * Hybrid AI search across moments with filters
 *
 * @doc.type screen
 * @doc.purpose Search moments with AI-powered hybrid search
 * @doc.layer product
 * @doc.pattern MobileScreen
 */

import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Keyboard,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useApi } from '../contexts/ApiContext';
import { formatDistanceToNow } from 'date-fns';

interface SearchResult {
  id: string;
  content: string;
  sphereName?: string;
  createdAt: string;
  tags: string[];
  emotions: string[];
  score?: number;
}

type SearchType = 'hybrid' | 'semantic' | 'keyword';

export default function SearchScreen() {
  const { apiClient } = useApi();
  const [query, setQuery] = useState('');
  const [searchType, setSearchType] = useState<SearchType>('hybrid');
  const [submittedQuery, setSubmittedQuery] = useState('');

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['search', submittedQuery, searchType],
    queryFn: async () => {
      if (!submittedQuery.trim()) return { results: [], totalCount: 0 };
      return apiClient.search({
        query: submittedQuery,
        type: searchType,
        limit: 50,
      });
    },
    enabled: submittedQuery.length > 0,
  });

  const handleSearch = useCallback(() => {
    if (query.trim()) {
      setSubmittedQuery(query.trim());
      Keyboard.dismiss();
    }
  }, [query]);

  const results: SearchResult[] = data?.results || data?.moments || [];

  const renderSearchTypeChip = (type: SearchType, label: string) => (
    <TouchableOpacity
      key={type}
      style={[styles.chip, searchType === type && styles.chipActive]}
      onPress={() => setSearchType(type)}
      accessibilityRole="button"
      accessibilityState={{ selected: searchType === type }}
      accessibilityLabel={`${label} search`}
    >
      <Text style={[styles.chipText, searchType === type && styles.chipTextActive]}>
        {label}
      </Text>
    </TouchableOpacity>
  );

  const renderResult = ({ item }: { item: SearchResult }) => (
    <TouchableOpacity
      style={styles.resultCard}
      accessibilityRole="button"
      accessibilityLabel={`Search result: ${item.content.substring(0, 50)}`}
    >
      <Text style={styles.resultContent} numberOfLines={3}>
        {item.content}
      </Text>
      {item.sphereName && (
        <View style={styles.sphereBadge}>
          <Ionicons name="grid-outline" size={12} color="#6366f1" />
          <Text style={styles.sphereText}>{item.sphereName}</Text>
        </View>
      )}
      <View style={styles.resultMeta}>
        {item.tags?.length > 0 && (
          <View style={styles.tagsRow}>
            {item.tags.slice(0, 3).map((tag) => (
              <Text key={tag} style={styles.tag}>#{tag}</Text>
            ))}
          </View>
        )}
        <Text style={styles.resultDate}>
          {formatDistanceToNow(new Date(item.createdAt), { addSuffix: true })}
        </Text>
      </View>
      {item.score !== undefined && (
        <View style={styles.scoreBadge}>
          <Text style={styles.scoreText}>
            {Math.round(item.score * 100)}% match
          </Text>
        </View>
      )}
    </TouchableOpacity>
  );

  return (
    <View style={styles.container}>
      {/* Search Bar */}
      <View style={styles.searchBar}>
        <Ionicons name="search" size={20} color="#94a3b8" style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          value={query}
          onChangeText={setQuery}
          placeholder="Search your moments..."
          placeholderTextColor="#94a3b8"
          returnKeyType="search"
          onSubmitEditing={handleSearch}
          accessibilityLabel="Search input"
          accessibilityHint="Type a query and press search"
        />
        {query.length > 0 && (
          <TouchableOpacity
            onPress={() => { setQuery(''); setSubmittedQuery(''); }}
            accessibilityLabel="Clear search"
          >
            <Ionicons name="close-circle" size={20} color="#94a3b8" />
          </TouchableOpacity>
        )}
      </View>

      {/* Search Type Chips */}
      <View style={styles.chipRow}>
        {renderSearchTypeChip('hybrid', 'Hybrid AI')}
        {renderSearchTypeChip('semantic', 'Semantic')}
        {renderSearchTypeChip('keyword', 'Keyword')}
      </View>

      {/* Results */}
      {(isLoading || isFetching) ? (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color="#0ea5e9" />
          <Text style={styles.loadingText}>Searching...</Text>
        </View>
      ) : submittedQuery && results.length === 0 ? (
        <View style={styles.centered}>
          <Ionicons name="search-outline" size={48} color="#cbd5e1" />
          <Text style={styles.emptyTitle}>No results found</Text>
          <Text style={styles.emptySubtitle}>
            Try different keywords or switch search type
          </Text>
        </View>
      ) : !submittedQuery ? (
        <View style={styles.centered}>
          <Ionicons name="sparkles-outline" size={48} color="#cbd5e1" />
          <Text style={styles.emptyTitle}>Search your moments</Text>
          <Text style={styles.emptySubtitle}>
            Use AI-powered search to find moments by meaning, not just keywords
          </Text>
        </View>
      ) : (
        <FlatList
          data={results}
          keyExtractor={(item) => item.id}
          renderItem={renderResult}
          contentContainerStyle={styles.resultsList}
          showsVerticalScrollIndicator={false}
          ListHeaderComponent={
            <Text style={styles.resultsCount}>
              {data?.totalCount || results.length} result{results.length !== 1 ? 's' : ''}
            </Text>
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    margin: 16,
    marginBottom: 8,
    paddingHorizontal: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    height: 48,
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    fontSize: 16,
    color: '#1e293b',
  },
  chipRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    marginBottom: 12,
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  chipActive: {
    backgroundColor: '#0ea5e9',
    borderColor: '#0ea5e9',
  },
  chipText: {
    fontSize: 13,
    color: '#64748b',
    fontWeight: '500',
  },
  chipTextActive: {
    color: '#ffffff',
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  loadingText: {
    marginTop: 12,
    color: '#64748b',
    fontSize: 14,
  },
  emptyTitle: {
    marginTop: 16,
    fontSize: 18,
    fontWeight: '600',
    color: '#334155',
  },
  emptySubtitle: {
    marginTop: 8,
    fontSize: 14,
    color: '#94a3b8',
    textAlign: 'center',
    lineHeight: 20,
  },
  resultsCount: {
    fontSize: 13,
    color: '#64748b',
    marginBottom: 8,
  },
  resultsList: {
    padding: 16,
    paddingTop: 4,
  },
  resultCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  resultContent: {
    fontSize: 15,
    color: '#1e293b',
    lineHeight: 22,
    marginBottom: 8,
  },
  sphereBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginBottom: 8,
  },
  sphereText: {
    fontSize: 12,
    color: '#6366f1',
    fontWeight: '500',
  },
  resultMeta: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  tagsRow: {
    flexDirection: 'row',
    gap: 6,
  },
  tag: {
    fontSize: 12,
    color: '#0ea5e9',
    fontWeight: '500',
  },
  resultDate: {
    fontSize: 12,
    color: '#94a3b8',
  },
  scoreBadge: {
    position: 'absolute',
    top: 12,
    right: 12,
    backgroundColor: '#f0fdf4',
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  scoreText: {
    fontSize: 11,
    color: '#16a34a',
    fontWeight: '600',
  },
});
