/**
 * Node Comment Thread Component
 *
 * @description Displays and manages a thread of comments attached to a
 * canvas node. Supports replies, resolution, mentions, and real-time updates.
 *
 * @doc.type component
 * @doc.purpose Canvas node comments
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  MessageSquare,
  Reply,
  MoreHorizontal,
  Check,
  CheckCheck,
  Trash2,
  Edit3,
  Pin,
  Clock,
  Send,
  X,
  AtSign,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Textarea } from '@ghatana/yappc-ui';
import { Avatar } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from '@ghatana/yappc-ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

// =============================================================================
// Types
// =============================================================================

export interface CommentAuthor {
  id: string;
  name: string;
  avatar?: string;
  role?: string;
}

export interface CommentReaction {
  emoji: string;
  count: number;
  users: string[];
  reacted: boolean;
}

export interface Comment {
  id: string;
  nodeId: string;
  content: string;
  author: CommentAuthor;
  createdAt: string;
  updatedAt?: string;
  isEdited?: boolean;
  resolved?: boolean;
  resolvedBy?: CommentAuthor;
  resolvedAt?: string;
  pinned?: boolean;
  parentId?: string;
  replies?: Comment[];
  reactions?: CommentReaction[];
  mentions?: string[];
  attachments?: Array<{
    id: string;
    name: string;
    url: string;
    type: string;
  }>;
}

export interface NodeCommentThreadProps {
  /** Node ID the comments are attached to */
  nodeId: string;
  /** Node label/title for display */
  nodeLabel?: string;
  /** Comments in the thread */
  comments: Comment[];
  /** Current user ID */
  currentUserId: string;
  /** Called when a new comment is added */
  onAddComment: (content: string, parentId?: string) => Promise<void>;
  /** Called when a comment is edited */
  onEditComment: (commentId: string, content: string) => Promise<void>;
  /** Called when a comment is deleted */
  onDeleteComment: (commentId: string) => Promise<void>;
  /** Called when a comment is resolved/unresolve */
  onResolveComment: (commentId: string, resolved: boolean) => Promise<void>;
  /** Called when a comment is pinned/unpinned */
  onPinComment?: (commentId: string, pinned: boolean) => Promise<void>;
  /** Called when a reaction is added */
  onReaction?: (commentId: string, emoji: string) => Promise<void>;
  /** List of mentionable users */
  mentionableUsers?: CommentAuthor[];
  /** Allow attachments */
  allowAttachments?: boolean;
  /** Show resolved comments */
  showResolved?: boolean;
  /** Collapsed by default */
  defaultCollapsed?: boolean;
  /** Loading state */
  loading?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Animation Variants
// =============================================================================

const threadVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.05 },
  },
} as const;

const commentVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
  exit: { opacity: 0, x: -10 },
} as const;

// =============================================================================
// Comment Input
// =============================================================================

interface CommentInputProps {
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  onCancel?: () => void;
  loading?: boolean;
  showCancel?: boolean;
  mentionableUsers?: CommentAuthor[];
  autoFocus?: boolean;
}

const CommentInput: React.FC<CommentInputProps> = ({
  placeholder = 'Add a comment...',
  value,
  onChange,
  onSubmit,
  onCancel,
  loading = false,
  showCancel = false,
  mentionableUsers = [],
  autoFocus = false,
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [showMentions, setShowMentions] = useState(false);
  const [mentionSearch, setMentionSearch] = useState('');

  useEffect(() => {
    if (autoFocus && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [autoFocus]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        onSubmit();
      }
      if (e.key === 'Escape' && onCancel) {
        onCancel();
      }
      if (e.key === '@') {
        setShowMentions(true);
        setMentionSearch('');
      }
    },
    [onSubmit, onCancel]
  );

  const filteredUsers = useMemo(() => {
    if (!mentionSearch) return mentionableUsers;
    return mentionableUsers.filter((user) =>
      user.name.toLowerCase().includes(mentionSearch.toLowerCase())
    );
  }, [mentionableUsers, mentionSearch]);

  const handleMention = useCallback(
    (user: CommentAuthor) => {
      const cursorPos = textareaRef.current?.selectionStart || value.length;
      const beforeCursor = value.substring(0, cursorPos);
      const afterCursor = value.substring(cursorPos);
      const lastAt = beforeCursor.lastIndexOf('@');
      const newValue = `${beforeCursor.substring(0, lastAt)}@${user.name} ${afterCursor}`;
      onChange(newValue);
      setShowMentions(false);
    },
    [value, onChange]
  );

  return (
    <div className="relative space-y-2">
      <div className="relative">
        <Textarea
          ref={textareaRef}
          value={value}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className="min-h-[80px] resize-none pr-10"
          disabled={loading}
        />
        <div className="absolute right-2 bottom-2 flex items-center gap-1">
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={() => setShowMentions(!showMentions)}
                className="p-1 text-neutral-400 hover:text-neutral-600 dark:hover:text-neutral-300"
              >
                <AtSign className="h-4 w-4" />
              </button>
            </TooltipTrigger>
            <TooltipContent>Mention someone</TooltipContent>
          </Tooltip>
        </div>
      </div>

      {/* Mention suggestions */}
      <AnimatePresence>
        {showMentions && filteredUsers.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="absolute bottom-full left-0 right-0 z-10 mb-1 max-h-[200px] overflow-auto rounded-lg border bg-white shadow-lg dark:border-neutral-700 dark:bg-neutral-800"
          >
            {filteredUsers.map((user) => (
              <button
                key={user.id}
                type="button"
                onClick={() => handleMention(user)}
                className="flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-neutral-100 dark:hover:bg-neutral-700"
              >
                <Avatar size="small" alt={user.name} src={user.avatar} />
                <span className="text-sm font-medium">{user.name}</span>
                {user.role && (
                  <span className="text-xs text-neutral-500">{user.role}</span>
                )}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Actions */}
      <div className="flex items-center justify-between">
        <span className="text-xs text-neutral-500">
          ⌘+Enter to submit
        </span>
        <div className="flex items-center gap-2">
          {showCancel && (
            <Button
              variant="ghost"
              size="sm"
              onClick={onCancel}
              disabled={loading}
            >
              Cancel
            </Button>
          )}
          <Button
            variant="solid"
            size="sm"
            onClick={onSubmit}
            disabled={!value.trim() || loading}
          >
            {loading ? (
              <motion.div
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
              >
                <Clock className="h-4 w-4" />
              </motion.div>
            ) : (
              <>
                <Send className="mr-1 h-4 w-4" />
                Send
              </>
            )}
          </Button>
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Single Comment
// =============================================================================

interface CommentItemProps {
  comment: Comment;
  currentUserId: string;
  isReply?: boolean;
  onReply: (commentId: string) => void;
  onEdit: (commentId: string, content: string) => Promise<void>;
  onDelete: (commentId: string) => Promise<void>;
  onResolve: (commentId: string, resolved: boolean) => Promise<void>;
  onPin?: (commentId: string, pinned: boolean) => Promise<void>;
  onReaction?: (commentId: string, emoji: string) => Promise<void>;
}

const CommentItem: React.FC<CommentItemProps> = ({
  comment,
  currentUserId,
  isReply = false,
  onReply,
  onEdit,
  onDelete,
  onResolve,
  onPin,
  // onReaction - available for future emoji reactions
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editContent, setEditContent] = useState(comment.content);

  const isAuthor = comment.author.id === currentUserId;
  const timeAgo = useMemo(() => {
    const date = new Date(comment.createdAt);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (minutes > 0) return `${minutes}m ago`;
    return 'Just now';
  }, [comment.createdAt]);

  const handleEdit = async () => {
    await onEdit(comment.id, editContent);
    setIsEditing(false);
  };

  const handleDelete = async () => {
    await onDelete(comment.id);
  };

  return (
    <motion.div
      variants={commentVariants}
      className={cn(
        'group relative rounded-lg p-3',
        comment.resolved
          ? 'bg-success-50/50 dark:bg-success-950/20'
          : 'bg-neutral-50 dark:bg-neutral-800/50',
        comment.pinned && 'ring-2 ring-amber-300 dark:ring-amber-600',
        isReply && 'ml-8 border-l-2 border-neutral-200 dark:border-neutral-700'
      )}
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <Avatar size="small" alt={comment.author.name} src={comment.author.avatar} />
          <div>
            <span className="text-sm font-medium text-neutral-900 dark:text-neutral-100">
              {comment.author.name}
            </span>
            {comment.author.role && (
              <Badge variant="outline" className="ml-2 text-xs">
                {comment.author.role}
              </Badge>
            )}
            <span className="ml-2 text-xs text-neutral-500">{timeAgo}</span>
            {comment.isEdited && (
              <span className="ml-1 text-xs text-neutral-400">(edited)</span>
            )}
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          {comment.pinned && (
            <Pin className="h-4 w-4 text-amber-500" />
          )}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="p-1 rounded hover:bg-neutral-200 dark:hover:bg-neutral-700">
                <MoreHorizontal className="h-4 w-4 text-neutral-500" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {!isReply && (
                <>
                  <DropdownMenuItem onClick={() => onResolve(comment.id, !comment.resolved)}>
                    {comment.resolved ? (
                      <>
                        <X className="mr-2 h-4 w-4" />
                        Unresolve
                      </>
                    ) : (
                      <>
                        <Check className="mr-2 h-4 w-4" />
                        Resolve
                      </>
                    )}
                  </DropdownMenuItem>
                  {onPin && (
                    <DropdownMenuItem onClick={() => onPin(comment.id, !comment.pinned)}>
                      <Pin className="mr-2 h-4 w-4" />
                      {comment.pinned ? 'Unpin' : 'Pin'}
                    </DropdownMenuItem>
                  )}
                  <DropdownMenuSeparator />
                </>
              )}
              {isAuthor && (
                <>
                  <DropdownMenuItem onClick={() => setIsEditing(true)}>
                    <Edit3 className="mr-2 h-4 w-4" />
                    Edit
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={handleDelete}
                    className="text-error-600 dark:text-error-400"
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    Delete
                  </DropdownMenuItem>
                </>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Content */}
      {isEditing ? (
        <div className="mt-2">
          <Textarea
            value={editContent}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setEditContent(e.target.value)}
            className="min-h-[60px]"
          />
          <div className="mt-2 flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setIsEditing(false)}>
              Cancel
            </Button>
            <Button variant="solid" size="sm" onClick={handleEdit}>
              Save
            </Button>
          </div>
        </div>
      ) : (
        <p className="mt-2 text-sm text-neutral-700 dark:text-neutral-300 whitespace-pre-wrap">
          {comment.content}
        </p>
      )}

      {/* Resolved indicator */}
      {comment.resolved && comment.resolvedBy && (
        <div className="mt-2 flex items-center gap-1 text-xs text-success-600 dark:text-success-400">
          <CheckCheck className="h-3 w-3" />
          <span>Resolved by {comment.resolvedBy.name}</span>
        </div>
      )}

      {/* Reply button */}
      {!isReply && (
        <button
          onClick={() => onReply(comment.id)}
          className="mt-2 flex items-center gap-1 text-xs text-neutral-500 hover:text-primary-600 dark:hover:text-primary-400"
        >
          <Reply className="h-3 w-3" />
          Reply
        </button>
      )}
    </motion.div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const NodeCommentThread: React.FC<NodeCommentThreadProps> = ({
  // nodeId - used for comment association (passed to callbacks)
  nodeLabel,
  comments,
  currentUserId,
  onAddComment,
  onEditComment,
  onDeleteComment,
  onResolveComment,
  onPinComment,
  onReaction,
  mentionableUsers = [],
  // allowAttachments - reserved for future attachment support
  showResolved = true,
  defaultCollapsed = false,
  // loading - reserved for loading states
  className,
}) => {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  const [newComment, setNewComment] = useState('');
  const [replyingTo, setReplyingTo] = useState<string | null>(null);
  const [replyContent, setReplyContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Filter comments
  const filteredComments = useMemo(() => {
    if (showResolved) return comments;
    return comments.filter((c) => !c.resolved);
  }, [comments, showResolved]);

  // Sort: pinned first, then by date
  const sortedComments = useMemo(() => {
    return [...filteredComments].sort((a, b) => {
      if (a.pinned && !b.pinned) return -1;
      if (!a.pinned && b.pinned) return 1;
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    });
  }, [filteredComments]);

  const handleAddComment = useCallback(async () => {
    if (!newComment.trim() || isSubmitting) return;
    setIsSubmitting(true);
    try {
      await onAddComment(newComment);
      setNewComment('');
    } finally {
      setIsSubmitting(false);
    }
  }, [newComment, isSubmitting, onAddComment]);

  const handleAddReply = useCallback(async () => {
    if (!replyContent.trim() || !replyingTo || isSubmitting) return;
    setIsSubmitting(true);
    try {
      await onAddComment(replyContent, replyingTo);
      setReplyContent('');
      setReplyingTo(null);
    } finally {
      setIsSubmitting(false);
    }
  }, [replyContent, replyingTo, isSubmitting, onAddComment]);

  const unresolvedCount = comments.filter((c) => !c.resolved).length;
  const totalCount = comments.length;

  return (
    <div className={cn('rounded-lg border bg-white dark:border-neutral-700 dark:bg-neutral-900', className)}>
      {/* Header */}
      <button
        type="button"
        onClick={() => setCollapsed(!collapsed)}
        className="flex w-full items-center justify-between p-4 text-left hover:bg-neutral-50 dark:hover:bg-neutral-800/50"
      >
        <div className="flex items-center gap-2">
          <MessageSquare className="h-5 w-5 text-neutral-500" />
          <span className="font-medium text-neutral-900 dark:text-neutral-100">
            Comments
          </span>
          {nodeLabel && (
            <span className="text-sm text-neutral-500">on {nodeLabel}</span>
          )}
          <Badge variant="outline" className="ml-2">
            {unresolvedCount}/{totalCount}
          </Badge>
        </div>
        <motion.div
          animate={{ rotate: collapsed ? 0 : 180 }}
          transition={{ duration: 0.2 }}
        >
          <X className="h-4 w-4 text-neutral-500" />
        </motion.div>
      </button>

      {/* Content */}
      <AnimatePresence>
        {!collapsed && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <div className="border-t p-4 dark:border-neutral-700">
              {/* New comment input */}
              <CommentInput
                placeholder="Add a comment..."
                value={newComment}
                onChange={setNewComment}
                onSubmit={handleAddComment}
                loading={isSubmitting}
                mentionableUsers={mentionableUsers}
              />

              {/* Comments list */}
              <motion.div
                variants={threadVariants}
                initial="hidden"
                animate="visible"
                className="mt-4 space-y-3"
              >
                {sortedComments.map((comment) => (
                  <div key={comment.id}>
                    <CommentItem
                      comment={comment}
                      currentUserId={currentUserId}
                      onReply={setReplyingTo}
                      onEdit={onEditComment}
                      onDelete={onDeleteComment}
                      onResolve={onResolveComment}
                      onPin={onPinComment}
                      onReaction={onReaction}
                    />

                    {/* Reply input */}
                    <AnimatePresence>
                      {replyingTo === comment.id && (
                        <motion.div
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: 'auto', opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          className="ml-8 mt-2"
                        >
                          <CommentInput
                            placeholder={`Reply to ${comment.author.name}...`}
                            value={replyContent}
                            onChange={setReplyContent}
                            onSubmit={handleAddReply}
                            onCancel={() => setReplyingTo(null)}
                            loading={isSubmitting}
                            showCancel
                            mentionableUsers={mentionableUsers}
                            autoFocus
                          />
                        </motion.div>
                      )}
                    </AnimatePresence>

                    {/* Replies */}
                    {comment.replies && comment.replies.length > 0 && (
                      <div className="mt-2 space-y-2">
                        {comment.replies.map((reply) => (
                          <CommentItem
                            key={reply.id}
                            comment={reply}
                            currentUserId={currentUserId}
                            isReply
                            onReply={() => setReplyingTo(comment.id)}
                            onEdit={onEditComment}
                            onDelete={onDeleteComment}
                            onResolve={onResolveComment}
                          />
                        ))}
                      </div>
                    )}
                  </div>
                ))}

                {sortedComments.length === 0 && (
                  <div className="py-8 text-center text-neutral-500">
                    <MessageSquare className="mx-auto h-8 w-8 opacity-50" />
                    <p className="mt-2 text-sm">No comments yet</p>
                    <p className="text-xs">Be the first to add a comment</p>
                  </div>
                )}
              </motion.div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default NodeCommentThread;
