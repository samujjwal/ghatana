/**
 * Analytics Plugin Interface
 * Defines the contract for analytics and metrics plugins
 */

import { IPlugin } from '@ghatana/dcmaar-types';

export interface IAnalytics extends IPlugin {
  /**
   * Track an event
   * @param eventName - Name of the event to track
   * @param properties - Event properties
   */
  trackEvent(eventName: string, properties?: Record<string, unknown>): void;

  /**
   * Set user properties
   * @param userId - User identifier
   * @param properties - User properties to set
   */
  setUserProperties(userId: string, properties: Record<string, unknown>): void;

  /**
   * Get analytics summary
   * @returns Summary of analytics data
   */
  getSummary(): Promise<Record<string, unknown>>;

  /**
   * Clear all tracking data
   */
  clear(): Promise<void>;
}
