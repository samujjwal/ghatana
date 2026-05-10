import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

/**
 * i18n configuration for Data Cloud Platform UI.
 *
 * Supports multiple languages with locale-specific translations.
 * Default locale is 'en' (English).
 *
 * @doc.type configuration
 * @doc.purpose Internationalization setup
 * @doc.layer frontend
 */

const resources = {
  en: {
    translation: {
      // Common
      common: {
        loading: 'Loading...',
        error: 'Error',
        success: 'Success',
        cancel: 'Cancel',
        save: 'Save',
        delete: 'Delete',
        edit: 'Edit',
        search: 'Search',
        filter: 'Filter',
        export: 'Export',
        import: 'Import',
      },
      // Navigation
      nav: {
        dashboard: 'Dashboard',
        data: 'Data',
        events: 'Events',
        query: 'Query',
        pipelines: 'Pipelines',
        trust: 'Trust',
        settings: 'Settings',
      },
      // Connectors
      connectors: {
        title: 'Data Sources',
        add: 'Add Data Source',
        name: 'Name',
        type: 'Type',
        status: 'Status',
        enabled: 'Enabled',
        disabled: 'Disabled',
        testConnection: 'Test Connection',
        enable: 'Enable',
        disable: 'Disable',
        rotateCredentials: 'Rotate Credentials',
        sync: 'Sync',
        health: 'Health',
        healthy: 'Healthy',
        unhealthy: 'Unhealthy',
        pending: 'Pending',
      },
    },
  },
};

i18n
  .use(initReactI18next)
  .init({
    resources,
    lng: 'en', // default language
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false, // React already escapes values
    },
  });

export default i18n;
