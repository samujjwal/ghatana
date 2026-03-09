import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  Modal,
} from 'react-native';
import { offlineQueueService, QueuedItem } from '../services/offlineQueue';
import { uploadManager } from '../services/uploadManager';
import { mediaCompressionService } from '../services/mediaCompressionService';
import { uploadProgressService, UploadProgress } from '../services/uploadProgressService';
import { UploadItemCard, NetworkBadge, UploadStatsSummary } from './UploadItemCard';

/**
 * Upload Progress Indicator Component
 * 
 * @doc.type component
 * @doc.purpose Display upload queue status and progress
 * @doc.layer product
 * @doc.pattern Component
 */
export const UploadProgressIndicator: React.FC = () => {
  const [isVisible, setIsVisible] = useState(false);
  const [queue, setQueue] = useState<QueuedItem[]>([]);
  const [activeUploads, setActiveUploads] = useState<UploadProgress[]>([]);
  const [stats, setStats] = useState({
    total: 0,
    pending: 0,
    uploading: 0,
    failed: 0,
    completed: 0,
  });
  const [compressionStats, setCompressionStats] = useState({
    totalSaved: 0,
    averageRatio: 0,
  });
  const [uploadStats, setUploadStats] = useState({
    totalFiles: 0,
    completedFiles: 0,
    failedFiles: 0,
    totalBytes: 0,
    uploadedBytes: 0,
    overallProgress: 0,
    estimatedTotalTimeMs: 0,
    averageSpeedBps: 0,
  });

  useEffect(() => {
    loadQueue();
    const interval = setInterval(loadQueue, 2000); // Refresh every 2s
    
    // Subscribe to real-time upload progress
    const unsubscribe = uploadProgressService.subscribe((progressMap) => {
      const uploads = Array.from(progressMap.values());
      setActiveUploads(uploads);
      setUploadStats(uploadProgressService.getStats());
    });
    
    return () => {
      clearInterval(interval);
      unsubscribe();
    };
  }, []);

  const loadQueue = async () => {
    const queueData = await offlineQueueService.getQueue();
    const statsData = await offlineQueueService.getStats();
    const compressionStatsData = await mediaCompressionService.getCompressionStats();
    setQueue(queueData);
    setStats(statsData);
    setCompressionStats(compressionStatsData);
  };

  const handleRetry = async (id: string) => {
    await offlineQueueService.updateItemStatus(id, 'pending');
    await loadQueue();
  };

  const handleDelete = async (id: string) => {
    await offlineQueueService.removeItem(id);
    uploadProgressService.stopTracking(id);
    await loadQueue();
  };

  const handlePause = (id: string) => {
    uploadProgressService.pauseUpload(id);
  };

  const handleResume = (id: string) => {
    uploadProgressService.resumeUpload(id);
  };

  const getStatusColor = (status: QueuedItem['status']): string => {
    switch (status) {
      case 'pending':
        return '#888';
      case 'uploading':
        return '#007aff';
      case 'failed':
        return '#ff3b30';
      case 'completed':
        return '#34c759';
      default:
        return '#888';
    }
  };

  const getStatusText = (status: QueuedItem['status']): string => {
    switch (status) {
      case 'pending':
        return 'Pending';
      case 'uploading':
        return 'Uploading...';
      case 'failed':
        return 'Failed';
      case 'completed':
        return 'Completed';
      default:
        return 'Unknown';
    }
  };

  const getTypeIcon = (type: QueuedItem['type']): string => {
    switch (type) {
      case 'audio':
        return '🎙️';
      case 'image':
        return '📷';
      case 'video':
        return '🎥';
      case 'text':
        return '📝';
      default:
        return '📄';
    }
  };

  const renderItem = ({ item }: { item: QueuedItem }) => (
    <View style={styles.queueItem}>
      <View style={styles.itemHeader}>
        <Text style={styles.itemIcon}>{getTypeIcon(item.type)}</Text>
        <View style={styles.itemInfo}>
          <Text style={styles.itemType}>{item.type.toUpperCase()}</Text>
          <Text style={styles.itemTime}>
            {new Date(item.metadata.timestamp).toLocaleString()}
          </Text>
        </View>
        <View style={[styles.statusBadge, { backgroundColor: getStatusColor(item.status) }]}>
          <Text style={styles.statusText}>{getStatusText(item.status)}</Text>
        </View>
      </View>
      
      {item.status === 'failed' && (
        <View style={styles.itemActions}>
          <TouchableOpacity
            style={styles.retryButton}
            onPress={() => handleRetry(item.id)}
          >
            <Text style={styles.retryButtonText}>Retry</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.deleteButton}
            onPress={() => handleDelete(item.id)}
          >
            <Text style={styles.deleteButtonText}>Delete</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );

  if (stats.pending === 0 && stats.uploading === 0 && stats.failed === 0) {
    return null; // Don't show indicator if nothing to upload
  }

  return (
    <>
      {/* Floating Indicator */}
      <TouchableOpacity
        style={styles.floatingIndicator}
        onPress={() => setIsVisible(true)}
      >
        <View style={styles.indicatorContent}>
          <Text style={styles.indicatorIcon}>⬆️</Text>
          <Text style={styles.indicatorText}>
            {stats.uploading > 0 ? `Uploading ${stats.uploading}` : `${stats.pending} pending`}
          </Text>
        </View>
      </TouchableOpacity>

      {/* Full Queue Modal */}
      <Modal
        visible={isVisible}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => setIsVisible(false)}
      >
        <View style={styles.modalContainer}>
          <View style={styles.modalHeader}>
            <Text style={styles.modalTitle}>Upload Queue</Text>
            <View style={styles.headerRight}>
              <NetworkBadge />
              <TouchableOpacity onPress={() => setIsVisible(false)}>
                <Text style={styles.closeButton}>Done</Text>
              </TouchableOpacity>
            </View>
          </View>

          {/* Overall Progress Summary */}
          {uploadStats.totalFiles > 0 && (
            <View style={styles.summaryContainer}>
              <UploadStatsSummary
                totalFiles={uploadStats.totalFiles}
                completedFiles={uploadStats.completedFiles}
                failedFiles={uploadStats.failedFiles}
                overallProgress={uploadStats.overallProgress}
                estimatedTimeMs={uploadStats.estimatedTotalTimeMs}
                averageSpeed={uploadStats.averageSpeedBps}
              />
            </View>
          )}

          <View style={styles.statsContainer}>
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{stats.pending}</Text>
              <Text style={styles.statLabel}>Pending</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{stats.uploading}</Text>
              <Text style={styles.statLabel}>Uploading</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={[styles.statValue, styles.statValueFailed]}>{stats.failed}</Text>
              <Text style={styles.statLabel}>Failed</Text>
            </View>
            <View style={styles.statItem}>
              <Text style={[styles.statValue, styles.statValueCompleted]}>{stats.completed}</Text>
              <Text style={styles.statLabel}>Completed</Text>
            </View>
          </View>

          {/* Compression Stats */}
          {compressionStats.totalSaved > 0 && (
            <View style={styles.compressionContainer}>
              <Text style={styles.compressionTitle}>💾 Compression Savings</Text>
              <View style={styles.compressionStats}>
                <View style={styles.compressionStat}>
                  <Text style={styles.compressionValue}>
                    {(compressionStats.totalSaved / (1024 * 1024)).toFixed(1)} MB
                  </Text>
                  <Text style={styles.compressionLabel}>Total Saved</Text>
                </View>
                <View style={styles.compressionStat}>
                  <Text style={styles.compressionValue}>
                    {compressionStats.averageRatio.toFixed(0)}%
                  </Text>
                  <Text style={styles.compressionLabel}>Avg Reduction</Text>
                </View>
              </View>
            </View>
          )}

          {/* Active Uploads with Progress */}
          {activeUploads.length > 0 && (
            <View style={styles.activeUploadsContainer}>
              <Text style={styles.sectionTitle}>📤 Active Uploads</Text>
              {activeUploads.map((upload) => (
                <UploadItemCard
                  key={upload.id}
                  item={upload}
                  onRetry={handleRetry}
                  onDelete={handleDelete}
                  onPause={handlePause}
                  onResume={handleResume}
                />
              ))}
            </View>
          )}

          {/* Queue List */}
          <Text style={styles.sectionTitle}>📋 Queue</Text>
          <FlatList
            data={queue}
            renderItem={renderItem}
            keyExtractor={(item) => item.id}
            contentContainerStyle={styles.list}
            ListEmptyComponent={
              <Text style={styles.emptyText}>No items in queue</Text>
            }
          />
        </View>
      </Modal>
    </>
  );
};

const styles = StyleSheet.create({
  floatingIndicator: {
    position: 'absolute',
    bottom: 80,
    right: 20,
    backgroundColor: '#007aff',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 5,
  },
  indicatorContent: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  indicatorIcon: {
    fontSize: 16,
    marginRight: 8,
  },
  indicatorText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  modalContainer: {
    flex: 1,
    backgroundColor: '#fff',
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e5e5',
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '700',
  },
  closeButton: {
    fontSize: 16,
    color: '#007aff',
    fontWeight: '600',
  },
  summaryContainer: {
    padding: 16,
  },
  activeUploadsContainer: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 12,
    paddingHorizontal: 16,
    marginTop: 8,
  },
  statsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 24,
    fontWeight: '700',
    color: '#000',
  },
  statValueFailed: {
    color: '#ff3b30',
  },
  statValueCompleted: {
    color: '#34c759',
  },
  statLabel: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  list: {
    padding: 16,
  },
  queueItem: {
    backgroundColor: '#f9f9f9',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  itemHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  itemIcon: {
    fontSize: 32,
    marginRight: 12,
  },
  itemInfo: {
    flex: 1,
  },
  itemType: {
    fontSize: 14,
    fontWeight: '600',
    color: '#000',
  },
  itemTime: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#fff',
  },
  itemActions: {
    flexDirection: 'row',
    marginTop: 12,
    gap: 8,
  },
  retryButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: '#007aff',
    alignItems: 'center',
  },
  retryButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
  },
  deleteButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: '#ff3b30',
    alignItems: 'center',
  },
  deleteButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
  },
  emptyText: {
    textAlign: 'center',
    color: '#888',
    fontSize: 16,
    marginTop: 40,
  },
  compressionContainer: {
    backgroundColor: '#e3f2fd',
    padding: 16,
    marginHorizontal: 16,
    marginTop: 8,
    borderRadius: 12,
  },
  compressionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1976d2',
    marginBottom: 12,
  },
  compressionStats: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  compressionStat: {
    alignItems: 'center',
  },
  compressionValue: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1976d2',
  },
  compressionLabel: {
    fontSize: 12,
    color: '#1976d2',
    marginTop: 4,
  },
});
