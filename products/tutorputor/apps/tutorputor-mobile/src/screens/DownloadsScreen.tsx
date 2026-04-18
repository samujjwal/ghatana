/**
 * Downloads Screen
 *
 * Manage downloaded offline content.
 *
 * @doc.type component
 * @doc.purpose Offline content management
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
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { ProfileStackParamList } from '../navigation/types';
import { useDownloadedModules } from '../hooks/useOffline';

type Props = NativeStackScreenProps<ProfileStackParamList, 'Downloads'>;

interface DownloadedModule {
  id: string;
  title: string;
  description: string;
  downloadDate: string;
  sizeBytes: number;
  lessonsCount: number;
}

async function fetchDownloads(): Promise<DownloadedModule[]> {
  // In a real app, this would query SQLite storage
  return [];
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

export function DownloadsScreen({ navigation }: Props): React.ReactElement {
  const { modules, isLoading } = useDownloadedModules();

  const renderDownload = ({ item }: { item: DownloadedModule }) => (
    <View style={styles.downloadCard}>
      <View style={styles.downloadInfo}>
        <Text style={styles.downloadTitle}>{item.title}</Text>
        <Text style={styles.downloadMeta}>
          {item.lessonsCount} lessons • {formatBytes(item.sizeBytes)}
        </Text>
        <Text style={styles.downloadDate}>
          Downloaded {new Date(item.downloadDate).toLocaleDateString()}
        </Text>
      </View>
      <TouchableOpacity style={styles.deleteButton}>
        <Text style={styles.deleteIcon}>🗑️</Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={modules}
        renderItem={renderDownload}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>💾</Text>
            <Text style={styles.emptyTitle}>No downloads</Text>
            <Text style={styles.emptyText}>
              Download modules to learn offline. Browse modules and tap the download button.
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  listContent: {
    padding: 16,
  },
  downloadCard: {
    flexDirection: 'row',
    alignItems: 'center',
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
  downloadInfo: {
    flex: 1,
  },
  downloadTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 4,
  },
  downloadMeta: {
    fontSize: 13,
    color: '#6B7280',
    marginBottom: 2,
  },
  downloadDate: {
    fontSize: 12,
    color: '#9CA3AF',
  },
  deleteButton: {
    padding: 8,
  },
  deleteIcon: {
    fontSize: 20,
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
