/**
 * Notification Service
 *
 * Provides AI-driven notification prioritization, consolidation, and delivery.
 * Includes contextual notification routing and intelligent alert grouping.
 *
 * @doc.type service
 * @doc.purpose AI-powered notification management
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export type NotificationType = 
  | 'info'
  | 'success'
  | 'warning'
  | 'error'
  | 'task'
  | 'mention'
  | 'system';

export type NotificationPriority = 'urgent' | 'high' | 'normal' | 'low';

export interface Notification {
  id: string;
  type: NotificationType;
  priority: NotificationPriority;
  title: string;
  message: string;
  context?: {
    projectId?: string;
    taskId?: string;
    userId?: string;
    source?: string;
  };
  timestamp: number;
  expiresAt?: number;
  read: boolean;
  actions?: NotificationAction[];
  metadata?: Record<string, unknown>;
}

export interface NotificationAction {
  id: string;
  label: string;
  action: () => void;
  primary?: boolean;
}

export interface ConsolidatedNotification {
  id: string;
  type: NotificationType;
  priority: NotificationPriority;
  title: string;
  count: number;
  notifications: Notification[];
  context?: Notification['context'];
  timestamp: number;
}

export interface NotificationRequest {
  notifications: Notification[];
  userContext?: {
    currentProject?: string;
    currentTask?: string;
    preferences?: NotificationPreferences;
  };
}

export interface NotificationPreferences {
  enableConsolidation: boolean;
  quietHours?: {
    start: string; // HH:MM
    end: string; // HH:MM
  };
  categories: Record<NotificationType, boolean>;
  priorities: Record<NotificationPriority, boolean>;
}

export interface NotificationResponse {
  prioritized: Notification[];
  consolidated: ConsolidatedNotification[];
  suppressed: Notification[];
  metadata: {
    timestamp: string;
    algorithm: 'ai-hybrid';
  };
}

// ============================================================================
// Notification Prioritization
// ============================================================================

/**
 * Calculate notification priority score
 */
function calculatePriorityScore(notification: Notification): number {
  let score = 0;

  // Base score by priority
  switch (notification.priority) {
    case 'urgent':
      score += 100;
      break;
    case 'high':
      score += 75;
      break;
    case 'normal':
      score += 50;
      break;
    case 'low':
      score += 25;
      break;
  }

  // Adjust by type
  switch (notification.type) {
    case 'error':
      score += 30;
      break;
    case 'warning':
      score += 20;
      break;
    case 'task':
      score += 15;
      break;
    case 'mention':
      score += 25;
      break;
    case 'system':
      score += 10;
      break;
    case 'success':
      score += 5;
      break;
    case 'info':
      score += 0;
      break;
  }

  // Adjust by recency (more recent = higher score)
  const age = Date.now() - notification.timestamp;
  const ageFactor = Math.max(0, 1 - age / (24 * 60 * 60 * 1000)); // Decay over 24 hours
  score *= ageFactor;

  // Adjust by context relevance
  if (notification.context) {
    score += 10; // Context-aware notifications get a boost
  }

  return score;
}

/**
 * Check if notification should be suppressed during quiet hours
 */
function shouldSuppressDuringQuietHours(
  notification: Notification,
  preferences: NotificationPreferences
): boolean {
  if (!preferences.quietHours) return false;

  const now = new Date();
  const currentTime = now.getHours() * 60 + now.getMinutes();

  const [startHour, startMin] = preferences.quietHours.start.split(':').map(Number);
  const [endHour, endMin] = preferences.quietHours.end.split(':').map(Number);

  const startTime = startHour * 60 + startMin;
  const endTime = endHour * 60 + endMin;

  // Check if current time is within quiet hours
  const inQuietHours = currentTime >= startTime && currentTime < endTime;

  // Only suppress non-urgent notifications during quiet hours
  return inQuietHours && notification.priority !== 'urgent';
}

/**
 * Check if notification type is enabled
 */
function isTypeEnabled(notification: Notification, preferences: NotificationPreferences): boolean {
  return preferences.categories[notification.type] !== false;
}

/**
 * Check if notification priority is enabled
 */
function isPriorityEnabled(notification: Notification, preferences: NotificationPreferences): boolean {
  return preferences.priorities[notification.priority] !== false;
}

// ============================================================================
// Notification Consolidation
// ============================================================================

/**
 * Check if two notifications can be consolidated
 */
function canConsolidate(n1: Notification, n2: Notification): boolean {
  // Same type and priority
  if (n1.type !== n2.type || n1.priority !== n2.priority) return false;

  // Same context (project/task)
  if (n1.context?.projectId !== n2.context?.projectId) return false;

  // Within consolidation window (5 minutes)
  const timeDiff = Math.abs(n1.timestamp - n2.timestamp);
  return timeDiff < 5 * 60 * 1000;
}

/**
 * Consolidate similar notifications
 */
function consolidateNotifications(notifications: Notification[]): ConsolidatedNotification[] {
  const groups: Map<string, Notification[]> = new Map();

  // Group by consolidation key
  for (const notification of notifications) {
    const key = `${notification.type}-${notification.priority}-${notification.context?.projectId || 'global'}`;
    
    if (!groups.has(key)) {
      groups.set(key, []);
    }
    
    const group = groups.get(key)!;
    
    // Check if can consolidate with existing
    const canConsolidateWithGroup = group.some(n => canConsolidate(n, notification));
    
    if (canConsolidateWithGroup || group.length === 0) {
      group.push(notification);
    }
  }

  // Create consolidated notifications
  const consolidated: ConsolidatedNotification[] = [];
  
  for (const [key, group] of groups.entries()) {
    if (group.length === 1) {
      const n = group[0];
      consolidated.push({
        id: n.id,
        type: n.type,
        priority: n.priority,
        title: n.title,
        count: 1,
        notifications: group,
        context: n.context,
        timestamp: n.timestamp,
      });
    } else {
      // Create consolidated notification
      const first = group[0];
      consolidated.push({
        id: `consolidated-${key}`,
        type: first.type,
        priority: first.priority,
        title: `${group.length} ${first.type} notifications`,
        count: group.length,
        notifications: group,
        context: first.context,
        timestamp: Math.max(...group.map(n => n.timestamp)),
      });
    }
  }

  return consolidated;
}

// ============================================================================
// Service Implementation
// ============================================================================

/**
 * Process notifications with AI-hybrid algorithm
 */
export async function processNotifications(
  request: NotificationRequest
): Promise<NotificationResponse> {
  const { notifications, userContext } = request;

  const preferences = userContext?.preferences || {
    enableConsolidation: true,
    categories: {
      info: true,
      success: true,
      warning: true,
      error: true,
      task: true,
      mention: true,
      system: true,
    },
    priorities: {
      urgent: true,
      high: true,
      normal: true,
      low: true,
    },
  };

  // Filter out suppressed notifications
  const filtered = notifications.filter(n => {
    if (shouldSuppressDuringQuietHours(n, preferences)) return false;
    if (!isTypeEnabled(n, preferences)) return false;
    if (!isPriorityEnabled(n, preferences)) return false;
    return true;
  });

  // Sort by priority score
  const prioritized = [...filtered].sort((a, b) => {
    const scoreA = calculatePriorityScore(a);
    const scoreB = calculatePriorityScore(b);
    return scoreB - scoreA;
  });

  // Consolidate if enabled
  const consolidated = preferences.enableConsolidation
    ? consolidateNotifications(prioritized)
    : prioritized.map(n => ({
        id: n.id,
        type: n.type,
        priority: n.priority,
        title: n.title,
        count: 1,
        notifications: [n],
        context: n.context,
        timestamp: n.timestamp,
      }));

  // Calculate suppressed notifications
  const suppressed = notifications.filter(n => !filtered.includes(n));

  return {
    prioritized,
    consolidated,
    suppressed,
    metadata: {
      timestamp: new Date().toISOString(),
      algorithm: 'ai-hybrid',
    },
  };
}

/**
 * Get notification statistics
 */
export function getNotificationStats(notifications: Notification[]): {
  total: number;
  unread: number;
  byType: Record<NotificationType, number>;
  byPriority: Record<NotificationPriority, number>;
} {
  const stats = {
    total: notifications.length,
    unread: notifications.filter(n => !n.read).length,
    byType: {} as Record<NotificationType, number>,
    byPriority: {} as Record<NotificationPriority, number>,
  };

  for (const notification of notifications) {
    stats.byType[notification.type] = (stats.byType[notification.type] || 0) + 1;
    stats.byPriority[notification.priority] = (stats.byPriority[notification.priority] || 0) + 1;
  }

  return stats;
}

/**
 * Mark notification as read
 */
export function markAsRead(notificationId: string, notifications: Notification[]): Notification[] {
  return notifications.map(n =>
    n.id === notificationId ? { ...n, read: true } : n
  );
}

/**
 * Mark all notifications as read
 */
export function markAllAsRead(notifications: Notification[]): Notification[] {
  return notifications.map(n => ({ ...n, read: true }));
}

/**
 * Remove notification
 */
export function removeNotification(notificationId: string, notifications: Notification[]): Notification[] {
  return notifications.filter(n => n.id !== notificationId);
}

/**
 * Clear expired notifications
 */
export function clearExpiredNotifications(notifications: Notification[]): Notification[] {
  const now = Date.now();
  return notifications.filter(n => !n.expiresAt || n.expiresAt > now);
}
