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

import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { User, Mail, Save, Camera } from 'lucide-react';
import { parseJsonResponse } from '@/lib/http';
import { useCurrentUser } from '../providers/AuthProvider';
import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

interface UserProfile {
    id: string;
    name: string;
    email: string;
    avatar?: string;
    role?: string;
    createdAt?: string;
}

interface ProfileFormState {
    firstName: string;
    lastName: string;
    email: string;
    avatarUrl: string;
}

const inputCls =
    'w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg placeholder-fg-muted focus:outline-none focus:ring-2 focus:ring-brand';
const labelCls = 'block text-xs font-medium text-fg-muted mb-1';

/**
 * Profile Page Component
 */
export default function Component() {
    const currentUser = useCurrentUser();

    // ── Fetch full profile from API ────────────────────────────────────────────
    const { data: profile, isLoading } = useQuery<UserProfile>({
        queryKey: ['user-profile'],
        queryFn: async () => {
            const res = await fetch('/api/auth/me');
            if (!res.ok) throw new Error('Failed to load profile');
            return parseJsonResponse<UserProfile>(res, 'profile load');
        },
        enabled: currentUser.isAuthenticated,
    });

    // ── Local form state, seeded from API data ─────────────────────────────────
    const [form, setForm] = useState<ProfileFormState>({
        firstName: '',
        lastName: '',
        email: '',
        avatarUrl: '',
    });

    useEffect(() => {
        if (profile) {
            const [firstName = '', ...rest] = profile.name.split(' ');
            setForm({
                firstName,
                lastName: rest.join(' '),
                email: profile.email ?? '',
                avatarUrl: profile.avatar ?? '',
            });
        }
    }, [profile]);

    // ── Derive initials for avatar fallback ────────────────────────────────────
    const initials =
        ((form.firstName[0] ?? '') + (form.lastName[0] ?? '')).toUpperCase() ||
        currentUser.initials;

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
            {/* Page header */}
            <div className="mb-6">
                <h1 className="text-2xl font-semibold text-fg">Your Profile</h1>
                <p className="mt-1 text-sm text-fg-muted">
                    Manage your personal information and account settings.
                </p>
            </div>

            <div className="rounded-xl border border-border bg-surface-raised shadow-sm overflow-hidden">
                {/* Avatar section */}
                <div className="flex items-center gap-5 border-b border-border bg-surface px-6 py-5">
                    <div className="relative">
                        {form.avatarUrl ? (
                            <img
                                src={form.avatarUrl}
                                alt={`${form.firstName} ${form.lastName}`}
                                className="h-16 w-16 rounded-full object-cover border-2 border-border"
                            />
                        ) : (
                            <div className="h-16 w-16 rounded-full bg-brand/20 border-2 border-border flex items-center justify-center">
                                <span className="text-xl font-bold text-brand">{initials}</span>
                            </div>
                        )}
                        <button
                            className="absolute -bottom-1 -right-1 rounded-full bg-surface-raised border border-border p-1 hover:bg-surface-muted transition-colors"
                            title="Change avatar"
                            disabled
                        >
                            <Camera className="h-3 w-3 text-fg-muted" />
                        </button>
                    </div>
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

                {/* Edit form */}
                <div className="space-y-4 p-6">
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        <div>
                            <label className={labelCls} htmlFor="profile-first-name">
                                First Name
                            </label>
                            <div className="relative">
                                <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-fg-muted pointer-events-none" />
                                <input
                                    id="profile-first-name"
                                    className={`${inputCls} pl-9`}
                                    value={form.firstName}
                                    readOnly
                                    placeholder="First name"
                                />
                            </div>
                        </div>
                        <div>
                            <label className={labelCls} htmlFor="profile-last-name">
                                Last Name
                            </label>
                            <input
                                id="profile-last-name"
                                className={inputCls}
                                value={form.lastName}
                                readOnly
                                placeholder="Last name"
                            />
                        </div>
                    </div>

                    <div>
                        <label className={labelCls} htmlFor="profile-email">
                            Email Address
                        </label>
                        <div className="relative">
                            <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-fg-muted pointer-events-none" />
                            <input
                                id="profile-email"
                                type="email"
                                className={`${inputCls} pl-9`}
                                value={form.email}
                                readOnly
                                placeholder="you@example.com"
                            />
                        </div>
                    </div>

                    <p className="text-sm text-fg-muted" role="status">
                        Profile data is available, but edits are disabled until the backed profile update API is restored.
                    </p>
                </div>

                {/* Footer actions */}
                <div className="flex justify-end gap-2 border-t border-border bg-surface px-6 py-4">
                    <button
                        className="rounded-lg border border-border px-4 py-2 text-sm text-fg-muted hover:bg-surface-muted transition-colors"
                        onClick={() => {
                            if (profile) {
                                const [firstName = '', ...rest] = profile.name.split(' ');
                                setForm({
                                    firstName,
                                    lastName: rest.join(' '),
                                    email: profile.email ?? '',
                                    avatarUrl: profile.avatar ?? '',
                                });
                            }
                        }}
                    >
                        Cancel
                    </button>
                    <button
                        className="flex items-center gap-2 rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark transition-colors disabled:opacity-60"
                        disabled
                    >
                        <Save className="h-4 w-4" />
                        Editing Unavailable
                    </button>
                </div>
            </div>

            {/* Account metadata */}
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
