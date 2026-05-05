import { useAtomValue } from 'jotai';
import { currentUserAtom } from '../../../../stores/user.store';
import { useAuth } from '../../../../hooks/useAuth';
import { useCollaboration } from '../../../../hooks/useCollaboration';

interface CanvasCollaborationBannerProps {
  projectId: string;
}

interface PresenceEntry {
  user: { id: string; name: string };
}

export function CanvasCollaborationBanner({ projectId }: CanvasCollaborationBannerProps) {
  const currentUser = useAtomValue(currentUserAtom);
  const { getToken } = useAuth();
  const collaboration = useCollaboration({
    projectId,
    getToken,
    currentUser: currentUser
      ? { id: currentUser.id, name: currentUser.name, email: currentUser.email, color: '#4ECDC4' }
      : null,
    enabled: Boolean(projectId && currentUser),
  });

  if (!currentUser) return null;

  const presence = collaboration.presence as Map<string, PresenceEntry>;
  const collaborators = Array.from(presence.values()).slice(0, 3);
  const onlineCount = 1 + presence.size;

  return (
    <div className="absolute right-4 top-4 z-20 rounded-xl border border-divider bg-bg-paper/95 px-4 py-3 shadow-lg backdrop-blur"
      data-testid="canvas-collaboration-banner">
      <div className="flex items-center gap-2 text-sm font-medium text-text-primary">
        <span className={`h-2.5 w-2.5 rounded-full ${collaboration.isConnected ? 'bg-success-bg' : 'bg-warning-bg'}`}
          data-testid="canvas-collaboration-status-dot" />
        <span>{collaboration.isConnected ? 'Live collaboration connected' : 'Collaboration standby'}</span>
      </div>
      <p className="mt-1 text-xs text-text-secondary" data-testid="canvas-collaboration-summary">
        {onlineCount} collaborator{onlineCount === 1 ? '' : 's'} visible.
      </p>
      {collaborators.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {collaborators.map((presence) => (
            <span key={presence.user.id}
              className="rounded-full bg-primary-50 px-2.5 py-1 text-[11px] font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-200">
              {presence.user.name}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

export const NODE_DEFAULT_SIZES: Record<string, { width: number; height: number }> = {
  frame: { width: 400, height: 300 },
  'sticky-note': { width: 200, height: 200 },
  text: { width: 300, height: 150 },
  task: { width: 250, height: 70 },
  default: { width: 150, height: 150 },
};

export function getNodeSize(type: string | undefined): { width: number; height: number } {
  return NODE_DEFAULT_SIZES[type ?? 'default'] ?? NODE_DEFAULT_SIZES.default;
}

export type { CanvasCollaborationBannerProps };
export default CanvasCollaborationBanner;
