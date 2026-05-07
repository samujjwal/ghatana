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
import { z } from 'zod';

const ContinueWorkingItemTypeSchema = z
  .enum(['collection', 'workflow', 'query', 'insight', 'dashboard'])
  .transform((type) => (type === 'dashboard' ? 'insight' : type));

const UserActivityItemSchema = z.object({
  id: z.string(),
  action: z.string(),
  target: z.string(),
  timestamp: z.string(),
  type: z.enum(['create', 'update', 'delete', 'query', 'alert']),
  resourceType: z.string().optional(),
  resourceId: z.string().optional(),
});

const ContinueWorkingItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: ContinueWorkingItemTypeSchema,
  lastAccessed: z.string(),
  path: z.string(),
});

const RecentActivityResponseSchema = z.object({
  activities: z.array(UserActivityItemSchema),
  continueWorking: z.array(ContinueWorkingItemSchema),
});

const LogActivityRequestSchema = z.object({
  action: z.string(),
  target: z.string(),
  type: z.enum(['create', 'update', 'delete', 'query', 'alert']),
  resourceType: z.string().optional(),
  resourceId: z.string().optional(),
});

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
  type: 'collection' | 'workflow' | 'query' | 'insight';
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
  const rawResponse = await apiClient.get<RecentActivityResponse>('/user-activity/recent');
  return RecentActivityResponseSchema.parse(rawResponse);
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
  const request = LogActivityRequestSchema.parse(data);
  await apiClient.post('/user-activity/log', request);
}
