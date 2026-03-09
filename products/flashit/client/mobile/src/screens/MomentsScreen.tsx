/**
 * Moments Screen for Flashit Mobile
 * Browse, search, and manage captured moments
 *
 * @doc.type screen
 * @doc.purpose Display and manage user's Moments on mobile
 * @doc.layer product
 * @doc.pattern MobileScreen
 */

import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  FlatList,
  Alert,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { RootStackParamList } from '../navigation';
import { useApi } from '../contexts/ApiContext';
import { Moment } from '@ghatana/flashit-shared';
import { formatDistanceToNow, format } from 'date-fns';

type Props = NativeStackScreenProps<RootStackParamList, 'Moments'>;

export default function MomentsScreen({ navigation }: Props) {
  const { apiClient } = useApi();
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState('');
  const [refreshing, setRefreshing] = useState(false);

  // Fetch moments
  const {
    data: momentsData,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ['moments', searchQuery],
    queryFn: async () => {
      // Use Enhanced AI Search if query is present
      if (searchQuery) {
        const result = await apiClient.search({
          query: searchQuery,
          type: 'hybrid', // AI + Keyword
          limit: 50,
        });
        return { moments: result.results }; // Transform search result to expected format
      }
      
      // Fallback to standard list if no query
      return apiClient.getMoments({
        limit: 50,
      });
    }
  });

  // Delete moment mutation
  const deleteMoment = useMutation({
    mutationFn: (id: string) => apiClient.deleteMoment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['moments'] });
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
    },
    onError: (error: any) => {
      Alert.alert('Error', error.response?.data?.message || 'Failed to delete moment');
    },
  });

  const handleDelete = useCallback(
    (moment: Moment) => {
      Alert.alert('Delete Moment', 'Are you sure you want to delete this moment?', [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => deleteMoment.mutate(moment.id),
        },
      ]);
    },
    [deleteMoment]
  );

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await refetch();
    setRefreshing(false);
  }, [refetch]);

  const renderMoment = useCallback(
    ({ item }: { item: Moment }) => (
      <View 
        style={styles.momentCard}
        accessible={true}
        accessibilityRole="button"
        accessibilityLabel={`Moment in ${item.sphere.name}. ${item.contentText}. Captured ${formatDistanceToNow(new Date(item.capturedAt), { addSuffix: true })}`}
        accessibilityHint="Double tap to view moment details"
      >
        <View style={styles.momentHeader}>
          <View 
            style={styles.sphereBadge}
            accessible={true}
            accessibilityLabel={`Sphere: ${item.sphere.name}`}
          >
            <Text style={styles.sphereBadgeText}>{item.sphere.name}</Text>
          </View>
          <Text 
            style={styles.momentTime}
            accessibilityLabel={formatDistanceToNow(new Date(item.capturedAt), { addSuffix: true })}
          >
            {formatDistanceToNow(new Date(item.capturedAt), { addSuffix: true })}
          </Text>
        </View>

        <Text 
          style={styles.momentText}
          accessibilityLabel={`Content: ${item.contentText}`}
        >
          {item.contentText}
        </Text>

        {item.emotions.length > 0 && (
          <View 
            style={styles.tagsRow}
            accessible={true}
            accessibilityLabel={`Emotions: ${item.emotions.join(', ')}`}
          >
            {item.emotions.map((emotion) => (
              <View key={emotion} style={styles.emotionTag}>
                <Text style={styles.emotionTagText}>{emotion}</Text>
              </View>
            ))}
          </View>
        )}

        {item.tags.length > 0 && (
          <View 
            style={styles.tagsRow}
            accessible={true}
            accessibilityLabel={`Tags: ${item.tags.join(', ')}`}
          >
            {item.tags.map((tag) => (
              <View key={tag} style={styles.tagBadge}>
                <Text style={styles.tagBadgeText}>#{tag}</Text>
              </View>
            ))}
          </View>
        )}

        <View style={styles.momentFooter}>
          <Text 
            style={styles.dateText}
            accessibilityLabel={format(new Date(item.capturedAt), 'MMM d, yyyy')}
          >
            {format(new Date(item.capturedAt), 'MMM d, yyyy')}
          </Text>
          <TouchableOpacity
            style={styles.deleteButton}
            onPress={() => handleDelete(item)}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Delete moment"
            accessibilityHint="Double tap to delete this moment"
          >
            <Text style={styles.deleteButtonText}>Delete</Text>
          </TouchableOpacity>
        </View>
      </View>
    ),
    [handleDelete]
  );

  const renderEmpty = useCallback(
    () => (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyText}>No moments found</Text>
        <Text style={styles.emptySubtext}>
          {searchQuery ? 'Try a different search' : 'Capture your first moment!'}
        </Text>
        {!searchQuery && (
          <TouchableOpacity
            style={styles.captureButton}
            onPress={() => navigation.navigate('Capture')}
          >
            <Text style={styles.captureButtonText}>Capture Moment</Text>
          </TouchableOpacity>
        )}
      </View>
    ),
    [searchQuery, navigation]
  );

  if (isLoading && !refreshing) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#0ea5e9" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <TextInput
          style={styles.searchInput}
          placeholder="Search moments..."
          value={searchQuery}
          onChangeText={setSearchQuery}
          returnKeyType="search"
        />
        {searchQuery.length > 0 && (
          <TouchableOpacity
            style={styles.clearButton}
            onPress={() => setSearchQuery('')}
          >
            <Text style={styles.clearButtonText}>×</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Results Count */}
      {momentsData && (
        <View style={styles.resultsInfo}>
          <Text style={styles.resultsText}>
            {momentsData.moments.length} of {momentsData.totalCount} moments
          </Text>
        </View>
      )}

      {/* Moments List */}
      <FlatList
        data={momentsData?.moments || []}
        renderItem={renderMoment}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={renderEmpty}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor="#0ea5e9"
          />
        }
        showsVerticalScrollIndicator={false}
      />

      {/* FAB */}
      <TouchableOpacity
        style={styles.fab}
        onPress={() => navigation.navigate('Capture')}
      >
        <Text style={styles.fabText}>+</Text>
      </TouchableOpacity>
    </View>
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
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    margin: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    paddingHorizontal: 12,
  },
  searchInput: {
    flex: 1,
    padding: 14,
    fontSize: 16,
  },
  clearButton: {
    padding: 8,
  },
  clearButtonText: {
    fontSize: 24,
    color: '#94a3b8',
  },
  resultsInfo: {
    paddingHorizontal: 16,
    paddingBottom: 8,
  },
  resultsText: {
    fontSize: 13,
    color: '#64748b',
  },
  listContent: {
    padding: 16,
    paddingTop: 0,
    paddingBottom: 100,
  },
  momentCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#0ea5e9',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  momentHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  sphereBadge: {
    backgroundColor: '#e0f2fe',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
  },
  sphereBadgeText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#0284c7',
  },
  momentTime: {
    fontSize: 11,
    color: '#94a3b8',
  },
  momentText: {
    fontSize: 15,
    color: '#334155',
    lineHeight: 22,
    marginBottom: 10,
  },
  tagsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginBottom: 8,
  },
  emotionTag: {
    backgroundColor: '#f3e8ff',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 4,
  },
  emotionTagText: {
    fontSize: 11,
    color: '#7c3aed',
  },
  tagBadge: {
    backgroundColor: '#f1f5f9',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 4,
  },
  tagBadgeText: {
    fontSize: 11,
    color: '#475569',
  },
  momentFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 6,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
  },
  dateText: {
    fontSize: 11,
    color: '#94a3b8',
  },
  deleteButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  deleteButtonText: {
    fontSize: 12,
    color: '#ef4444',
    fontWeight: '500',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#475569',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#94a3b8',
    marginBottom: 20,
  },
  captureButton: {
    backgroundColor: '#0ea5e9',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  captureButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
  },
  fab: {
    position: 'absolute',
    right: 20,
    bottom: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#0ea5e9',
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 8,
  },
  fabText: {
    fontSize: 28,
    fontWeight: '300',
    color: '#fff',
    marginTop: -2,
  },
});

