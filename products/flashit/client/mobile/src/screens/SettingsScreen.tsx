import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Switch,
  TouchableOpacity,
  ScrollView,
  Alert,
  Platform,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import { backgroundUploadService } from '../services/backgroundUploadService';
import { offlineQueueService } from '../services/offlineQueue';
import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Settings Screen
 * 
 * Allows users to configure:
 * - Background upload settings
 * - Network preferences (WiFi only)
 * - Upload quality settings
 * - Cache management
 */

const SETTINGS_KEYS = {
  BACKGROUND_UPLOADS: 'settings:backgroundUploads',
  WIFI_ONLY: 'settings:wifiOnly',
  AUTO_COMPRESS: 'settings:autoCompress',
  UPLOAD_QUALITY: 'settings:uploadQuality',
};

type UploadQuality = 'high' | 'medium' | 'low';

export function SettingsScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<any>>();
  const [backgroundUploadsEnabled, setBackgroundUploadsEnabled] = useState(false);
  const [wifiOnly, setWifiOnly] = useState(false);
  const [autoCompress, setAutoCompress] = useState(true);
  const [uploadQuality, setUploadQuality] = useState<UploadQuality>('medium');
  const [backgroundTaskStatus, setBackgroundTaskStatus] = useState('Unknown');
  const [queueStats, setQueueStats] = useState({ total: 0, pending: 0, failed: 0 });

  useEffect(() => {
    loadSettings();
    checkBackgroundTaskStatus();
    loadQueueStats();
  }, []);

  const loadSettings = async () => {
    try {
      const [bgUploads, wifiPref, compress, quality] = await Promise.all([
        AsyncStorage.getItem(SETTINGS_KEYS.BACKGROUND_UPLOADS),
        AsyncStorage.getItem(SETTINGS_KEYS.WIFI_ONLY),
        AsyncStorage.getItem(SETTINGS_KEYS.AUTO_COMPRESS),
        AsyncStorage.getItem(SETTINGS_KEYS.UPLOAD_QUALITY),
      ]);

      setBackgroundUploadsEnabled(bgUploads !== null ? JSON.parse(bgUploads) : false);
      setWifiOnly(wifiPref !== null ? JSON.parse(wifiPref) : false);
      setAutoCompress(compress !== null ? JSON.parse(compress) : true); // Default true
      setUploadQuality((quality as UploadQuality) || 'medium');
    } catch (error) {
      console.error('Failed to load settings:', error);
    }
  };

  const checkBackgroundTaskStatus = async () => {
    try {
      const status = await backgroundUploadService.getStatus();
      setBackgroundTaskStatus(status.message);
    } catch (error) {
      setBackgroundTaskStatus('Error checking status');
    }
  };

  const loadQueueStats = async () => {
    try {
      const stats = await offlineQueueService.getQueueStats();
      setQueueStats(stats);
    } catch (error) {
      console.error('Failed to load queue stats:', error);
    }
  };

  const toggleBackgroundUploads = async (enabled: boolean) => {
    try {
      if (enabled) {
        // Register background task
        const success = await backgroundUploadService.register();
        if (success) {
          await AsyncStorage.setItem(SETTINGS_KEYS.BACKGROUND_UPLOADS, JSON.stringify(true));
          setBackgroundUploadsEnabled(true);
          Alert.alert(
            'Background Uploads Enabled',
            'Your moments will be uploaded automatically in the background.'
          );
        } else {
          Alert.alert(
            'Error',
            'Failed to enable background uploads. Please check app permissions.'
          );
        }
      } else {
        // Unregister background task
        await backgroundUploadService.unregister();
        await AsyncStorage.setItem(SETTINGS_KEYS.BACKGROUND_UPLOADS, JSON.stringify(false));
        setBackgroundUploadsEnabled(false);
        Alert.alert(
          'Background Uploads Disabled',
          'Moments will only upload when the app is open.'
        );
      }

      await checkBackgroundTaskStatus();
    } catch (error) {
      console.error('Failed to toggle background uploads:', error);
      Alert.alert('Error', 'Failed to update background upload setting.');
    }
  };

  const toggleWifiOnly = async (enabled: boolean) => {
    try {
      await AsyncStorage.setItem(SETTINGS_KEYS.WIFI_ONLY, JSON.stringify(enabled));
      setWifiOnly(enabled);
    } catch (error) {
      console.error('Failed to toggle WiFi only:', error);
    }
  };

  const toggleAutoCompress = async (enabled: boolean) => {
    try {
      await AsyncStorage.setItem(SETTINGS_KEYS.AUTO_COMPRESS, JSON.stringify(enabled));
      setAutoCompress(enabled);
    } catch (error) {
      console.error('Failed to toggle auto compress:', error);
    }
  };

  const setQuality = async (quality: UploadQuality) => {
    try {
      await AsyncStorage.setItem(SETTINGS_KEYS.UPLOAD_QUALITY, quality);
      setUploadQuality(quality);
    } catch (error) {
      console.error('Failed to set upload quality:', error);
    }
  };

  const clearCache = async () => {
    Alert.alert(
      'Clear Cache',
      'This will remove all locally stored data including pending uploads. Are you sure?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear',
          style: 'destructive',
          onPress: async () => {
            try {
              await offlineQueueService.clearAll();
              await loadQueueStats();
              Alert.alert('Success', 'Cache cleared successfully');
            } catch (error) {
              Alert.alert('Error', 'Failed to clear cache');
            }
          },
        },
      ]
    );
  };

  const retryFailedUploads = async () => {
    try {
      const queue = await offlineQueueService.getQueue();
      const failedItems = queue.filter((item) => item.status === 'failed');

      for (const item of failedItems) {
        await offlineQueueService.updateItemStatus(item.id, 'pending');
      }

      await loadQueueStats();
      Alert.alert('Success', `${failedItems.length} uploads queued for retry`);
    } catch (error) {
      Alert.alert('Error', 'Failed to retry uploads');
    }
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Settings</Text>

      {/* Background Uploads */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Background Uploads</Text>

        <View style={styles.settingRow}>
          <View style={styles.settingInfo}>
            <Text style={styles.settingLabel}>Enable Background Uploads</Text>
            <Text style={styles.settingDescription}>
              Upload moments automatically even when app is closed
            </Text>
          </View>
          <Switch
            value={backgroundUploadsEnabled}
            onValueChange={toggleBackgroundUploads}
            accessible={true}
            accessibilityLabel="Enable Background Uploads"
            accessibilityHint="Toggle automatic background uploads"
          />
        </View>

        <View style={styles.infoBox}>
          <Text style={styles.infoText}>Status: {backgroundTaskStatus}</Text>
        </View>
      </View>

      {/* Network Preferences */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Network Preferences</Text>

        <View style={styles.settingRow}>
          <View style={styles.settingInfo}>
            <Text style={styles.settingLabel}>Upload on WiFi Only</Text>
            <Text style={styles.settingDescription}>
              Save cellular data by uploading only on WiFi
            </Text>
          </View>
          <Switch 
            value={wifiOnly} 
            onValueChange={toggleWifiOnly} 
            accessible={true}
            accessibilityLabel="Upload on WiFi Only"
            accessibilityHint="Toggle to restrict uploads to WiFi networks"
          />
        </View>
      </View>

      {/* Upload Quality */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Upload Quality</Text>

        <View style={styles.settingRow}>
          <View style={styles.settingInfo}>
            <Text style={styles.settingLabel}>Auto-Compress Media</Text>
            <Text style={styles.settingDescription}>
              Reduce file size before uploading
            </Text>
          </View>
          <Switch 
            value={autoCompress} 
            onValueChange={toggleAutoCompress} 
            accessible={true}
            accessibilityLabel="Auto-Compress Media"
            accessibilityHint="Toggle automatic media compression"
          />
        </View>

        <View style={styles.qualityButtons}>
          {(['high', 'medium', 'low'] as UploadQuality[]).map((quality) => (
            <TouchableOpacity
              key={quality}
              style={[
                styles.qualityButton,
                uploadQuality === quality && styles.qualityButtonActive,
              ]}
              onPress={() => setQuality(quality)}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel={`Set upload quality to ${quality}`}
              accessibilityState={{ selected: uploadQuality === quality }}
            >
              <Text
                style={[
                  styles.qualityButtonText,
                  uploadQuality === quality && styles.qualityButtonTextActive,
                ]}
              >
                {quality.charAt(0).toUpperCase() + quality.slice(1)}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.infoBox}>
          <Text style={styles.infoText}>
            High: Best quality, larger files{'\n'}
            Medium: Balanced quality and size{'\n'}
            Low: Fastest uploads, smaller files
          </Text>
        </View>
      </View>

      {/* Storage & Cache */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Storage & Cache</Text>

        <View style={styles.statsBox}>
          <Text style={styles.statsText}>Upload Queue: {queueStats.total} items</Text>
          <Text style={styles.statsText}>Pending: {queueStats.pending}</Text>
          <Text style={styles.statsText}>Failed: {queueStats.failed}</Text>
        </View>

        {queueStats.failed > 0 && (
          <TouchableOpacity 
            style={styles.actionButton} 
            onPress={retryFailedUploads}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Retry only failed uploads"
          >
            <Text style={styles.actionButtonText}>Retry Failed Uploads</Text>
          </TouchableOpacity>
        )}

        <TouchableOpacity 
          style={styles.actionButtonDanger} 
          onPress={clearCache}
          accessible={true}
          accessibilityRole="button"
          accessibilityLabel="Clear all app cache"
          accessibilityHint="Double tap to delete all cached data"
        >
          <Text style={styles.actionButtonTextDanger}>Clear All Cache</Text>
        </TouchableOpacity>
      </View>

      {/* Quick Links */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>More</Text>

        {([
          { screen: 'Search', icon: 'search-outline' as const, label: 'Search Moments' },
          { screen: 'Analytics', icon: 'bar-chart-outline' as const, label: 'Analytics' },
          { screen: 'Billing', icon: 'card-outline' as const, label: 'Billing & Subscription' },
          { screen: 'Collaboration', icon: 'people-outline' as const, label: 'Collaboration' },
          { screen: 'Reflection', icon: 'bulb-outline' as const, label: 'Reflection & Insights' },
          { screen: 'MemoryExpansion', icon: 'extension-puzzle-outline' as const, label: 'Memory Expansion' },
          { screen: 'NotificationSettings', icon: 'notifications-outline' as const, label: 'Notification Settings' },
        ] as const).map(({ screen, icon, label }) => (
          <TouchableOpacity
            key={screen}
            style={styles.navRow}
            onPress={() => navigation.navigate(screen)}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel={`Navigate to ${label}`}
          >
            <Ionicons name={icon} size={22} color="#333" style={styles.navIcon} />
            <Text style={styles.navLabel}>{label}</Text>
            <Ionicons name="chevron-forward-outline" size={20} color="#999" />
          </TouchableOpacity>
        ))}
      </View>

      {/* Debug Info (Development Only) */}
      {__DEV__ && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Debug</Text>
          <TouchableOpacity
            style={styles.actionButton}
            onPress={async () => {
              await backgroundUploadService.triggerManually();
              Alert.alert('Debug', 'Manual upload triggered');
            }}
          >
            <Text style={styles.actionButtonText}>Trigger Manual Upload</Text>
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    padding: 20,
    backgroundColor: '#fff',
  },
  section: {
    backgroundColor: '#fff',
    marginTop: 20,
    padding: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 15,
    color: '#333',
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 15,
  },
  settingInfo: {
    flex: 1,
    marginRight: 15,
  },
  settingLabel: {
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 4,
    color: '#000',
  },
  settingDescription: {
    fontSize: 14,
    color: '#666',
  },
  infoBox: {
    backgroundColor: '#f0f0f0',
    padding: 10,
    borderRadius: 8,
    marginTop: 10,
  },
  infoText: {
    fontSize: 13,
    color: '#666',
  },
  qualityButtons: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 10,
  },
  qualityButton: {
    flex: 1,
    padding: 12,
    borderRadius: 8,
    backgroundColor: '#f0f0f0',
    alignItems: 'center',
  },
  qualityButtonActive: {
    backgroundColor: '#007AFF',
  },
  qualityButtonText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#666',
  },
  qualityButtonTextActive: {
    color: '#fff',
  },
  statsBox: {
    backgroundColor: '#f9f9f9',
    padding: 15,
    borderRadius: 8,
    marginBottom: 15,
  },
  statsText: {
    fontSize: 14,
    marginBottom: 5,
    color: '#333',
  },
  actionButton: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 10,
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  actionButtonDanger: {
    backgroundColor: '#FF3B30',
    padding: 15,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 10,
  },
  actionButtonTextDanger: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  navRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e0e0e0',
  },
  navIcon: {
    marginRight: 12,
  },
  navLabel: {
    flex: 1,
    fontSize: 16,
    color: '#333',
  },
});
