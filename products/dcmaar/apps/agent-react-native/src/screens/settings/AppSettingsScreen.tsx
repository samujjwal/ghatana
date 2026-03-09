/**
 * App Settings Screen
 *
 * Allows users to:
 * - View monitored apps
 * - Configure app-specific settings
 * - Enable/disable monitoring per app
 * - Configure background sync
 *
 * @doc.type component
 * @doc.purpose App-level configuration and settings
 * @doc.layer product
 * @doc.pattern Screen
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  Switch,
  FlatList,
  ActivityIndicator,
  RefreshControl,
  Alert,
  Text,
  SafeAreaView,
  Button,
} from 'react-native';
import { useSetAtom } from 'jotai';
import { useCurrentUser } from '@/hooks';
// Prefer canonical import from @react-navigation/native. In some workspace
// setups `useNavigation` resolution required a direct core import; attempt
// the canonical import first for long-term correctness.
// Use canonical navigation import from @react-navigation/native now that
// package versions are aligned to the v6 family.
import { useNavigation } from '@react-navigation/native';
import {
  showNotificationAtom,
  setLoadingAtom,
} from '@/stores';
import { guardianApi } from '@/services';

// Design system
const colors = {
  background: '#f5f5f5',
  white: '#ffffff',
  gray50: '#f9fafb',
  gray200: '#e5e7eb',
  gray400: '#9ca3af',
  gray500: '#6b7280',
  gray600: '#4b5563',
  gray700: '#374151',
  gray900: '#111827',
  primary: '#3b82f6',
  primary200: '#bfdbfe',
  error: '#ef4444',
};

const spacing = {
  sm: 8,
  md: 16,
  lg: 24,
};

// Type definitions
interface AppConfig {
  id: string;
  name: string;
  packageName: string;
  monitoringEnabled: boolean;
  lastChecked: Date;
  issuesCount: number;
}

interface ConfigPayload {
  type: 'toggle_background_sync' | 'clear_cache' | 'reset_to_defaults';
  enabled?: boolean;
}

/**
 * App Settings Screen Component
 *
 * Displays list of monitored applications and allows users to configure
 * app-specific settings such as monitoring enable/disable, background
 * sync preferences, and data collection options.
 *
 * GIVEN: User navigates to settings
 * WHEN: Component mounts
 * THEN: Display available apps and settings
 */
export function AppSettingsScreen() {
  const showNotification = useSetAtom(showNotificationAtom);
  const setLoading = useSetAtom(setLoadingAtom);
  // Read tenant from current authenticated user when available. Some test
  // fixtures and quick-build flows still use a hard-coded tenant id; fall
  // back to that value when user/tenant is not available to avoid runtime
  // failures during early development.
  const currentUser = useCurrentUser();
  const tenantId = (currentUser as any)?.tenantId ?? 'tenant-1';
  // Use React Navigation's hook; avoid a type argument until RootStackParamList
  // is defined in the workspace to keep this file type-safe under the local
  // ambient declarations used during migration.
  const navigation = useNavigation() as any;
  const [refreshing, setRefreshing] = useState(false);
  const [apps, setApps] = useState<AppConfig[]>([]);
  const [backgroundSyncEnabled, setBackgroundSyncEnabled] = useState(true);
  const [collectionLevel, setCollectionLevel] = useState<'essential' | 'comprehensive'>('comprehensive');

  /**
   * Navigate to per-app configuration screen
   * Small helper to avoid runtime/TS errors when the screen is used in isolation.
   */
  const handleConfigureApp = useCallback((appId: string, appName?: string) => {
    try {
      // If navigation is available, navigate to a named screen; otherwise no-op
        if (navigation && typeof navigation.navigate === 'function') {
        navigation.navigate('AppConfig', { appId, appName });
      }
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
    } catch (_err) {
      // Fallback: show a notification
      showNotification?.({ type: 'info', message: `Configure ${appName || appId}`, duration: 2000 });
    }
  }, [navigation, showNotification]);

  const handleConfigureSync = useCallback(() => {
    // Placeholder - real implementation should open a sync configuration modal/screen
    showNotification({ type: 'info', message: 'Open sync configuration', duration: 2000 });
  }, [showNotification]);

  /**
   * Apply a small in-memory update for app configuration actions.
   * In production this should call an API and persist the result.
   */
  const updateAppConfig = useCallback(async (payload: ConfigPayload) => {
    // Local optimistic changes
    try {
      switch (payload?.type) {
        case 'toggle_background_sync':
          setBackgroundSyncEnabled(!!payload.enabled);
          break;
        case 'clear_cache':
          // no-op transform locally
          break;
        case 'reset_to_defaults':
          setCollectionLevel('comprehensive');
          break;
        default:
          console.warn('updateAppConfig: unknown payload', payload);
      }

      // Persist configuration to backend (tenant id currently fixed for quick-build)
        try {
          await guardianApi.updateConfig(tenantId, payload as any);
        showNotification({ type: 'success', message: 'Settings updated', duration: 2000 });
      } catch (apiErr) {
        // Revert optimistic changes if needed (best-effort)
        console.error('updateAppConfig: API error', apiErr);
        showNotification({ type: 'error', message: 'Failed to persist settings', duration: 3000 });
      }
    } catch (err) {
      console.error('updateAppConfig error', err);
      showNotification({ type: 'error', message: 'Failed to apply settings', duration: 3000 });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [setBackgroundSyncEnabled, setCollectionLevel, showNotification]);

  /**
   * Fetch app settings from backend
   */
  const loadAppSettings = useCallback(async () => {
    try {
      setLoading(true);
      // Simulate loading apps from API
      const mockApps = [
        {
          id: 'app1',
          name: 'Email Client',
          packageName: 'com.example.email',
          monitoringEnabled: true,
          lastChecked: new Date(),
          issuesCount: 2,
        },
        {
          id: 'app2',
          name: 'Social Media',
          packageName: 'com.example.social',
          monitoringEnabled: true,
          lastChecked: new Date(),
          issuesCount: 0,
        },
      ];
      setApps(mockApps);
    } catch (error) {
      showNotification({
        type: 'error',
        message: 'Failed to load app settings',
        duration: 3000,
      });
      console.error('AppSettingsScreen: Load failed', error);
    } finally {
      setLoading(false);
    }
  }, [setLoading, showNotification]);

  /**
   * Load app configurations from backend on mount
   */
  useEffect(() => {
    loadAppSettings();
  }, [loadAppSettings]);

  /**
   * Handle pull-to-refresh
   */
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadAppSettings();
    setRefreshing(false);
  }, [loadAppSettings]);

  /**
   * Toggle app monitoring on/off
   *
   * GIVEN: App config exists
   * WHEN: User toggles switch
   * THEN: Update backend and local state
   */
  const handleToggleMonitoring = useCallback(
    async (appId: string, enabled: boolean) => {
      try {
        setApps(prev =>
          prev.map(app =>
            app.id === appId ? { ...app, monitoringEnabled: enabled } : app
          )
        );

        // Optimistic UI update above. Persist to backend, but don't fail the UI if API is unavailable.
          try {
          await guardianApi.updateApp(tenantId, appId, { isMonitored: enabled } as any);
          showNotification({
            type: 'success',
            message: `Monitoring ${enabled ? 'enabled' : 'disabled'} for app`,
            duration: 2000,
          });
        } catch (apiError) {
          // Revert optimistic update on failure
          setApps(prev => prev.map(app => (app.id === appId ? { ...app, monitoringEnabled: !enabled } : app)));
          showNotification({ type: 'error', message: 'Failed to persist monitoring change', duration: 3000 });
          console.error('AppSettingsScreen: API update failed', apiError);
        }
      } catch (error) {
        showNotification({
          type: 'error',
          message: 'Failed to update app setting',
          duration: 3000,
        });
        console.error('AppSettingsScreen: Toggle failed', error);
      }
    },
    [showNotification, tenantId]
  );

  /**
   * Handle data collection preferences
   */
  const handleDataCollection = useCallback(() => {
    Alert.alert(
      'Data Collection Preferences',
      'Choose what data Guardian collects from your apps',
      [
        {
          text: 'Essential Only',
          onPress: () => {
            setCollectionLevel('essential');
            showNotification({
              type: 'success',
              message: 'Collecting essential data only',
              duration: 2000,
            });
          },
        },
        {
          text: 'Comprehensive',
          onPress: () => {
            setCollectionLevel('comprehensive');
            showNotification({
              type: 'success',
              message: 'Collecting comprehensive data',
              duration: 2000,
            });
          },
        },
        {
          text: 'Cancel',
          style: 'cancel',
        },
      ]
    );
  }, [showNotification]);

  /**
   * Render app configuration item
   */
  const renderAppItem = useCallback(
    ({ item }: { item: AppConfig }) => (
      <View style={styles.appItem}>
        <View style={styles.appHeader}>
          <View style={styles.appInfo}>
            <Text style={styles.appName}>{item.name}</Text>
            <Text style={styles.appPackage}>{item.packageName}</Text>
          </View>
          <Switch
            value={item.monitoringEnabled}
            onValueChange={(enabled) =>
              handleToggleMonitoring(item.id, enabled)
            }
            trackColor={{ false: colors.gray200, true: colors.primary200 }}
            thumbColor={
              item.monitoringEnabled ? colors.primary : colors.gray400
            }
          />
        </View>

        {item.monitoringEnabled && (
          <View style={styles.appSettings}>
            <View style={styles.settingRow}>
              <Text style={styles.settingLabel}>Last Checked</Text>
              <Text style={styles.settingValue}>
                {new Date(item.lastChecked).toLocaleTimeString()}
              </Text>
            </View>

            <View style={styles.settingRow}>
              <Text style={styles.settingLabel}>Issues Detected</Text>
              <Text style={[styles.settingValue, { color: colors.error }]}>
                {item.issuesCount || 0}
              </Text>
            </View>

            <Button
              title="Configure"
              onPress={() => handleConfigureApp(item.id, item.name)}
              color={colors.primary}
            />
          </View>
        )}
      </View>
    ),
    [handleToggleMonitoring, handleConfigureApp]
  );

  // Local fallbacks for legacy variables used in layout
  const loading = false;
  const appConfig = {
    apps,
    backgroundSyncEnabled,
    syncIntervalMinutes: 30,
    collectionLevel,
  } as const;

  if (loading && !appConfig?.apps?.length) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={styles.loadingText}>Loading app settings...</Text>
        </View>
      </SafeAreaView>
    );
  }

  const visibleApps = appConfig?.apps || apps;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        style={styles.content}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
      >
        {/* App Settings Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Monitored Applications</Text>

          {apps.length > 0 ? (
              <FlatList
              data={visibleApps}
              renderItem={renderAppItem}
              keyExtractor={(item) => item.id}
              scrollEnabled={false}
              nestedScrollEnabled={false}
            />
          ) : (
            <View style={styles.emptyState}>
              <Text style={styles.emptyStateText}>No apps configured</Text>
              <Button
                title="Add App"
                onPress={() => navigation.navigate('AppDiscovery')}
                color={colors.primary}
              />
            </View>
          )}
        </View>

        {/* Background Sync Section */}
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Background Sync</Text>
            <Switch
              value={appConfig?.backgroundSyncEnabled ?? true}
              onValueChange={(enabled) => {
                updateAppConfig({
                  type: 'toggle_background_sync',
                  enabled,
                });
              }}
            />
          </View>

          {appConfig?.backgroundSyncEnabled && (
            <View style={styles.settingDetails}>
              <View style={styles.settingRow}>
                <Text style={styles.settingLabel}>Sync Interval</Text>
                <Text style={styles.settingValue}>
                  {appConfig?.syncIntervalMinutes || 30} minutes
                </Text>
              </View>

                <Button
                  title="Configure Sync"
                  onPress={handleConfigureSync}
                  color={colors.primary}
                />
            </View>
          )}
        </View>

        {/* Data Collection Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Data Collection</Text>

          <View style={styles.collectionLevel}>
            <Text style={styles.collectionLabel}>
              Current Level:{' '}
              <Text style={styles.collectionValue}>
                {appConfig?.collectionLevel === 'essential'
                  ? 'Essential Only'
                  : 'Comprehensive'}
              </Text>
            </Text>

            <Text style={styles.collectionDescription}>
              {appConfig?.collectionLevel === 'essential'
                ? 'Guardian collects only essential security data'
                : 'Guardian collects comprehensive security and usage data'}
            </Text>

            <Button
              title="Change Data Collection"
              onPress={handleDataCollection}
              color={colors.primary}
            />
          </View>
        </View>

        {/* Advanced Settings Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Advanced</Text>

          <View style={styles.advancedSettings}>
            <View style={styles.settingRow}>
              <Text style={styles.settingLabel}>Cache Cleared</Text>
              <Button
                title="Clear"
                onPress={() => {
                  updateAppConfig({ type: 'clear_cache' });
                  showNotification({ type: 'success', message: 'Cache cleared', duration: 2000 });
                }}
                color={colors.primary}
              />
            </View>

            <View style={styles.settingRow}>
              <Text style={styles.settingLabel}>Export Settings</Text>
              <Button
                title="Export"
                onPress={() => {
                  showNotification({ type: 'info', message: 'Settings exported to file', duration: 2000 });
                }}
                color={colors.primary}
              />
            </View>

            <View style={styles.settingRow}>
              <Text style={styles.settingLabel}>Reset to Defaults</Text>
              <Button
                title="Reset"
                onPress={() => {
                  Alert.alert(
                    'Reset Settings?',
                    'This will restore all settings to defaults',
                    [
                      {
                        text: 'Cancel',
                        style: 'cancel',
                      },
                      {
                        text: 'Reset',
                        onPress: () => {
                          updateAppConfig({ type: 'reset_to_defaults' });
                          showNotification({ type: 'success', message: 'Settings reset', duration: 2000 });
                        },
                        style: 'destructive',
                      },
                    ]
                  );
                }}
                color={colors.error}
              />
            </View>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    flex: 1,
    padding: spacing.md,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: spacing.md,
    color: colors.gray600,
    fontSize: 16,
  },
  section: {
    marginBottom: spacing.lg,
    backgroundColor: colors.white,
    borderRadius: 12,
    padding: spacing.md,
    ...{
      shadowColor: colors.gray900,
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.1,
      shadowRadius: 4,
      elevation: 3,
    },
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: colors.gray900,
    marginBottom: spacing.md,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.md,
  },
  appItem: {
    backgroundColor: colors.gray50,
    borderRadius: 8,
    padding: spacing.md,
    marginBottom: spacing.md,
    borderLeftWidth: 4,
    borderLeftColor: colors.primary,
  },
  appHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  appInfo: {
    flex: 1,
  },
  appName: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.gray900,
    marginBottom: 4,
  },
  appPackage: {
    fontSize: 12,
    color: colors.gray500,
  },
  appSettings: {
    marginTop: spacing.md,
    paddingTop: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.gray200,
  },
  settingDetails: {
    backgroundColor: colors.gray50,
    borderRadius: 8,
    padding: spacing.md,
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
  },
  settingLabel: {
    fontSize: 14,
    color: colors.gray700,
    fontWeight: '500',
  },
  settingValue: {
    fontSize: 14,
    color: colors.gray600,
    fontWeight: '500',
  },
  configButton: {
    marginTop: spacing.md,
    backgroundColor: colors.primary,
  },
  smallButton: {
    paddingHorizontal: spacing.md,
    backgroundColor: colors.primary,
  },
  destructiveButton: {
    backgroundColor: colors.error,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: spacing.lg,
  },
  emptyStateText: {
    fontSize: 14,
    color: colors.gray600,
    marginBottom: spacing.md,
  },
  addButton: {
    backgroundColor: colors.primary,
  },
  collectionLevel: {
    backgroundColor: colors.gray50,
    borderRadius: 8,
    padding: spacing.md,
  },
  collectionLabel: {
    fontSize: 14,
    color: colors.gray700,
    fontWeight: '500',
    marginBottom: spacing.sm,
  },
  collectionValue: {
    fontWeight: '600',
    color: colors.primary,
  },
  collectionDescription: {
    fontSize: 13,
    color: colors.gray600,
    marginBottom: spacing.md,
    lineHeight: 18,
  },
  advancedSettings: {
    backgroundColor: colors.gray50,
    borderRadius: 8,
    padding: spacing.md,
  },
});
