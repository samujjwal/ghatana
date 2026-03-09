/**
 * Permissions Management Screen
 *
 * Displays all required app permissions with explanations and current status.
 * Allows users to grant/revoke permissions and redirects to system settings.
 *
 * Features:
 * - List all required permissions
 * - Show permission grant status
 * - Explain why each permission is needed
 * - Quick access to system settings
 * - Permission request handling
 *
 * @doc.type component
 * @doc.purpose Permissions management and explanation
 * @doc.layer product
 * @doc.pattern Screen Component
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Linking,
  ActivityIndicator,
} from 'react-native';
import { useAtom } from 'jotai';

import { permissionsAtom, uiAtom } from '../stores';

interface PermissionItem {
  id: string;
  name: string;
  description: string;
  granted: boolean;
  isRequired: boolean;
}

/**
 * Permission card component
 *
 * Displays a single permission with status and action
 *
 * @param permission - Permission details
 * @param onPress - Permission action handler
 * @returns Permission card JSX
 */
const PermissionCard: React.FC<{
  permission: PermissionItem;
  onPress: () => void;
  loading?: boolean;
}> = ({ permission, onPress, loading }) => (
  <TouchableOpacity
    style={[
      styles.permissionCard,
      permission.granted && styles.permissionGranted,
    ]}
    onPress={onPress}
    disabled={permission.granted || loading}
  >
    <View style={styles.permissionContent}>
      <View style={styles.permissionHeader}>
        <Text style={styles.permissionName}>{permission.name}</Text>
        <View
          style={[
            styles.statusBadge,
            permission.granted ? styles.statusGranted : styles.statusDenied,
          ]}
        >
          <Text style={styles.statusText}>
            {permission.granted ? '✓ Granted' : '✗ Denied'}
          </Text>
        </View>
      </View>
      <Text style={styles.permissionDescription}>{permission.description}</Text>
      {permission.isRequired && (
        <Text style={styles.requiredText}>* Required for app functionality</Text>
      )}
    </View>
    {!permission.granted && !loading && (
      <Text style={styles.actionArrow}>›</Text>
    )}
    {loading && <ActivityIndicator size="small" color="#6366f1" />}
  </TouchableOpacity>
);

/**
 * PermissionsScreen Component
 *
 * Main permissions interface showing all app permissions with status
 * and ability to grant/manage them. Integrates with system permissions
 * dialog and settings redirection.
 *
 * @returns Permissions screen JSX
 */
export default function PermissionsScreen() {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [permissions, setPermissions] = useAtom(permissionsAtom);
  const [, setUIState] = useAtom(uiAtom);
  const [permissionsList, setPermissionsList] = useState<PermissionItem[]>([]);
  const [loadingPermission, setLoadingPermission] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  /**
   * Initialize permissions list on mount
   *
   * GIVEN: Component mounts
   * WHEN: useEffect with empty deps runs
   * THEN: Load current permissions from atom
   */
  useEffect(() => {
    initializePermissions();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * Initialize permissions
   *
   * GIVEN: Component initializing
   * WHEN: initializePermissions() called
   * THEN: Set up permissions list with current status
   */
  const initializePermissions = useCallback(() => {
    try {
      setIsLoading(true);
      // Build permissions list from permission sets (defaults to false/not granted)
      const permsList: PermissionItem[] = [
        {
          id: 'camera',
          name: 'Camera',
          description: 'Monitor camera access attempts by apps',
          granted: false,
          isRequired: true,
        },
        {
          id: 'microphone',
          name: 'Microphone',
          description: 'Monitor microphone usage by installed apps',
          granted: false,
          isRequired: true,
        },
        {
          id: 'location',
          name: 'Location',
          description: 'Track location data access patterns',
          granted: false,
          isRequired: true,
        },
        {
          id: 'contacts',
          name: 'Contacts',
          description: 'Monitor contact list access',
          granted: false,
          isRequired: true,
        },
        {
          id: 'calendar',
          name: 'Calendar',
          description: 'Monitor calendar data access',
          granted: false,
          isRequired: false,
        },
        {
          id: 'photos',
          name: 'Photos & Media',
          description: 'Monitor media library access',
          granted: false,
          isRequired: false,
        },
        {
          id: 'sms',
          name: 'SMS & Calls',
          description: 'Monitor messaging and call logs',
          granted: false,
          isRequired: false,
        },
        {
          id: 'notification',
          name: 'Notifications',
          description: 'Send real-time security alerts and updates',
          granted: false,
          isRequired: true,
        },
      ];
      setPermissionsList(permsList);
    } finally {
      setIsLoading(false);
    }
  }, []);

  /**
   * Handle permission request
   *
   * GIVEN: User taps a denied permission
   * WHEN: handleRequestPermission() called with permission ID
   * THEN: Request permission and update status
   */
  const handleRequestPermission = useCallback(
    (permissionId: string) => {
      Alert.alert(
        'Grant Permission',
        `Guardian needs access to ${permissionId} to provide comprehensive security monitoring.`,
        [
          {
            text: 'Cancel',
            style: 'cancel',
          },
          {
            text: 'Open Settings',
            onPress: () => {
              setLoadingPermission(permissionId);
              // Open app settings
              Linking.openSettings()
                .then(() => {
                  setLoadingPermission(null);
                  // Simulate permission grant (in production, check actual permissions)
                  setTimeout(() => {
                    setPermissions((prev) => ({
                      ...prev,
                      [permissionId]: true,
                    }));

                    setPermissionsList((prev) =>
                      prev.map((p) =>
                        p.id === permissionId ? { ...p, granted: true } : p
                      )
                    );

                    // Show notification
                    const notificationId = `notif-${Date.now()}`;
                    setUIState((prev) => ({
                      ...prev,
                      notifications: [
                        ...prev.notifications,
                        {
                          id: notificationId,
                          type: 'success',
                          message: 'Permission granted successfully',
                          duration: 3000,
                          createdAt: new Date(),
                        },
                      ],
                    }));

                    setTimeout(() => {
                      setUIState((prev) => ({
                        ...prev,
                        notifications: prev.notifications.filter(
                          (n) => n.id !== notificationId
                        ),
                      }));
                    }, 3000);
                  }, 500);
                })
                .catch(() => {
                  setLoadingPermission(null);
                });
            },
          },
        ]
      );
    },
    [setPermissions, setUIState]
  );

  /**
   * Handle grant all permissions
   *
   * GIVEN: User taps grant all button
   * WHEN: handleGrantAll() called
   * THEN: Request all missing permissions
   */
  const handleGrantAll = useCallback(() => {
    const missingPerms = permissionsList.filter((p) => !p.granted);

    if (missingPerms.length === 0) {
      Alert.alert('All Permissions Granted', 'All permissions are already enabled.');
      return;
    }

    Alert.alert(
      'Grant All Permissions',
      `Grant ${missingPerms.length} missing permissions? You'll need to enable them in Settings.`,
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Open Settings',
          onPress: () => {
            Linking.openSettings();

            // Simulate all permissions granted
            setTimeout(() => {
              setPermissions((prev) => ({
                ...prev,
                pendingRequests: [],
              }));

              setPermissionsList((prev) =>
                prev.map((p) => ({
                  ...p,
                  granted: true,
                }))
              );

              // Show notification
              const notificationId = `notif-${Date.now()}`;
              setUIState((prev) => ({
                ...prev,
                notifications: [
                  ...prev.notifications,
                  {
                    id: notificationId,
                    type: 'success',
                    message: 'All permissions granted successfully',
                    duration: 3000,
                    createdAt: new Date(),
                  },
                ],
              }));

              setTimeout(() => {
                setUIState((prev) => ({
                  ...prev,
                  notifications: prev.notifications.filter(
                    (n) => n.id !== notificationId
                  ),
                }));
              }, 3000);
            }, 500);
          },
        },
      ]
    );
  }, [permissionsList, setPermissions, setUIState]);

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#6366f1" />
        <Text style={styles.loadingText}>Loading permissions...</Text>
      </View>
    );
  }

  const grantedCount = permissionsList.filter((p) => p.granted).length;
  const totalCount = permissionsList.length;
  const allGranted = grantedCount === totalCount;

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Permissions</Text>
        <Text style={styles.headerSubtitle}>
          Manage app access to device features
        </Text>
      </View>

      {/* Progress */}
      <View style={styles.progressSection}>
        <View style={styles.progressHeader}>
          <Text style={styles.progressLabel}>Permissions Status</Text>
          <Text style={styles.progressCount}>
            {grantedCount} of {totalCount}
          </Text>
        </View>
        <View style={styles.progressBar}>
          <View
            style={[
              styles.progressFill,
              { width: `${(grantedCount / totalCount) * 100}%` },
            ]}
          />
        </View>
      </View>

      {/* Grant All Button */}
      {!allGranted && (
        <TouchableOpacity
          style={styles.grantAllButton}
          onPress={handleGrantAll}
        >
          <Text style={styles.grantAllText}>Grant Missing Permissions</Text>
        </TouchableOpacity>
      )}

      {/* Permissions List */}
      <View style={styles.permissionsSection}>
        <Text style={styles.sectionHeader}>Required Permissions</Text>
        {permissionsList
          .filter((p) => p.isRequired)
          .map((permission) => (
            <PermissionCard
              key={permission.id}
              permission={permission}
              onPress={() => handleRequestPermission(permission.id)}
              loading={loadingPermission === permission.id}
            />
          ))}
      </View>

      <View style={styles.permissionsSection}>
        <Text style={styles.sectionHeader}>Optional Permissions</Text>
        {permissionsList
          .filter((p) => !p.isRequired)
          .map((permission) => (
            <PermissionCard
              key={permission.id}
              permission={permission}
              onPress={() => handleRequestPermission(permission.id)}
              loading={loadingPermission === permission.id}
            />
          ))}
      </View>

      {/* Info Section */}
      <View style={styles.infoSection}>
        <Text style={styles.infoTitle}>Why We Need These Permissions</Text>
        <Text style={styles.infoText}>
          Guardian requires these permissions to monitor app behavior and detect
          security threats. Each permission is used only for security analysis and
          is never shared with third parties.
        </Text>
        <TouchableOpacity
          onPress={() =>
            Alert.alert(
              'Privacy Policy',
              'Your privacy is important to us. All data is encrypted and stored securely.'
            )
          }
        >
          <Text style={styles.privacyLink}>Read Privacy Policy ›</Text>
        </TouchableOpacity>
      </View>

      {/* Footer */}
      <View style={styles.footer}>
        <Text style={styles.footerText}>
          {allGranted
            ? '✓ All permissions granted'
            : `${permissionsList.filter((p) => !p.granted).length} permission(s) needed`}
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f9fafb',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f9fafb',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
    color: '#6b7280',
  },
  header: {
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1f2937',
    marginBottom: 4,
  },
  headerSubtitle: {
    fontSize: 14,
    color: '#6b7280',
  },
  progressSection: {
    marginHorizontal: 16,
    marginVertical: 16,
    paddingHorizontal: 12,
    paddingVertical: 12,
    backgroundColor: '#ffffff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  progressLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1f2937',
  },
  progressCount: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6366f1',
  },
  progressBar: {
    height: 6,
    backgroundColor: '#e5e7eb',
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#6366f1',
    borderRadius: 3,
  },
  grantAllButton: {
    marginHorizontal: 16,
    marginBottom: 16,
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#6366f1',
    borderRadius: 8,
  },
  grantAllText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#ffffff',
    textAlign: 'center',
  },
  permissionsSection: {
    marginHorizontal: 16,
    marginBottom: 24,
  },
  sectionHeader: {
    fontSize: 12,
    fontWeight: '600',
    color: '#6b7280',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 12,
    marginLeft: 4,
  },
  permissionCard: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 12,
    marginBottom: 8,
    backgroundColor: '#ffffff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  permissionGranted: {
    borderColor: '#d1fae5',
    backgroundColor: '#f0fdf4',
  },
  permissionContent: {
    flex: 1,
  },
  permissionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  permissionName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1f2937',
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    marginLeft: 8,
  },
  statusGranted: {
    backgroundColor: '#d1fae5',
  },
  statusDenied: {
    backgroundColor: '#fee2e2',
  },
  statusText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#065f46',
  },
  permissionDescription: {
    fontSize: 13,
    color: '#6b7280',
    marginBottom: 6,
  },
  requiredText: {
    fontSize: 11,
    color: '#dc2626',
    fontStyle: 'italic',
  },
  actionArrow: {
    fontSize: 20,
    color: '#d1d5db',
    marginLeft: 8,
  },
  infoSection: {
    marginHorizontal: 16,
    paddingHorizontal: 12,
    paddingVertical: 12,
    marginBottom: 24,
    backgroundColor: '#eff6ff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#bfdbfe',
  },
  infoTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1e40af',
    marginBottom: 8,
  },
  infoText: {
    fontSize: 13,
    color: '#1e40af',
    lineHeight: 18,
    marginBottom: 8,
  },
  privacyLink: {
    fontSize: 13,
    fontWeight: '600',
    color: '#2563eb',
  },
  footer: {
    alignItems: 'center',
    paddingVertical: 24,
  },
  footerText: {
    fontSize: 13,
    color: '#6b7280',
  },
});
