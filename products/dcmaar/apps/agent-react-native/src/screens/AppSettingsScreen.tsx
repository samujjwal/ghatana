/**
 * AppSettingsScreen - Manage application preferences and settings
 *
 * Allows users to:
 * - Configure language and timezone
 * - Manage notification preferences
 * - Adjust privacy settings
 * - Control auto-lock timeout
 * - View and modify all app settings
 *
 * @component
 */

import React from 'react';
import {
  View,
  ScrollView,
  Switch,
  TouchableOpacity,
  Text,
  Alert,
  StyleSheet,
} from 'react-native';
import { useAtom } from 'jotai';
import { settingsAtom } from '../stores/settings.store';
import { uiAtom } from '../stores/ui.store';

/**
 * Settings screen component
 *
 * GIVEN: User accessing app settings
 * WHEN: User interacts with preference controls
 * THEN: Settings persist and preferences apply
 */
export const AppSettingsScreen: React.FC = () => {
  const [settings, updateSettings] = useAtom(settingsAtom);
  const [, updateUI] = useAtom(uiAtom);

  /**
   * Toggle dark mode
   *
   * GIVEN: Dark mode toggle activated
   * WHEN: User toggles switch
   * THEN: Dark mode preference updated
   */
  const handleDarkModeToggle = () => {
    try {
      updateSettings((prev) => ({
        ...prev,
        darkModeEnabled: !prev.darkModeEnabled,
      }));
    } catch (error) {
      updateUI((prev) => ({
        ...prev,
        notifications: [
          ...prev.notifications,
          {
            id: `error-${Date.now()}`,
            type: 'error' as const,
            message: `Failed to update dark mode: ${error}`,
            duration: 3000,
            createdAt: new Date(),
          },
        ],
      }));
    }
  };

  /**
   * Toggle auto-lock
   *
   * GIVEN: User toggles auto-lock switch
   * WHEN: Switch changes state
   * THEN: Auto-lock enabled/disabled
   */
  const handleAutoLockToggle = () => {
    try {
      updateSettings((prev) => ({
        ...prev,
        autoLockEnabled: !prev.autoLockEnabled,
      }));
    } catch (error) {
      updateUI((prev) => ({
        ...prev,
        notifications: [
          ...prev.notifications,
          {
            id: `error-${Date.now()}`,
            type: 'error' as const,
            message: `Failed to update auto-lock: ${error}`,
            duration: 3000,
            createdAt: new Date(),
          },
        ],
      }));
    }
  };

  /**
   * Toggle push notifications
   *
   * GIVEN: User toggles push notifications
   * WHEN: Switch changes
   * THEN: Notification preference updated
   */
  const handlePushNotificationsToggle = () => {
    try {
      updateSettings((prev) => ({
        ...prev,
        notifications: {
          ...prev.notifications,
          pushEnabled: !prev.notifications.pushEnabled,
        },
      }));
    } catch (error) {
      updateUI((prev) => ({
        ...prev,
        notifications: [
          ...prev.notifications,
          {
            id: `error-${Date.now()}`,
            type: 'error' as const,
            message: `Failed to update notifications: ${error}`,
            duration: 3000,
            createdAt: new Date(),
          },
        ],
      }));
    }
  };

  /**
   * Toggle privacy setting
   *
   * GIVEN: User adjusts privacy control
   * WHEN: Privacy toggle changes
   * THEN: Privacy preference updated
   */
  const handlePrivacyToggle = (setting: 'analyticsEnabled' | 'crashReportingEnabled' | 'locationEnabled') => {
    try {
      updateSettings((prev) => ({
        ...prev,
        privacy: {
          ...prev.privacy,
          [setting]: !prev.privacy[setting],
        },
      }));
    } catch (error) {
      updateUI((prev) => ({
        ...prev,
        notifications: [
          ...prev.notifications,
          {
            id: `error-${Date.now()}`,
            type: 'error' as const,
            message: `Failed to update privacy: ${error}`,
            duration: 3000,
            createdAt: new Date(),
          },
        ],
      }));
    }
  };

  /**
   * Reset to defaults
   *
   * GIVEN: User confirms factory reset
   * WHEN: Confirmation accepted
   * THEN: All settings restored to defaults
   */
  const handleReset = () => {
    Alert.alert(
      'Factory Reset',
      'This will reset all settings to defaults. Continue?',
      [
        { text: 'Cancel', onPress: () => {} },
        {
          text: 'Reset',
          onPress: () => {
            try {
              updateSettings((prev) => ({
                ...prev,
                darkModeEnabled: false,
                autoLockEnabled: true,
                autoLockTimeoutSecs: 300,
              }));

              updateUI((prev) => ({
                ...prev,
                notifications: [
                  ...prev.notifications,
                  {
                    id: `reset-${Date.now()}`,
                    type: 'success' as const,
                    message: 'Settings reset to defaults',
                    duration: 2000,
                    createdAt: new Date(),
                  },
                ],
              }));
            } catch (error) {
              updateUI((prev) => ({
                ...prev,
                notifications: [
                  ...prev.notifications,
                  {
                    id: `error-${Date.now()}`,
                    type: 'error' as const,
                    message: `Failed to reset settings: ${error}`,
                    duration: 3000,
                    createdAt: new Date(),
                  },
                ],
              }));
            }
          },
        },
      ]
    );
  };

  return (
    <ScrollView style={styles.container}>
      {/* Localization Section */}
      <View style={styles.section}>
        <Text style={styles.title}>Localization</Text>

        <Text style={styles.label}>Language</Text>
        <View style={styles.infoRow}>
          <Text style={styles.infoText}>{settings.language}</Text>
        </View>

        <Text style={styles.label}>Timezone</Text>
        <View style={styles.infoRow}>
          <Text style={styles.infoText}>{settings.timezone}</Text>
        </View>
      </View>
      <View style={styles.section}>
        <Text style={styles.title}>Display</Text>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Dark Mode</Text>
          <Switch
            value={settings.darkModeEnabled}
            onValueChange={handleDarkModeToggle}
          />
        </View>
      </View>

      {/* Security Section */}
      <View style={styles.section}>
        <Text style={styles.title}>Security</Text>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Auto-Lock</Text>
          <Switch
            value={settings.autoLockEnabled}
            onValueChange={handleAutoLockToggle}
          />
        </View>

        {settings.autoLockEnabled && (
          <Text style={styles.description}>
            Lock after {settings.autoLockTimeoutSecs} seconds of inactivity
          </Text>
        )}
      </View>

      {/* Notifications Section */}
      <View style={styles.section}>
        <Text style={styles.title}>Notifications</Text>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Push Notifications</Text>
          <Switch
            value={settings.notifications.pushEnabled}
            onValueChange={handlePushNotificationsToggle}
          />
        </View>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Email Notifications</Text>
          <Switch
            value={settings.notifications.emailEnabled}
            onValueChange={() => {
              updateSettings((prev) => ({
                ...prev,
                notifications: {
                  ...prev.notifications,
                  emailEnabled: !prev.notifications.emailEnabled,
                },
              }));
            }}
          />
        </View>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Quiet Hours</Text>
          <Switch
            value={settings.notifications.quietHoursEnabled}
            onValueChange={() => {
              updateSettings((prev) => ({
                ...prev,
                notifications: {
                  ...prev.notifications,
                  quietHoursEnabled: !prev.notifications.quietHoursEnabled,
                },
              }));
            }}
          />
        </View>

        {settings.notifications.quietHoursEnabled && (
          <Text style={styles.description}>
            {settings.notifications.quietHoursStart} -{' '}
            {settings.notifications.quietHoursEnd}
          </Text>
        )}
      </View>

      {/* Privacy Section */}
      <View style={styles.section}>
        <Text style={styles.title}>Privacy</Text>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Analytics</Text>
          <Switch
            value={settings.privacy.analyticsEnabled}
            onValueChange={() => handlePrivacyToggle('analyticsEnabled')}
          />
        </View>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Crash Reporting</Text>
          <Switch
            value={settings.privacy.crashReportingEnabled}
            onValueChange={() => handlePrivacyToggle('crashReportingEnabled')}
          />
        </View>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Location Sharing</Text>
          <Switch
            value={settings.privacy.locationEnabled}
            onValueChange={() => handlePrivacyToggle('locationEnabled')}
          />
        </View>

        <View style={styles.controlRow}>
          <Text style={styles.label}>Data Minimization</Text>
          <Switch
            value={settings.privacy.dataMinimization}
            onValueChange={() => {
              updateSettings((prev) => ({
                ...prev,
                privacy: {
                  ...prev.privacy,
                  dataMinimization: !prev.privacy.dataMinimization,
                },
              }));
            }}
          />
        </View>
      </View>

      {/* Advanced Section */}
      <View style={styles.section}>
        <Text style={styles.title}>Advanced</Text>

        <TouchableOpacity
          style={[styles.button, styles.buttonDanger]}
          onPress={handleReset}
        >
          <Text style={styles.buttonText}>Factory Reset</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: '#f5f5f5',
  },
  section: {
    backgroundColor: 'white',
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 12,
    color: '#333',
  },
  label: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 8,
    color: '#555',
  },
  description: {
    fontSize: 12,
    color: '#999',
    marginTop: 8,
  },
  controlRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  pickerContainer: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 6,
    marginBottom: 12,
    overflow: 'hidden',
  },
  picker: {
    height: 200,
  },
  infoRow: {
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#f9f9f9',
    borderRadius: 6,
    marginBottom: 12,
    borderLeftWidth: 3,
    borderLeftColor: '#007AFF',
  },
  infoText: {
    fontSize: 14,
    color: '#333',
    fontWeight: '500',
  },
  button: {
    backgroundColor: '#007AFF',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDanger: {
    backgroundColor: '#ff3b30',
  },
  buttonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: '600',
  },
});

export default AppSettingsScreen;
