/**
 * Bootstrap Collaborate Page
 *
 * @description Team collaboration page for bootstrapping sessions.
 * Enables real-time collaboration with presence indicators, shared
 * canvas editing, and node-level comments.
 *
 * @doc.type page
 * @doc.purpose Real-time team collaboration during bootstrapping
 * @doc.layer page
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Users,
  UserPlus,
  Copy,
  Check,
  ArrowLeft,
  MessageSquare,
  Eye,
  Edit3,
  Crown,
  Send,
  X,
  Search,
  MoreVertical,
  Mail,
} from 'lucide-react';

import { cn } from '../../utils/cn';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Avatar } from '@ghatana/ui';
import { Dialog } from '@ghatana/ui';
import { Menu } from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { Tooltip } from '@ghatana/ui';

import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

interface Collaborator {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  role: 'owner' | 'editor' | 'viewer';
  status: 'online' | 'away' | 'offline';
  cursor?: { x: number; y: number; nodeId?: string };
  joinedAt: string;
}

interface Comment {
  id: string;
  nodeId: string;
  userId: string;
  userName: string;
  userAvatar?: string;
  content: string;
  createdAt: string;
  resolved: boolean;
  replies: CommentReply[];
}

interface CommentReply {
  id: string;
  userId: string;
  userName: string;
  userAvatar?: string;
  content: string;
  createdAt: string;
}

interface PendingInvite {
  id: string;
  email: string;
  role: 'editor' | 'viewer';
  sentAt: string;
  status: 'pending' | 'accepted' | 'expired';
}

// =============================================================================
// Animation Variants
// =============================================================================

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.1 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

// =============================================================================
// Collaborator Card Component
// =============================================================================

interface CollaboratorCardProps {
  collaborator: Collaborator;
  isOwner: boolean;
  onChangeRole: (id: string, role: 'editor' | 'viewer') => void;
  onRemove: (id: string) => void;
}

const CollaboratorCard: React.FC<CollaboratorCardProps> = ({
  collaborator,
  isOwner,
  onChangeRole,
  onRemove,
}) => {
  const [menuOpen, setMenuOpen] = useState(false);

  const statusColors: Record<string, string> = {
    online: 'bg-green-500',
    away: 'bg-yellow-500',
    offline: 'bg-zinc-500',
  };

  const roleLabels: Record<string, string> = {
    owner: 'Owner',
    editor: 'Can edit',
    viewer: 'Can view',
  };

  return (
    <motion.div
      variants={itemVariants}
      className="flex items-center gap-3 rounded-lg border border-zinc-800 bg-zinc-900/50 p-3"
    >
      <div className="relative">
        <Avatar
          src={collaborator.avatar}
          alt={collaborator.name}
          size="medium"
        />
        <span
          className={cn(
            'absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-zinc-900',
            statusColors[collaborator.status]
          )}
        />
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="font-medium text-zinc-200 truncate">{collaborator.name}</p>
          {collaborator.role === 'owner' && (
            <Crown className="h-4 w-4 text-yellow-500 flex-shrink-0" />
          )}
        </div>
        <p className="text-sm text-zinc-500 truncate">{collaborator.email}</p>
      </div>

      <div className="flex items-center gap-2">
        <span className="rounded-full bg-zinc-800 px-2 py-0.5 text-xs text-zinc-400">
          {roleLabels[collaborator.role]}
        </span>

        {isOwner && collaborator.role !== 'owner' && (
          <Menu
            trigger={
              <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
                <MoreVertical className="h-4 w-4" />
              </Button>
            }
            open={menuOpen}
            onOpenChange={setMenuOpen}
          >
            <MenuItem
              text={collaborator.role === 'editor' ? 'Change to Viewer' : 'Change to Editor'}
              icon={collaborator.role === 'editor' ? <Eye /> : <Edit3 />}
              onClick={() => {
                onChangeRole(collaborator.id, collaborator.role === 'editor' ? 'viewer' : 'editor');
                setMenuOpen(false);
              }}
            />
            <MenuItem
              text="Remove"
              icon={<X />}
              onClick={() => {
                onRemove(collaborator.id);
                setMenuOpen(false);
              }}
            />
          </Menu>
        )}
      </div>
    </motion.div>
  );
};

// =============================================================================
// Pending Invite Card Component
// =============================================================================

interface PendingInviteCardProps {
  invite: PendingInvite;
  onResend: (id: string) => void;
  onCancel: (id: string) => void;
}

const PendingInviteCard: React.FC<PendingInviteCardProps> = ({
  invite,
  onResend,
  onCancel,
}) => {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <motion.div
      variants={itemVariants}
      className="flex items-center gap-3 rounded-lg border border-zinc-800/50 bg-zinc-900/30 p-3"
    >
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-zinc-800">
        <Mail className="h-5 w-5 text-zinc-400" />
      </div>

      <div className="flex-1 min-w-0">
        <p className="font-medium text-zinc-300 truncate">{invite.email}</p>
        <p className="text-sm text-zinc-500">
          Invited as {invite.role === 'editor' ? 'Editor' : 'Viewer'} •{' '}
          {new Date(invite.sentAt).toLocaleDateString()}
        </p>
      </div>

      <div className="flex items-center gap-2">
        <span className="rounded-full bg-yellow-500/10 px-2 py-0.5 text-xs text-yellow-400">
          Pending
        </span>
        <Menu
          trigger={
            <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
              <MoreVertical className="h-4 w-4" />
            </Button>
          }
          open={menuOpen}
          onOpenChange={setMenuOpen}
        >
          <MenuItem
            text="Resend Invite"
            icon={<Send />}
            onClick={() => {
              onResend(invite.id);
              setMenuOpen(false);
            }}
          />
          <MenuItem
            text="Cancel Invite"
            icon={<X />}
            onClick={() => {
              onCancel(invite.id);
              setMenuOpen(false);
            }}
          />
        </Menu>
      </div>
    </motion.div>
  );
};

// =============================================================================
// Comment Thread Component
// =============================================================================

interface CommentThreadProps {
  comment: Comment;
  onReply: (commentId: string, content: string) => void;
  onResolve: (commentId: string) => void;
}

const CommentThread: React.FC<CommentThreadProps> = ({
  comment,
  onReply,
  onResolve,
}) => {
  const [replyContent, setReplyContent] = useState('');
  const [showReplyInput, setShowReplyInput] = useState(false);

  const handleSubmitReply = () => {
    if (replyContent.trim()) {
      onReply(comment.id, replyContent);
      setReplyContent('');
      setShowReplyInput(false);
    }
  };

  return (
    <motion.div
      variants={itemVariants}
      className={cn(
        'rounded-lg border p-3',
        comment.resolved
          ? 'border-zinc-800/50 bg-zinc-900/30'
          : 'border-zinc-800 bg-zinc-900/50'
      )}
    >
      <div className="flex items-start gap-3">
        <Avatar
          src={comment.userAvatar}
          alt={comment.userName}
          size="small"
        />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium text-zinc-200">{comment.userName}</span>
            <span className="text-xs text-zinc-500">
              {new Date(comment.createdAt).toLocaleString()}
            </span>
            {comment.resolved && (
              <span className="rounded-full bg-green-500/10 px-2 py-0.5 text-xs text-green-400">
                Resolved
              </span>
            )}
          </div>
          <p className="mt-1 text-sm text-zinc-300">{comment.content}</p>

          {/* Replies */}
          {comment.replies.length > 0 && (
            <div className="mt-3 space-y-2 border-l-2 border-zinc-700 pl-3">
              {comment.replies.map((reply) => (
                <div key={reply.id} className="flex items-start gap-2">
                  <Avatar
                    src={reply.userAvatar}
                    alt={reply.userName}
                    size="small"
                  />
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-zinc-300">
                        {reply.userName}
                      </span>
                      <span className="text-xs text-zinc-500">
                        {new Date(reply.createdAt).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-sm text-zinc-400">{reply.content}</p>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Actions */}
          {!comment.resolved && (
            <div className="mt-3 flex items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowReplyInput(!showReplyInput)}
              >
                <MessageSquare className="mr-1 h-3 w-3" />
                Reply
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onResolve(comment.id)}
              >
                <Check className="mr-1 h-3 w-3" />
                Resolve
              </Button>
            </div>
          )}

          {/* Reply input */}
          <AnimatePresence>
            {showReplyInput && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="mt-3 flex items-center gap-2"
              >
                <Input
                  value={replyContent}
                  onChange={(e) => setReplyContent(e.target.value)}
                  placeholder="Write a reply..."
                  className="flex-1"
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      handleSubmitReply();
                    }
                  }}
                />
                <Button size="sm" onClick={handleSubmitReply}>
                  <Send className="h-4 w-4" />
                </Button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </motion.div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

const BootstrapCollaboratePage: React.FC = () => {
  const navigate = useNavigate();
  const { projectId, sessionId } = useParams<{ projectId: string; sessionId: string }>();

  // Local UI state
  const [showInviteDialog, setShowInviteDialog] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<'editor' | 'viewer'>('editor');
  const [linkCopied, setLinkCopied] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState<'people' | 'comments'>('people');

  // Mock data (would come from API/WebSocket in production)
  const [collaborators, setCollaborators] = useState<Collaborator[]>([
    {
      id: '1',
      name: 'Current User',
      email: 'user@example.com',
      role: 'owner',
      status: 'online',
      joinedAt: new Date().toISOString(),
    },
  ]);

  const [pendingInvites, setPendingInvites] = useState<PendingInvite[]>([]);

  const [comments, setComments] = useState<Comment[]>([
    {
      id: '1',
      nodeId: 'node-1',
      userId: '1',
      userName: 'Current User',
      content: 'Should we add authentication here?',
      createdAt: new Date(Date.now() - 3600000).toISOString(),
      resolved: false,
      replies: [],
    },
  ]);

  // Handlers
  const handleCopyLink = useCallback(() => {
    const link = `${window.location.origin}/project/${projectId}/bootstrap/session/${sessionId}?invite=true`;
    navigator.clipboard.writeText(link);
    setLinkCopied(true);
    setTimeout(() => setLinkCopied(false), 2000);
  }, [projectId, sessionId]);

  const handleInvite = useCallback(() => {
    if (!inviteEmail.trim()) return;

    const newInvite: PendingInvite = {
      id: `invite-${Date.now()}`,
      email: inviteEmail,
      role: inviteRole,
      sentAt: new Date().toISOString(),
      status: 'pending',
    };

    setPendingInvites((prev) => [...prev, newInvite]);
    setInviteEmail('');
    setShowInviteDialog(false);

    // NOTE: Send actual invite via API
  }, [inviteEmail, inviteRole]);

  const handleChangeRole = useCallback((id: string, role: 'editor' | 'viewer') => {
    setCollaborators((prev) =>
      prev.map((c) => (c.id === id ? { ...c, role } : c))
    );
    // NOTE: Update via API
  }, []);

  const handleRemoveCollaborator = useCallback((id: string) => {
    setCollaborators((prev) => prev.filter((c) => c.id !== id));
    // NOTE: Update via API
  }, []);

  const handleResendInvite = useCallback((id: string) => {
    // NOTE: Resend via API
    console.log('Resending invite:', id);
  }, []);

  const handleCancelInvite = useCallback((id: string) => {
    setPendingInvites((prev) => prev.filter((i) => i.id !== id));
    // NOTE: Cancel via API
  }, []);

  const handleReplyToComment = useCallback((commentId: string, content: string) => {
    setComments((prev) =>
      prev.map((c) =>
        c.id === commentId
          ? {
              ...c,
              replies: [
                ...c.replies,
                {
                  id: `reply-${Date.now()}`,
                  userId: '1',
                  userName: 'Current User',
                  content,
                  createdAt: new Date().toISOString(),
                },
              ],
            }
          : c
      )
    );
  }, []);

  const handleResolveComment = useCallback((commentId: string) => {
    setComments((prev) =>
      prev.map((c) => (c.id === commentId ? { ...c, resolved: true } : c))
    );
  }, []);

  // Filtered data
  const filteredCollaborators = useMemo(() => {
    if (!searchQuery) return collaborators;
    const query = searchQuery.toLowerCase();
    return collaborators.filter(
      (c) =>
        c.name.toLowerCase().includes(query) ||
        c.email.toLowerCase().includes(query)
    );
  }, [collaborators, searchQuery]);

  const unresolvedComments = useMemo(
    () => comments.filter((c) => !c.resolved),
    [comments]
  );

  return (
    <div className="flex min-h-screen flex-col bg-zinc-950">
      {/* Header */}
      <header className="border-b border-zinc-800 bg-zinc-900/50">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-4">
            <Button
              variant="ghost"
              size="sm"
              onClick={() =>
                navigate(
                  ROUTES.bootstrap.session(projectId || '', sessionId || '')
                )
              }
            >
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to Session
            </Button>
            <div className="h-6 w-px bg-zinc-700" />
            <div className="flex items-center gap-2">
              <Users className="h-5 w-5 text-violet-400" />
              <h1 className="text-lg font-semibold text-zinc-100">
                Collaborate
              </h1>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Tooltip title="Copy collaboration link">
              <Button
                variant="outline"
                size="sm"
                onClick={handleCopyLink}
              >
                {linkCopied ? (
                  <>
                    <Check className="mr-2 h-4 w-4 text-green-400" />
                    Copied!
                  </>
                ) : (
                  <>
                    <Copy className="mr-2 h-4 w-4" />
                    Copy Link
                  </>
                )}
              </Button>
            </Tooltip>
            <Button
              variant="solid"
              colorScheme="primary"
              size="sm"
              onClick={() => setShowInviteDialog(true)}
            >
              <UserPlus className="mr-2 h-4 w-4" />
              Invite
            </Button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 px-6 py-8">
        <div className="mx-auto max-w-4xl">
          {/* Tabs */}
          <div className="mb-6 flex items-center gap-4 border-b border-zinc-800">
            <button
              onClick={() => setActiveTab('people')}
              className={cn(
                'flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors',
                activeTab === 'people'
                  ? 'border-violet-500 text-violet-400'
                  : 'border-transparent text-zinc-400 hover:text-zinc-300'
              )}
            >
              <Users className="h-4 w-4" />
              People
              <span className="ml-1 rounded-full bg-zinc-800 px-2 py-0.5 text-xs">
                {collaborators.length + pendingInvites.length}
              </span>
            </button>
            <button
              onClick={() => setActiveTab('comments')}
              className={cn(
                'flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors',
                activeTab === 'comments'
                  ? 'border-violet-500 text-violet-400'
                  : 'border-transparent text-zinc-400 hover:text-zinc-300'
              )}
            >
              <MessageSquare className="h-4 w-4" />
              Comments
              {unresolvedComments.length > 0 && (
                <span className="ml-1 rounded-full bg-violet-500 px-2 py-0.5 text-xs text-white">
                  {unresolvedComments.length}
                </span>
              )}
            </button>
          </div>

          <AnimatePresence mode="wait">
            {activeTab === 'people' ? (
              <motion.div
                key="people"
                variants={containerVariants}
                initial="hidden"
                animate="visible"
                exit={{ opacity: 0 }}
              >
                {/* Search */}
                <div className="mb-6">
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-500" />
                    <Input
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Search collaborators..."
                      className="pl-10"
                    />
                  </div>
                </div>

                {/* Active Collaborators */}
                <section className="mb-8">
                  <h2 className="mb-4 text-sm font-medium text-zinc-400">
                    Active Collaborators ({filteredCollaborators.length})
                  </h2>
                  <div className="space-y-3">
                    {filteredCollaborators.map((collaborator) => (
                      <CollaboratorCard
                        key={collaborator.id}
                        collaborator={collaborator}
                        isOwner={collaborators.find((c) => c.role === 'owner')?.id === '1'}
                        onChangeRole={handleChangeRole}
                        onRemove={handleRemoveCollaborator}
                      />
                    ))}
                  </div>
                </section>

                {/* Pending Invites */}
                {pendingInvites.length > 0 && (
                  <section>
                    <h2 className="mb-4 text-sm font-medium text-zinc-400">
                      Pending Invites ({pendingInvites.length})
                    </h2>
                    <div className="space-y-3">
                      {pendingInvites.map((invite) => (
                        <PendingInviteCard
                          key={invite.id}
                          invite={invite}
                          onResend={handleResendInvite}
                          onCancel={handleCancelInvite}
                        />
                      ))}
                    </div>
                  </section>
                )}
              </motion.div>
            ) : (
              <motion.div
                key="comments"
                variants={containerVariants}
                initial="hidden"
                animate="visible"
                exit={{ opacity: 0 }}
              >
                {comments.length === 0 ? (
                  <div className="py-12 text-center">
                    <MessageSquare className="mx-auto h-12 w-12 text-zinc-700" />
                    <h3 className="mt-4 text-lg font-medium text-zinc-300">
                      No comments yet
                    </h3>
                    <p className="mt-2 text-zinc-500">
                      Comments added to canvas nodes will appear here.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-4">
                    {comments.map((comment) => (
                      <CommentThread
                        key={comment.id}
                        comment={comment}
                        onReply={handleReplyToComment}
                        onResolve={handleResolveComment}
                      />
                    ))}
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </main>

      {/* Invite Dialog */}
      <Dialog
        open={showInviteDialog}
        onOpenChange={(open) => setShowInviteDialog(open)}
        header="Invite Collaborator"
        actions={
          <>
            <Button
              variant="ghost"
              onClick={() => setShowInviteDialog(false)}
            >
              Cancel
            </Button>
            <Button
              variant="solid"
              colorScheme="primary"
              onClick={handleInvite}
              disabled={!inviteEmail.trim()}
            >
              Send Invite
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="mb-2 block text-sm font-medium text-zinc-300">
              Email Address
            </label>
            <Input
              type="email"
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
              placeholder="colleague@company.com"
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-zinc-300">
              Permission
            </label>
            <div className="flex gap-3">
              <button
                onClick={() => setInviteRole('editor')}
                className={cn(
                  'flex-1 rounded-lg border p-3 text-left transition-colors',
                  inviteRole === 'editor'
                    ? 'border-violet-500 bg-violet-500/10'
                    : 'border-zinc-700 hover:border-zinc-600'
                )}
              >
                <div className="flex items-center gap-2">
                  <Edit3 className="h-4 w-4 text-violet-400" />
                  <span className="font-medium text-zinc-200">Editor</span>
                </div>
                <p className="mt-1 text-sm text-zinc-500">
                  Can edit canvas and add comments
                </p>
              </button>
              <button
                onClick={() => setInviteRole('viewer')}
                className={cn(
                  'flex-1 rounded-lg border p-3 text-left transition-colors',
                  inviteRole === 'viewer'
                    ? 'border-violet-500 bg-violet-500/10'
                    : 'border-zinc-700 hover:border-zinc-600'
                )}
              >
                <div className="flex items-center gap-2">
                  <Eye className="h-4 w-4 text-violet-400" />
                  <span className="font-medium text-zinc-200">Viewer</span>
                </div>
                <p className="mt-1 text-sm text-zinc-500">
                  Can view and add comments only
                </p>
              </button>
            </div>
          </div>
        </div>
      </Dialog>
    </div>
  );
};

export default BootstrapCollaboratePage;
