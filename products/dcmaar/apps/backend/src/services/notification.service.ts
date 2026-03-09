/**
 * Notification Service
 *
 * Manages notifications for parents based on Guardian events.
 * Supports prioritization, filtering, and digest generation.
 *
 * @doc.type service
 * @doc.purpose Notification management for parental alerts
 * @doc.layer backend
 * @doc.pattern Service
 */

import { query } from '../db';
import { logger } from '../utils/logger';
import { v4 as uuidv4 } from 'uuid';

/**
 * Notification priority levels
 */
export type NotificationPriority = 'low' | 'medium' | 'high' | 'critical';

/**
 * Notification types
 */
export type NotificationType =
    | 'block_event'
    | 'risk_alert'
    | 'child_request'
    | 'request_decision'
    | 'usage_alert'
    | 'device_offline'
    | 'policy_violation'
    | 'system';

/**
 * Notification entity
 */
export interface Notification {
    id: string;
    user_id: string;
    child_id?: string;
    device_id?: string;
    type: NotificationType;
    priority: NotificationPriority;
    title: string;
    message: string;
    metadata?: Record<string, unknown>;
    is_read: boolean;
    created_at: string;
    read_at?: string;
}

/**
 * Create notification input
 */
export interface CreateNotificationInput {
    user_id: string;
    child_id?: string;
    device_id?: string;
    type: NotificationType;
    priority: NotificationPriority;
    title: string;
    message: string;
    metadata?: Record<string, unknown>;
}

/**
 * Notification filters
 */
export interface NotificationFilters {
    child_id?: string;
    type?: NotificationType;
    priority?: NotificationPriority;
    is_read?: boolean;
    limit?: number;
    offset?: number;
}

/**
 * Create a new notification
 */
export async function createNotification(
    input: CreateNotificationInput
): Promise<Notification> {
    const id = uuidv4();

    const result = await query<Notification>(
        `INSERT INTO notifications (id, user_id, child_id, device_id, type, priority, title, message, metadata, is_read, created_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, false, NOW())
     RETURNING *`,
        [
            id,
            input.user_id,
            input.child_id || null,
            input.device_id || null,
            input.type,
            input.priority,
            input.title,
            input.message,
            JSON.stringify(input.metadata || {}),
        ]
    );

    logger.info('Notification created', {
        id,
        type: input.type,
        priority: input.priority,
        userId: input.user_id,
    });

    return result[0];
}

/**
 * Get notifications for a user
 */
export async function getNotifications(
    userId: string,
    filters?: NotificationFilters
): Promise<Notification[]> {
    const conditions: string[] = ['user_id = $1'];
    const params: unknown[] = [userId];
    let paramIndex = 2;

    if (filters?.child_id) {
        conditions.push(`child_id = $${paramIndex++}`);
        params.push(filters.child_id);
    }

    if (filters?.type) {
        conditions.push(`type = $${paramIndex++}`);
        params.push(filters.type);
    }

    if (filters?.priority) {
        conditions.push(`priority = $${paramIndex++}`);
        params.push(filters.priority);
    }

    if (filters?.is_read !== undefined) {
        conditions.push(`is_read = $${paramIndex++}`);
        params.push(filters.is_read);
    }

    const limit = filters?.limit || 50;
    const offset = filters?.offset || 0;

    const sql = `
    SELECT * FROM notifications
    WHERE ${conditions.join(' AND ')}
    ORDER BY 
      CASE priority 
        WHEN 'critical' THEN 1 
        WHEN 'high' THEN 2 
        WHEN 'medium' THEN 3 
        ELSE 4 
      END,
      created_at DESC
    LIMIT $${paramIndex++} OFFSET $${paramIndex}
  `;

    params.push(limit, offset);

    return query<Notification>(sql, params);
}

/**
 * Get unread notification count
 */
export async function getUnreadCount(
    userId: string,
    childId?: string
): Promise<{ total: number; high: number; critical: number }> {
    const conditions = ['user_id = $1', 'is_read = false'];
    const params: unknown[] = [userId];

    if (childId) {
        conditions.push('child_id = $2');
        params.push(childId);
    }

    const result = await query<{
        total: string;
        high: string;
        critical: string;
    }>(
        `SELECT 
       COUNT(*) as total,
       COUNT(*) FILTER (WHERE priority = 'high') as high,
       COUNT(*) FILTER (WHERE priority = 'critical') as critical
     FROM notifications
     WHERE ${conditions.join(' AND ')}`,
        params
    );

    return {
        total: parseInt(result[0]?.total || '0', 10),
        high: parseInt(result[0]?.high || '0', 10),
        critical: parseInt(result[0]?.critical || '0', 10),
    };
}

/**
 * Mark notification as read
 */
export async function markAsRead(
    notificationId: string,
    userId: string
): Promise<boolean> {
    const result = await query(
        `UPDATE notifications 
     SET is_read = true, read_at = NOW()
     WHERE id = $1 AND user_id = $2`,
        [notificationId, userId]
    );

    return (result as unknown as { rowCount: number }).rowCount > 0;
}

/**
 * Mark all notifications as read
 */
export async function markAllAsRead(
    userId: string,
    childId?: string
): Promise<number> {
    const conditions = ['user_id = $1', 'is_read = false'];
    const params: unknown[] = [userId];

    if (childId) {
        conditions.push('child_id = $2');
        params.push(childId);
    }

    const result = await query(
        `UPDATE notifications 
     SET is_read = true, read_at = NOW()
     WHERE ${conditions.join(' AND ')}`,
        params
    );

    return (result as unknown as { rowCount: number }).rowCount;
}

/**
 * Create notification from block event
 */
export async function createBlockNotification(
    userId: string,
    childId: string,
    deviceId: string,
    blockedItem: string,
    category: string,
    reason: string
): Promise<Notification> {
    const priority: NotificationPriority =
        category === 'adult' || category === 'violence' ? 'high' : 'medium';

    return createNotification({
        user_id: userId,
        child_id: childId,
        device_id: deviceId,
        type: 'block_event',
        priority,
        title: `Blocked: ${blockedItem}`,
        message: reason,
        metadata: { category, blocked_item: blockedItem },
    });
}

/**
 * Create notification from child request
 */
export async function createRequestNotification(
    userId: string,
    childId: string,
    childName: string,
    requestType: 'unblock' | 'extend_session',
    resource?: string,
    reason?: string
): Promise<Notification> {
    const title =
        requestType === 'unblock'
            ? `${childName} requests access to ${resource || 'a site'}`
            : `${childName} requests more time`;

    return createNotification({
        user_id: userId,
        child_id: childId,
        type: 'child_request',
        priority: 'medium',
        title,
        message: reason || 'No reason provided',
        metadata: { request_type: requestType, resource },
    });
}

/**
 * Create notification from risk alert
 */
export async function createRiskNotification(
    userId: string,
    childId: string,
    childName: string,
    riskBucket: 'high' | 'critical',
    factors: string[]
): Promise<Notification> {
    const priority: NotificationPriority =
        riskBucket === 'critical' ? 'critical' : 'high';

    return createNotification({
        user_id: userId,
        child_id: childId,
        type: 'risk_alert',
        priority,
        title: `${riskBucket === 'critical' ? 'Critical' : 'High'} risk alert for ${childName}`,
        message: factors.slice(0, 3).join(', '),
        metadata: { risk_bucket: riskBucket, factors },
    });
}

/**
 * Generate daily digest summary
 */
export async function generateDigest(
    userId: string,
    since: Date
): Promise<{
    totalNotifications: number;
    byType: Record<NotificationType, number>;
    byPriority: Record<NotificationPriority, number>;
    highlights: Notification[];
}> {
    const notifications = await query<Notification>(
        `SELECT * FROM notifications
     WHERE user_id = $1 AND created_at >= $2
     ORDER BY priority, created_at DESC`,
        [userId, since.toISOString()]
    );

    const byType: Record<string, number> = {};
    const byPriority: Record<string, number> = {};

    for (const n of notifications) {
        byType[n.type] = (byType[n.type] || 0) + 1;
        byPriority[n.priority] = (byPriority[n.priority] || 0) + 1;
    }

    // Get top 5 high-priority notifications as highlights
    const highlights = notifications
        .filter(n => n.priority === 'critical' || n.priority === 'high')
        .slice(0, 5);

    return {
        totalNotifications: notifications.length,
        byType: byType as Record<NotificationType, number>,
        byPriority: byPriority as Record<NotificationPriority, number>,
        highlights,
    };
}

/**
 * Delete old notifications (cleanup job)
 */
export async function deleteOldNotifications(
    olderThanDays: number = 30
): Promise<number> {
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - olderThanDays);

    const result = await query(
        `DELETE FROM notifications WHERE created_at < $1 AND is_read = true`,
        [cutoff.toISOString()]
    );

    const count = (result as unknown as { rowCount: number }).rowCount;

    logger.info('Cleaned up old notifications', { deleted: count, olderThanDays });

    return count;
}
