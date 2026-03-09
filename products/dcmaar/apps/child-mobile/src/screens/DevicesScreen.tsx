import React from 'react';
// Diagnostic hook for Jest: prints when this module is evaluated
/* istanbul ignore next */
console.log('[JEST-DIAG] DevicesScreen module loaded');
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
} from 'react-native';
import { useDevices } from '@/hooks/useApi';
import { formatRelativeTime } from '@/utils/format';

const DevicesScreen: React.FC = () => {
  const { data: devices, isLoading, refetch } = useDevices();
  const [refreshing, setRefreshing] = React.useState(false);

  const onRefresh = React.useCallback(async () => {
    setRefreshing(true);
    await refetch();
    setRefreshing(false);
  }, [refetch]);

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <Text style={styles.loadingText}>Loading devices...</Text>
      </View>
    );
  }

  if (!devices || devices.length === 0) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyTitle}>No Devices</Text>
        <Text style={styles.emptySubtitle}>Add a device to get started</Text>
        <TouchableOpacity style={styles.addButton}>
          <Text style={styles.addButtonText}>+ Add Device</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.content}>
        {devices.map((device) => (
          <TouchableOpacity key={device.id} style={styles.deviceCard}>
            <View style={styles.deviceIcon}>
              <Text style={styles.deviceIconText}>
                {device.type === 'ios' ? '📱' : device.type === 'android' ? '🤖' : '💻'}
              </Text>
            </View>
            <View style={styles.deviceInfo}>
              <Text style={styles.deviceName}>{device.name}</Text>
              <Text style={styles.deviceChild}>{device.childName}</Text>
              <Text style={styles.deviceSync}>
                Last sync: {formatRelativeTime(device.lastSync)}
              </Text>
            </View>
            <View style={styles.deviceRight}>
              <View
                style={[
                  styles.statusIndicator,
                  { backgroundColor: device.status === 'online' ? '#10b981' : '#6b7280' },
                ]}
              />
              {device.batteryLevel !== undefined && (
                <View style={styles.batteryContainer}>
                  <Text style={styles.batteryText}>🔋 {device.batteryLevel}%</Text>
                </View>
              )}
              {device.location && (
                <Text style={styles.locationText}>📍 {device.location.address}</Text>
              )}
            </View>
          </TouchableOpacity>
        ))}
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f3f4f6',
  },
  content: {
    padding: 16,
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  loadingText: {
    fontSize: 18,
    color: '#6b7280',
  },
  emptyTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1f2937',
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 16,
    color: '#6b7280',
    marginBottom: 24,
  },
  addButton: {
    backgroundColor: '#3b82f6',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  addButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  deviceCard: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  deviceIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#e5e7eb',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  deviceIconText: {
    fontSize: 24,
  },
  deviceInfo: {
    flex: 1,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1f2937',
  },
  deviceChild: {
    fontSize: 14,
    color: '#6b7280',
    marginTop: 2,
  },
  deviceSync: {
    fontSize: 12,
    color: '#9ca3af',
    marginTop: 4,
  },
  deviceRight: {
    alignItems: 'flex-end',
  },
  statusIndicator: {
    width: 12,
    height: 12,
    borderRadius: 6,
    marginBottom: 8,
  },
  batteryContainer: {
    marginTop: 4,
  },
  batteryText: {
    fontSize: 12,
    color: '#6b7280',
  },
  locationText: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 4,
  },
});

export default DevicesScreen;
