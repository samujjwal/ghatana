/**
 * Navigation Service Interface
 * 
 * @doc.type interface
 * @doc.purpose Navigation state management and route resolution
 * @doc.layer ui
 * @doc.pattern Service
 */
export interface NavigationService {
  /** Get current route path */
  getCurrentPath(): string;
  
  /** Navigate to route */
  navigate(path: string, options?: NavigateOptions): Promise<void>;
  
  /** Check if user has access to route */
  checkRouteAccess(route: string, userPermissions: string[]): boolean;
  
  /** Get navigation items for user role */
  getNavigationItems(role: UserRole): NavigationItem[];
  
  /** Get breadcrumbs for current path */
  getBreadcrumbs(path: string): BreadcrumbItem[];
  
  /** Resolve route parameters */
  resolveRouteParams(path: string): Record<string, string>;
  
  /** Subscribe to navigation changes */
  subscribe(callback: (event: NavigationEvent) => void): () => void;
}

/** Navigation options */
export interface NavigateOptions {
  replace?: boolean;
  state?: Record<string, unknown>;
  queryParams?: Record<string, string>;
}

/** User role type */
export type UserRole = 
  | 'admin' 
  | 'editor' 
  | 'viewer' 
  | 'developer' 
  | 'guest';

/** Navigation item */
export interface NavigationItem {
  id: string;
  label: string;
  icon?: string;
  route: string;
  children?: NavigationItem[];
  requiredPermission?: string;
  badge?: number;
  exact?: boolean;
}

/** Breadcrumb item */
export interface BreadcrumbItem {
  label: string;
  route?: string;
  icon?: string;
}

/** Navigation event */
export interface NavigationEvent {
  type: 'push' | 'pop' | 'replace';
  from: string;
  to: string;
  timestamp: number;
}
