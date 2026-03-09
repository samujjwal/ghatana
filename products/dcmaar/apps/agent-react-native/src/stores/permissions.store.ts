/**
 * Permissions Store - Jotai Atoms
 *
 * Manages app permissions state including:
 * - Individual app permission statuses
 * - Permission requests and grants
 * - Dangerous vs normal permissions
 * - Permission audit trail
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose App permissions management state
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';

/**
 * Android permission type constants.
 *
 * @type {Object}
 */
export const PERMISSION_TYPES = {
  CAMERA: 'android.permission.CAMERA',
  MICROPHONE: 'android.permission.RECORD_AUDIO',
  LOCATION: 'android.permission.ACCESS_FINE_LOCATION',
  CONTACTS: 'android.permission.READ_CONTACTS',
  CALENDAR: 'android.permission.READ_CALENDAR',
  CALL_LOG: 'android.permission.READ_CALL_LOG',
  PHONE: 'android.permission.CALL_PHONE',
  SMS: 'android.permission.SEND_SMS',
  STORAGE: 'android.permission.READ_EXTERNAL_STORAGE',
  INTERNET: 'android.permission.INTERNET',
} as const;

/**
 * Permission status enumeration.
 *
 * @type {'granted' | 'denied' | 'unknown' | 'pending'}
 */
export type PermissionStatus = 'granted' | 'denied' | 'unknown' | 'pending';

/**
 * Permission metadata.
 *
 * @interface Permission
 * @property {string} id - Permission identifier
 * @property {string} name - Human-readable permission name (e.g., "Camera")
 * @property {string} description - Permission description
 * @property {'dangerous' | 'normal'} riskLevel - Risk level of permission
 * @property {PermissionStatus} status - Current grant status
 * @property {Date} [grantedAt] - When permission was granted
 * @property {Date} [deniedAt] - When permission was denied
 * @property {string} [denialReason] - Why permission was denied
 */
export interface Permission {
  id: string;
  name: string;
  description: string;
  riskLevel: 'dangerous' | 'normal';
  status: PermissionStatus;
  grantedAt?: Date;
  deniedAt?: Date;
  denialReason?: string;
}

/**
 * App-specific permission set.
 *
 * @interface AppPermissionSet
 * @property {string} appId - App identifier
 * @property {string} appName - App display name
 * @property {Permission[]} permissions - List of permissions for app
 * @property {Date} [lastChecked] - When permissions were last verified
 * @property {number} grantedCount - Number of granted permissions
 * @property {number} deniedCount - Number of denied permissions
 */
export interface AppPermissionSet {
  appId: string;
  appName: string;
  permissions: Permission[];
  lastChecked?: Date;
  grantedCount: number;
  deniedCount: number;
}

/**
 * Permission request.
 *
 * @interface PermissionRequest
 * @property {string} id - Request identifier
 * @property {string} appId - App requesting permission
 * @property {string} permissionId - Permission being requested
 * @property {'pending' | 'granted' | 'denied'} status - Request status
 * @property {Date} requestedAt - When permission was requested
 * @property {Date} [resolvedAt] - When request was resolved
 * @property {string} [reason] - Reason for request
 */
export interface PermissionRequest {
  id: string;
  appId: string;
  permissionId: string;
  status: 'pending' | 'granted' | 'denied';
  requestedAt: Date;
  resolvedAt?: Date;
  reason?: string;
}

/**
 * Permissions store state.
 *
 * @interface PermissionsState
 * @property {Record<string, AppPermissionSet>} permissionsByApp - App-specific permission sets
 * @property {PermissionRequest[]} pendingRequests - Pending permission requests
 * @property {PermissionRequest[]} requestHistory - Historical requests
 * @property {'idle' | 'loading' | 'loaded' | 'error'} status - Loading status
 * @property {string | null} error - Error message if load failed
 */
export interface PermissionsState {
  permissionsByApp: Record<string, AppPermissionSet>;
  pendingRequests: PermissionRequest[];
  requestHistory: PermissionRequest[];
  status: 'idle' | 'loading' | 'loaded' | 'error';
  error: string | null;
}

/**
 * Initial permissions state.
 *
 * GIVEN: App initialization
 * WHEN: permissionsAtom is first accessed
 * THEN: Permissions start with empty map, no requests
 */
const initialPermissionsState: PermissionsState = {
  permissionsByApp: {},
  pendingRequests: [],
  requestHistory: [],
  status: 'idle',
  error: null,
};

/**
 * Core permissions atom.
 *
 * Holds complete permissions state including:
 * - Per-app permission sets
 * - Pending requests
 * - Request history
 * - Loading and error state
 *
 * Usage (in components):
 * ```typescript
 * const [permissionsState, setPermissionsState] = useAtom(permissionsAtom);
 * ```
 */
export const permissionsAtom = atom<PermissionsState>(initialPermissionsState);

/**
 * Derived atom: Permissions for specific app.
 *
 * Returns a function that takes appId and returns permission set or null.
 *
 * Usage (in components):
 * ```typescript
 * const [getAppPerms] = useAtom(appPermissionsAtom);
 * const perms = getAppPerms('app-id-123');
 * if (perms) show permissions list
 * ```
 */
export const appPermissionsAtom = atom<(appId: string) => AppPermissionSet | null>(
  (get) => {
    const state = get(permissionsAtom);
    return (appId: string) => {
      return state.permissionsByApp[appId] ?? null;
    };
  }
);

/**
 * Derived atom: All granted permissions across all apps.
 *
 * GIVEN: permissionsByApp with multiple apps and their permissions
 * WHEN: grantedPermissionsAtom is read
 * THEN: Returns flat list of all granted permissions
 *
 * Usage (in components):
 * ```typescript
 * const [grantedPerms] = useAtom(grantedPermissionsAtom);
 * // Show "X permissions granted across all apps"
 * ```
 */
export const grantedPermissionsAtom = atom<Permission[]>((get) => {
  const state = get(permissionsAtom);
  return Object.values(state.permissionsByApp)
    .flatMap((appSet) => appSet.permissions)
    .filter((perm) => perm.status === 'granted');
});

/**
 * Derived atom: All denied permissions across all apps.
 *
 * GIVEN: permissionsByApp with multiple apps and their permissions
 * WHEN: deniedPermissionsAtom is read
 * THEN: Returns flat list of all denied permissions
 *
 * Usage (in components):
 * ```typescript
 * const [deniedPerms] = useAtom(deniedPermissionsAtom);
 * ```
 */
export const deniedPermissionsAtom = atom<Permission[]>((get) => {
  const state = get(permissionsAtom);
  return Object.values(state.permissionsByApp)
    .flatMap((appSet) => appSet.permissions)
    .filter((perm) => perm.status === 'denied');
});

/**
 * Derived atom: Dangerous permissions (high-risk).
 *
 * GIVEN: permissionsByApp with permissions of varying risk levels
 * WHEN: dangerousPermissionsAtom is read
 * THEN: Returns only 'dangerous' classified permissions
 *
 * Usage (in components):
 * ```typescript
 * const [dangerous] = useAtom(dangerousPermissionsAtom);
 * // Highlight dangerous permissions in red
 * ```
 */
export const dangerousPermissionsAtom = atom<Permission[]>((get) => {
  const state = get(permissionsAtom);
  return Object.values(state.permissionsByApp)
    .flatMap((appSet) => appSet.permissions)
    .filter((perm) => perm.riskLevel === 'dangerous');
});

/**
 * Derived atom: Count of pending permission requests.
 *
 * GIVEN: pendingRequests array
 * WHEN: pendingRequestCountAtom is read
 * THEN: Returns length of pending requests
 *
 * Usage (in components):
 * ```typescript
 * const [pendingCount] = useAtom(pendingRequestCountAtom);
 * // Show badge with count on permissions icon
 * ```
 */
export const pendingRequestCountAtom = atom<number>((get) => {
  const state = get(permissionsAtom);
  return state.pendingRequests.length;
});

/**
 * Derived atom: Permission status for specific app and permission.
 *
 * Returns a function taking appId and permissionId, returns status or 'unknown'.
 *
 * Usage (in components):
 * ```typescript
 * const [getPermStatus] = useAtom(permissionStatusAtom);
 * const status = getPermStatus('app-1', PERMISSION_TYPES.CAMERA);
 * if (status === 'granted') show checkmark
 * ```
 */
export const permissionStatusAtom = atom<
  (appId: string, permId: string) => PermissionStatus
>((get) => {
  const state = get(permissionsAtom);
  return (appId: string, permId: string) => {
    const appSet = state.permissionsByApp[appId];
    if (!appSet) return 'unknown';
    const perm = appSet.permissions.find((p) => p.id === permId);
    return perm?.status ?? 'unknown';
  };
});

/**
 * Action atom: Update app permission set.
 *
 * GIVEN: App ID and permission set
 * WHEN: updateAppPermissionsAtom is called
 * THEN: Stores/updates the permission set for that app
 *
 * Usage (in services):
 * ```typescript
 * const [, updatePerms] = useAtom(updateAppPermissionsAtom);
 * updatePerms(appId, permissionSet);
 * ```
 *
 * @param {string} appId - App identifier
 * @param {AppPermissionSet} permissionSet - Permission set to store
 */
export const updateAppPermissionsAtom = atom<null, [string, AppPermissionSet], void>(
  null,
  (get, set, appId: string, permissionSet: AppPermissionSet) => {
    const state = get(permissionsAtom);
    set(permissionsAtom, {
      ...state,
      permissionsByApp: {
        ...state.permissionsByApp,
        [appId]: permissionSet,
      },
    });
  }
);

/**
 * Action atom: Update single permission status.
 *
 * GIVEN: App ID, permission ID, and new status
 * WHEN: updatePermissionStatusAtom is called
 * THEN: Updates status for that specific permission
 *
 * GIVEN: appId = "app-1", permId = CAMERA, status = "granted"
 * WHEN: Action called
 * THEN: Permission updated, grantedAt set to now
 *
 * Usage (in services):
 * ```typescript
 * const [, updateStatus] = useAtom(updatePermissionStatusAtom);
 * updateStatus(appId, permissionId, 'granted');
 * ```
 *
 * @param {string} appId - App identifier
 * @param {string} permId - Permission identifier
 * @param {PermissionStatus} status - New status
 */
export const updatePermissionStatusAtom = atom<
  null,
  [string, string, PermissionStatus],
  void
>(null, (get, set, appId: string, permId: string, status: PermissionStatus) => {
  const state = get(permissionsAtom);
  const appSet = state.permissionsByApp[appId];

  if (!appSet) return;

  const updatedPermissions = appSet.permissions.map((perm) =>
    perm.id === permId
      ? {
          ...perm,
          status,
          grantedAt: status === 'granted' ? new Date() : perm.grantedAt,
          deniedAt: status === 'denied' ? new Date() : perm.deniedAt,
        }
      : perm
  );

  const grantedCount = updatedPermissions.filter(
    (p) => p.status === 'granted'
  ).length;
  const deniedCount = updatedPermissions.filter((p) => p.status === 'denied')
    .length;

  set(permissionsAtom, {
    ...state,
    permissionsByApp: {
      ...state.permissionsByApp,
      [appId]: {
        ...appSet,
        permissions: updatedPermissions,
        grantedCount,
        deniedCount,
        lastChecked: new Date(),
      },
    },
  });
});

/**
 * Action atom: Add pending permission request.
 *
 * GIVEN: App requesting a permission
 * WHEN: requestPermissionAtom is called
 * THEN: Adds request to pendingRequests, waits for resolution
 *
 * Usage (in services):
 * ```typescript
 * const [, requestPerm] = useAtom(requestPermissionAtom);
 * requestPerm({
 *   id: 'req-1',
 *   appId: 'app-1',
 *   permissionId: CAMERA,
 *   requestedAt: new Date(),
 *   reason: 'Camera needed for video calls'
 * });
 * ```
 *
 * @param {PermissionRequest} request - Permission request
 */
export const requestPermissionAtom = atom<null, [PermissionRequest], void>(
  null,
  (get, set, request: PermissionRequest) => {
    const state = get(permissionsAtom);
    set(permissionsAtom, {
      ...state,
      pendingRequests: [...state.pendingRequests, request],
    });
  }
);

/**
 * Action atom: Resolve permission request (grant).
 *
 * GIVEN: Pending permission request ID
 * WHEN: grantPermissionAtom is called
 * THEN: Moves request from pending to history, updates permission status
 *
 * Usage (in services):
 * ```typescript
 * const [, grant] = useAtom(grantPermissionAtom);
 * grant('req-id-123');
 * ```
 *
 * @param {string} requestId - ID of request to grant
 */
export const grantPermissionAtom = atom<null, [string], void>(
  null,
  (get, set, requestId: string) => {
    const state = get(permissionsAtom);
    const request = state.pendingRequests.find((r) => r.id === requestId);

    if (!request) return;

    const resolvedRequest: PermissionRequest = {
      ...request,
      status: 'granted',
      resolvedAt: new Date(),
    };

    set(permissionsAtom, {
      ...state,
      pendingRequests: state.pendingRequests.filter((r) => r.id !== requestId),
      requestHistory: [...state.requestHistory, resolvedRequest],
    });

    // Also update permission status
    const updateStatusGetter = (get as any)(updatePermissionStatusAtom as any);
    updateStatusGetter(get, set, request.appId, request.permissionId, 'granted');
  }
);

/**
 * Action atom: Resolve permission request (deny).
 *
 * GIVEN: Pending permission request ID and reason
 * WHEN: denyPermissionAtom is called
 * THEN: Moves request to history, updates permission status
 *
 * Usage (in services):
 * ```typescript
 * const [, deny] = useAtom(denyPermissionAtom);
 * deny('req-id-123', 'User declined');
 * ```
 *
 * @param {string} requestId - ID of request to deny
 * @param {string} [reason] - Reason for denial
 */
export const denyPermissionAtom = atom<null, [string, string?], void>(
  null,
  (get, set, requestId: string, reason?: string) => {
    const state = get(permissionsAtom);
    const request = state.pendingRequests.find((r) => r.id === requestId);

    if (!request) return;

    const resolvedRequest: PermissionRequest = {
      ...request,
      status: 'denied',
      resolvedAt: new Date(),
      reason: reason || request.reason,
    };

    set(permissionsAtom, {
      ...state,
      pendingRequests: state.pendingRequests.filter((r) => r.id !== requestId),
      requestHistory: [...state.requestHistory, resolvedRequest],
    });

    // Also update permission status
    const updateStatusGetter = (get as any)(updatePermissionStatusAtom as any);
    updateStatusGetter(get, set, request.appId, request.permissionId, 'denied');
  }
);

/**
 * Action atom: Revoke permission from app.
 *
 * GIVEN: App has been granted permission, user wants to revoke
 * WHEN: revokePermissionAtom is called
 * THEN: Sets permission status to 'denied', records in history
 *
 * Usage (in components):
 * ```typescript
 * const [, revoke] = useAtom(revokePermissionAtom);
 * <TouchableOpacity onPress={() => revoke(appId, permissionId)} />
 * ```
 *
 * @param {string} appId - App identifier
 * @param {string} permId - Permission identifier
 */
export const revokePermissionAtom = atom<null, [string, string], void>(
  null,
  (get, set, appId: string, permId: string) => {
    set((get as any)(updatePermissionStatusAtom as any), [appId, permId, 'denied']);
  }
);

/**
 * Action atom: Load permissions from API.
 *
 * GIVEN: App needs current permission state
 * WHEN: fetchPermissionsAtom is called
 * THEN: Fetches from API, updates permissionsAtom
 *
 * GIVEN: Fetch succeeds
 * WHEN: Promise resolves
 * THEN: All permissions loaded, status set to 'loaded'
 *
 * Usage (in components):
 * ```typescript
 * const [, fetchPerms] = useAtom(fetchPermissionsAtom);
 * useEffect(() => { fetchPerms(); }, []);
 * ```
 *
 * @async
 * @returns {Promise<PermissionsState>} Updated permissions state
 * @throws {Error} If fetch fails
 */
export const fetchPermissionsAtom = atom<null, [], Promise<PermissionsState>>(
  null,
  async (get, set) => {
    const state = get(permissionsAtom);
    set(permissionsAtom, {
      ...state,
      status: 'loading',
      error: null,
    });

    try {
      // TODO: Replace with actual API call
      // const permissions = await guardianApi.getPermissions();

      // Mock implementation
      const mockPermissionsByApp: Record<string, AppPermissionSet> = {
        'app-1': {
          appId: 'app-1',
          appName: 'Chrome',
          permissions: [
            {
              id: PERMISSION_TYPES.CAMERA,
              name: 'Camera',
              description: 'Access to device camera',
              riskLevel: 'dangerous',
              status: 'granted',
              grantedAt: new Date(),
            },
            {
              id: PERMISSION_TYPES.LOCATION,
              name: 'Location',
              description: 'Access to device location',
              riskLevel: 'dangerous',
              status: 'denied',
              deniedAt: new Date(),
              denialReason: 'Not needed for browsing',
            },
            {
              id: PERMISSION_TYPES.INTERNET,
              name: 'Internet',
              description: 'Access to network',
              riskLevel: 'normal',
              status: 'granted',
              grantedAt: new Date(),
            },
          ],
          grantedCount: 2,
          deniedCount: 1,
          lastChecked: new Date(),
        },
      };

      const updatedState: PermissionsState = {
        permissionsByApp: mockPermissionsByApp,
        pendingRequests: [],
        requestHistory: [],
        status: 'loaded',
        error: null,
      };

      set(permissionsAtom, updatedState);
      return updatedState;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to fetch permissions';
      set(permissionsAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });
      throw error;
    }
  }
);

/**
 * Action atom: Clear permissions error.
 *
 * GIVEN: Error message is displayed
 * WHEN: User dismisses error
 * THEN: clearPermissionsErrorAtom clears error message
 *
 * Usage (in components):
 * ```typescript
 * const [, clearError] = useAtom(clearPermissionsErrorAtom);
 * <TouchableOpacity onPress={() => clearError()} />
 * ```
 */
export const clearPermissionsErrorAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(permissionsAtom);
    set(permissionsAtom, {
      ...state,
      error: null,
    });
  }
);
