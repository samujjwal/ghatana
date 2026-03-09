/**
 * Device Settings Screen - Jotai Integration
 *
 * Manages device-specific settings, preferences, and permissions.
 *
 * Features:
 * - Device information and status
 * - Permission management
 * - Notification preferences
 * - Privacy settings
 * - Sync settings
 * - Language preferences
 * - Auto-lock configuration
 *
 * State Management:
 * - useDevice() - Device information
 * - useSettings() - User preferences
 * - useNotificationPreferences() - Notification settings
 * - usePrivacySettings() - Privacy configuration
 *
 * @component
 * @example
 * return <DeviceSettings />
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Platform,
  Switch,
  Alert,
} from 'react-native';
import {
  useDevice,
  useSettings,
  useNotificationPreferences,
  usePrivacySettings,
  useLanguage,
} from '../hooks';

/**
 * Device Settings Screen Component
 *
 * @component
 * @returns {React.ReactElement} DeviceSettings screen
 */
export default function DeviceSettings() {
  // State management
  const device = useDevice();
  const settings = useSettings();
  const notificationPrefs = useNotificationPreferences();
  const privacySettings = usePrivacySettings();
  const currentLanguage = useLanguage();

  // Local UI state
  const [syncInProgress, setSyncInProgress] = useState(false);

  /**
   * Handles sync operation
   */
  const handleForceSync = async () => {
    setSyncInProgress(true);
    try {
      // Simulate sync operation
      await new Promise((resolve) => setTimeout(resolve, 1500));
      Alert.alert('Sync Complete', 'Device has been synced successfully.');
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
    } catch (_error) {
      Alert.alert('Sync Error', 'Failed to sync device. Please try again.');
    } finally {
      setSyncInProgress(false);
    }
  };

  /**
   * Handles clearing local data
   */
  const handleClearData = () => {
    Alert.alert(
      'Clear Local Data',
      'Are you sure you want to clear all local data? This cannot be undone.',
      [
        {
          text: 'Cancel',
          onPress: () => {},
          style: 'cancel',
        },
        {
          text: 'Clear',
          onPress: async () => {
            try {
              Alert.alert('Success', 'Local data has been cleared.');
              // eslint-disable-next-line @typescript-eslint/no-unused-vars
            } catch (_error) {
              Alert.alert('Error', 'Failed to clear data. Please try again.');
            }
          },
          style: 'destructive',
        },
      ]
    );
  };

  /**
   * Handles notification preference toggle
   */
  const handleToggleNotification = (type: string, value: boolean) => {
    if (settings.updateNotificationPrefs) {
      const updated = { ...notificationPrefs, [type]: value };
      settings.updateNotificationPrefs(updated);
      Alert.alert(
        'Updated',
        `${type} notifications have been ${value ? 'enabled' : 'disabled'}.`
      );
    }
  };

  /**
   * Handles language change
   */
  const handleChangeLanguage = (language: string) => {
    if (settings.setLanguage) {
      settings.setLanguage(language);
      Alert.alert('Language Changed', `Language set to ${language}.`);
    }
  };

  /**
   * Renders a toggle setting
   */
  const renderToggleSetting = (
    title: string,
    description: string,
    value: boolean,
    onToggle: (value: boolean) => void
  ) => (
    <View style={styles.settingItem}>
      <View style={styles.settingInfo}>
        <Text style={styles.settingTitle}>{title}</Text>
        <Text style={styles.settingDescription}>{description}</Text>
      </View>
      <Switch
        value={value}
        onValueChange={onToggle}
        trackColor={{ false: '#e5e7eb', true: '#86efac' }}
        thumbColor={value ? '#22c55e' : '#9ca3af'}
      />
    </View>
  );

  /**
   * Renders a button setting
   */
  const renderButtonSetting = (
    title: string,
    description: string,
    onPress: () => void,
    buttonText: string = 'Configure'
  ) => (
    <TouchableOpacity
      style={styles.settingButton}
      onPress={onPress}
      activeOpacity={0.7}
    >
      <View style={styles.settingInfo}>
        <Text style={styles.settingTitle}>{title}</Text>
        <Text style={styles.settingDescription}>{description}</Text>
      </View>
      <Text style={styles.settingButtonText}>{buttonText}</Text>
    </TouchableOpacity>
  );

  return (
    <ScrollView style={styles.container}>
      {/* Device Information Section */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Device Information</Text>
        </View>

        {device.selectedDevice && (
          <View style={styles.infoCard}>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Device Name:</Text>
              <Text style={styles.infoValue}>{device.selectedDevice.name}</Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Model:</Text>
              <Text style={styles.infoValue}>{device.selectedDevice.model}</Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>OS Version:</Text>
              <Text style={styles.infoValue}>
                {device.selectedDevice.osVersion}
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Status:</Text>
              <Text
                style={[
                  styles.infoValue,
                  {
                    color:
                      device.selectedDevice.status === 'online'
                        ? '#22c55e'
                        : device.selectedDevice.status === 'offline'
                          ? '#ef4444'
                          : '#f97316',
                  },
                ]}
              >
                {device.selectedDevice.status === 'online'
                  ? '● Online'
                  : device.selectedDevice.status === 'offline'
                    ? '● Offline'
                    : '● Error'}
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Last Seen:</Text>
              <Text style={styles.infoValue}>
                {device.selectedDevice.lastSeen
                  ? new Date(device.selectedDevice.lastSeen).toLocaleDateString()
                  : 'Never'}
              </Text>
            </View>
          </View>
        )}
      </View>

      {/* Platform-Specific Permissions */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Permissions</Text>
          <Text style={styles.sectionSubtitle}>
            Required for full functionality
          </Text>
        </View>

        <View style={styles.permissionCard}>
          {Platform.OS === 'android' && (
            <>
              <PermissionItem
                title="Accessibility Service"
                description="Required to monitor app usage"
                status="Check"
                icon="🔐"
              />
              <PermissionItem
                title="Usage Stats Access"
                description="Required to track app usage time"
                status="Check"
                icon="📊"
              />
              <PermissionItem
                title="Device Admin"
                description="Required to prevent uninstallation"
                status="Check"
                icon="⚙️"
              />
              <PermissionItem
                title="Display Over Other Apps"
                description="Required to show blocking overlays"
                status="Check"
                icon="📺"
              />
            </>
          )}

          {Platform.OS === 'ios' && (
            <>
              <PermissionItem
                title="Family Controls"
                description="Required to manage screen time"
                status="Check"
                icon="👨‍👩‍👧‍👦"
              />
              <PermissionItem
                title="Screen Time"
                description="Required to track app usage"
                status="Check"
                icon="⏱️"
              />
            </>
          )}
        </View>
      </View>

      {/* Notification Preferences */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Notifications</Text>
        </View>

        <View style={styles.settingsCard}>
          {notificationPrefs && (
            <>
              {renderToggleSetting(
                'Push Notifications',
                'Receive push notifications from the app',
                notificationPrefs.pushEnabled,
                (value) => handleToggleNotification('pushEnabled', value)
              )}

              {renderToggleSetting(
                'Email Notifications',
                'Receive email notifications',
                notificationPrefs.emailEnabled,
                (value) => handleToggleNotification('emailEnabled', value)
              )}

              {renderToggleSetting(
                'SMS Notifications',
                'Receive SMS text notifications',
                notificationPrefs.smsEnabled,
                (value) => handleToggleNotification('smsEnabled', value)
              )}

              {renderToggleSetting(
                'Quiet Hours',
                'Silence notifications during quiet hours',
                notificationPrefs.quietHoursEnabled,
                (value) => handleToggleNotification('quietHoursEnabled', value)
              )}

              {notificationPrefs.quietHoursEnabled && (
                <View style={styles.quietHoursInfo}>
                  <Text style={styles.quietHoursText}>
                    🔇 Quiet Hours: {notificationPrefs.quietHoursStart} -{' '}
                    {notificationPrefs.quietHoursEnd}
                  </Text>
                </View>
              )}
            </>
          )}
        </View>
      </View>

      {/* Privacy Settings */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Privacy</Text>
        </View>

        <View style={styles.settingsCard}>
          {privacySettings && (
            <>
              {renderToggleSetting(
                'Analytics',
                'Help us improve by sending usage data',
                privacySettings.analyticsEnabled,
                () => {}
              )}

              {renderToggleSetting(
                'Crash Reporting',
                'Send crash reports to help fix issues',
                privacySettings.crashReportingEnabled,
                () => {}
              )}

              {renderToggleSetting(
                'Location Sharing',
                'Share location data with the app',
                privacySettings.locationEnabled,
                () => {}
              )}

              <View style={styles.privacyLevelContainer}>
                <Text style={styles.privacyLevelTitle}>Profile Visibility</Text>
                <View style={styles.privacyOptions}>
                  <TouchableOpacity
                    style={[
                      styles.privacyOption,
                      privacySettings.profileVisibility === 'public' &&
                        styles.privacyOptionActive,
                    ]}
                  >
                    <Text
                      style={[
                        styles.privacyOptionText,
                        privacySettings.profileVisibility === 'public' &&
                          styles.privacyOptionTextActive,
                      ]}
                    >
                      Public
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[
                      styles.privacyOption,
                      privacySettings.profileVisibility === 'friends' &&
                        styles.privacyOptionActive,
                    ]}
                  >
                    <Text
                      style={[
                        styles.privacyOptionText,
                        privacySettings.profileVisibility === 'friends' &&
                          styles.privacyOptionTextActive,
                      ]}
                    >
                      Friends
                    </Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[
                      styles.privacyOption,
                      privacySettings.profileVisibility === 'private' &&
                        styles.privacyOptionActive,
                    ]}
                  >
                    <Text
                      style={[
                        styles.privacyOptionText,
                        privacySettings.profileVisibility === 'private' &&
                          styles.privacyOptionTextActive,
                      ]}
                    >
                      Private
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            </>
          )}
        </View>
      </View>

      {/* Language & Localization */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Language & Region</Text>
        </View>

        <View style={styles.settingsCard}>
          <View style={styles.languageContainer}>
            <View style={styles.settingInfo}>
              <Text style={styles.settingTitle}>Language</Text>
              <Text style={styles.settingDescription}>
                Current: {currentLanguage || 'en-US'}
              </Text>
            </View>
          </View>

          <View style={styles.languageOptions}>
            {['en-US', 'es-ES', 'fr-FR', 'de-DE'].map((lang) => (
              <TouchableOpacity
                key={lang}
                style={[
                  styles.languageButton,
                  currentLanguage === lang && styles.languageButtonActive,
                ]}
                onPress={() => handleChangeLanguage(lang)}
              >
                <Text
                  style={[
                    styles.languageButtonText,
                    currentLanguage === lang && styles.languageButtonTextActive,
                  ]}
                >
                  {lang}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      </View>

      {/* Sync Settings */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Sync & Storage</Text>
        </View>

        <View style={styles.settingsCard}>
          {renderButtonSetting(
            'Force Sync',
            'Manually sync with cloud servers',
            handleForceSync,
            syncInProgress ? 'Syncing...' : 'Sync Now'
          )}

          {renderButtonSetting(
            'Clear Local Data',
            'Remove all locally cached data (cannot be undone)',
            handleClearData,
            'Clear'
          )}
        </View>
      </View>

      {/* Device Controls */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Device Controls</Text>
        </View>

        <View style={styles.settingsCard}>
          <View style={styles.dangerZone}>
            <Text style={styles.dangerZoneTitle}>Danger Zone</Text>
            <TouchableOpacity style={styles.uninstallButton}>
              <Text style={styles.uninstallButtonText}>
                Request Uninstall Permission
              </Text>
            </TouchableOpacity>
            <Text style={styles.uninstallWarning}>
              ⚠️ This will request admin rights to allow uninstallation.
            </Text>
          </View>
        </View>
      </View>
    </ScrollView>
  );
}

/**
 * Permission Item Component
 *
 * @component
 * @param {object} props - Component props
 * @param {string} props.title - Permission title
 * @param {string} props.description - Permission description
 * @param {string} props.status - Permission status
 * @param {string} props.icon - Emoji icon
 * @returns {React.ReactElement} Permission item
 */
function PermissionItem({
  title,
  description,
  status,
  icon,
}: {
  title: string;
  description: string;
  status: string;
  icon?: string;
}) {
  return (
    <View style={styles.permissionItem}>
      <View style={styles.permissionInfo}>
        <View style={styles.permissionTitle}>
          <Text style={styles.permissionTitleText}>{icon} </Text>
          <Text style={styles.permissionTitleText}>{title}</Text>
        </View>
        <Text style={styles.permissionDescription}>{description}</Text>
      </View>
      <TouchableOpacity style={styles.permissionButton}>
        <Text style={styles.permissionButtonText}>{status}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  section: {
    paddingHorizontal: 12,
    paddingVertical: 12,
  },
  sectionHeader: {
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1f2937',
    marginBottom: 4,
  },
  sectionSubtitle: {
    fontSize: 13,
    color: '#6b7280',
  },

  // Info Card
  infoCard: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    gap: 12,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  infoLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#6b7280',
  },
  infoValue: {
    fontSize: 14,
    fontWeight: '500',
    color: '#1f2937',
  },

  // Permission Card
  permissionCard: {
    backgroundColor: 'white',
    borderRadius: 12,
    overflow: 'hidden',
  },
  permissionItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  permissionInfo: {
    flex: 1,
  },
  permissionTitle: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  permissionTitleText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1f2937',
  },
  permissionDescription: {
    fontSize: 12,
    color: '#6b7280',
  },
  permissionButton: {
    backgroundColor: '#dbeafe',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  permissionButtonText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#0284c7',
  },

  // Settings Card
  settingsCard: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    gap: 16,
  },
  settingItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  settingInfo: {
    flex: 1,
  },
  settingTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1f2937',
    marginBottom: 4,
  },
  settingDescription: {
    fontSize: 12,
    color: '#6b7280',
  },
  settingButton: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  settingButtonText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#0284c7',
  },

  // Quiet Hours
  quietHoursInfo: {
    backgroundColor: '#fef3c7',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 6,
    marginTop: -8,
  },
  quietHoursText: {
    fontSize: 12,
    color: '#92400e',
    fontWeight: '500',
  },

  // Privacy Level
  privacyLevelContainer: {
    paddingVertical: 12,
  },
  privacyLevelTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1f2937',
    marginBottom: 12,
  },
  privacyOptions: {
    flexDirection: 'row',
    gap: 8,
  },
  privacyOption: {
    flex: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 6,
    backgroundColor: '#f3f4f6',
    alignItems: 'center',
  },
  privacyOptionActive: {
    backgroundColor: '#dbeafe',
  },
  privacyOptionText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#6b7280',
  },
  privacyOptionTextActive: {
    color: '#0284c7',
  },

  // Language
  languageContainer: {
    paddingVertical: 12,
  },
  languageOptions: {
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
  },
  languageButton: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 6,
    backgroundColor: '#f3f4f6',
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  languageButtonActive: {
    backgroundColor: '#dbeafe',
    borderColor: '#0284c7',
  },
  languageButtonText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#6b7280',
  },
  languageButtonTextActive: {
    color: '#0284c7',
  },

  // Danger Zone
  dangerZone: {
    backgroundColor: '#fef2f2',
    padding: 12,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#ef4444',
  },
  dangerZoneTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#dc2626',
    marginBottom: 12,
  },
  uninstallButton: {
    backgroundColor: '#fee2e2',
    paddingVertical: 12,
    borderRadius: 6,
    alignItems: 'center',
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#fecaca',
  },
  uninstallButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#dc2626',
  },
  uninstallWarning: {
    fontSize: 12,
    color: '#991b1b',
    fontStyle: 'italic',
  },
});
