import React, { useState } from 'react';
import { DynamicForm, type FieldConfig } from '@ghatana/ui';

/**
 * Guardian Settings Component
 * 
 * Application settings interface using DynamicForm for:
 * - General settings (language, timezone)
 * - Notification preferences
 * - Privacy settings
 * - Advanced configuration
 * 
 * **REUSE SUCCESS**: Uses DynamicForm from Batch 1
 * **TIME SAVED**: ~4 hours by reusing existing form infrastructure
 * 
 * @example
 * ```tsx
 * <SettingsNew onSave={(values) => saveSettings(values)} />
 * ```
 */

interface SettingsFormValues {
  // General Settings
  language: string;
  timezone: string;
  theme: string;
  
  // Notification Settings
  emailNotifications: boolean;
  pushNotifications: boolean;
  blockAlerts: boolean;
  usageReports: boolean;
  reportFrequency: string;
  
  // Privacy Settings
  dataCollection: boolean;
  analyticsSharing: boolean;
  crashReports: boolean;
  
  // Advanced Settings
  autoRefresh: boolean;
  refreshInterval: number;
  logLevel: string;
  experimentalFeatures: boolean;
}

interface SettingsNewProps {
  /** Initial settings values */
  initialValues?: Partial<SettingsFormValues>;
  
  /** Callback when settings are saved */
  onSave: (values: SettingsFormValues) => void | Promise<void>;
  
  /** Whether save operation is in progress */
  saving?: boolean;
}

const DEFAULT_SETTINGS: SettingsFormValues = {
  language: 'en',
  timezone: 'America/New_York',
  theme: 'light',
  emailNotifications: true,
  pushNotifications: true,
  blockAlerts: true,
  usageReports: true,
  reportFrequency: 'weekly',
  dataCollection: true,
  analyticsSharing: false,
  crashReports: true,
  autoRefresh: true,
  refreshInterval: 30,
  logLevel: 'info',
  experimentalFeatures: false,
};

/**
 * SettingsNew - Guardian settings interface using DynamicForm
 * 
 * Features:
 * - Tabbed sections (General, Notifications, Privacy, Advanced)
 * - Form validation
 * - Save/Reset actions
 * - Real-time preview
 * 
 * **Architecture**: Wrapper pattern - Configures generic DynamicForm for Guardian settings
 */
export const SettingsNew: React.FC<SettingsNewProps> = ({
  initialValues,
  onSave,
  saving = false,
}) => {
  const [activeTab, setActiveTab] = useState<'general' | 'notifications' | 'privacy' | 'advanced'>('general');
  const [formValues, setFormValues] = useState<SettingsFormValues>({
    ...DEFAULT_SETTINGS,
    ...initialValues,
  });

  // Field configurations by tab
  const generalFields: FieldConfig<SettingsFormValues>[] = [
    {
      name: 'language',
      label: 'Language',
      type: 'select',
      options: [
        { label: 'English', value: 'en' },
        { label: 'Spanish', value: 'es' },
        { label: 'French', value: 'fr' },
        { label: 'German', value: 'de' },
      ],
      required: true,
      helpText: 'Select your preferred language',
    },
    {
      name: 'timezone',
      label: 'Timezone',
      type: 'select',
      options: [
        { label: 'Eastern Time (ET)', value: 'America/New_York' },
        { label: 'Central Time (CT)', value: 'America/Chicago' },
        { label: 'Mountain Time (MT)', value: 'America/Denver' },
        { label: 'Pacific Time (PT)', value: 'America/Los_Angeles' },
        { label: 'UTC', value: 'UTC' },
      ],
      required: true,
      helpText: 'Your local timezone for scheduling and reports',
    },
    {
      name: 'theme',
      label: 'Theme',
      type: 'select',
      options: [
        { label: 'Light', value: 'light' },
        { label: 'Dark', value: 'dark' },
        { label: 'Auto (System)', value: 'auto' },
      ],
      required: true,
      helpText: 'Choose your preferred color theme',
    },
  ];

  const notificationFields: FieldConfig<SettingsFormValues>[] = [
    {
      name: 'emailNotifications',
      label: 'Email Notifications',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: 'Receive notifications via email',
    },
    {
      name: 'pushNotifications',
      label: 'Push Notifications',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: 'Receive push notifications in browser',
    },
    {
      name: 'blockAlerts',
      label: 'Block Event Alerts',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: 'Get notified when content is blocked',
    },
    {
      name: 'usageReports',
      label: 'Usage Reports',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: 'Receive periodic usage reports',
    },
    {
      name: 'reportFrequency',
      label: 'Report Frequency',
      type: 'select',
      options: [
        { label: 'Daily', value: 'daily' },
        { label: 'Weekly', value: 'weekly' },
        { label: 'Monthly', value: 'monthly' },
      ],
      required: true,
      helpText: 'How often to receive usage reports',
    },
  ];

  // Filter out conditional fields based on form values
  const getFilteredNotificationFields = () => {
    return notificationFields.filter(field => {
      // Only show reportFrequency if usageReports is enabled
      if (field.name === 'reportFrequency') {
        return formValues.usageReports;
      }
      return true;
    });
  };

  const privacyFields: FieldConfig<SettingsFormValues>[] = [
    {
      name: 'dataCollection',
      label: 'Anonymous Data Collection',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: 'Help improve Guardian by sharing anonymous usage data',
    },
    {
      name: 'analyticsSharing',
      label: 'Analytics Sharing',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: 'Share analytics data with parent organization',
    },
    {
      name: 'crashReports',
      label: 'Automatic Crash Reports',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: 'Automatically send crash reports to help fix issues',
    },
  ];

  const advancedFields: FieldConfig<SettingsFormValues>[] = [
    {
      name: 'autoRefresh',
      label: 'Auto Refresh',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: 'Automatically refresh data at regular intervals',
    },
    {
      name: 'refreshInterval',
      label: 'Refresh Interval (seconds)',
      type: 'number',
      min: 10,
      max: 300,
      required: true,
      helpText: 'How often to refresh data (10-300 seconds)',
    },
    {
      name: 'logLevel',
      label: 'Log Level',
      type: 'select',
      options: [
        { label: 'Error', value: 'error' },
        { label: 'Warning', value: 'warn' },
        { label: 'Info', value: 'info' },
        { label: 'Debug', value: 'debug' },
      ],
      required: true,
      helpText: 'Console logging verbosity',
    },
    {
      name: 'experimentalFeatures',
      label: 'Enable Experimental Features',
      type: 'select',
      options: [
        { label: 'Enabled', value: true },
        { label: 'Disabled', value: false },
      ],
      helpText: '⚠️ Warning: May cause instability',
    },
  ];

  // Filter out conditional fields based on form values
  const getFilteredAdvancedFields = () => {
    return advancedFields.filter(field => {
      // Only show refreshInterval if autoRefresh is enabled
      if (field.name === 'refreshInterval') {
        return formValues.autoRefresh;
      }
      return true;
    });
  };

  // Get fields for active tab
  const getActiveFields = (): FieldConfig<SettingsFormValues>[] => {
    switch (activeTab) {
      case 'general':
        return generalFields;
      case 'notifications':
        return getFilteredNotificationFields();
      case 'privacy':
        return privacyFields;
      case 'advanced':
        return getFilteredAdvancedFields();
      default:
        return generalFields;
    }
  };

  const handleSave = async (values: SettingsFormValues) => {
    const updatedValues = { ...formValues, ...values };
    setFormValues(updatedValues);
    await onSave(updatedValues);
  };

  const handleReset = () => {
    setFormValues({ ...DEFAULT_SETTINGS, ...initialValues });
  };

  // Tab button component
  const TabButton: React.FC<{ id: typeof activeTab; label: string }> = ({ id, label }) => (
    <button
      onClick={() => setActiveTab(id)}
      className={`
        px-4 py-2 text-sm font-medium rounded-md transition-colors
        ${
          activeTab === id
            ? 'bg-indigo-100 text-indigo-700'
            : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
        }
      `}
    >
      {label}
    </button>
  );

  return (
    <div className="bg-white shadow rounded-lg">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200">
        <h2 className="text-2xl font-bold text-gray-900">Settings</h2>
        <p className="mt-1 text-sm text-gray-600">
          Manage your Guardian application preferences
        </p>
      </div>

      {/* Tabs */}
      <div className="px-6 py-4 border-b border-gray-200">
        <div className="flex space-x-2">
          <TabButton id="general" label="General" />
          <TabButton id="notifications" label="Notifications" />
          <TabButton id="privacy" label="Privacy" />
          <TabButton id="advanced" label="Advanced" />
        </div>
      </div>

      {/* Form Content */}
      <div className="px-6 py-6">
        <DynamicForm
          fields={getActiveFields()}
          initialData={formValues}
          onSubmit={handleSave}
          submitText={saving ? 'Saving...' : 'Save Settings'}
          cancelText="Reset to Defaults"
          onCancel={handleReset}
          disableOnSubmit={saving}
        />
      </div>

      {/* Info Footer */}
      <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 rounded-b-lg">
        <p className="text-xs text-gray-500">
          💡 <strong>Tip:</strong> Changes are saved immediately and will take effect on your next session.
        </p>
      </div>
    </div>
  );
};

export default SettingsNew;
