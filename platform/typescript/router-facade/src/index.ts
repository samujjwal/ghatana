/**
 * Kernel Router Facade
 * 
 * Provides a facade over react-router-dom to allow products to use routing
 * without depending directly on react-router-dom. This enables easier
 * migration to alternative routing solutions if needed.
 * 
 * @doc.type module
 * @doc.purpose Kernel router facade for product routing
 * @doc.layer platform
 * @doc.pattern Facade
 */

// Re-export commonly used react-router-dom types and components
export type {
  NavigateFunction,
  NavigateOptions,
  Location,
  Path,
  Params,
  RouteObject,
} from 'react-router-dom';

export {
  // Components
  Router,
  Routes,
  Route,
  Navigate,
  Outlet,
  Link,
  NavLink,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
  useRoutes,
  // Hooks
  createBrowserRouter,
  createMemoryRouter,
  createHashRouter,
} from 'react-router-dom';

/**
 * Kernel router facade that wraps react-router-dom
 * Products should use this facade instead of directly importing react-router-dom
 */
export class KernelRouterFacade {
  /**
   * Creates a router instance with Kernel-specific configuration
   */
  static createRouter(routes: RouteObject[], options?: any) {
    return createBrowserRouter(routes, options);
  }
}
