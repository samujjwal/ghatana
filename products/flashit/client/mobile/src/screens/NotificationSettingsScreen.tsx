/**
 * FlashIt Mobile - Notification Settings Screen
 *
 * User interface for managing notification preferences.
 *
 * @doc.type screen
 * @doc.purpose Notification settings management
 * @doc.layer product
 * @doc.pattern Screen Component
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  Switch,
  StyleSheet,
  Alert,
  Linking,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  notificationService,
  NotificationPreferences,
} from '../../services/notificationService';

/**
 * Time picker modal (simplified - use a proper time picker in production).
 */
const TimePicker: React.FC<{
  value: string;
  onChange: (time: string) => void;
  label: string;
}> = ({ value, onChange, label }) => {
  return (
    <View style={styles.timePickerRow}>
      <Text style={styles.timePickerLabel}>{label}</Text>
      <Text style={styles.timePickerValue}>{value}</Text>
    </View>
  );
};

/**
 * Settings row component.
 */
const SettingsRow: React.FC<{
  title: string;
  description?: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
  disabled?: boolean;
}> = ({ title, description, value, onValueChange, disabled }) => {
  return (
    <View style={[styles.row, disabled && styles.rowDisabled]}>
      <View style={styles.rowTextContainer}>
        <Text style={[styles.rowTitle, disabled && styles.textDisabled]}>
          {title}
        </Text>
        {description && (
          <Text style={[styles.rowDescription, disabled && styles.textDisabled]}>
            {description}
          </Text>
        )}
      </View>
      <Switch
        value={value}
        onValueChange={onValueChange}
        disabled={disabled}
        trackColor={{ false: '#767577', true: '#818CF8' }}
        thumbColor={value ? '#6366F1' : '#f4f3f4'}
        ios_backgroundColor="#3e3e3e"
      />
    </View>
  );
};

/**
 * Section header component.
 */
const SectionHeader: React.FC<{ title: string }> = ({ title }) => (
  <View style={styles.sectionHeader}>
    <Text style={styles.sectionHeaderText}>{title}</Text>
  </View>
);

/**
 * Notification Settings Screen.
 */
const NotificationSettingsScreen: React.FC = () => {
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null);
  const [permissionGranted, setPermissionGranted] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);

  // Load preferences
  useEffect(() => {
    loadPreferences();
  }, []);

  const loadPreferences = async () => {
    try {
      const granted = await notificationService.checkPermissions();
      setPermissionGranted(granted);

      const prefs = notificationService.getPreferences();
      setPreferences(prefs);
    } catch (error) {
      console.error('Failed to load preferences:', error);
    } finally {
      setLoading(false);
    }
  };

  const updatePreference = useCallback(
    async (key: keyof NotificationPreferences, value: boolean | string) => {
      if (!preferences) return;

      const updated = { ...preferences, [key]: value };
      setPreferences(updated);

      try {
        await notificationService.updatePreferences({ [key]: value });
      } catch (error) {
        console.error('Failed to update preference:', error);
        // Revert on error
        setPreferences(preferences);
      }
    },
    [preferences]
  );

  const handleEnableNotifications = async () => {
    if (!permissionGranted) {
      const granted = await notificationService.requestPermissions();
      
      if (!granted) {
        Alert.alert(
          'Permissions Required',
          'Please enable notifications in your device settings to receive updates.',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Open Settings', onPress: () => Linking.openSettings() },
          ]
        );
        return;
      }
      
      setPermissionGranted(true);
    }

    updatePreference('enabled', true);
  };

  const handleDisableNotifications = () => {
    Alert.alert(
      'Disable Notifications',
      'Are you sure you want to disable all notifications? You may miss important updates.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Disable',
          style: 'destructive',
          onPress: () => updatePreference('enabled', false),
        },
      ]
    );
  };

  if (loading || !preferences) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <Text style={styles.loadingText}>Loading...</Text>
        </View>
      </SafeAreaView>
    );
  }

  const notificationsEnabled = preferences.enabled && permissionGranted;

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        {/* Master Toggle */}
        <View style={styles.masterToggle}>
          <View style={styles.masterToggleContent}>
            <Text style={styles.masterToggleTitle}>Push Notifications</Text>
            <Text style={styles.masterToggleDescription}>
              {notificationsEnabled
                ? 'You will receive push notifications'
                : 'Notifications are disabled'}
            </Text>
          </View>
          <Switch
            value={notificationsEnabled}
            onValueChange={(value) => {
              if (value) {
                handleEnableNotifications();
              } else {
                handleDisableNotifications();
              }
            }}
            trackColor={{ false: '#767577', true: '#818CF8' }}
            thumbColor={notificationsEnabled ? '#6366F1' : '#f4f3f4'}
          />
        </View>

        {!permissionGranted && (
          <View style={styles.warningBanner}>
            <Text style={styles.warningText}>
              ⚠️ Notification permissions not granted. Tap above to enable.
            </Text>
          </View>
        )}

        {/* Notification Types */}
        <SectionHeader title="Notification Types" />
        
        <SettingsRow
          title="Sphere Shares"
          description="When someone shares a sphere with you"
          value={preferences.sphereShares}
          onValueChange={(v) => updatePreference('sphereShares', v)}
          disabled={!notificationsEnabled}
        />

        <SettingsRow
          title="Moment Comments"
          description="When someone comments on your moments"
          value={preferences.momentComments}
          onValueChange={(v) => updatePreference('momentComments', v)}
          disabled={!notificationsEnabled}
        />

        <SettingsRow
          title="Reactions"
          description="When someone reacts to your moments"
          value={preferences.momentReactions}
          onValueChange={(v) => updatePreference('momentReactions', v)}
          disabled={!notificationsEnabled}
        />

        <SettingsRow
          title="Transcription Complete"
          description="When audio/video transcription is ready"
          value={preferences.transcriptionComplete}
          onValueChange={(v) => updatePreference('transcriptionComplete', v)}
          disabled={!notificationsEnabled}
        />

        {/* Digest */}
        <SectionHeader title="Digest" />

        <SettingsRow
          title="Weekly Digest"
          description="Receive a weekly summary of your moments"
          value={preferences.weeklyDigest}
          onValueChange={(v) => updatePreference('weeklyDigest', v)}
          disabled={!notificationsEnabled}
        />

        {/* Quiet Hours */}
        <SectionHeader title="Quiet Hours" />

        <SettingsRow
          title="Enable Quiet Hours"
          description="Mute notifications during specified hours"
          value={preferences.quietHoursEnabled}
          onValueChange={(v) => updatePreference('quietHoursEnabled', v)}
          disabled={!notificationsEnabled}
        />

        {preferences.quietHoursEnabled && notificationsEnabled && (
          <View style={styles.quietHoursContainer}>
            <TimePicker
              label="Start"
              value={preferences.quietHoursStart}
              onChange={(time) => updatePreference('quietHoursStart', time)}
            />
            <TimePicker
              label="End"
              value={preferences.quietHoursEnd}
              onChange={(time) => updatePreference('quietHoursEnd', time)}
            />
            <Text style={styles.quietHoursNote}>
              Notifications will be silenced during these hours
            </Text>
          </View>
        )}

        {/* Info */}
        <View style={styles.infoSection}>
          <Text style={styles.infoText}>
            Some notifications like security alerts cannot be disabled.
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  scrollView: {
    flex: 1,
  },
  content: {
    paddingBottom: 40,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    fontSize: 16,
    color: '#6B7280',
  },
  masterToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#FFFFFF',
    padding: 16,
    marginBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  masterToggleContent: {
    flex: 1,
    marginRight: 16,
  },
  masterToggleTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 4,
  },
  masterToggleDescription: {
    fontSize: 14,
    color: '#6B7280',
  },
  warningBanner: {
    backgroundColor: '#FEF3C7',
    padding: 12,
    marginHorizontal: 16,
    marginVertical: 8,
    borderRadius: 8,
  },
  warningText: {
    fontSize: 14,
    color: '#92400E',
    textAlign: 'center',
  },
  sectionHeader: {
    paddingHorizontal: 16,
    paddingTop: 24,
    paddingBottom: 8,
  },
  sectionHeaderText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6B7280',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#FFFFFF',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  rowDisabled: {
    opacity: 0.5,
  },
  rowTextContainer: {
    flex: 1,
    marginRight: 16,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#111827',
    marginBottom: 2,
  },
  rowDescription: {
    fontSize: 13,
    color: '#6B7280',
  },
  textDisabled: {
    color: '#9CA3AF',
  },
  quietHoursContainer: {
    backgroundColor: '#FFFFFF',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  timePickerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  timePickerLabel: {
    fontSize: 15,
    color: '#374151',
  },
  timePickerValue: {
    fontSize: 15,
    fontWeight: '500',
    color: '#6366F1',
  },
  quietHoursNote: {
    fontSize: 12,
    color: '#9CA3AF',
    marginTop: 8,
    textAlign: 'center',
  },
  infoSection: {
    padding: 16,
    marginTop: 16,
  },
  infoText: {
    fontSize: 13,
    color: '#9CA3AF',
    textAlign: 'center',
  },
});

export default NotificationSettingsScreen;
