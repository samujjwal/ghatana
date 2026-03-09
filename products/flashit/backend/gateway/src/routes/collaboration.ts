/**
 * Collaboration API Service for Flashit
 * REST API endpoints for collaboration and sharing features
 *
 * @doc.type service
 * @doc.purpose HTTP API for collaboration features
 * @doc.layer product
 * @doc.pattern APIService
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { requireAuth } from '../lib/auth.js';
import { prisma } from '../lib/prisma.js';
import { sendEmail } from '../lib/email';
import { v4 as uuidv4 } from 'uuid';
import { checkCollaboratorLimit } from '../services/billing/usage-limits.js';
import { StripeBillingService } from '../services/billing/stripe-service.js';
import { notifySphereShared, notifyCommentAdded, notifyCommentReply, notifyReactionAdded, notifyInvitationAccepted } from '../services/notifications/notification-service.js';

// Validation schemas
const shareSphereSchema = z.object({
  sphereId: z.string().uuid(),
  sharedWithEmail: z.string().email(),
  permissionLevel: z.enum(['VIEWER', 'COMMENTER', 'EDITOR', 'ADMIN']).default('VIEWER'),
  message: z.string().max(500).optional(),
});

const updateSphereShareSchema = z.object({
  shareId: z.string().uuid(),
  permissionLevel: z.enum(['VIEWER', 'COMMENTER', 'EDITOR', 'ADMIN']),
});

const acceptInvitationSchema = z.object({
  invitationToken: z.string(),
});

const createCommentSchema = z.object({
  momentId: z.string().uuid(),
  content: z.string().min(1).max(2000),
  parentCommentId: z.string().uuid().optional(),
  mentions: z.array(z.string().uuid()).optional(),
});

const updateCommentSchema = z.object({
  commentId: z.string().uuid(),
  content: z.string().min(1).max(2000),
});

const addReactionSchema = z.object({
  momentId: z.string().uuid(),
  reactionType: z.enum(['like', 'love', 'insightful', 'helpful', 'inspiring']),
});

const followUserSchema = z.object({
  userIdToFollow: z.string().uuid(),
});

const updateNotificationPreferencesSchema = z.object({
  emailNotifications: z.boolean().optional(),
  pushNotifications: z.boolean().optional(),
  inAppNotifications: z.boolean().optional(),
  sphereShares: z.boolean().optional(),
  momentComments: z.boolean().optional(),
  momentReactions: z.boolean().optional(),
  newFollowers: z.boolean().optional(),
  mentionNotifications: z.boolean().optional(),
  digestFrequency: z.enum(['never', 'daily', 'weekly', 'monthly']).optional(),
  quietHoursEnabled: z.boolean().optional(),
  quietHoursStart: z.string().regex(/^\d{2}:\d{2}$/).optional(),
  quietHoursEnd: z.string().regex(/^\d{2}:\d{2}$/).optional(),
  quietHoursTimezone: z.string().optional(),
});

export default async function collaborationRoutes(fastify: FastifyInstance) {

  /**
   * Share a sphere with another user
   * POST /api/collaboration/spheres/share
   */
  fastify.post('/spheres/share', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(shareSphereSchema),
      response: {
        200: zodToJsonSchema(z.object({
          shareId: z.string(),
          invitationToken: z.string().optional(),
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { sphereId, sharedWithEmail, permissionLevel, message } = request.body;
    const userId = request.user.userId;

    try {
      // Verify user owns or has admin access to the sphere
      const sphere = await prisma.sphere.findFirst({
        where: {
          id: sphereId,
          OR: [
            { createdByUserId: userId },
            {
              sphereAccess: {
                some: {
                  userId,
                  role: 'ADMIN',
                  revokedAt: null,
                },
              },
            },
          ],
        },
      });

      if (!sphere) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You can only share spheres you own or admin',
        });
      }

      // Check collaborator limit for user's billing tier
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkCollaboratorLimit(userId, sphereId, subscriptionInfo.tier);
      if (!limitCheck.allowed) {
        return reply.status(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'Collaborator limit reached for your plan',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl,
        });
      }

      // Check if user is already shared with
      const existingShare = await prisma.collaboration.sphereShare.findFirst({
        where: {
          sphereId,
          sharedWithEmail,
          revokedAt: null,
        },
      });

      if (existingShare) {
        return reply.status(400).send({
          error: 'Bad Request',
          message: 'Sphere is already shared with this user',
        });
      }

      // Check if email corresponds to existing user
      const existingUser = await prisma.user.findUnique({
        where: { email: sharedWithEmail },
      });

      // Create invitation token for external users
      const invitationToken = existingUser ? null : uuidv4();

      // Create sphere share
      const sphereShare = await prisma.collaboration.sphereShare.create({
        data: {
          sphereId,
          sharedByUserId: userId,
          sharedWithUserId: existingUser?.id,
          sharedWithEmail: existingUser ? null : sharedWithEmail,
          permissionLevel,
          invitationToken,
          invitationExpiresAt: invitationToken ? new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) : null, // 7 days
          acceptedAt: existingUser ? new Date() : null,
        },
      });

      // Send invitation email
      const inviterUser = await prisma.user.findUnique({
        where: { id: userId },
        select: { displayName: true, email: true },
      });

      if (existingUser) {
        // Send notification to existing user
        await sendEmail({
          to: sharedWithEmail,
          subject: `${inviterUser?.displayName} shared a sphere with you`,
          html: `
            <h2>Sphere Shared</h2>
            <p>${inviterUser?.displayName} has shared the sphere "${sphere.name}" with you.</p>
            ${message ? `<p>Message: "${message}"</p>` : ''}
            <p>Permission level: ${permissionLevel}</p>
            <p><a href="${process.env.FRONTEND_URL}/spheres/${sphereId}">View Sphere</a></p>
          `,
        });

        // Create in-app notification
        notifySphereShared(existingUser.id, inviterUser?.displayName || 'Someone', sphere.name, sphereId).catch(() => {});
      } else {
        // Send invitation to external email
        const invitationUrl = `${process.env.FRONTEND_URL}/invitations/${invitationToken}`;
        await sendEmail({
          to: sharedWithEmail,
          subject: `${inviterUser?.displayName} invited you to Flashit`,
          html: `
            <h2>You're invited to Flashit</h2>
            <p>${inviterUser?.displayName} has shared the sphere "${sphere.name}" with you on Flashit.</p>
            ${message ? `<p>Message: "${message}"</p>` : ''}
            <p>Permission level: ${permissionLevel}</p>
            <p><a href="${invitationUrl}">Accept Invitation</a></p>
            <p>This invitation expires in 7 days.</p>
          `,
        });
      }

      // Create collaboration event
      await prisma.collaboration.collaborationEvent.create({
        data: {
          eventType: 'sphere_shared',
          userId,
          sphereId,
          targetUserId: existingUser?.id,
          eventData: {
            sharedWithEmail,
            permissionLevel,
            message,
          },
        },
      });

      return {
        shareId: sphereShare.id,
        invitationToken,
        message: existingUser ? 'Sphere shared successfully' : 'Invitation sent successfully',
      };

    } catch (error) {
      fastify.log.error('Failed to share sphere:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to share sphere',
      });
    }
  });

  /**
   * Accept sphere invitation
   * POST /api/collaboration/invitations/accept
   */
  fastify.post('/invitations/accept', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(acceptInvitationSchema),
      response: {
        200: zodToJsonSchema(z.object({
          sphereId: z.string(),
          sphereName: z.string(),
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { invitationToken } = request.body;
    const userId = request.user.userId;

    try {
      // Find invitation
      const invitation = await prisma.collaboration.sphereShare.findFirst({
        where: {
          invitationToken,
          revokedAt: null,
          invitationExpiresAt: { gt: new Date() },
          acceptedAt: null,
        },
        include: {
          sphere: { select: { name: true } },
        },
      });

      if (!invitation) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Invalid or expired invitation',
        });
      }

      // Update invitation with user acceptance
      await prisma.collaboration.sphereShare.update({
        where: { id: invitation.id },
        data: {
          sharedWithUserId: userId,
          sharedWithEmail: null,
          acceptedAt: new Date(),
          invitationToken: null,
          invitationExpiresAt: null,
        },
      });

      // Create sphere access record
      await prisma.sphereAccess.create({
        data: {
          sphereId: invitation.sphereId,
          userId,
          role: invitation.permissionLevel.toUpperCase() as any,
          grantedByUserId: invitation.sharedByUserId,
        },
      });

      // Create collaboration event
      await prisma.collaboration.collaborationEvent.create({
        data: {
          eventType: 'sphere_joined',
          userId,
          sphereId: invitation.sphereId,
          eventData: {
            permissionLevel: invitation.permissionLevel,
          },
        },
      });

      return {
        sphereId: invitation.sphereId,
        sphereName: invitation.sphere.name,
        message: 'Invitation accepted successfully',
      };

    } catch (error) {
      fastify.log.error('Failed to accept invitation:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to accept invitation',
      });
    }
  });

  /**
   * Get sphere collaborators
   * GET /api/collaboration/spheres/:sphereId/collaborators
   */
  fastify.get('/spheres/:sphereId/collaborators', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        sphereId: z.string().uuid(),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          collaborators: z.array(z.object({
            userId: z.string(),
            email: z.string(),
            displayName: z.string(),
            permissionLevel: z.string(),
            joinedAt: z.string(),
            lastActivity: z.string().nullable(),
          })),
        })),
      },
    },
  }, async (request, reply) => {
    const { sphereId } = request.params;
    const userId = request.user.userId;

    try {
      // Check access to sphere
      const hasAccess = await prisma.sphereAccess.findFirst({
        where: {
          sphereId,
          userId,
          revokedAt: null,
        },
      });

      if (!hasAccess) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to this sphere',
        });
      }

      // Get collaborators using database function
      const collaborators = await prisma.$queryRaw`
        SELECT * FROM collaboration.get_sphere_collaborators(${sphereId}::uuid)
      ` as any[];

      return {
        collaborators: collaborators.map((collab: any) => ({
          userId: collab.user_id,
          email: collab.email,
          displayName: collab.display_name,
          permissionLevel: collab.permission_level,
          joinedAt: collab.joined_at.toISOString(),
          lastActivity: collab.last_activity?.toISOString() || null,
        })),
      };

    } catch (error) {
      fastify.log.error('Failed to get collaborators:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get collaborators',
      });
    }
  });

  /**
   * Create moment comment
   * POST /api/collaboration/comments
   */
  fastify.post('/comments', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(createCommentSchema),
      response: {
        200: zodToJsonSchema(z.object({
          comment: z.object({
            id: z.string(),
            content: z.string(),
            author: z.object({
              id: z.string(),
              displayName: z.string(),
            }),
            parentCommentId: z.string().nullable(),
            mentions: z.array(z.string()),
            createdAt: z.string(),
          }),
        })),
      },
    },
  }, async (request, reply) => {
    const { momentId, content, parentCommentId, mentions = [] } = request.body;
    const userId = request.user.userId;

    try {
      // Verify access to moment
      const moment = await prisma.moment.findFirst({
        where: {
          id: momentId,
          OR: [
            { userId },
            {
              sphere: {
                sphereAccess: {
                  some: {
                    userId,
                    revokedAt: null,
                  },
                },
              },
            },
          ],
        },
        select: { sphereId: true },
      });

      if (!moment) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to this moment',
        });
      }

      // Check comment permission
      const hasPermission = await prisma.$queryRaw`
        SELECT collaboration.check_collaboration_permission(
          ${userId}::uuid,
          ${moment.sphereId}::uuid,
          'commenter'
        ) as has_permission
      ` as any[];

      if (!hasPermission[0]?.has_permission) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'Insufficient permissions to comment',
        });
      }

      // Create comment
      const comment = await prisma.collaboration.momentComment.create({
        data: {
          momentId,
          userId,
          parentCommentId,
          content,
          mentions: JSON.stringify(mentions),
        },
        include: {
          user: {
            select: { displayName: true },
          },
        },
      });

      // Create collaboration event
      await prisma.collaboration.collaborationEvent.create({
        data: {
          eventType: 'comment_created',
          userId,
          sphereId: moment.sphereId,
          momentId,
          eventData: {
            commentId: comment.id,
            mentions,
          },
        },
      });

      // Notify moment owner about the comment (fire-and-forget)
      if (moment.userId !== userId) {
        notifyCommentAdded(moment.userId, comment.user.displayName || 'Someone', momentId).catch(() => {});
      }
      // If this is a reply, notify the parent comment author
      if (parentCommentId) {
        prisma.comment.findUnique({ where: { id: parentCommentId }, select: { userId: true } })
          .then((parentComment) => {
            if (parentComment && parentComment.userId !== userId) {
              notifyCommentReply(parentComment.userId, comment.user.displayName || 'Someone', momentId).catch(() => {});
            }
          })
          .catch(() => {});
      }

      return {
        comment: {
          id: comment.id,
          content: comment.content,
          author: {
            id: userId,
            displayName: comment.user.displayName,
          },
          parentCommentId,
          mentions,
          createdAt: comment.createdAt.toISOString(),
        },
      };

    } catch (error) {
      fastify.log.error('Failed to create comment:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to create comment',
      });
    }
  });

  /**
   * Get moment comments
   * GET /api/collaboration/moments/:momentId/comments
   */
  fastify.get('/moments/:momentId/comments', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        momentId: z.string().uuid(),
      })),
      querystring: zodToJsonSchema(z.object({
        limit: z.coerce.number().min(1).max(100).default(50),
        offset: z.coerce.number().min(0).default(0),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          comments: z.array(z.object({
            id: z.string(),
            content: z.string(),
            author: z.object({
              id: z.string(),
              displayName: z.string(),
            }),
            parentCommentId: z.string().nullable(),
            replies: z.array(z.any()).optional(),
            reactions: z.record(z.number()),
            mentions: z.array(z.string()),
            createdAt: z.string(),
            updatedAt: z.string(),
          })),
          totalCount: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const { momentId } = request.params;
    const { limit, offset } = request.query;
    const userId = request.user.userId;

    try {
      // Verify access to moment
      const moment = await prisma.moment.findFirst({
        where: {
          id: momentId,
          OR: [
            { userId },
            {
              sphere: {
                sphereAccess: {
                  some: {
                    userId,
                    revokedAt: null,
                  },
                },
              },
            },
          ],
        },
        select: { id: true },
      });

      if (!moment) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to this moment',
        });
      }

      // Get comments
      const comments = await prisma.collaboration.momentComment.findMany({
        where: {
          momentId,
          deletedAt: null,
        },
        include: {
          user: {
            select: { displayName: true },
          },
          replies: {
            include: {
              user: {
                select: { displayName: true },
              },
            },
            where: { deletedAt: null },
            orderBy: { createdAt: 'asc' },
          },
        },
        orderBy: { createdAt: 'desc' },
        skip: offset,
        take: limit,
      });

      const totalCount = await prisma.collaboration.momentComment.count({
        where: {
          momentId,
          deletedAt: null,
        },
      });

      return {
        comments: comments.map(comment => ({
          id: comment.id,
          content: comment.content,
          author: {
            id: comment.userId,
            displayName: comment.user.displayName,
          },
          parentCommentId: comment.parentCommentId,
          replies: comment.replies?.map(reply => ({
            id: reply.id,
            content: reply.content,
            author: {
              id: reply.userId,
              displayName: reply.user.displayName,
            },
            createdAt: reply.createdAt.toISOString(),
          })),
          reactions: comment.reactions as Record<string, number> || {},
          mentions: JSON.parse(comment.mentions as string) || [],
          createdAt: comment.createdAt.toISOString(),
          updatedAt: comment.updatedAt.toISOString(),
        })),
        totalCount,
      };

    } catch (error) {
      fastify.log.error('Failed to get comments:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get comments',
      });
    }
  });

  /**
   * Add reaction to moment
   * POST /api/collaboration/reactions
   */
  fastify.post('/reactions', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(addReactionSchema),
      response: {
        200: zodToJsonSchema(z.object({
          message: z.string(),
          reactionAdded: z.boolean(),
        })),
      },
    },
  }, async (request, reply) => {
    const { momentId, reactionType } = request.body;
    const userId = request.user.userId;

    try {
      // Verify access to moment
      const moment = await prisma.moment.findFirst({
        where: {
          id: momentId,
          OR: [
            { userId },
            {
              sphere: {
                sphereAccess: {
                  some: {
                    userId,
                    revokedAt: null,
                  },
                },
              },
            },
          ],
        },
        select: { sphereId: true, userId: true },
      });

      if (!moment) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'You do not have access to this moment',
        });
      }

      // Check existing reaction
      const existingReaction = await prisma.collaboration.momentReaction.findFirst({
        where: { momentId, userId, reactionType },
      });

      if (existingReaction) {
        // Remove reaction
        await prisma.collaboration.momentReaction.delete({
          where: { id: existingReaction.id },
        });

        return {
          message: 'Reaction removed',
          reactionAdded: false,
        };
      } else {
        // Add reaction
        await prisma.collaboration.momentReaction.create({
          data: { momentId, userId, reactionType },
        });

        // Notify moment owner about the reaction (fire-and-forget)
        if (moment.userId !== userId) {
          const reactor = await prisma.user.findUnique({ where: { id: userId }, select: { displayName: true } });
          notifyReactionAdded(moment.userId, reactor?.displayName || 'Someone', momentId, reactionType).catch(() => {});
        }

        return {
          message: 'Reaction added',
          reactionAdded: true,
        };
      }

    } catch (error) {
      fastify.log.error('Failed to handle reaction:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to handle reaction',
      });
    }
  });

  /**
   * Follow/unfollow user
   * POST /api/collaboration/follow
   */
  fastify.post('/follow', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(followUserSchema),
      response: {
        200: zodToJsonSchema(z.object({
          message: z.string(),
          isFollowing: z.boolean(),
        })),
      },
    },
  }, async (request, reply) => {
    const { userIdToFollow } = request.body;
    const userId = request.user.userId;

    if (userId === userIdToFollow) {
      return reply.status(400).send({
        error: 'Bad Request',
        message: 'You cannot follow yourself',
      });
    }

    try {
      // Check if already following
      const existingFollow = await prisma.collaboration.userFollow.findFirst({
        where: {
          followerUserId: userId,
          followedUserId: userIdToFollow,
        },
      });

      if (existingFollow) {
        // Unfollow
        await prisma.collaboration.userFollow.delete({
          where: { id: existingFollow.id },
        });

        return {
          message: 'User unfollowed',
          isFollowing: false,
        };
      } else {
        // Follow
        await prisma.collaboration.userFollow.create({
          data: {
            followerUserId: userId,
            followedUserId: userIdToFollow,
          },
        });

        // Create collaboration event
        await prisma.collaboration.collaborationEvent.create({
          data: {
            eventType: 'user_followed',
            userId,
            sphereId: '00000000-0000-0000-0000-000000000000', // Dummy sphere ID for user events
            targetUserId: userIdToFollow,
            eventData: {},
          },
        });

        return {
          message: 'User followed',
          isFollowing: true,
        };
      }

    } catch (error) {
      fastify.log.error('Failed to handle follow:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to handle follow',
      });
    }
  });

  /**
   * Get activity feed
   * GET /api/collaboration/activity-feed
   */
  fastify.get('/activity-feed', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(z.object({
        limit: z.coerce.number().min(1).max(100).default(20),
        offset: z.coerce.number().min(0).default(0),
        unreadOnly: z.coerce.boolean().default(false),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          activities: z.array(z.object({
            id: z.string(),
            actorUserId: z.string(),
            actorDisplayName: z.string(),
            activityType: z.string(),
            sphereName: z.string().optional(),
            activityData: z.any(),
            readAt: z.string().nullable(),
            createdAt: z.string(),
          })),
          unreadCount: z.number(),
          totalCount: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const { limit, offset, unreadOnly } = request.query;
    const userId = request.user.userId;

    try {
      const whereClause: any = { userId };
      if (unreadOnly) {
        whereClause.readAt = null;
      }

      const activities = await prisma.collaboration.activityFeed.findMany({
        where: whereClause,
        include: {
          actorUser: {
            select: { displayName: true },
          },
          sphere: {
            select: { name: true },
          },
        },
        orderBy: { createdAt: 'desc' },
        skip: offset,
        take: limit,
      });

      const unreadCount = await prisma.collaboration.activityFeed.count({
        where: { userId, readAt: null },
      });

      const totalCount = await prisma.collaboration.activityFeed.count({
        where: { userId },
      });

      return {
        activities: activities.map(activity => ({
          id: activity.id,
          actorUserId: activity.actorUserId,
          actorDisplayName: activity.actorUser.displayName,
          activityType: activity.activityType,
          sphereName: activity.sphere?.name,
          activityData: activity.activityData,
          readAt: activity.readAt?.toISOString() || null,
          createdAt: activity.createdAt.toISOString(),
        })),
        unreadCount,
        totalCount,
      };

    } catch (error) {
      fastify.log.error('Failed to get activity feed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get activity feed',
      });
    }
  });

  /**
   * Update notification preferences
   * PUT /api/collaboration/notification-preferences
   */
  fastify.put('/notification-preferences', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(updateNotificationPreferencesSchema),
      response: {
        200: zodToJsonSchema(z.object({
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const preferences = request.body;

    try {
      await prisma.collaboration.notificationPreference.upsert({
        where: { userId },
        update: preferences,
        create: {
          userId,
          ...preferences,
        },
      });

      return {
        message: 'Notification preferences updated successfully',
      };

    } catch (error) {
      fastify.log.error('Failed to update notification preferences:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to update preferences',
      });
    }
  });

  /**
   * Get collaboration statistics
   * GET /api/collaboration/stats
   */
  fastify.get('/stats', {
    onRequest: [requireAuth],
    schema: {
      response: {
        200: zodToJsonSchema(z.object({
          sharedSpheres: z.number(),
          receivedShares: z.number(),
          followers: z.number(),
          following: z.number(),
          commentsCreated: z.number(),
          reactionsGiven: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const [
        sharedSpheres,
        receivedShares,
        followers,
        following,
        commentsCreated,
        reactionsGiven,
      ] = await Promise.all([
        prisma.collaboration.sphereShare.count({
          where: { sharedByUserId: userId, revokedAt: null },
        }),
        prisma.collaboration.sphereShare.count({
          where: { sharedWithUserId: userId, revokedAt: null },
        }),
        prisma.collaboration.userFollow.count({
          where: { followedUserId: userId },
        }),
        prisma.collaboration.userFollow.count({
          where: { followerUserId: userId },
        }),
        prisma.collaboration.momentComment.count({
          where: { userId, deletedAt: null },
        }),
        prisma.collaboration.momentReaction.count({
          where: { userId },
        }),
      ]);

      return {
        sharedSpheres,
        receivedShares,
        followers,
        following,
        commentsCreated,
        reactionsGiven,
      };

    } catch (error) {
      fastify.log.error('Failed to get collaboration stats:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get collaboration statistics',
      });
    }
  });
}
