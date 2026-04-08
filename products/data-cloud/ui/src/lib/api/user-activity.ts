/**
 * User Activity API
 *
 * Provides endpoints for tracking and retrieving user activity.
 *
 * @doc.type service
 * @doc.purpose User activity tracking and retrieval
 * @doc.layer frontend
 * @doc.pattern Repository Pattern
 */

import { apiClient } from './client';

/**
 * User activity item
 */
export interface UserActivityItem {
  id: string;
  action: string;
  target: string;
  timestamp: string;
  type: 'create' | 'update' | 'delete' | 'query' | 'alert';
  resourceType?: string;
  resourceId?: string;
}

/**
 * Continue working item
 */
export interface ContinueWorkingItem {
  id: string;
  name: string;
  type: 'collection' | 'workflow' | 'query' | 'dashboard';
  lastAccessed: string;
  path: string;
}

/**
 * Recent activity response
 */
export interface RecentActivityResponse {
  activities: UserActivityItem[];
  continueWorking: ContinueWorkingItem[];
}

/**
 * Get recent user activity
 */
export async function getRecentActivity(): Promise<RecentActivityResponse> {
  return apiClient.get<RecentActivityResponse>('/user-activity/recent');
}

/**
 * Log user activity
 */
export async function logActivity(data: {
  action: string;
  target: string;
  type: UserActivityItem['type'];
  resourceType?: string;
  resourceId?: string;
}): Promise<void> {
  await apiClient.post('/user-activity/log', data);
}
