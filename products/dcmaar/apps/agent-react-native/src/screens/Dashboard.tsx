/**
 * Dashboard Screen - Agent Status & Monitoring
 *
 * Main screen displaying:
 * - Current authentication status
 * - Active monitoring state
 * - Device information and status
 * - Current app being monitored
 * - Recent activity summary
 * - Quick navigation actions
 *
 * Uses Jotai stores (Auth, Device, Monitoring, Policy, WebSocket) for state management.
 *
 * @doc.type component
 * @doc.purpose Main dashboard screen with status overview
 * @doc.layer product
 * @doc.pattern Screen (Container Component)
 */

import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
// Using basic navigation prop type since we're having module resolution issues
type NavigationProp = {
  navigate: (screen: string) => void;
};
import { useIsAuthenticated, useDevice, useSelectedDevice } from '../hooks/useStores';
import { Guardian } from '../hooks/useNativeModule';

// Simple navigation type since we're having module resolution issues

export function Dashboard() {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const navigation = useNavigation<NavigationProp>();
  const isAuthenticated = useIsAuthenticated();
  const { devices } = useDevice();
  const selectedDevice = useSelectedDevice();
  const [currentApp, setCurrentApp] = useState<string>('Unknown');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;
    
    const init = async () => {
      try {
        const subscription = Guardian.AccessibilityService.onAppChange((app) => {
          if (isMounted) {
            setCurrentApp(app.appName);
          }
        });

        // Initial app
        const app = await Guardian.AccessibilityService.getCurrentApp();
        if (isMounted) {
          setCurrentApp(app.appName);
          setIsLoading(false);
        }

        return () => {
          subscription.remove();
        };
      } catch (error) {
        console.error('Error initializing dashboard:', error);
        if (isMounted) {
          setIsLoading(false);
        }
        return () => {}; // Return cleanup function even on error
      }
    };

    init();
    
    return () => {
      isMounted = false;
    };
  }, []);

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text>Loading...</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Guardian Dashboard</Text>
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Current Status</Text>
        <Text>Authenticated: {isAuthenticated ? 'Yes' : 'No'}</Text>
        <Text>Devices: {devices.length}</Text>
        <Text>Selected Device: {selectedDevice?.name || 'None'}</Text>
        <Text>Current App: {currentApp}</Text>
      </View>
    </ScrollView>
  );
}

/**
 * InfoRow Component - Display label/value pairs
 *
 * @param label - Left side label text
 * @param value - Right side value text
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}:</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

// ============================================================================
// STYLES
// ============================================================================

const styles = StyleSheet.create({
  container: {
    flex: 1,
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#e5e5e5',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1f2937',
  },
  subtitle: {
    fontSize: 14,
    color: '#6b7280',
    marginTop: 4,
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  statusActive: {
    backgroundColor: '#d1fae5',
  },
  statusInactive: {
    backgroundColor: '#fee2e2',
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
  },
  connectionBar: {
    backgroundColor: '#f0f9ff',
    padding: 12,
    marginHorizontal: 12,
    marginTop: 12,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#3b82f6',
  },
  connectionLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#1f2937',
  },
  connectionValue: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  card: {
    backgroundColor: 'white',
    margin: 12,
    padding: 16,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
    color: '#1f2937',
  },
  appName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1f2937',
    marginBottom: 4,
  },
  appId: {
    fontSize: 12,
    color: '#6b7280',
    marginBottom: 8,
  },
  category: {
    fontSize: 14,
    color: '#4b5563',
    fontStyle: 'italic',
  },
  noData: {
    fontSize: 14,
    color: '#9ca3af',
    fontStyle: 'italic',
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  infoLabel: {
    fontSize: 14,
    color: '#6b7280',
  },
  infoValue: {
    fontSize: 14,
    color: '#1f2937',
    fontWeight: '500',
  },
  policyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  policyName: {
    fontSize: 14,
    color: '#1f2937',
    flex: 1,
  },
  policyBadge: {
    backgroundColor: '#dbeafe',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  policyBadgeText: {
    fontSize: 11,
    color: '#1e40af',
    fontWeight: '600',
  },
  activityStat: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1f2937',
    marginBottom: 8,
  },
  eventRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  eventDetail: {
    fontSize: 13,
    color: '#1f2937',
    flex: 1,
  },
  eventTime: {
    fontSize: 11,
    color: '#9ca3af',
  },
  moreText: {
    fontSize: 12,
    color: '#6366f1',
    fontWeight: '600',
    marginTop: 8,
  },
  actionButton: {
    backgroundColor: '#6366f1',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    alignItems: 'center',
  },
  syncButton: {
    backgroundColor: '#10b981',
  },
  actionButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  spacer: {
    height: 20,
  },
});

export default Dashboard;
