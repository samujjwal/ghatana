/**
 * Resume Session Page
 *
 * @description Lists saved bootstrapping sessions that users can resume.
 * Shows session progress, expiration warnings, and quick actions.
 *
 * @doc.type page
 * @doc.purpose Session management
 * @doc.layer page
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router';
import { useAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Play,
  Trash2,
  Download,
  Share2,
  AlertTriangle,
  Plus,
  Search,
  Filter,
  MoreHorizontal,
  Calendar,
  Users,
  Archive,
} from 'lucide-react';
import { formatDistanceToNow, differenceInDays, isPast } from 'date-fns';

import { cn } from '../../utils/cn';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Menu } from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { Dialog } from '@ghatana/ui';
import { Progress } from '@ghatana/ui';

import { savedSessionsAtom, type BootstrapSession as StateSession } from '../../state/atoms';
import { ROUTES } from '../../router/paths';

// =============================================================================
// Types
// =============================================================================

interface BootstrapSession extends StateSession {
  // Local extension for UI purposes - these may be optional in the state type
  featuresIdentified?: number;
}

type SortOption = 'recent' | 'progress' | 'expiring' | 'alphabetical';
type FilterOption = 'all' | 'active' | 'expiring' | 'completed';

// =============================================================================
// Phase Configuration
// =============================================================================

const PHASE_CONFIG: Record<
  string,
  { label: string; color: string; step: number }
> = {
  enter: { label: 'Enter', color: 'text-blue-400', step: 1 },
  explore: { label: 'Explore', color: 'text-purple-400', step: 2 },
  refine: { label: 'Refine', color: 'text-orange-400', step: 3 },
  validate: { label: 'Validate', color: 'text-cyan-400', step: 4 },
  complete: { label: 'Complete', color: 'text-success-400', step: 5 },
};

// =============================================================================
// Session Card Component
// =============================================================================

interface SessionCardProps {
  session: BootstrapSession;
  onResume: (sessionId: string) => void;
  onDelete: (sessionId: string) => void;
  onExport: (sessionId: string) => void;
  onShare: (sessionId: string) => void;
}

const SessionCard: React.FC<SessionCardProps> = ({
  session,
  onResume,
  onDelete,
  onExport,
  onShare,
}) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const phaseConfig = PHASE_CONFIG[session.phase] || PHASE_CONFIG.enter;
  const daysUntilExpiry = session.expiresAt 
    ? differenceInDays(new Date(session.expiresAt), new Date()) 
    : 30;
  const isExpiring = daysUntilExpiry <= 5 && daysUntilExpiry > 0;
  const isExpired = session.expiresAt ? isPast(new Date(session.expiresAt)) : false;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      className={cn(
        'group relative rounded-lg border bg-zinc-900 transition-all duration-200',
        isExpired
          ? 'border-error-500/30 opacity-60'
          : isExpiring
            ? 'border-warning-500/30'
            : 'border-zinc-800 hover:border-zinc-700'
      )}
    >
      <div className="p-5">
        {/* Header */}
        <div className="mb-4 flex items-start justify-between">
          <div className="flex-1">
            <h3 className="mb-1 text-lg font-semibold text-zinc-100">
              {session.projectName}
            </h3>
            <p className="text-sm text-zinc-400 line-clamp-2">
              {session.description || 'No description provided'}
            </p>
          </div>

          {/* Actions Menu */}
          <Menu
            open={menuOpen}
            onOpenChange={setMenuOpen}
            trigger={
              <Button
                variant="ghost"
                size="sm"
                className="h-8 w-8 p-0 opacity-0 group-hover:opacity-100"
                aria-label="Session actions"
              >
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            }
          >
            <MenuItem
              icon={<Download className="h-4 w-4" />}
              text="Export Canvas"
              onClick={() => onExport(session.id)}
            />
            <MenuItem
              icon={<Share2 className="h-4 w-4" />}
              text="Share for Review"
              onClick={() => onShare(session.id)}
            />
            <MenuItem
              icon={<Trash2 className="h-4 w-4" />}
              text="Delete"
              className="text-error-400"
              onClick={() => onDelete(session.id)}
            />
          </Menu>
        </div>

        {/* Phase & Progress */}
        <div className="mb-4">
          <div className="mb-2 flex items-center justify-between text-sm">
            <span className={cn('font-medium', phaseConfig.color)}>
              Phase: {phaseConfig.label} ({phaseConfig.step}/5)
            </span>
            <span className="text-zinc-400">{session.progress}% complete</span>
          </div>
          <Progress value={session.progress} className="h-2" />
        </div>

        {/* Stats Row */}
        <div className="mb-4 grid grid-cols-3 gap-4 text-center">
          <div>
            <div className="text-lg font-semibold text-zinc-100">
              {session.featuresIdentified || 0}
            </div>
            <div className="text-xs text-zinc-500">Features</div>
          </div>
          <div>
            <div className="text-lg font-semibold text-zinc-100">
              {session.questionsAnswered}/{session.totalQuestions}
            </div>
            <div className="text-xs text-zinc-500">Questions</div>
          </div>
          <div>
            <div className="text-lg font-semibold text-zinc-100">
              {session.confidenceScore}%
            </div>
            <div className="text-xs text-zinc-500">Confidence</div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between border-t border-zinc-800 pt-4">
          <div className="flex items-center gap-3 text-xs text-zinc-500">
            <span className="flex items-center gap-1">
              <Calendar className="h-3 w-3" />
              Updated {formatDistanceToNow(new Date(session.updatedAt))} ago
            </span>
            {session.collaboratorIds && session.collaboratorIds.length > 0 && (
              <span className="flex items-center gap-1">
                <Users className="h-3 w-3" />
                {session.collaboratorIds.length}
              </span>
            )}
          </div>

          <Button
            onClick={() => onResume(session.id)}
            disabled={isExpired}
            size="sm"
          >
            <Play className="mr-2 h-4 w-4" />
            Resume
          </Button>
        </div>

        {/* Expiry Warning */}
        {(isExpiring || isExpired) && (
          <div
            className={cn(
              'mt-3 flex items-center gap-2 rounded-md p-2 text-xs',
              isExpired
                ? 'bg-error-500/10 text-error-400'
                : 'bg-warning-500/10 text-warning-400'
            )}
          >
            <AlertTriangle className="h-4 w-4 flex-shrink-0" />
            <span>
              {isExpired
                ? 'This session has expired. Please start a new project.'
                : `Expires in ${daysUntilExpiry} day${daysUntilExpiry === 1 ? '' : 's'} - save or complete to keep your progress`}
            </span>
          </div>
        )}
      </div>
    </motion.div>
  );
};

// =============================================================================
// Empty State Component
// =============================================================================

const EmptyState: React.FC<{ onCreateNew: () => void }> = ({ onCreateNew }) => (
  <div className="flex flex-col items-center justify-center py-16 text-center">
    <div className="mb-4 rounded-full bg-zinc-800 p-4">
      <Archive className="h-8 w-8 text-zinc-400" />
    </div>
    <h3 className="mb-2 text-lg font-semibold text-zinc-100">
      No saved sessions
    </h3>
    <p className="mb-6 max-w-md text-sm text-zinc-400">
      You don't have any bootstrapping sessions in progress. Start a new project
      to begin defining your application with AI assistance.
    </p>
    <Button onClick={onCreateNew}>
      <Plus className="mr-2 h-4 w-4" />
      Start New Project
    </Button>
  </div>
);

// =============================================================================
// Main Page Component
// =============================================================================

const ResumeSessionPage: React.FC = () => {
  const navigate = useNavigate();
  const [sessions, setSessions] = useAtom(savedSessionsAtom);

  // Local state
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('recent');
  const [filterBy, setFilterBy] = useState<FilterOption>('all');
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [sessionToDelete, setSessionToDelete] = useState<string | null>(null);
  const [filterMenuOpen, setFilterMenuOpen] = useState(false);
  const [sortMenuOpen, setSortMenuOpen] = useState(false);

  // Filter and sort sessions
  const filteredSessions = useMemo(() => {
    // Ensure sessions is an array
    const sessionList = Array.isArray(sessions) ? sessions : [];
    let result = [...sessionList] as BootstrapSession[];

    // Search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter(
        (s) =>
          s.projectName.toLowerCase().includes(query) ||
          s.description?.toLowerCase().includes(query)
      );
    }

    // Status filter
    switch (filterBy) {
      case 'active':
        result = result.filter((s) => s.phase !== 'complete' && (!s.expiresAt || !isPast(new Date(s.expiresAt))));
        break;
      case 'expiring':
        result = result.filter((s) => {
          if (!s.expiresAt) return false;
          const daysLeft = differenceInDays(new Date(s.expiresAt), new Date());
          return daysLeft <= 5 && daysLeft > 0;
        });
        break;
      case 'completed':
        result = result.filter((s) => s.phase === 'complete');
        break;
    }

    // Sort
    switch (sortBy) {
      case 'recent':
        result.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
        break;
      case 'progress':
        result.sort((a, b) => b.progress - a.progress);
        break;
      case 'expiring':
        result.sort((a, b) => {
          const aTime = a.expiresAt ? new Date(a.expiresAt).getTime() : Infinity;
          const bTime = b.expiresAt ? new Date(b.expiresAt).getTime() : Infinity;
          return aTime - bTime;
        });
        break;
      case 'alphabetical':
        result.sort((a, b) => a.projectName.localeCompare(b.projectName));
        break;
    }

    return result;
  }, [sessions, searchQuery, sortBy, filterBy]);

  // Handlers
  const handleResume = useCallback(
    (sessionId: string) => {
      const sessionList = Array.isArray(sessions) ? sessions : [];
      const session = sessionList.find((s) => s.id === sessionId);
      if (session) {
        navigate(`/project/${session.id}/bootstrap/session/${sessionId}`, {
          state: { resuming: true },
        });
      }
    },
    [sessions, navigate]
  );

  const handleDelete = useCallback((sessionId: string) => {
    setSessionToDelete(sessionId);
    setDeleteDialogOpen(true);
  }, []);

  const confirmDelete = useCallback(() => {
    if (sessionToDelete) {
      setSessions((prev) => {
        const prevArray = Array.isArray(prev) ? prev : [];
        return prevArray.filter((s) => s.id !== sessionToDelete);
      });
      setDeleteDialogOpen(false);
      setSessionToDelete(null);
    }
  }, [sessionToDelete, setSessions]);

  const handleExport = useCallback((sessionId: string) => {
    // NOTE: Implement canvas export
    console.log('Export session:', sessionId);
  }, []);

  const handleShare = useCallback((sessionId: string) => {
    // NOTE: Implement share functionality
    console.log('Share session:', sessionId);
  }, []);

  const handleCreateNew = useCallback(() => {
    navigate(ROUTES.TEMPLATES);
  }, [navigate]);

  const sessionCount = Array.isArray(sessions) ? sessions.length : 0;

  return (
    <div className="min-h-screen bg-zinc-950 p-6">
      <div className="mx-auto max-w-6xl">
        {/* Header */}
        <div className="mb-8 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-zinc-100">
              Resume Bootstrapping
            </h1>
            <p className="mt-1 text-sm text-zinc-400">
              Continue where you left off or start a new project
            </p>
          </div>
          <Button onClick={handleCreateNew}>
            <Plus className="mr-2 h-4 w-4" />
            New Project
          </Button>
        </div>

        {/* Filters & Search */}
        {sessionCount > 0 && (
          <div className="mb-6 flex flex-wrap items-center gap-4">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-500" />
              <Input
                placeholder="Search sessions..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9"
              />
            </div>

            {/* Filter Menu */}
            <Menu
              open={filterMenuOpen}
              onOpenChange={setFilterMenuOpen}
              trigger={
                <Button variant="outline" className="gap-2">
                  <Filter className="h-4 w-4" />
                  {filterBy === 'all' ? 'All Sessions' : filterBy}
                </Button>
              }
            >
              <MenuItem text="All Sessions" onClick={() => setFilterBy('all')} />
              <MenuItem text="Active Only" onClick={() => setFilterBy('active')} />
              <MenuItem text="Expiring Soon" onClick={() => setFilterBy('expiring')} />
              <MenuItem text="Completed" onClick={() => setFilterBy('completed')} />
            </Menu>

            {/* Sort Menu */}
            <Menu
              open={sortMenuOpen}
              onOpenChange={setSortMenuOpen}
              trigger={
                <Button variant="outline" className="gap-2">
                  Sort: {sortBy}
                </Button>
              }
            >
              <MenuItem text="Most Recent" onClick={() => setSortBy('recent')} />
              <MenuItem text="By Progress" onClick={() => setSortBy('progress')} />
              <MenuItem text="Expiring Soonest" onClick={() => setSortBy('expiring')} />
              <MenuItem text="Alphabetical" onClick={() => setSortBy('alphabetical')} />
            </Menu>
          </div>
        )}

        {/* Sessions Grid or Empty State */}
        {filteredSessions.length > 0 ? (
          <div className="grid gap-6 md:grid-cols-2">
            <AnimatePresence mode="popLayout">
              {filteredSessions.map((session) => (
                <SessionCard
                  key={session.id}
                  session={session}
                  onResume={handleResume}
                  onDelete={handleDelete}
                  onExport={handleExport}
                  onShare={handleShare}
                />
              ))}
            </AnimatePresence>
          </div>
        ) : searchQuery || filterBy !== 'all' ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <Search className="mb-4 h-12 w-12 text-zinc-600" />
            <h3 className="mb-2 text-lg font-semibold text-zinc-100">
              No matching sessions
            </h3>
            <p className="text-sm text-zinc-400">
              Try adjusting your search or filter criteria
            </p>
          </div>
        ) : (
          <EmptyState onCreateNew={handleCreateNew} />
        )}

        {/* Delete Confirmation Dialog */}
        <Dialog
          open={deleteDialogOpen}
          onOpenChange={setDeleteDialogOpen}
          header="Delete Session?"
          actions={
            <>
              <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
                Cancel
              </Button>
              <Button colorScheme="error" onClick={confirmDelete}>
                Delete Session
              </Button>
            </>
          }
        >
          <p className="text-sm text-zinc-400">
            This action cannot be undone. All progress and canvas data for
            this session will be permanently deleted.
          </p>
        </Dialog>
      </div>
    </div>
  );
};

export default ResumeSessionPage;
