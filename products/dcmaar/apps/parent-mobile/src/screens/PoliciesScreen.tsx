import React from 'react';
// Diagnostic hook for Jest: prints when this module is evaluated
/* istanbul ignore next */
console.log('[JEST-DIAG] PoliciesScreen module loaded');
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
} from 'react-native';
import { usePolicies, useDevices } from '@/hooks/useApi';

const PoliciesScreen: React.FC = () => {
  const { data: policies, isLoading, refetch } = usePolicies();
  const { data: devices } = useDevices();
  const [refreshing, setRefreshing] = React.useState(false);

  const onRefresh = React.useCallback(async () => {
    setRefreshing(true);
    await refetch();
    setRefreshing(false);
  }, [refetch]);

  const getDeviceName = (deviceId: string) => {
    return devices?.find((d) => d.id === deviceId)?.name || 'Unknown Device';
  };

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <Text style={styles.loadingText}>Loading policies...</Text>
      </View>
    );
  }

  if (!policies || policies.length === 0) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyTitle}>No Policies</Text>
        <Text style={styles.emptySubtitle}>Create a policy to manage device usage</Text>
        <TouchableOpacity style={styles.createButton}>
          <Text style={styles.createButtonText}>+ Create Policy</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.header}>
        <TouchableOpacity style={styles.createButton}>
          <Text style={styles.createButtonText}>+ Create Policy</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.content}>
        {policies.map((policy) => (
          <TouchableOpacity key={policy.id} style={styles.policyCard}>
            <View style={styles.policyHeader}>
              <View style={styles.policyTitleContainer}>
                <Text style={styles.policyName}>{policy.name}</Text>
                <View
                  style={[
                    styles.statusBadge,
                    { backgroundColor: policy.enabled ? '#d1fae5' : '#e5e7eb' },
                  ]}
                >
                  <Text
                    style={[
                      styles.statusText,
                      { color: policy.enabled ? '#065f46' : '#6b7280' },
                    ]}
                  >
                    {policy.enabled ? 'Active' : 'Inactive'}
                  </Text>
                </View>
              </View>
              <Text style={styles.deviceName}>{getDeviceName(policy.deviceId)}</Text>
            </View>

            <View style={styles.policyDetails}>
              {policy.screenTimeLimit && (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>⏱️ Screen Time Limit:</Text>
                  <Text style={styles.detailValue}>{policy.screenTimeLimit} min/day</Text>
                </View>
              )}

              {policy.blockedApps.length > 0 && (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>🚫 Blocked Apps:</Text>
                  <Text style={styles.detailValue}>{policy.blockedApps.length} apps</Text>
                </View>
              )}

              {policy.blockedWebsites.length > 0 && (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>🌐 Blocked Websites:</Text>
                  <Text style={styles.detailValue}>{policy.blockedWebsites.length} sites</Text>
                </View>
              )}

              {policy.timeRestrictions && policy.timeRestrictions.length > 0 && (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>🕐 Time Restrictions:</Text>
                  <Text style={styles.detailValue}>
                    {policy.timeRestrictions.length} schedule{policy.timeRestrictions.length > 1 ? 's' : ''}
                  </Text>
                </View>
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
    textAlign: 'center',
  },
  header: {
    padding: 16,
  },
  createButton: {
    backgroundColor: '#3b82f6',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  createButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  content: {
    padding: 16,
    paddingTop: 0,
  },
  policyCard: {
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
  policyHeader: {
    marginBottom: 12,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e7eb',
  },
  policyTitleContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  policyName: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1f2937',
    flex: 1,
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
  },
  deviceName: {
    fontSize: 14,
    color: '#6b7280',
  },
  policyDetails: {
    gap: 8,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  detailLabel: {
    fontSize: 14,
    color: '#6b7280',
  },
  detailValue: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1f2937',
  },
});

export default PoliciesScreen;
