/**
 * Profile Route
 *
 * Displays the current user's profile information using the supported auth API.
 *
 * @doc.type route
 * @doc.purpose Current user profile view and edit
 * @doc.layer product
 * @doc.pattern Route Module
 */

import { useQuery } from '@tanstack/react-query';
import { User, Mail } from 'lucide-react';
import { useCurrentUser } from '../providers/AuthProvider';
import { fetchAuthSession, mapAuthSessionToUser } from '../providers/auth-session';
import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

interface UserProfile {
    id: string;
    name: string;
    email: string;
    avatar?: string;
    role?: string;
    createdAt?: string;
}

export default function Component() {
    const currentUser = useCurrentUser();

    const { data: profile, isLoading } = useQuery<UserProfile>({
        queryKey: ['user-profile'],
        queryFn: async () => {
            const sessionUser = await fetchAuthSession();
            if (!sessionUser) throw new Error('Failed to load profile');
            const user = mapAuthSessionToUser(sessionUser);
            return {
                id: user.id,
                name: user.name,
                email: user.email,
                avatar: sessionUser.avatarUrl,
                role: user.role,
                // createdAt not available from auth session; omit or derive if needed
            } as UserProfile;
        },
        enabled: currentUser.isAuthenticated,
    });

    if (!currentUser.isAuthenticated) {
        return (
            <div className="flex items-center justify-center min-h-[60vh]">
                <p className="text-fg-muted">Please log in to view your profile.</p>
            </div>
        );
    }

    if (isLoading) {
        return (
            <div className="p-6 max-w-2xl mx-auto">
                <div className="animate-pulse space-y-4">
                    <div className="h-8 w-48 rounded bg-surface-muted" />
                    <div className="h-20 w-20 rounded-full bg-surface-muted" />
                    <div className="h-10 rounded-lg bg-surface-muted" />
                    <div className="h-10 rounded-lg bg-surface-muted" />
                </div>
            </div>
        );
    }

    return (
        <div className="p-6 max-w-2xl mx-auto">
            <div className="mb-6">
                <h1 className="text-2xl font-semibold text-fg">Account Summary</h1>
                <p className="mt-1 text-sm text-fg-muted">
                    Read-only view of your account details. Profile editing is not yet supported.
                </p>
            </div>

            <div className="rounded-xl border border-border bg-surface-raised shadow-sm overflow-hidden">
                <div className="flex items-center gap-5 border-b border-border bg-surface px-6 py-5">
                    {profile?.avatar ? (
                        <img
                            src={profile.avatar}
                            alt={profile.name}
                            className="h-16 w-16 rounded-full object-cover border-2 border-border"
                        />
                    ) : (
                        <div className="h-16 w-16 rounded-full bg-brand/20 border-2 border-border flex items-center justify-center">
                            <span className="text-xl font-bold text-brand">{currentUser.initials}</span>
                        </div>
                    )}
                    <div>
                        <p className="font-semibold text-fg">{currentUser.name}</p>
                        <p className="text-sm text-fg-muted">{currentUser.email}</p>
                        {profile?.role && (
                            <span className="mt-1 inline-block rounded-full bg-brand/10 px-2 py-0.5 text-xs font-medium text-brand capitalize">
                                {profile.role}
                            </span>
                        )}
                    </div>
                </div>

                <div className="p-6 space-y-4">
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        <div>
                            <p className="text-xs font-medium text-fg-muted mb-1">Name</p>
                            <div className="flex items-center gap-2 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg">
                                <User className="h-4 w-4 text-fg-muted" />
                                <span>{profile?.name ?? currentUser.name}</span>
                            </div>
                        </div>
                        <div>
                            <p className="text-xs font-medium text-fg-muted mb-1">Email</p>
                            <div className="flex items-center gap-2 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg">
                                <Mail className="h-4 w-4 text-fg-muted" />
                                <span>{profile?.email ?? currentUser.email}</span>
                            </div>
                        </div>
                    </div>

                    <p className="text-sm text-fg-muted" role="status">
                        Editing is not yet available. Contact an administrator to update account details.
                    </p>
                </div>
            </div>

            {profile?.createdAt && (
                <p className="mt-4 text-xs text-fg-muted">
                    Member since {new Date(profile.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })}
                </p>
            )}
        </div>
    );
}

/**
 * Route Error Boundary
 */
export function ErrorBoundary() {
    return (
        <RouteErrorBoundary
            title="Profile Error"
            message="Something went wrong while loading your profile."
        />
    );
}
