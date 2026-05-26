/**
 * English locale strings for the PHR mobile application.
 */
export const en = {
  tabs: {
    home: 'Home',
    records: 'Records',
    consents: 'Consents',
    alerts: 'Alerts',
    emergency: 'Emergency',
    settings: 'Settings',
  },
  dashboard: {
    title: 'My Health Dashboard',
    loading: 'Loading dashboard…',
    error: 'Failed to load dashboard. Please try again.',
    welcome: 'Welcome, {{name}}',
    lastSync: 'Last synced: {{time}}',
    noData: 'No health data available.',
  },
  records: {
    title: 'Health Records',
    loading: 'Loading records…',
    error: 'Failed to load records.',
    noRecords: 'No records found.',
    detail: 'Record Detail',
    type: 'Type',
    date: 'Date',
    status: 'Status',
    value: 'Value',
    unit: 'Unit',
  },
  consents: {
    title: 'My Consents',
    loading: 'Loading consents…',
    error: 'Failed to load consents.',
    noConsents: 'No active consents.',
    empty: 'No consents found.',
    granted: 'Granted',
    revoked: 'Revoked',
    active: 'Active',
    inactive: 'Inactive',
    expires: 'Expires: {{date}}',
    revoke: 'Revoke',
    revoking: 'Revoking…',
    revokeConfirm: 'Are you sure you want to revoke this consent?',
    revokeError: 'Failed to revoke consent.',
  },
  alerts: {
    title: 'Alerts',
    loading: 'Loading alerts…',
    error: 'Failed to load alerts.',
    noAlerts: 'No alerts.',
    markRead: 'Mark as read',
  },
  emergency: {
    title: 'Emergency Access',
    reasonLabel: 'Reason for access',
    reasonPlaceholder: 'Enter reason (required)',
    requestButton: 'Request Emergency Access',
    requesting: 'Requesting…',
    authorized: 'Emergency access authorized.',
    denied: 'Emergency access denied.',
    error: 'Failed to request emergency access.',
    biometricPrompt: 'Authenticate to confirm emergency access',
  },
  settings: {
    title: 'Settings',
    logoutButton: 'Sign Out',
    logoutConfirmTitle: 'Sign Out',
    logoutConfirmMessage: 'Are you sure you want to sign out?',
    logoutConfirmOk: 'Sign Out',
    logoutConfirmCancel: 'Cancel',
    languageLabel: 'Language',
    languageEnglish: 'English',
    languageNepali: 'Nepali',
    version: 'Version {{version}}',
  },
  error: {
    generic: 'An error occurred. Please try again.',
    network: 'Network error. Please check your connection.',
    session: 'Your session has expired. Please sign in again.',
    unauthorized: 'You are not authorized to perform this action.',
    notFound: 'The requested resource was not found.',
  },
  login: {
    title: 'PHR Nepal',
    subtitle: 'Secure mobile record access',
    nationalIdLabel: 'National ID',
    nationalIdPlaceholder: 'National ID or MRN',
    passwordLabel: 'Password',
    passwordPlaceholder: 'Password',
    nationalIdRequired: 'National ID is required.',
    passwordRequired: 'Password is required.',
    signIn: 'Sign In',
    signingIn: 'Signing in…',
    failed: 'Login failed. Please try again.',
  },
  offline: {
    banner: 'You are offline. Some features may be unavailable.',
  },
  common: {
    retry: 'Retry',
    cancel: 'Cancel',
    confirm: 'Confirm',
    back: 'Back',
    save: 'Save',
    loading: 'Loading…',
    ok: 'OK',
    yes: 'Yes',
    no: 'No',
    error: 'An error occurred.',
  },
} as const;

/**
 * Recursively replaces all leaf string values with `string`, preserving the
 * nested key structure. Use this as the type for non-English locale files so
 * translations can contain any strings while still enforcing key completeness.
 */
type DeepStringRecord<T> = {
  [K in keyof T]: T[K] extends Record<string, unknown> ? DeepStringRecord<T[K]> : string;
};

/** Exact type of the English locale (string literals). */
export type EnLocale = typeof en;

/** Structural shape of a locale: same keys as English, any string values. */
export type LocaleShape = DeepStringRecord<EnLocale>;
