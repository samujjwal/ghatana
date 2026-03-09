/**
 * Settings Page
 * Account settings, billing, notification preferences, and privacy controls
 */

import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useCurrentUser } from '../hooks/use-api';
import Layout from '../components/Layout';
import {
  User,
  CreditCard,
  Bell,
  Shield,
  CheckCircle,
  XCircle,
  ExternalLink,
} from 'lucide-react';

type SettingsTab = 'account' | 'billing' | 'notifications' | 'privacy';

export default function SettingsPage() {
  const [searchParams] = useSearchParams();
  const [activeTab, setActiveTab] = useState<SettingsTab>('account');
  const { data: user } = useCurrentUser();

  // Handle Stripe redirect
  const billingSuccess = searchParams.get('success') === 'true';
  const billingCanceled = searchParams.get('canceled') === 'true';

  useEffect(() => {
    if (billingSuccess || billingCanceled) {
      setActiveTab('billing');
    }
  }, [billingSuccess, billingCanceled]);

  const tabs = [
    { id: 'account' as SettingsTab, label: 'Account', icon: User },
    { id: 'billing' as SettingsTab, label: 'Billing', icon: CreditCard },
    { id: 'notifications' as SettingsTab, label: 'Notifications', icon: Bell },
    { id: 'privacy' as SettingsTab, label: 'Privacy', icon: Shield },
  ];

  return (
    <Layout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
          <p className="mt-1 text-sm text-gray-500">
            Manage your account, billing, and preferences
          </p>
        </div>

        {/* Stripe redirect banners */}
        {billingSuccess && (
          <div className="bg-green-50 border border-green-200 rounded-lg p-4 flex items-center gap-3">
            <CheckCircle className="h-5 w-5 text-green-500 flex-shrink-0" />
            <div>
              <p className="text-sm font-medium text-green-800">Payment successful!</p>
              <p className="text-sm text-green-600">Your subscription has been updated. Changes may take a moment to reflect.</p>
            </div>
          </div>
        )}
        {billingCanceled && (
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 flex items-center gap-3">
            <XCircle className="h-5 w-5 text-yellow-500 flex-shrink-0" />
            <div>
              <p className="text-sm font-medium text-yellow-800">Payment canceled</p>
              <p className="text-sm text-yellow-600">No charges were made. You can try again anytime.</p>
            </div>
          </div>
        )}

        <div className="flex flex-col sm:flex-row gap-6">
          {/* Sidebar */}
          <nav className="sm:w-48 flex-shrink-0">
            <ul className="space-y-1">
              {tabs.map(({ id, label, icon: Icon }) => (
                <li key={id}>
                  <button
                    onClick={() => setActiveTab(id)}
                    className={`w-full flex items-center gap-3 px-3 py-2 text-sm font-medium rounded-md ${
                      activeTab === id
                        ? 'bg-primary-50 text-primary-700'
                        : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                    }`}
                    aria-current={activeTab === id ? 'page' : undefined}
                  >
                    <Icon className="h-4 w-4" />
                    {label}
                  </button>
                </li>
              ))}
            </ul>
          </nav>

          {/* Content */}
          <div className="flex-1 min-w-0">
            {activeTab === 'account' && (
              <AccountSection user={user} />
            )}
            {activeTab === 'billing' && (
              <BillingSection user={user} />
            )}
            {activeTab === 'notifications' && (
              <NotificationsSection />
            )}
            {activeTab === 'privacy' && (
              <PrivacySection />
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}

function AccountSection({ user }: { user?: { email?: string; displayName?: string; tier?: string; createdAt?: string } | null }) {
  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">Profile</h3>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">Display Name</label>
            <input
              type="text"
              defaultValue={user?.displayName || ''}
              className="mt-1 w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
              placeholder="Enter your name"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Email</label>
            <input
              type="email"
              defaultValue={user?.email || ''}
              disabled
              className="mt-1 w-full border border-gray-200 rounded-md px-3 py-2 text-sm bg-gray-50 text-gray-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Current Plan</label>
            <p className="mt-1 text-sm text-gray-900 font-medium">
              {user?.tier || 'FREE'} tier
            </p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Member Since</label>
            <p className="mt-1 text-sm text-gray-600">
              {user?.createdAt ? new Date(user.createdAt).toLocaleDateString() : 'N/A'}
            </p>
          </div>
        </div>
        <div className="mt-6">
          <button className="px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700">
            Save Changes
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">Security</h3>
        <div className="space-y-4">
          <button className="text-sm text-primary-600 hover:text-primary-700 font-medium">
            Change Password
          </button>
          <div className="border-t border-gray-200 pt-4">
            <h4 className="text-sm font-medium text-gray-900">Active Sessions</h4>
            <p className="text-sm text-gray-500 mt-1">Manage your active sessions and devices.</p>
          </div>
        </div>
      </div>
    </div>
  );
}

function BillingSection({ user }: { user?: { tier?: string } | null }) {
  const tiers = [
    {
      name: 'Free',
      id: 'FREE',
      price: '$0/mo',
      features: ['5 spheres', '100 moments/month', '0 collaborators', '10 hrs transcription', '30-day audit logs'],
    },
    {
      name: 'Pro',
      id: 'PRO',
      price: '$9.99/mo',
      features: ['25 spheres', '1,000 moments/month', '10 collaborators', '50 hrs transcription', '1-year audit logs'],
    },
    {
      name: 'Teams',
      id: 'TEAMS',
      price: '$29/user/mo',
      features: ['Unlimited spheres', 'Unlimited moments', 'Unlimited collaborators', '200 hrs transcription', '3-year audit logs', 'SSO/SAML (Coming Soon)', 'Full API access'],
    },
  ];

  const currentTier = user?.tier || 'FREE';

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">Subscription</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {tiers.map((tier) => (
            <div
              key={tier.id}
              className={`p-4 rounded-lg border-2 ${
                currentTier === tier.id
                  ? 'border-primary-500 bg-primary-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <h4 className="font-medium text-gray-900">{tier.name}</h4>
              <p className="text-2xl font-bold text-gray-900 mt-1">{tier.price}</p>
              <ul className="mt-3 space-y-1">
                {tier.features.map((feature) => (
                  <li key={feature} className="flex items-center text-xs text-gray-600">
                    <CheckCircle className="h-3 w-3 text-green-500 mr-2 flex-shrink-0" />
                    {feature}
                  </li>
                ))}
              </ul>
              <button
                className={`mt-4 w-full px-3 py-2 text-sm font-medium rounded-md ${
                  currentTier === tier.id
                    ? 'bg-gray-100 text-gray-500 cursor-default'
                    : 'bg-primary-600 text-white hover:bg-primary-700'
                }`}
                disabled={currentTier === tier.id}
              >
                {currentTier === tier.id ? 'Current Plan' : 'Upgrade'}
              </button>
            </div>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">Billing History</h3>
        <p className="text-sm text-gray-500">
          No invoices yet.{' '}
          <a href="#" className="text-primary-600 hover:text-primary-700 inline-flex items-center gap-1">
            Manage in Stripe <ExternalLink className="h-3 w-3" />
          </a>
        </p>
      </div>
    </div>
  );
}

function NotificationsSection() {
  const [prefs, setPrefs] = useState({
    emailDigest: true,
    pushNotifications: true,
    collaborationAlerts: true,
    insightNotifications: true,
    billingAlerts: true,
  });

  const toggle = (key: keyof typeof prefs) => {
    setPrefs((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const items = [
    { key: 'emailDigest' as const, label: 'Weekly email digest', description: 'Summary of your activity and insights' },
    { key: 'pushNotifications' as const, label: 'Push notifications', description: 'Real-time alerts for important events' },
    { key: 'collaborationAlerts' as const, label: 'Collaboration alerts', description: 'When someone shares, comments, or reacts' },
    { key: 'insightNotifications' as const, label: 'AI insight notifications', description: 'When new insights are generated' },
    { key: 'billingAlerts' as const, label: 'Billing alerts', description: 'Payment confirmations and limit warnings' },
  ];

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <h3 className="text-lg font-medium text-gray-900 mb-4">Notification Preferences</h3>
      <div className="space-y-4">
        {items.map(({ key, label, description }) => (
          <div key={key} className="flex items-center justify-between py-2">
            <div>
              <p className="text-sm font-medium text-gray-900">{label}</p>
              <p className="text-xs text-gray-500">{description}</p>
            </div>
            <button
              onClick={() => toggle(key)}
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                prefs[key] ? 'bg-primary-600' : 'bg-gray-200'
              }`}
              role="switch"
              aria-checked={prefs[key]}
              aria-label={label}
            >
              <span
                className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                  prefs[key] ? 'translate-x-6' : 'translate-x-1'
                }`}
              />
            </button>
          </div>
        ))}
      </div>
      <div className="mt-6">
        <button className="px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700">
          Save Preferences
        </button>
      </div>
    </div>
  );
}

function PrivacySection() {
  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">Data Privacy</h3>
        <div className="space-y-4">
          <div className="flex items-center justify-between py-2 border-b border-gray-100">
            <div>
              <p className="text-sm font-medium text-gray-900">Export My Data</p>
              <p className="text-xs text-gray-500">Download a copy of all your data (GDPR Article 20)</p>
            </div>
            <button className="px-3 py-1.5 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50">
              Request Export
            </button>
          </div>
          <div className="flex items-center justify-between py-2 border-b border-gray-100">
            <div>
              <p className="text-sm font-medium text-gray-900">Processing Consent</p>
              <p className="text-xs text-gray-500">Controls for AI analysis of your moments</p>
            </div>
            <button className="px-3 py-1.5 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50">
              Manage
            </button>
          </div>
          <div className="flex items-center justify-between py-2">
            <div>
              <p className="text-sm font-medium text-red-600">Delete My Account</p>
              <p className="text-xs text-gray-500">Permanently delete your account and all data (GDPR Article 17)</p>
            </div>
            <button className="px-3 py-1.5 border border-red-300 rounded-md text-sm font-medium text-red-600 hover:bg-red-50">
              Request Deletion
            </button>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-medium text-gray-900 mb-4">Audit Log</h3>
        <p className="text-sm text-gray-500">
          Your activity audit log helps you track how your data is accessed.
          Retention period depends on your subscription tier.
        </p>
        <button className="mt-3 text-sm text-primary-600 hover:text-primary-700 font-medium">
          View Audit Log
        </button>
      </div>
    </div>
  );
}
