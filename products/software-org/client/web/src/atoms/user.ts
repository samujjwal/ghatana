/**
 * User atoms for Jotai state management
 *
 * @doc.type state
 * @doc.purpose User authentication and profile state
 * @doc.layer presentation
 * @doc.pattern State Management
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

export interface CurrentUser {
    id: string;
    name: string;
    email: string;
    role: string;
    department?: string;
    avatar?: string;
}

/**
 * Current authenticated user
 */
export const currentUserAtom = atomWithStorage<CurrentUser | null>('currentUser', null);

/**
 * User session token
 */
export const authTokenAtom = atomWithStorage<string | null>('authToken', null);

/**
 * User preferences
 */
export const userPreferencesAtom = atomWithStorage('userPreferences', {
    theme: 'light' as 'light' | 'dark',
    notifications: true,
    emailAlerts: true,
});
