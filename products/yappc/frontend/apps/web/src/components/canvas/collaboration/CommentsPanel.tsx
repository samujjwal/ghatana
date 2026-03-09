import { MessageSquare as CommentIcon, Send as SendIcon, Check as ResolveIcon, Reply as ReplyIcon } from 'lucide-react';
import {
  Box,
  Typography,
  ListItem,
  Button,
  Avatar,
  Chip,
  IconButton,
  Divider,
  Surface as Paper,
  InteractiveList as List,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import React, { useState, useEffect, useCallback } from 'react';

import { mockCollaborationProvider } from '../../../services/collaboration/MockCollaborationProvider';

import type { Comment, User } from '../../../services/collaboration/types';

/**
 *
 */
interface CommentsPanelProps {
  selectedElementId?: string;
  currentUser: User;
}

export const CommentsPanel: React.FC<CommentsPanelProps> = ({
  selectedElementId,
  currentUser,
}) => {
  const [comments, setComments] = useState<Comment[]>([]);
  const [newComment, setNewComment] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const buildCommentList = useCallback(
    (commentsMap: Map<string, Comment>) => {
      return Array.from(commentsMap.values())
        .filter((comment) => !selectedElementId || comment.elementId === selectedElementId)
        .sort(
          (a, b) =>
            new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
        );
    },
    [selectedElementId],
  );

  useEffect(() => {
    if (!mockCollaborationProvider.isConnected()) {
      mockCollaborationProvider
        .connect('canvas-collaboration', currentUser)
        .catch((error) =>
          console.warn('Failed to init collaboration provider', error),
        );
    }

    // Subscribe to comments changes
    const unsubscribe = mockCollaborationProvider.onCommentsChange((commentsMap) => {
      setComments(buildCommentList(commentsMap));
    });

    // Load initial comments
    setComments(buildCommentList(mockCollaborationProvider.getComments()));

    return unsubscribe;
  }, [buildCommentList, currentUser, selectedElementId]);

  const handleAddComment = async () => {
    if (!newComment.trim()) return;

    setIsLoading(true);
    try {
      const comment = await mockCollaborationProvider.addComment({
        elementId: selectedElementId,
        author: currentUser,
        content: newComment.trim(),
        mentions: [], // NOTE: Parse @mentions
        resolved: false,
      });

      setComments((prev) => {
        const next = [...prev, comment];
        return next
          .filter((item) => !selectedElementId || item.elementId === selectedElementId)
          .sort(
            (a, b) =>
              new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
          );
      });
      setNewComment('');
    } catch (error) {
      console.error('Failed to add comment:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleResolveComment = async (commentId: string) => {
    try {
      await mockCollaborationProvider.resolveComment(commentId);
    } catch (error) {
      console.error('Failed to resolve comment:', error);
    }
  };

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    return `${days}d ago`;
  };

  return (
    <Paper
      className="h-full flex flex-col"
      data-testid="comments-panel"
    >
      {/* Header */}
      <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
        <Box className="flex items-center mb-2">
          <CommentIcon className="mr-2" />
          <Typography variant="h6">
            Comments
            {selectedElementId && ' (Element)'}
          </Typography>
        </Box>
        {comments.length > 0 && (
          <Typography variant="caption" color="text.secondary">
            {comments.filter(c => !c.resolved).length} active, {comments.filter(c => c.resolved).length} resolved
          </Typography>
        )}
      </Box>

      {/* Comments List */}
      <Box className="flex-1 overflow-auto p-2">
        {comments.length === 0 ? (
          <Box className="p-6 text-center text-gray-500 dark:text-gray-400">
            <CommentIcon className="mb-2 text-5xl opacity-[0.3]" />
            <Typography variant="body2">
              {selectedElementId 
                ? 'No comments on this element yet'
                : 'No comments yet'
              }
            </Typography>
            <Typography variant="caption">
              Add the first comment below
            </Typography>
          </Box>
        ) : (
          <List className="py-0">
            {comments.map((comment) => (
              <ListItem
                key={comment.id}
                className="flex-col items-start mb-4 p-0"
              >
                <Paper
                  variant="outlined"
                  className={`w-full p-4 ${comment.resolved ? 'bg-gray-50' : 'bg-white dark:bg-gray-900'}`} style={{ opacity: comment.resolved ? 0.6 : 1 }}
                >
                  {/* Comment Header */}
                  <Box className="flex items-center mb-2">
                    <Avatar
                      className="mr-2 text-xs w-[24px] h-[24px]" >
                      {comment.author.name.charAt(0)}
                    </Avatar>
                    <Typography variant="body2" className="font-medium mr-2">
                      {comment.author.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {formatTime(comment.createdAt)}
                    </Typography>
                    {comment.resolved && (
                      <Chip
                        label="Resolved"
                        size="small"
                        color="success"
                        className="ml-2 h-[20px]"
                      />
                    )}
                  </Box>

                  {/* Comment Content */}
                  <Typography variant="body2" className="mb-2 whitespace-pre-wrap">
                    {comment.content}
                  </Typography>

                  {/* Comment Actions */}
                  <Box className="flex gap-2">
                    {!comment.resolved && (
                      <IconButton
                        size="small"
                        onClick={() => handleResolveComment(comment.id)}
                        title="Resolve comment"
                      >
                        <ResolveIcon size={16} />
                      </IconButton>
                    )}
                    <IconButton size="small" title="Reply" disabled>
                      <ReplyIcon size={16} />
                    </IconButton>
                  </Box>
                </Paper>
              </ListItem>
            ))}
          </List>
        )}
      </Box>

      <Divider />

      {/* Add Comment */}
      <Box className="p-4">
        <Box className="flex gap-2">
          <TextField
            fullWidth
            multiline
            rows={2}
            placeholder={selectedElementId 
              ? 'Comment on this element...' 
              : 'Add a comment...'
            }
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            onKeyPress={(e) => {
              if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                handleAddComment();
              }
            }}
            size="small"
            inputProps={{ 'data-testid': 'comment-input' }}
          />
          <Button
            variant="contained"
            onClick={handleAddComment}
            disabled={!newComment.trim() || isLoading}
            className="px-4 min-w-0" data-testid="add-comment"
          >
            <SendIcon size={16} />
          </Button>
        </Box>
        <Typography variant="caption" color="text.secondary" className="mt-1 block">
          Press Ctrl+Enter to send
        </Typography>
      </Box>
    </Paper>
  );
};
