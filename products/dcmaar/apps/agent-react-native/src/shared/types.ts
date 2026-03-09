/**
 * Guardian Agent Types
 * Import shared types from @ghatana/dcmaar-agent-core
 */

// ============================================================================
// RE-EXPORTED TYPES FROM @ghatana/dcmaar-agent-core
// ============================================================================
// These types are maintained in libs/guardian-agent-core for consistency
// across all Guardian modules.

export type {
  // Core Device & App Types
  DeviceInfo,
  AppInfo,
  AppCategory,
  
  // Usage Tracking
  UsageData,
  
  // Policy Management
  Policy,
  TimeWindow,
  PolicyAction,
  BlockEvent,
  
  // Sync Protocol
  SyncRequest,
  SyncResponse,
} from '@ghatana/dcmaar-agent-core';

// ============================================================================
// PLATFORM-SPECIFIC TYPES (iOS Screen Time API)
// ============================================================================
// These types are specific to iOS Screen Time API and are not in agent-core
// because they are platform-specific implementation details.

/**
 * iOS Screen Time Managed Settings
 * Used to configure app/website blocking via iOS Screen Time API
 */
export interface ManagedSettings {
  blockedApplications: string[];
  blockedWebDomains: string[];
  shieldConfiguration?: {
    backgroundColor: string;
    title: string;
    subtitle: string;
  };
}

/**
 * iOS Screen Time Data
 * Aggregated screen time data from iOS Screen Time API
 */
export interface ScreenTimeData {
  totalScreenTime: number; // milliseconds
  appUsage: Record<string, number>; // appId -> duration in milliseconds
  pickupCount: number;
  notificationCount: number;
  date: string; // ISO 8601 date
}
