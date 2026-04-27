import { render, screen } from '@testing-library/react';
import React from 'react';

import { CollaborationBar } from '../CollaborationBar';
import { RemoteCursor } from '../RemoteCursor';
import type { RemoteUser } from '../useCanvasCollaborationBackend';

// ─── Shared fixtures ──────────────────────────────────────────────────────────

const currentUser = {
    userId: 'user-1',
    userName: 'Alice',
    userColor: '#6366f1',
};

function makeRemoteUser(overrides: Partial<RemoteUser> = {}): RemoteUser {
    return {
        userId: 'user-2',
        userName: 'Bob',
        userColor: '#f59e0b',
        lastSeen: Date.now(),
        isOnline: true,
        ...overrides,
    };
}

// ─── CollaborationBar ─────────────────────────────────────────────────────────

describe('CollaborationBar', () => {
    describe('connection status', () => {
        it('shows Connected when isConnected is true', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected
                />,
            );
            expect(screen.getByText('Connected')).toBeInTheDocument();
        });

        it('shows Disconnected when isConnected is false', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected={false}
                />,
            );
            expect(screen.getByText('Disconnected')).toBeInTheDocument();
        });

        it('shows syncStatus when connected', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected
                    syncStatus="syncing"
                />,
            );
            expect(screen.getByText(/syncing/i)).toBeInTheDocument();
        });

        it('does not show syncStatus when disconnected', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected={false}
                    syncStatus="syncing"
                />,
            );
            expect(screen.queryByText(/syncing/i)).not.toBeInTheDocument();
        });

        it('shows synced status when connected', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected
                    syncStatus="synced"
                />,
            );
            expect(screen.getByText(/synced/i)).toBeInTheDocument();
        });
    });

    describe('user count', () => {
        it('shows 1 person online when only currentUser is present', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected
                />,
            );
            expect(screen.getByText(/1 person online/i)).toBeInTheDocument();
        });

        it('shows multiple people online when remote users are present', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[makeRemoteUser()]}
                    isConnected
                />,
            );
            expect(screen.getByText(/2 people online/i)).toBeInTheDocument();
        });

        it('uses singular "person" for 1 user', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected
                />,
            );
            expect(screen.getByText(/1 person online/)).toBeInTheDocument();
        });

        it('uses plural "people" for multiple users', () => {
            const remoteUsers = [makeRemoteUser(), makeRemoteUser({ userId: 'user-3', userName: 'Carol' })];
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={remoteUsers}
                    isConnected
                />,
            );
            expect(screen.getByText(/3 people online/)).toBeInTheDocument();
        });
    });

    describe('user avatars', () => {
        it('shows current user avatar with first letter of username', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected
                />,
            );
            expect(screen.getByTitle('Alice')).toBeInTheDocument();
        });

        it('shows remote user avatars', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[makeRemoteUser()]}
                    isConnected
                />,
            );
            expect(screen.getByTitle('Bob')).toBeInTheDocument();
        });

        it('shows first letter of each user name', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[makeRemoteUser()]}
                    isConnected
                />,
            );
            expect(screen.getByText('A')).toBeInTheDocument();
            expect(screen.getByText('B')).toBeInTheDocument();
        });

        it('limits displayed avatars to 5', () => {
            const manyUsers: RemoteUser[] = Array.from({ length: 6 }, (_, i) =>
                makeRemoteUser({ userId: `user-${i + 2}`, userName: `User${i + 2}` }),
            );
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={manyUsers}
                    isConnected
                />,
            );
            // 7 total users; only 5 shown as avatars, +2 overflow badge
            expect(screen.getByText('+2')).toBeInTheDocument();
        });

        it('does not show overflow badge when users are 5 or fewer', () => {
            render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[makeRemoteUser()]}
                    isConnected
                />,
            );
            expect(screen.queryByText(/^\+\d+$/)).not.toBeInTheDocument();
        });
    });

    describe('className prop', () => {
        it('applies additional className to the root element', () => {
            const { container } = render(
                <CollaborationBar
                    currentUser={currentUser}
                    remoteUsers={[]}
                    isConnected
                    className="custom-class"
                />,
            );
            expect(container.firstChild).toHaveClass('custom-class');
        });
    });
});

// ─── RemoteCursor ─────────────────────────────────────────────────────────────

describe('RemoteCursor', () => {
    const onlineUserWithCursor = makeRemoteUser({
        cursor: { x: 100, y: 200 },
    });

    describe('visibility', () => {
        it('renders cursor when user is online and has cursor position', () => {
            render(<RemoteCursor user={onlineUserWithCursor} />);
            expect(document.querySelector('.remote-cursor')).toBeInTheDocument();
        });

        it('renders nothing when user has no cursor position', () => {
            const userNoCursor = makeRemoteUser({ cursor: undefined });
            const { container } = render(<RemoteCursor user={userNoCursor} />);
            expect(container.firstChild).toBeNull();
        });

        it('renders nothing when user is offline', () => {
            const offlineUser = makeRemoteUser({
                isOnline: false,
                cursor: { x: 50, y: 50 },
            });
            const { container } = render(<RemoteCursor user={offlineUser} />);
            expect(container.firstChild).toBeNull();
        });

        it('renders nothing when user is offline even with cursor', () => {
            const offlineUser = makeRemoteUser({ isOnline: false, cursor: { x: 0, y: 0 } });
            const { container } = render(<RemoteCursor user={offlineUser} />);
            expect(container.firstChild).toBeNull();
        });
    });

    describe('label', () => {
        it('shows username label by default (showLabel=true)', () => {
            render(<RemoteCursor user={onlineUserWithCursor} />);
            expect(screen.getByText('Bob')).toBeInTheDocument();
        });

        it('shows username label when showLabel is explicitly true', () => {
            render(<RemoteCursor user={onlineUserWithCursor} showLabel />);
            expect(screen.getByText('Bob')).toBeInTheDocument();
        });

        it('hides username label when showLabel is false', () => {
            render(<RemoteCursor user={onlineUserWithCursor} showLabel={false} />);
            expect(screen.queryByText('Bob')).not.toBeInTheDocument();
        });
    });

    describe('cursor SVG', () => {
        it('renders SVG cursor element', () => {
            render(<RemoteCursor user={onlineUserWithCursor} />);
            expect(document.querySelector('svg')).toBeInTheDocument();
        });

        it('uses user color for cursor fill', () => {
            render(<RemoteCursor user={onlineUserWithCursor} />);
            const path = document.querySelector('path');
            expect(path).toHaveAttribute('fill', onlineUserWithCursor.userColor);
        });
    });
});
