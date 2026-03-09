/**
 * Notification Service
 *
 * Central helper for creating in-app notifications.
 * All notification creation flows through this module so that
 * future integrations (push, email, WebSocket broadcast) have
 * a single insertion point.
 *
 * @doc.type service
 * @doc.purpose Create and manage in-app notifications
 * @doc.layer product
 * @doc.pattern Service
 */

import { prisma } from '../../lib/prisma';

// ============================================================================
// Types
// ============================================================================

export type NotificationType =
  | 'sphere_shared'
  | 'invitation_received'
  | 'invitation_accepted'
  | 'comment_added'
  | 'comment_reply'
  | 'reaction_added'
  | 'follow_new'
  | 'insight_generated'
  | 'weekly_reflection'
  | 'monthly_reflection'
  | 'billing_payment_failed'
  | 'billing_subscription_updated'
  | 'billing_downgraded'
  | 'export_ready'
  | 'deletion_scheduled'
  | 'deletion_completed'
  | 'system_maintenance'
  | 'admin_announcement';

export interface CreateNotificationInput {
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  /** Arbitrary JSON payload (e.g. { sphereId, momentId }) */
  data?: Record<string, unknown>;
  /** Channel: in_app (default), email, push */
  channel?: string;
}

// ============================================================================
// Service
// ============================================================================

/**
 * Create a single in-app notification for a user.
 */
export async function createNotification(input: CreateNotificationInput) {
  const { userId, type, title, body, data, channel = 'in_app' } = input;

  return prisma.notification.create({
    data: {
      userId,
      type,
      title,
      body,
      data: data ?? undefined,
      channel,
      sentAt: new Date(),
    },
  });
}

/**
 * Send the same notification to multiple users at once.
 */
export async function broadcastNotification(
  userIds: string[],
  omit: CreateNotificationInput,
) {
  if (userIds.length === 0) return [];

  const now = new Date();
  return prisma.notification.createMany({
    data: userIds.map((userId) => ({
      userId,
      type: omit.type,
      title: omit.title,
      body: omit.body,
      data: omit.data ?? undefined,
      channel: omit.channel ?? 'in_app',
      sentAt: now,
    })),
  });
}

// ============================================================================
// Convenience helpers (called from route handlers)
// ============================================================================

/**
 * Notify a user that a sphere was shared with them.
 */
export function notifySphereShared(
  recipientId: string,
  sharerName: string,
  sphereName: string,
  sphereId: string,
) {
  return createNotification({
    userId: recipientId,
    type: 'sphere_shared',
    title: 'Sphere shared with you',
    body: `${sharerName} shared the sphere "${sphereName}" with you.`,
    data: { sphereId },
  });
}

/**
 * Notify a sphere owner that someone accepted their invitation.
 */
export function notifyInvitationAccepted(
  ownerId: string,
  accepterName: string,
  sphereName: string,
  sphereId: string,
) {
  return createNotification({
    userId: ownerId,
    type: 'invitation_accepted',
    title: 'Invitation accepted',
    body: `${accepterName} accepted your invitation to "${sphereName}".`,
    data: { sphereId },
  });
}

/**
 * Notify a moment owner that someone commented on their moment.
 */
export function notifyCommentAdded(
  momentOwnerId: string,
  commenterName: string,
  momentId: string,
) {
  return createNotification({
    userId: momentOwnerId,
    type: 'comment_added',
    title: 'New comment',
    body: `${commenterName} commented on your moment.`,
    data: { momentId },
  });
}

/**
 * Notify a user about a reply to their comment.
 */
export function notifyCommentReply(
  parentCommentUserId: string,
  replierName: string,
  momentId: string,
) {
  return createNotification({
    userId: parentCommentUserId,
    type: 'comment_reply',
    title: 'Reply to your comment',
    body: `${replierName} replied to your comment.`,
    data: { momentId },
  });
}

/**
 * Notify a user that someone reacted to their moment.
 */
export function notifyReactionAdded(
  momentOwnerId: string,
  reactorName: string,
  momentId: string,
  reactionType: string,
) {
  return createNotification({
    userId: momentOwnerId,
    type: 'reaction_added',
    title: 'New reaction',
    body: `${reactorName} reacted (${reactionType}) to your moment.`,
    data: { momentId, reactionType },
  });
}

/**
 * Notify a user that AI insights have been generated.
 */
export function notifyInsightGenerated(userId: string, sphereName: string) {
  return createNotification({
    userId,
    type: 'insight_generated',
    title: 'New insights available',
    body: `Fresh AI insights are ready for your "${sphereName}" sphere.`,
  });
}

/**
 * Notify a user about a billing event (payment failed, downgraded, etc.)
 */
export function notifyBillingEvent(
  userId: string,
  type: 'billing_payment_failed' | 'billing_subscription_updated' | 'billing_downgraded',
  title: string,
  body: string,
) {
  return createNotification({ userId, type, title, body });
}

/**
 * Notify a user that their data export is ready for download.
 */
export function notifyExportReady(userId: string, exportId: string) {
  return createNotification({
    userId,
    type: 'export_ready',
    title: 'Data export ready',
    body: 'Your data export is complete and ready for download.',
    data: { exportId },
  });
}

/**
 * Notify a user that their account has been scheduled for deletion.
 */
export function notifyDeletionScheduled(userId: string, scheduledDate: string) {
  return createNotification({
    userId,
    type: 'deletion_scheduled',
    title: 'Account deletion scheduled',
    body: `Your account is scheduled for deletion on ${scheduledDate}. Log in to cancel.`,
    data: { scheduledDate },
  });
}
