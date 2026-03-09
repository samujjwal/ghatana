import React from 'react';
// Diagnostic hook for Jest: prints when this module is evaluated
/* istanbul ignore next */
console.log('[JEST-DIAG] AlertsScreen module loaded');
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
} from 'react-native';
import { useAlerts, useMarkAlertRead } from '@/hooks/useApi';
import { formatRelativeTime } from '@/utils/format';

const AlertsScreen: React.FC = () => {
  const { data: alerts, isLoading, refetch } = useAlerts();
  const markAlertRead = useMarkAlertRead();
  const [refreshing, setRefreshing] = React.useState(false);

  const onRefresh = React.useCallback(async () => {
    setRefreshing(true);
    await refetch();
    setRefreshing(false);
  }, [refetch]);

  const handleMarkRead = (id: string) => {
    markAlertRead.mutate(id);
  };

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <Text style={styles.loadingText}>Loading alerts...</Text>
      </View>
    );
  }

  if (!alerts || alerts.length === 0) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyIcon}>✅</Text>
        <Text style={styles.emptyTitle}>All Clear!</Text>
        <Text style={styles.emptySubtitle}>You have no alerts at this time</Text>
      </View>
    );
  }

  const getAlertIcon = (type: string) => {
    switch (type) {
      case 'policy_violation':
        return '⚠️';
      case 'device_offline':
        return '📴';
      case 'battery_low':
        return '🔋';
      case 'location_alert':
        return '📍';
      default:
        return 'ℹ️';
    }
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical':
        return { bg: '#fee2e2', border: '#ef4444', text: '#991b1b' };
      case 'warning':
        return { bg: '#fef3c7', border: '#f59e0b', text: '#92400e' };
      case 'info':
        return { bg: '#dbeafe', border: '#3b82f6', text: '#1e40af' };
      default:
        return { bg: '#f3f4f6', border: '#6b7280', text: '#374151' };
    }
  };

  const unreadAlerts = alerts.filter((a) => !a.read);
  const readAlerts = alerts.filter((a) => a.read);

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      {unreadAlerts.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Unread ({unreadAlerts.length})</Text>
          {unreadAlerts.map((alert) => {
            const colors = getSeverityColor(alert.severity);
            return (
              <View
                key={alert.id}
                style={[
                  styles.alertCard,
                  {
                    backgroundColor: colors.bg,
                    borderLeftColor: colors.border,
                  },
                ]}
              >
                <View style={styles.alertHeader}>
                  <Text style={styles.alertIcon}>{getAlertIcon(alert.type)}</Text>
                  <View style={styles.alertContent}>
                    <Text style={[styles.alertMessage, { color: colors.text }]}>
                      {alert.message}
                    </Text>
                    <Text style={styles.alertTime}>
                      {formatRelativeTime(alert.timestamp)}
                    </Text>
                  </View>
                </View>
                <TouchableOpacity
                  style={styles.markReadButton}
                  onPress={() => handleMarkRead(alert.id)}
                >
                  <Text style={styles.markReadText}>Mark as Read</Text>
                </TouchableOpacity>
              </View>
            );
          })}
        </View>
      )}

      {readAlerts.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Read ({readAlerts.length})</Text>
          {readAlerts.map((alert) => {
            const colors = getSeverityColor(alert.severity);
            return (
              <View
                key={alert.id}
                style={[
                  styles.alertCard,
                  styles.readAlert,
                  {
                    backgroundColor: colors.bg,
                    borderLeftColor: colors.border,
                  },
                ]}
              >
                <View style={styles.alertHeader}>
                  <Text style={styles.alertIcon}>{getAlertIcon(alert.type)}</Text>
                  <View style={styles.alertContent}>
                    <Text style={[styles.alertMessage, { color: colors.text }]}>
                      {alert.message}
                    </Text>
                    <Text style={styles.alertTime}>
                      {formatRelativeTime(alert.timestamp)}
                    </Text>
                  </View>
                </View>
              </View>
            );
          })}
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
    padding: 24,
  },
  loadingText: {
    fontSize: 18,
    color: '#6b7280',
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: 16,
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
    textAlign: 'center',
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
  alertCard: {
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    borderLeftWidth: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  readAlert: {
    opacity: 0.7,
  },
  alertHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  alertIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  alertContent: {
    flex: 1,
  },
  alertMessage: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 4,
  },
  alertTime: {
    fontSize: 12,
    color: '#6b7280',
  },
  markReadButton: {
    marginTop: 12,
    alignSelf: 'flex-end',
  },
  markReadText: {
    fontSize: 14,
    color: '#3b82f6',
    fontWeight: '600',
  },
});

export default AlertsScreen;
