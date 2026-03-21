import React from 'react';
import { CollaborationState, CollaborationUser } from '../state/canvasAtoms';

import './CollaborationPresence.css';

interface CollaborationPresenceProps {
  state: CollaborationState;
}

/**
 * CollaborationPresence - Shows active collaborators on canvas
 */
export function CollaborationPresence({ state }: CollaborationPresenceProps) {
  if (!state.enabled || state.users.length === 0) {
    return null;
  }

  return (
    <div className="collaboration-presence" data-testid="collaboration-presence">
      <div className="collaboration-presence__header">
        <span className="collaboration-presence__indicator"></span>
        <span className="collaboration-presence__count">
          {state.users.length} active
        </span>
      </div>
      
      <div className="collaboration-presence__users">
        {state.users.map((user) => (
          <CollaborationUserAvatar key={user.id} user={user} />
        ))}
      </div>
    </div>
  );
}

function CollaborationUserAvatar({ user }: { user: CollaborationUser }) {
  const initials = user.name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase();

  return (
    <div
      className="collaboration-user"
      style={{ '--user-color': user.color } as React.CSSProperties}
      title={user.name}
    >
      <div className="collaboration-user__avatar" style={{ backgroundColor: user.color }}>
        {initials}
      </div>
      <span className="collaboration-user__name">{user.name}</span>
      {user.cursor && (
        <div
          className="collaboration-user__cursor"
          style={{ left: user.cursor.x, top: user.cursor.y }}
        >
          <svg width="24" height="24" viewBox="0 0 24 24">
            <path
              fill={user.color}
              d="M3 3l7.07 16.97 2.51-7.39 7.39-2.51L3 3z"
            />
          </svg>
          <span className="collaboration-user__cursor-label">{user.name}</span>
        </div>
      )}
    </div>
  );
}

export default CollaborationPresence;
