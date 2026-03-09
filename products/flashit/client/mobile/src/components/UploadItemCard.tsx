import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Animated,
} from 'react-native';
import { uploadProgressService, UploadProgress } from '../services/uploadProgressService';
import { networkMonitor } from '../services/networkMonitor';

/**
 * Upload Item Card Component
 * 
 * @doc.type component
 * @doc.purpose Display individual upload with progress bar, ETA, and controls
 * @doc.layer product
 * @doc.pattern Component
 */

interface UploadItemCardProps {
  item: UploadProgress;
  onRetry?: (id: string) => void;
  onDelete?: (id: string) => void;
  onPause?: (id: string) => void;
  onResume?: (id: string) => void;
}

export const UploadItemCard: React.FC<UploadItemCardProps> = ({
  item,
  onRetry,
  onDelete,
  onPause,
  onResume,
}) => {
  const [progressAnim] = useState(new Animated.Value(item.progress / 100));

  useEffect(() => {
    Animated.timing(progressAnim, {
      toValue: item.progress / 100,
      duration: 300,
      useNativeDriver: false,
    }).start();
  }, [item.progress, progressAnim]);

  const getTypeIcon = (type: UploadProgress['fileType']): string => {
    switch (type) {
      case 'audio': return '🎙️';
      case 'image': return '📷';
      case 'video': return '🎥';
      case 'text': return '📝';
      default: return '📄';
    }
  };

  const getStatusColor = (status: UploadProgress['status']): string => {
    switch (status) {
      case 'queued': return '#888';
      case 'compressing': return '#9b59b6';
      case 'uploading': return '#007aff';
      case 'paused': return '#ff9500';
      case 'completed': return '#34c759';
      case 'failed': return '#ff3b30';
      default: return '#888';
    }
  };

  const getStatusText = (status: UploadProgress['status']): string => {
    switch (status) {
      case 'queued': return 'Queued';
      case 'compressing': return 'Compressing...';
      case 'uploading': return 'Uploading...';
      case 'paused': return 'Paused';
      case 'completed': return 'Completed';
      case 'failed': return 'Failed';
      default: return 'Unknown';
    }
  };

  const formatFileSize = uploadProgressService.formatFileSize;
  const formatTimeRemaining = uploadProgressService.formatTimeRemaining;
  const formatSpeed = uploadProgressService.formatSpeed;

  const showProgress = item.status === 'uploading' || item.status === 'compressing';
  const showPauseResume = item.status === 'uploading' || item.status === 'paused';
  const showRetry = item.status === 'failed';

  return (
    <View style={styles.container}>
      {/* Header Row */}
      <View style={styles.header}>
        <Text style={styles.icon}>{getTypeIcon(item.fileType)}</Text>
        <View style={styles.info}>
          <Text style={styles.fileName} numberOfLines={1}>
            {item.fileName}
          </Text>
          <Text style={styles.fileSize}>
            {formatFileSize(item.uploadedBytes)} / {formatFileSize(item.fileSizeBytes)}
          </Text>
        </View>
        <View style={[styles.statusBadge, { backgroundColor: getStatusColor(item.status) }]}>
          <Text style={styles.statusText}>{getStatusText(item.status)}</Text>
        </View>
      </View>

      {/* Progress Bar */}
      {(showProgress || item.progress > 0) && (
        <View style={styles.progressContainer}>
          <View style={styles.progressBackground}>
            <Animated.View
              style={[
                styles.progressBar,
                {
                  backgroundColor: getStatusColor(item.status),
                  width: progressAnim.interpolate({
                    inputRange: [0, 1],
                    outputRange: ['0%', '100%'],
                  }),
                },
              ]}
            />
          </View>
          <Text style={styles.progressText}>{Math.round(item.progress)}%</Text>
        </View>
      )}

      {/* ETA and Speed Row */}
      {showProgress && (
        <View style={styles.statsRow}>
          <View style={styles.stat}>
            <Text style={styles.statLabel}>Speed</Text>
            <Text style={styles.statValue}>{formatSpeed(item.uploadSpeedBps)}</Text>
          </View>
          <View style={styles.stat}>
            <Text style={styles.statLabel}>Remaining</Text>
            <Text style={styles.statValue}>
              {formatTimeRemaining(item.estimatedTimeRemainingMs)}
            </Text>
          </View>
        </View>
      )}

      {/* Error Message */}
      {item.error && (
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>❌ {item.error}</Text>
        </View>
      )}

      {/* Action Buttons */}
      {(showPauseResume || showRetry) && (
        <View style={styles.actions}>
          {showPauseResume && (
            <TouchableOpacity
              style={[styles.actionButton, styles.pauseButton]}
              onPress={() => {
                if (item.isPaused) {
                  onResume?.(item.id);
                } else {
                  onPause?.(item.id);
                }
              }}
            >
              <Text style={styles.actionButtonText}>
                {item.isPaused ? '▶️ Resume' : '⏸️ Pause'}
              </Text>
            </TouchableOpacity>
          )}
          {showRetry && (
            <>
              <TouchableOpacity
                style={[styles.actionButton, styles.retryButton]}
                onPress={() => onRetry?.(item.id)}
              >
                <Text style={styles.actionButtonText}>🔄 Retry</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.actionButton, styles.deleteButton]}
                onPress={() => onDelete?.(item.id)}
              >
                <Text style={styles.actionButtonText}>🗑️ Delete</Text>
              </TouchableOpacity>
            </>
          )}
        </View>
      )}
    </View>
  );
};

/**
 * Network Quality Badge Component
 */
export const NetworkBadge: React.FC = () => {
  const [badge, setBadge] = useState(uploadProgressService.getNetworkBadge());

  useEffect(() => {
    const unsubscribe = networkMonitor.subscribe(() => {
      setBadge(uploadProgressService.getNetworkBadge());
    });
    return unsubscribe;
  }, []);

  return (
    <View style={[styles.networkBadge, { backgroundColor: badge.color }]}>
      <Text style={styles.networkBadgeIcon}>{badge.icon}</Text>
      <Text style={styles.networkBadgeText}>{badge.label}</Text>
    </View>
  );
};

/**
 * Overall Upload Stats Component
 */
interface UploadStatsSummaryProps {
  totalFiles: number;
  completedFiles: number;
  failedFiles: number;
  overallProgress: number;
  estimatedTimeMs: number;
  averageSpeed: number;
}

export const UploadStatsSummary: React.FC<UploadStatsSummaryProps> = ({
  totalFiles,
  completedFiles,
  failedFiles,
  overallProgress,
  estimatedTimeMs,
  averageSpeed,
}) => {
  const formatTimeRemaining = uploadProgressService.formatTimeRemaining;
  const formatSpeed = uploadProgressService.formatSpeed;

  return (
    <View style={styles.statsSummary}>
      <View style={styles.overallProgressContainer}>
        <View style={styles.overallProgressBackground}>
          <View
            style={[
              styles.overallProgressBar,
              { width: `${Math.min(100, overallProgress)}%` },
            ]}
          />
        </View>
        <Text style={styles.overallProgressText}>
          {Math.round(overallProgress)}% Complete
        </Text>
      </View>
      
      <View style={styles.summaryStats}>
        <View style={styles.summaryStat}>
          <Text style={styles.summaryStatValue}>{completedFiles}/{totalFiles}</Text>
          <Text style={styles.summaryStatLabel}>Files</Text>
        </View>
        {failedFiles > 0 && (
          <View style={styles.summaryStat}>
            <Text style={[styles.summaryStatValue, styles.failedValue]}>{failedFiles}</Text>
            <Text style={styles.summaryStatLabel}>Failed</Text>
          </View>
        )}
        <View style={styles.summaryStat}>
          <Text style={styles.summaryStatValue}>{formatSpeed(averageSpeed)}</Text>
          <Text style={styles.summaryStatLabel}>Speed</Text>
        </View>
        <View style={styles.summaryStat}>
          <Text style={styles.summaryStatValue}>{formatTimeRemaining(estimatedTimeMs)}</Text>
          <Text style={styles.summaryStatLabel}>ETA</Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#f9f9f9',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  icon: {
    fontSize: 32,
    marginRight: 12,
  },
  info: {
    flex: 1,
  },
  fileName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#000',
    marginBottom: 2,
  },
  fileSize: {
    fontSize: 12,
    color: '#666',
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 10,
  },
  statusText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#fff',
  },
  progressContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
  },
  progressBackground: {
    flex: 1,
    height: 6,
    backgroundColor: '#e0e0e0',
    borderRadius: 3,
    overflow: 'hidden',
    marginRight: 8,
  },
  progressBar: {
    height: '100%',
    borderRadius: 3,
  },
  progressText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#666',
    minWidth: 40,
    textAlign: 'right',
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#e0e0e0',
  },
  stat: {
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 10,
    color: '#888',
    marginBottom: 2,
  },
  statValue: {
    fontSize: 12,
    fontWeight: '600',
    color: '#333',
  },
  errorContainer: {
    marginTop: 8,
    padding: 8,
    backgroundColor: '#ffe5e5',
    borderRadius: 6,
  },
  errorText: {
    fontSize: 12,
    color: '#ff3b30',
  },
  actions: {
    flexDirection: 'row',
    marginTop: 12,
    gap: 8,
  },
  actionButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  pauseButton: {
    backgroundColor: '#ff9500',
  },
  retryButton: {
    backgroundColor: '#007aff',
  },
  deleteButton: {
    backgroundColor: '#ff3b30',
  },
  actionButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
  },
  networkBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 12,
    gap: 4,
  },
  networkBadgeIcon: {
    fontSize: 14,
  },
  networkBadgeText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#fff',
  },
  statsSummary: {
    backgroundColor: '#f5f5f5',
    padding: 16,
    borderRadius: 12,
  },
  overallProgressContainer: {
    marginBottom: 12,
  },
  overallProgressBackground: {
    height: 8,
    backgroundColor: '#ddd',
    borderRadius: 4,
    overflow: 'hidden',
    marginBottom: 4,
  },
  overallProgressBar: {
    height: '100%',
    backgroundColor: '#007aff',
    borderRadius: 4,
  },
  overallProgressText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    textAlign: 'center',
  },
  summaryStats: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  summaryStat: {
    alignItems: 'center',
  },
  summaryStatValue: {
    fontSize: 16,
    fontWeight: '700',
    color: '#333',
  },
  failedValue: {
    color: '#ff3b30',
  },
  summaryStatLabel: {
    fontSize: 10,
    color: '#888',
    marginTop: 2,
  },
});
