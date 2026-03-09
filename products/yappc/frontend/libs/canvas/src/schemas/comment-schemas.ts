import { z } from 'zod';

// Comment System Schemas - Phase 6 Implementation  
export const CommentStatusSchema = z.enum(['active', 'resolved', 'deleted']);

export const CommentMentionSchema = z.object({
  userId: z.string(),
  username: z.string(),
  displayName: z.string(),
  position: z.object({
    start: z.number(),
    end: z.number(),
  }),
});

export const CommentReactionSchema = z.object({
  emoji: z.string(),
  userId: z.string(),
  timestamp: z.string().datetime(),
});

export const CommentAttachmentSchema = z.object({
  id: z.string(),
  type: z.enum(['image', 'file', 'link']),
  url: z.string(),
  filename: z.string().optional(),
  size: z.number().optional(),
  mimeType: z.string().optional(),
});

export const BaseCommentSchema = z.object({
  id: z.string(),
  canvasId: z.string(),
  authorId: z.string(),
  authorName: z.string(),
  authorAvatar: z.string().optional(),
  content: z.string().min(1),
  mentions: z.array(CommentMentionSchema).default([]),
  reactions: z.array(CommentReactionSchema).default([]),
  attachments: z.array(CommentAttachmentSchema).default([]),
  status: CommentStatusSchema.default('active'),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  editedAt: z.string().datetime().optional(),
  resolvedAt: z.string().datetime().optional(),
  resolvedBy: z.string().optional(),
});

// Thread comment (reply to another comment)
export const ThreadCommentSchema = BaseCommentSchema.extend({
  parentId: z.string(),
  threadDepth: z.number().min(1).max(10),
});

// Positioned comment (attached to canvas position)
export const PositionedCommentSchema = BaseCommentSchema.extend({
  position: z.object({
    x: z.number(),
    y: z.number(),
  }),
  parentId: z.null().optional(),
});

// Node/Edge comment (attached to specific element)
export const ElementCommentSchema = BaseCommentSchema.extend({
  elementType: z.enum(['node', 'edge']),
  elementId: z.string(),
  parentId: z.null().optional(),
});

// Union of all comment types
export const CommentSchema = z.discriminatedUnion('parentId', [
  ThreadCommentSchema.extend({ parentId: z.string() }),
  PositionedCommentSchema.extend({ parentId: z.null().optional() }),
  ElementCommentSchema.extend({ parentId: z.null().optional() }),
]);

// Comment thread schema
export const CommentThreadSchema = z.object({
  id: z.string(),
  rootComment: CommentSchema,
  replies: z.array(ThreadCommentSchema).default([]),
  totalReplies: z.number().default(0),
  lastActivity: z.string().datetime(),
  participants: z.array(z.string()).default([]),
  isResolved: z.boolean().default(false),
});

// Comment creation schemas
export const CreateCommentRequestSchema = z.object({
  canvasId: z.string(),
  content: z.string().min(1),
  mentions: z.array(CommentMentionSchema).default([]),
  attachments: z.array(CommentAttachmentSchema).default([]),
  // Position-based comment
  position: z.object({
    x: z.number(),
    y: z.number(),
  }).optional(),
  // Element-based comment
  elementType: z.enum(['node', 'edge']).optional(),
  elementId: z.string().optional(),
  // Reply comment
  parentId: z.string().optional(),
});

// Comment update schemas
export const UpdateCommentRequestSchema = z.object({
  content: z.string().min(1).optional(),
  mentions: z.array(CommentMentionSchema).optional(),
  status: CommentStatusSchema.optional(),
});

// Comment notification schemas
export const CommentNotificationSchema = z.object({
  id: z.string(),
  type: z.enum(['mention', 'reply', 'reaction', 'resolution']),
  commentId: z.string(),
  canvasId: z.string(),
  triggeredBy: z.string(),
  triggeredByName: z.string(),
  recipientId: z.string(),
  message: z.string(),
  isRead: z.boolean().default(false),
  createdAt: z.string().datetime(),
});

// Type inference
/**
 *
 */
export type CommentStatus = z.infer<typeof CommentStatusSchema>;
/**
 *
 */
export type CommentMention = z.infer<typeof CommentMentionSchema>;
/**
 *
 */
export type CommentReaction = z.infer<typeof CommentReactionSchema>;
/**
 *
 */
export type CommentAttachment = z.infer<typeof CommentAttachmentSchema>;
/**
 *
 */
export type BaseComment = z.infer<typeof BaseCommentSchema>;
/**
 *
 */
export type ThreadComment = z.infer<typeof ThreadCommentSchema>;
/**
 *
 */
export type PositionedComment = z.infer<typeof PositionedCommentSchema>;
/**
 *
 */
export type ElementComment = z.infer<typeof ElementCommentSchema>;
/**
 *
 */
export type Comment = z.infer<typeof CommentSchema>;
/**
 *
 */
export type CommentThread = z.infer<typeof CommentThreadSchema>;
/**
 *
 */
export type CreateCommentRequest = z.infer<typeof CreateCommentRequestSchema>;
/**
 *
 */
export type UpdateCommentRequest = z.infer<typeof UpdateCommentRequestSchema>;
/**
 *
 */
export type CommentNotification = z.infer<typeof CommentNotificationSchema>;

// Comment helpers
export const isThreadComment = (comment: Comment): comment is ThreadComment => {
  return comment.parentId !== null && comment.parentId !== undefined;
};

export const isPositionedComment = (comment: Comment): comment is PositionedComment => {
  return 'position' in comment && comment.position !== undefined;
};

export const isElementComment = (comment: Comment): comment is ElementComment => {
  return 'elementType' in comment && 'elementId' in comment;
};

export const extractMentions = (content: string): CommentMention[] => {
  const mentionRegex = /@(\w+)/g;
  const mentions: CommentMention[] = [];
  let match;

  while ((match = mentionRegex.exec(content)) !== null) {
    mentions.push({
      userId: match[1], // In real implementation, resolve username to userId
      username: match[1],
      displayName: match[1],
      position: {
        start: match.index,
        end: match.index + match[0].length,
      },
    });
  }

  return mentions;
};

export const formatCommentContent = (content: string, mentions: CommentMention[]): string => {
  let formattedContent = content;
  
  // Replace mentions with formatted text (from end to start to preserve positions)
  mentions
    .sort((a, b) => b.position.start - a.position.start)
    .forEach(mention => {
      const before = formattedContent.slice(0, mention.position.start);
      const after = formattedContent.slice(mention.position.end);
      formattedContent = `${before}<span class="mention" data-user-id="${mention.userId}">@${mention.displayName}</span>${after}`;
    });

  return formattedContent;
};