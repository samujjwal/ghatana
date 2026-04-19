/**
 * DCMAAR Mobile Shared Library
 *
 * Shared services, hooks, and utilities for DCMAAR mobile apps (parent and child).
 * This library consolidates duplicated code between parent-mobile and child-mobile apps.
 *
 * @doc.type module
 * @doc.purpose Shared mobile app utilities for DCMAAR
 * @doc.layer product
 */

// Services
export { api, storage, notifications } from './services';
export type { Device, UsageData, AppUsage, WebsiteVisit, Policy, TimeRestriction, Alert, PushNotification } from './types';

// Hooks
export {
  useDevices,
  useUsageData,
  usePolicies,
  useCreatePolicy,
  useUpdatePolicy,
  useDeletePolicy,
  useAlerts,
  useMarkAlertRead,
} from './hooks';
