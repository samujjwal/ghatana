import { useState, useCallback } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Card } from "../components/ui";
import { Button, Input, Spinner } from "@ghatana/design-system";
import { useAuth } from "../hooks/useAuth";
import {
  useAnnouncer,
  ConfirmDialog,
  useConfirmDialog,
  toast,
  FormField,
} from "@ghatana/design-system";

interface TenantSettings {
  tenantId: string;
  organizationName: string;
  subdomain: string;
  logoUrl?: string;
  primaryColor: string;
  allowPublicRegistration: boolean;
  requireEmailVerification: boolean;
  defaultUserRole: string;
  maxUsersPerClassroom: number;
  enabledFeatures: string[];
  supportEmail: string;
  timezone: string;
}

export function SettingsPage() {
  const { tenantId } = useAuth();
  const queryClient = useQueryClient();
  const announce = useAnnouncer();
  const [activeTab, setActiveTab] = useState<
    "general" | "branding" | "features" | "notifications" | "danger"
  >("general");
  const [isResetting, setIsResetting] = useState(false);

  // Delete/Reset confirmation dialog
  const {
    dialogProps,
    confirm: confirmAction,
    isOpen: isDialogOpen,
  } = useConfirmDialog();
  const [pendingAction, setPendingAction] = useState<
    "reset-settings" | "clear-data" | null
  >(null);

  const handleResetSettings = useCallback(() => {
    setPendingAction("reset-settings");
    confirmAction({
      title: "Reset Settings",
      message:
        "This will reset all settings to their default values. Your data and content will not be affected. This action cannot be undone.",
      confirmText: "Reset Settings",
      confirmVariant: "destructive",
    });
  }, [confirmAction]);

  const handleClearData = useCallback(() => {
    setPendingAction("clear-data");
    confirmAction({
      title: "Clear All Data",
      message:
        "This will permanently delete ALL content, users, and analytics data. This action is irreversible and cannot be undone.",
      confirmText: "Clear All Data",
      confirmVariant: "destructive",
    });
  }, [confirmAction]);

  const executeAction = useCallback(async () => {
    if (!pendingAction) return;

    setIsResetting(true);
    try {
      if (pendingAction === "reset-settings") {
        // Simulate API call
        await new Promise((resolve) => setTimeout(resolve, 1000));
        queryClient.invalidateQueries({ queryKey: ["tenant-settings"] });
        toast.success("Settings have been reset to defaults");
        announce("Settings reset successfully");
      } else if (pendingAction === "clear-data") {
        // Simulate API call
        await new Promise((resolve) => setTimeout(resolve, 2000));
        toast.success("All data has been cleared");
        announce("All data cleared");
      }
    } catch (error) {
      toast.error("Operation failed. Please try again.");
    } finally {
      setIsResetting(false);
      setPendingAction(null);
    }
  }, [pendingAction, queryClient, announce]);

  const { data: settings, isLoading } = useQuery({
    queryKey: ["tenant-settings", tenantId],
    queryFn: async () => {
      const res = await fetch("/admin/api/v1/settings");
      if (!res.ok) throw new Error("Failed to fetch settings");
      return res.json() as Promise<TenantSettings>;
    },
  });

  const [formData, setFormData] = useState<Partial<TenantSettings>>({});

  // Sync form data when settings load
  useState(() => {
    if (settings) {
      setFormData(settings);
    }
  });

  const updateMutation = useMutation({
    mutationFn: async (updates: Partial<TenantSettings>) => {
      const res = await fetch("/admin/api/v1/settings", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(updates),
      });
      if (!res.ok) throw new Error("Failed to update settings");
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tenant-settings"] });
      announce("Settings saved successfully", 3000);
    },
    onError: (error: Error) => {
      announce(`Error saving settings: ${error.message}`, 5000);
    },
  });

  const handleSave = () => {
    updateMutation.mutate(formData);
  };

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <Spinner size="lg" />
      </div>
    );
  }

  const tabs = [
    { id: "general" as const, label: "General" },
    { id: "branding" as const, label: "Branding" },
    { id: "features" as const, label: "Features" },
    { id: "notifications" as const, label: "Notifications" },
    { id: "danger" as const, label: "Danger Zone", className: "text-red-500" },
  ];

  return (
    <main className="space-y-6" role="main" aria-labelledby="settings-heading">
      {/* Header */}
      <div className="px-4 sm:px-0">
        <h1
          id="settings-heading"
          className="text-xl sm:text-2xl font-bold text-gray-900 dark:text-white"
        >
          Settings
        </h1>
        <p className="text-sm sm:text-base text-gray-600 dark:text-gray-400">
          Configure your organization's TutorPutor instance
        </p>
      </div>

      {/* Tabs - Horizontal scroll on mobile */}
      <div className="border-b border-gray-200 dark:border-gray-700 -mx-4 px-4 sm:mx-0 sm:px-0 overflow-x-auto scrollbar-hide">
        <nav
          className="flex gap-1 sm:gap-4 min-w-max"
          role="tablist"
          aria-label="Settings sections"
        >
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              role="tab"
              aria-selected={activeTab === tab.id}
              aria-controls={`${tab.id}-panel`}
              id={`${tab.id}-tab`}
              className={`px-3 sm:px-4 py-2 sm:py-3 text-sm sm:text-base border-b-2 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500 whitespace-nowrap ${
                activeTab === tab.id
                  ? "border-blue-500 text-blue-600 dark:text-blue-400"
                  : "border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
              } ${tab.className ?? ""}`}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* General Settings */}
      {activeTab === "general" && (
        <div
          className="space-y-4 sm:space-y-6 px-4 sm:px-0"
          role="tabpanel"
          id="general-panel"
          aria-labelledby="general-tab"
        >
          <Card className="p-4 sm:p-6">
            <h2 className="text-base sm:text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Organization Details
            </h2>
            <div className="space-y-4">
              <FormField
                label="Organization Name"
                required
                helperText="This will be displayed throughout the platform"
              >
                <Input
                  id="org-name"
                  value={
                    formData.organizationName ??
                    settings?.organizationName ??
                    ""
                  }
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      organizationName: e.target.value,
                    })
                  }
                  placeholder="Your School Name"
                />
              </FormField>

              <FormField
                label="Subdomain"
                required
                helperText="Your unique URL: subdomain.tutorputor.com"
              >
                <div className="flex items-center gap-2">
                  <Input
                    id="subdomain"
                    value={formData.subdomain ?? settings?.subdomain ?? ""}
                    onChange={(e) =>
                      setFormData({ ...formData, subdomain: e.target.value })
                    }
                    placeholder="yourschool"
                    className="flex-1"
                  />
                  <span className="text-gray-500">.tutorputor.com</span>
                </div>
              </FormField>

              <FormField
                label="Support Email"
                required
                helperText="Users will contact this email for support"
              >
                <Input
                  id="support-email"
                  type="email"
                  value={formData.supportEmail ?? settings?.supportEmail ?? ""}
                  onChange={(e) =>
                    setFormData({ ...formData, supportEmail: e.target.value })
                  }
                  placeholder="support@yourschool.edu"
                />
              </FormField>

              <FormField
                label="Timezone"
                required
                helperText="All dates and times will be displayed in this timezone"
              >
                <select
                  id="timezone"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  value={formData.timezone ?? settings?.timezone ?? "UTC"}
                  onChange={(e) =>
                    setFormData({ ...formData, timezone: e.target.value })
                  }
                >
                  <option value="America/New_York">Eastern Time (ET)</option>
                  <option value="America/Chicago">Central Time (CT)</option>
                  <option value="America/Denver">Mountain Time (MT)</option>
                  <option value="America/Los_Angeles">Pacific Time (PT)</option>
                  <option value="UTC">UTC</option>
                  <option value="Europe/London">London (GMT)</option>
                  <option value="Europe/Paris">Paris (CET)</option>
                  <option value="Asia/Tokyo">Tokyo (JST)</option>
                  <option value="Asia/Kolkata">India (IST)</option>
                </select>
              </FormField>
            </div>
          </Card>

          <Card className="p-4 sm:p-6">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              User Registration
            </h2>
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <input
                  id="allow-public-registration"
                  type="checkbox"
                  checked={
                    formData.allowPublicRegistration ??
                    settings?.allowPublicRegistration ??
                    false
                  }
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      allowPublicRegistration: e.target.checked,
                    })
                  }
                  className="w-4 h-4 rounded focus:ring-2 focus:ring-primary-500"
                />
                <label htmlFor="allow-public-registration">
                  Allow public registration
                </label>
              </div>
              <div className="flex items-center gap-3">
                <input
                  id="require-email-verification"
                  type="checkbox"
                  checked={
                    formData.requireEmailVerification ??
                    settings?.requireEmailVerification ??
                    true
                  }
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      requireEmailVerification: e.target.checked,
                    })
                  }
                  className="w-4 h-4 rounded focus:ring-2 focus:ring-primary-500"
                />
                <label htmlFor="require-email-verification">
                  Require email verification
                </label>
              </div>

              <FormField
                label="Default Role for New Users"
                helperText="New users will automatically be assigned this role"
              >
                <select
                  id="default-role"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  value={
                    formData.defaultUserRole ??
                    settings?.defaultUserRole ??
                    "student"
                  }
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      defaultUserRole: e.target.value,
                    })
                  }
                >
                  <option value="student">Student</option>
                  <option value="teacher">Teacher</option>
                </select>
              </FormField>

              <FormField
                label="Max Users Per Classroom"
                required
                helperText="Maximum number of students that can join a single classroom"
              >
                <Input
                  id="max-users"
                  type="number"
                  value={
                    formData.maxUsersPerClassroom ??
                    settings?.maxUsersPerClassroom ??
                    50
                  }
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      maxUsersPerClassroom: parseInt(e.target.value),
                    })
                  }
                  className="w-32"
                  min="1"
                  max="200"
                />
              </FormField>
            </div>
          </Card>

          <div className="flex justify-end">
            <Button onClick={handleSave} disabled={updateMutation.isPending}>
              {updateMutation.isPending ? "Saving..." : "Save Changes"}
            </Button>
          </div>
        </div>
      )}

      {/* Branding Settings */}
      {activeTab === "branding" && (
        <div className="space-y-6">
          <Card className="p-4 sm:p-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Logo & Colors
            </h3>
            <div className="space-y-6">
              <div>
                <label className="block text-sm font-medium mb-2">
                  Organization Logo
                </label>
                <div className="flex items-center gap-4">
                  <div className="w-24 h-24 bg-gray-100 dark:bg-gray-700 rounded-lg flex items-center justify-center">
                    {settings?.logoUrl ? (
                      <img
                        src={settings.logoUrl}
                        alt="Logo"
                        className="max-w-full max-h-full object-contain"
                      />
                    ) : (
                      <span className="text-gray-400">No logo</span>
                    )}
                  </div>
                  <div>
                    <Button variant="outline" size="sm">
                      Upload Logo
                    </Button>
                    <p className="mt-2 text-xs text-gray-500">
                      Recommended: 200x200px PNG or SVG
                    </p>
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">
                  Primary Brand Color
                </label>
                <div className="flex items-center gap-4">
                  <input
                    type="color"
                    value={
                      formData.primaryColor ??
                      settings?.primaryColor ??
                      "#3b82f6"
                    }
                    onChange={(e) =>
                      setFormData({ ...formData, primaryColor: e.target.value })
                    }
                    className="w-12 h-12 rounded cursor-pointer"
                  />
                  <Input
                    value={
                      formData.primaryColor ??
                      settings?.primaryColor ??
                      "#3b82f6"
                    }
                    onChange={(e) =>
                      setFormData({ ...formData, primaryColor: e.target.value })
                    }
                    className="w-32"
                    placeholder="#3b82f6"
                  />
                </div>
              </div>
            </div>
          </Card>

          <Card className="p-4 sm:p-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Preview
            </h3>
            <div
              className="p-6 rounded-lg"
              style={{
                backgroundColor: `${formData.primaryColor ?? settings?.primaryColor ?? "#3b82f6"}20`,
              }}
            >
              <div
                className="inline-block px-4 py-2 text-white rounded-lg font-medium"
                style={{
                  backgroundColor:
                    formData.primaryColor ??
                    settings?.primaryColor ??
                    "#3b82f6",
                }}
              >
                Sample Button
              </div>
              <p className="mt-4 text-gray-600 dark:text-gray-400">
                This is how your brand color will appear in the application.
              </p>
            </div>
          </Card>

          <div className="flex justify-end">
            <Button onClick={handleSave} disabled={updateMutation.isPending}>
              {updateMutation.isPending ? "Saving..." : "Save Changes"}
            </Button>
          </div>
        </div>
      )}

      {/* Features Settings */}
      {activeTab === "features" && (
        <FeaturesTab
          settings={settings}
          onSave={handleSave}
          isLoading={updateMutation.isPending}
        />
      )}

      {/* Notifications Settings */}
      {activeTab === "notifications" && <NotificationsTab />}

      {/* Danger Zone */}
      {activeTab === "danger" && (
        <DangerZoneTab
          onResetSettings={handleResetSettings}
          onClearData={handleClearData}
          isResetting={isResetting}
        />
      )}

      {/* Confirmation Dialog */}
      <ConfirmDialog
        {...dialogProps}
        loading={isResetting}
        onConfirm={executeAction}
      />

      {/* Success/Error Messages */}
      {updateMutation.isSuccess && (
        <div className="fixed bottom-4 right-4 bg-green-500 text-white px-4 py-2 rounded-lg shadow-lg">
          Settings saved successfully!
        </div>
      )}
      {updateMutation.isError && (
        <div className="fixed bottom-4 right-4 bg-red-500 text-white px-4 py-2 rounded-lg shadow-lg">
          Failed to save settings. Please try again.
        </div>
      )}
    </main>
  );
}

function FeaturesTab({
  settings,
  onSave,
  isLoading,
}: {
  settings?: TenantSettings;
  onSave: () => void;
  isLoading: boolean;
}) {
  const allFeatures = [
    {
      id: "offline_mode",
      label: "Offline Mode",
      description: "Allow users to download content for offline access",
    },
    {
      id: "ai_assistant",
      label: "AI Learning Assistant",
      description: "Enable AI-powered tutoring and hints",
    },
    {
      id: "gamification",
      label: "Gamification",
      description: "Points, badges, and leaderboards",
    },
    {
      id: "social_learning",
      label: "Social Learning",
      description: "Study groups and peer collaboration",
    },
    {
      id: "advanced_analytics",
      label: "Advanced Analytics",
      description: "Detailed learning analytics and insights",
    },
    {
      id: "custom_content",
      label: "Custom Content",
      description: "Allow teachers to create custom modules",
    },
    {
      id: "vr_labs",
      label: "VR Science Labs",
      description: "Virtual reality science experiments",
    },
    {
      id: "lti_integration",
      label: "LTI Integration",
      description: "Connect with external LMS platforms",
    },
  ];

  const [enabledFeatures, setEnabledFeatures] = useState<string[]>(
    settings?.enabledFeatures ?? [],
  );

  const toggleFeature = (featureId: string) => {
    if (enabledFeatures.includes(featureId)) {
      setEnabledFeatures(enabledFeatures.filter((f) => f !== featureId));
    } else {
      setEnabledFeatures([...enabledFeatures, featureId]);
    }
  };

  return (
    <div className="space-y-6">
      <Card className="p-4 sm:p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Feature Toggles
        </h3>
        <div className="space-y-4">
          {allFeatures.map((feature) => (
            <label
              key={feature.id}
              className="flex items-start gap-3 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 cursor-pointer"
            >
              <input
                type="checkbox"
                checked={enabledFeatures.includes(feature.id)}
                onChange={() => toggleFeature(feature.id)}
                className="w-5 h-5 mt-0.5 rounded"
              />
              <div>
                <span className="font-medium text-gray-900 dark:text-white">
                  {feature.label}
                </span>
                <p className="text-sm text-gray-500">{feature.description}</p>
              </div>
            </label>
          ))}
        </div>
      </Card>

      <div className="flex justify-end">
        <Button onClick={onSave} disabled={isLoading}>
          {isLoading ? "Saving..." : "Save Changes"}
        </Button>
      </div>
    </div>
  );
}

function NotificationsTab() {
  const [emailNotifications, setEmailNotifications] = useState({
    newUserRegistration: true,
    dataExportRequests: true,
    weeklyReports: false,
    securityAlerts: true,
    usageLimitWarnings: true,
  });

  return (
    <div className="space-y-6">
      <Card className="p-4 sm:p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Email Notifications
        </h3>
        <div className="space-y-4">
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={emailNotifications.newUserRegistration}
              onChange={(e) =>
                setEmailNotifications({
                  ...emailNotifications,
                  newUserRegistration: e.target.checked,
                })
              }
              className="w-4 h-4 rounded"
            />
            <span>New user registrations</span>
          </label>
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={emailNotifications.dataExportRequests}
              onChange={(e) =>
                setEmailNotifications({
                  ...emailNotifications,
                  dataExportRequests: e.target.checked,
                })
              }
              className="w-4 h-4 rounded"
            />
            <span>Data export/deletion requests</span>
          </label>
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={emailNotifications.weeklyReports}
              onChange={(e) =>
                setEmailNotifications({
                  ...emailNotifications,
                  weeklyReports: e.target.checked,
                })
              }
              className="w-4 h-4 rounded"
            />
            <span>Weekly usage reports</span>
          </label>
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={emailNotifications.securityAlerts}
              onChange={(e) =>
                setEmailNotifications({
                  ...emailNotifications,
                  securityAlerts: e.target.checked,
                })
              }
              className="w-4 h-4 rounded"
            />
            <span>Security alerts</span>
          </label>
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={emailNotifications.usageLimitWarnings}
              onChange={(e) =>
                setEmailNotifications({
                  ...emailNotifications,
                  usageLimitWarnings: e.target.checked,
                })
              }
              className="w-4 h-4 rounded"
            />
            <span>Usage limit warnings</span>
          </label>
        </div>
      </Card>

      <Card className="p-4 sm:p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Notification Recipients
        </h3>
        <p className="text-sm text-gray-500 mb-4">
          Admins with email notifications enabled will receive alerts. You can
          also add additional recipients:
        </p>
        <div className="flex gap-2">
          <Input placeholder="email@example.com" className="flex-1" />
          <Button variant="outline">Add Recipient</Button>
        </div>
      </Card>

      <div className="flex justify-end">
        <Button>Save Notification Settings</Button>
      </div>
    </div>
  );
}

function DangerZoneTab({
  onResetSettings,
  onClearData,
  isResetting,
}: {
  onResetSettings: () => void;
  onClearData: () => void;
  isResetting: boolean;
}) {
  return (
    <div
      className="space-y-4 sm:space-y-6 px-4 sm:px-0"
      role="tabpanel"
      id="danger-panel"
      aria-labelledby="danger-tab"
    >
      <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
        <h3 className="text-red-800 dark:text-red-200 font-semibold text-sm sm:text-base">
          ⚠️ Danger Zone
        </h3>
        <p className="text-red-700 dark:text-red-300 text-xs sm:text-sm mt-1">
          These actions are destructive and cannot be undone. Please proceed
          with caution.
        </p>
      </div>

      <Card className="p-4 sm:p-6 border-red-200 dark:border-red-800">
        <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
          <div>
            <h3 className="text-base sm:text-lg font-semibold text-gray-900 dark:text-white">
              Reset Settings
            </h3>
            <p className="text-sm text-gray-500 mt-1">
              Reset all settings to their default values. Your content and users
              will not be affected.
            </p>
          </div>
          <Button
            variant="outline"
            onClick={onResetSettings}
            disabled={isResetting}
            className="border-red-300 text-red-600 hover:bg-red-50 dark:border-red-700 dark:text-red-400 dark:hover:bg-red-900/20"
          >
            Reset Settings
          </Button>
        </div>
      </Card>

      <Card className="p-6 border-red-200 dark:border-red-800">
        <div className="flex items-start justify-between">
          <div>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
              Clear All Data
            </h3>
            <p className="text-sm text-gray-500 mt-1">
              Permanently delete all content, users, and analytics. This action
              is irreversible.
            </p>
          </div>
          <Button
            variant="destructive"
            onClick={onClearData}
            disabled={isResetting}
          >
            Clear All Data
          </Button>
        </div>
      </Card>

      <Card className="p-6 border-red-200 dark:border-red-800">
        <div className="flex items-start justify-between">
          <div>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
              Export Data
            </h3>
            <p className="text-sm text-gray-500 mt-1">
              Download a complete backup of all your data before making
              destructive changes.
            </p>
          </div>
          <Button variant="outline">Export Data</Button>
        </div>
      </Card>
    </div>
  );
}
