export interface Permissions {
  canViewAdmin: boolean;
  canEditConfig: boolean;
  // Add other permissions as needed
}

export const usePermissions: () => Permissions;

export interface AuthStore {
  isAuthenticated: boolean;
  user: {
    id: string;
    email: string;
    roles: string[];
  } | null;
  login: (credentials: { email: string; password: string }) => Promise<void>;
  logout: () => void;
  hasPermission: (permission: string) => boolean;
}

export const useAuthStore: () => AuthStore;
