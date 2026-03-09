import React from 'react';
// Diagnostic hook for Jest: prints when this module is evaluated
/* istanbul ignore next */
console.log('[JEST-DIAG] DashboardScreen module loaded');
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
} from 'react-native';
import { useDevices, useAlerts } from '@/hooks/useApi';
import { useCanSeeSections } from '@ghatana/dcmaar-dashboard-core';

const DashboardScreen: React.FC = () => {
  const { data: devices, isLoading: devicesLoading, refetch: refetchDevices } = useDevices();
  const { data: alerts, isLoading: alertsLoading, refetch: refetchAlerts } = useAlerts();

  const [refreshing, setRefreshing] = React.useState(false);

  const canSeeDevices = useCanSeeSections('devices');
  const canSeeAlerts = useCanSeeSections('alerts');
  const canSeeQuickActions = useCanSeeSections('policies');

  const onRefresh = React.useCallback(async () => {
    setRefreshing(true);
    await Promise.all([refetchDevices(), refetchAlerts()]);
    setRefreshing(false);
  }, [refetchDevices, refetchAlerts]);

  if (devicesLoading || alertsLoading) {
    return (
      <View style={styles.centered}>
        <Text style={styles.loadingText}>Loading...</Text>
      </View>
    );
  }

  const onlineDevices = devices?.filter((d) => d.status === 'online').length || 0;
  const unreadAlerts = alerts?.filter((a) => !a.read).length || 0;

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      {/* Stats Cards */}
      <View style={styles.statsContainer}>
        <View style={styles.statCard}>
          <Text style={styles.statValue}>{devices?.length || 0}</Text>
          <Text style={styles.statLabel}>Total Devices</Text>
        </View>
        <View style={styles.statCard}>
          <Text style={[styles.statValue, { color: '#10b981' }]}>{onlineDevices}</Text>
          <Text style={styles.statLabel}>Online</Text>
        </View>
        <View style={styles.statCard}>
          <Text style={[styles.statValue, { color: '#f59e0b' }]}>{unreadAlerts}</Text>
          <Text style={styles.statLabel}>Active Alerts</Text>
        </View>
      </View>

      {/* Alerts Section */}
      {canSeeAlerts && unreadAlerts > 0 && (
        <View style={styles.section}>
          <View style={styles.alertBanner}>
            <Text style={styles.alertBannerText}>
              ⚠️ You have {unreadAlerts} unread alert{unreadAlerts > 1 ? 's' : ''}
            </Text>
          </View>
        </View>
      )}

      {/* Devices Section */}
      {canSeeDevices && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Devices</Text>
          {devices?.map((device) => (
            <TouchableOpacity key={device.id} style={styles.deviceCard}>
              <View style={styles.deviceHeader}>
                <View>
                  <Text style={styles.deviceName}>{device.name}</Text>
                  <Text style={styles.deviceChild}>{device.childName}</Text>
                </View>
                <View style={styles.deviceStatus}>
                  <View
                    style={[
                      styles.statusDot,
                      { backgroundColor: device.status === 'online' ? '#10b981' : '#6b7280' },
                    ]}
                  />
                  <Text style={styles.statusText}>{device.status}</Text>
                </View>
              </View>
              {device.batteryLevel !== undefined && (
                <View style={styles.deviceMeta}>
                  <Text style={styles.metaText}>🔋 {device.batteryLevel}%</Text>
                </View>
              )}
            </TouchableOpacity>
          ))}
        </View>
      )}

      {/* Quick Actions */}
      {canSeeQuickActions && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Quick Actions</Text>
          <TouchableOpacity style={styles.actionButton}>
            <Text style={styles.actionButtonText}>+ Add New Device</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.actionButton}>
            <Text style={styles.actionButtonText}>+ Create Policy</Text>
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f3f4f6',
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    fontSize: 18,
    color: '#6b7280',
  },
  statsContainer: {
    flexDirection: 'row',
    padding: 16,
    gap: 12,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  statValue: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1f2937',
  },
  statLabel: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 4,
  },
  section: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1f2937',
    marginBottom: 12,
  },
  alertBanner: {
    backgroundColor: '#fef3c7',
    padding: 16,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#f59e0b',
  },
  alertBannerText: {
    color: '#92400e',
    fontSize: 14,
  },
  deviceCard: {
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
  deviceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
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
  deviceStatus: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusText: {
    fontSize: 14,
    color: '#6b7280',
    textTransform: 'capitalize',
  },
  deviceMeta: {
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#e5e7eb',
  },
  metaText: {
    fontSize: 12,
    color: '#6b7280',
  },
  actionButton: {
    backgroundColor: '#3b82f6',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 12,
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});

export default DashboardScreen;
