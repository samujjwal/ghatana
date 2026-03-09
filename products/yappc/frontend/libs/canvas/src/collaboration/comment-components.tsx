import { Send as SendIcon, MoreVertical as MoreVertIcon, Reply as ReplyIcon, Pencil as EditIcon, Trash2 as DeleteIcon, EmojiEmotions as EmojiIcon, Paperclip as AttachFileIcon, X as CloseIcon, User as PersonIcon } from 'lucide-react';
import {
  Box,
  Button,
  Typography,
  Avatar,
  IconButton,
  Menu,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tooltip,
  Badge,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField, MenuItem } from '@ghatana/ui';
import React, { useState, useCallback, useRef, useEffect } from 'react';

import {
  CommentReaction,
  CreateCommentRequest,
  UpdateCommentRequest,
} from '../schemas/comment-schemas';

import type { 
  Comment,
  CommentThread,
  CommentMention} from '../schemas/comment-schemas';

// Comment input component
/**
 *
 */
export interface CommentInputProps {
  onSubmit: (content: string, mentions: CommentMention[]) => Promise<void>;
  placeholder?: string;
  autoFocus?: boolean;
  disabled?: boolean;
  maxLength?: number;
  onCancel?: () => void;
  initialValue?: string;
}

export const CommentInput: React.FC<CommentInputProps> = ({
  onSubmit,
  placeholder = 'Write a comment...',
  autoFocus = false,
  disabled = false,
  maxLength = 1000,
  onCancel,
  initialValue = '',
}) => {
  const [content, setContent] = useState(initialValue);
  const [mentions, setMentions] = useState<CommentMention[]>([]);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const handleSubmit = useCallback(async () => {
    if (!content.trim() || loading) return;
    
    setLoading(true);
    try {
      await onSubmit(content.trim(), mentions);
      setContent('');
      setMentions([]);
    } catch (error) {
      console.error('Failed to submit comment:', error);
    } finally {
      setLoading(false);
    }
  }, [content, mentions, onSubmit, loading]);

  const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      handleSubmit();
    }
    if (event.key === 'Escape' && onCancel) {
      onCancel();
    }
  }, [handleSubmit, onCancel]);

  // Simple mention detection (@ symbol)
  const handleContentChange = useCallback((event: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newContent = event.target.value;
    if (newContent.length <= maxLength) {
      setContent(newContent);
      
      // Extract mentions (simplified)
      const mentionRegex = /@(\w+)/g;
      const foundMentions: CommentMention[] = [];
      let match;
      
      while ((match = mentionRegex.exec(newContent)) !== null) {
        foundMentions.push({
          userId: match[1],
          username: match[1],
          displayName: match[1],
          position: {
            start: match.index,
            end: match.index + match[0].length,
          },
        });
      }
      
      setMentions(foundMentions);
    }
  }, [maxLength]);

  return (
    <Box className="p-4">
      <TextField
        inputRef={inputRef}
        multiline
        fullWidth
        minRows={2}
        maxRows={6}
        value={content}
        onChange={handleContentChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={disabled || loading}
        autoFocus={autoFocus}
        variant="outlined"
        size="small"
        helperText={`${content.length}/${maxLength} characters`}
        InputProps={{
          endAdornment: (
            <Box className="flex gap-2 items-end">
              <Tooltip title="Add emoji">
                <IconButton size="small" disabled={disabled || loading}>
                  <EmojiIcon size={16} />
                </IconButton>
              </Tooltip>
              <Tooltip title="Attach file">
                <IconButton size="small" disabled={disabled || loading}>
                  <AttachFileIcon size={16} />
                </IconButton>
              </Tooltip>
            </Box>
          ),
        }}
      />
      
      {mentions.length > 0 && (
        <Box className="mt-2 flex gap-2 flex-wrap">
          {mentions.map((mention, index) => (
            <Chip
              key={index}
              size="small"
              icon={<PersonIcon />}
              label={mention.displayName}
              variant="outlined"
            />
          ))}
        </Box>
      )}
      
      <Box className="mt-4 flex gap-2 justify-end">
        {onCancel && (
          <Button
            variant="outlined"
            size="small"
            onClick={onCancel}
            disabled={loading}
          >
            Cancel
          </Button>
        )}
        <Button
          variant="contained"
          size="small"
          onClick={handleSubmit}
          disabled={!content.trim() || loading}
          startIcon={<SendIcon />}
        >
          {loading ? 'Posting...' : 'Post'}
        </Button>
      </Box>
    </Box>
  );
};

// Individual comment display component
/**
 *
 */
export interface CommentItemProps {
  comment: Comment;
  currentUserId?: string;
  canEdit?: boolean;
  canDelete?: boolean;
  onEdit?: (commentId: string, content: string) => Promise<void>;
  onDelete?: (commentId: string) => Promise<void>;
  onReply?: (parentId: string) => void;
  onReact?: (commentId: string, reaction: string) => Promise<void>;
}

export const CommentItem: React.FC<CommentItemProps> = ({
  comment,
  currentUserId,
  canEdit = false,
  canDelete = false,
  onEdit,
  onDelete,
  onReply,
  onReact,
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const isOwner = currentUserId === comment.authorId;
  const canEditComment = canEdit || isOwner;
  const canDeleteComment = canDelete || isOwner;

  const handleMenuOpen = useCallback((event: React.MouseEvent<HTMLElement>) => {
    setMenuAnchor(event.currentTarget);
  }, []);

  const handleMenuClose = useCallback(() => {
    setMenuAnchor(null);
  }, []);

  const handleEdit = useCallback(async (content: string) => {
    if (onEdit) {
      try {
        await onEdit(comment.id, content);
        setIsEditing(false);
      } catch (error) {
        console.error('Failed to edit comment:', error);
      }
    }
  }, [comment.id, onEdit]);

  const handleDelete = useCallback(async () => {
    if (onDelete) {
      try {
        await onDelete(comment.id);
        setDeleteDialogOpen(false);
      } catch (error) {
        console.error('Failed to delete comment:', error);
      }
    }
  }, [comment.id, onDelete]);

  const handleReact = useCallback(async (reaction: string) => {
    if (onReact) {
      try {
        await onReact(comment.id, reaction);
      } catch (error) {
        console.error('Failed to add reaction:', error);
      }
    }
  }, [comment.id, onReact]);

  const formatTimestamp = useCallback((timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffInHours = (now.getTime() - date.getTime()) / (1000 * 60 * 60);
    
    if (diffInHours < 24) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString();
  }, []);

  return (
    <Paper variant="outlined" className="p-4 mb-2">
      <Box className="flex items-start gap-4">
        <Avatar className="w-[32px] h-[32px]">
          {comment.authorId.charAt(0).toUpperCase()}
        </Avatar>
        
        <Box className="flex-1">
          <Box className="flex items-center justify-between mb-2">
            <Box className="flex items-center gap-2">
              <Typography variant="subtitle2" fontWeight="medium">
                {comment.authorId}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {formatTimestamp(comment.createdAt)}
              </Typography>
              {comment.updatedAt !== comment.createdAt && (
                <Typography variant="caption" color="text.secondary">
                  (edited)
                </Typography>
              )}
            </Box>
            
            {(canEditComment || canDeleteComment) && (
              <IconButton size="small" onClick={handleMenuOpen}>
                <MoreVertIcon size={16} />
              </IconButton>
            )}
          </Box>
          
          {isEditing ? (
            <CommentInput
              initialValue={comment.content}
              onSubmit={async (content) => handleEdit(content)}
              onCancel={() => setIsEditing(false)}
              placeholder="Edit your comment..."
              autoFocus
            />
          ) : (
            <>
              <Typography variant="body2" className="mb-2">
                {comment.content}
              </Typography>
              
              {comment.attachments && comment.attachments.length > 0 && (
                <Box className="mb-2">
                  {comment.attachments.map((attachment, index) => (
                    <Chip
                      key={index}
                      size="small"
                      label={attachment.filename}
                      icon={<AttachFileIcon />}
                      variant="outlined"
                      className="mr-2"
                    />
                  ))}
                </Box>
              )}
              
              {comment.reactions && comment.reactions.length > 0 && (
                <Box className="mb-2 flex gap-2 flex-wrap">
                  {comment.reactions.map((reaction, index) => (
                    <Chip
                      key={index}
                      size="small"
                      label={reaction.emoji}
                      variant="outlined"
                      onClick={() => handleReact(reaction.emoji)}
                      className="cursor-pointer"
                    />
                  ))}
                </Box>
              )}
              
              <Box className="flex gap-2">
                {onReply && (
                  <Button
                    size="small"
                    startIcon={<ReplyIcon />}
                    onClick={() => onReply(comment.id)}
                  >
                    Reply
                  </Button>
                )}
                
                <Button
                  size="small"
                  startIcon={<EmojiIcon />}
                  onClick={() => handleReact('👍')}
                >
                  React
                </Button>
              </Box>
            </>
          )}
        </Box>
      </Box>
      
      {/* Action menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        {canEditComment && (
          <MenuItem
            onClick={() => {
              setIsEditing(true);
              handleMenuClose();
            }}
          >
            <EditIcon className="mr-2" size={16} />
            Edit
          </MenuItem>
        )}
        {canDeleteComment && (
          <MenuItem
            onClick={() => {
              setDeleteDialogOpen(true);
              handleMenuClose();
            }}
          >
            <DeleteIcon className="mr-2" size={16} />
            Delete
          </MenuItem>
        )}
      </Menu>
      
      {/* Delete confirmation dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Delete Comment</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete this comment? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleDelete} color="error" autoFocus>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
};

// Comment thread component
/**
 *
 */
export interface CommentThreadProps {
  thread: CommentThread;
  currentUserId?: string;
  canEdit?: boolean;
  canDelete?: boolean;
  onAddComment?: (content: string, parentId?: string) => Promise<void>;
  onEditComment?: (commentId: string, content: string) => Promise<void>;
  onDeleteComment?: (commentId: string) => Promise<void>;
  onReactToComment?: (commentId: string, reaction: string) => Promise<void>;
}

export const CommentThreadComponent: React.FC<CommentThreadProps> = ({
  thread,
  currentUserId,
  canEdit = false,
  canDelete = false,
  onAddComment,
  onEditComment,
  onDeleteComment,
  onReactToComment,
}) => {
  const [replyingTo, setReplyingTo] = useState<string | null>(null);
  const [showAllReplies, setShowAllReplies] = useState(false);

  const handleReply = useCallback(async (content: string, mentions: CommentMention[]) => {
    if (onAddComment && replyingTo) {
      try {
        await onAddComment(content, replyingTo);
        setReplyingTo(null);
      } catch (error) {
        console.error('Failed to add reply:', error);
      }
    }
  }, [onAddComment, replyingTo]);

  const rootComment = thread.rootComment;
  const replies = thread.replies;
  const displayReplies = showAllReplies ? replies : replies.slice(0, 3);

  return (
    <Box className="mb-6">
      {/* Root comment */}
      <CommentItem
        comment={rootComment}
        currentUserId={currentUserId}
        canEdit={canEdit}
        canDelete={canDelete}
        onEdit={onEditComment}
        onDelete={onDeleteComment}
        onReply={(parentId) => setReplyingTo(parentId)}
        onReact={onReactToComment}
      />
      
      {/* Replies */}
      {replies.length > 0 && (
        <Box className="ml-12 mt-2">
          {displayReplies.map((reply: Comment) => (
            <CommentItem
              key={reply.id}
              comment={reply}
              currentUserId={currentUserId}
              canEdit={canEdit}
              canDelete={canDelete}
              onEdit={onEditComment}
              onDelete={onDeleteComment}
              onReply={(parentId) => setReplyingTo(parentId)}
              onReact={onReactToComment}
            />
          ))}
          
          {replies.length > 3 && !showAllReplies && (
            <Button
              size="small"
              onClick={() => setShowAllReplies(true)}
              className="mt-2"
            >
              Show {replies.length - 3} more replies
            </Button>
          )}
          
          {showAllReplies && replies.length > 3 && (
            <Button
              size="small"
              onClick={() => setShowAllReplies(false)}
              className="mt-2"
            >
              Show fewer replies
            </Button>
          )}
        </Box>
      )}
      
      {/* Reply input */}
      {replyingTo && (
        <Box className="ml-12 mt-2">
          <CommentInput
            onSubmit={handleReply}
            onCancel={() => setReplyingTo(null)}
            placeholder="Write a reply..."
            autoFocus
          />
        </Box>
      )}
    </Box>
  );
};