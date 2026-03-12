/**
 * PlatformShell — root shell component for the Ghatana platform.
 *
 * Provides:
 *   - Jotai `Provider` (isolates atom scope from any parent app)
 *   - `NavBar` with logo, tenant switcher, notification centre, user avatar
 *   - Content area rendered via `children` (connect your router `<Outlet />` here)
 *
 * Usage with React Router v6:
 * ```tsx
 * import { PlatformShell } from '@ghatana/platform-shell';
 *
 * function App() {
 *   return (
 *     <BrowserRouter>
 *       <Routes>
 *         <Route element={<PlatformShell onHome={() => navigate('/')} />}>
 *           <Route index element={<ProductPicker onNavigate={navigate} />} />
 *           <Route path="/aep/*" element={<AepShell />} />
 *           <Route path="/data-cloud/*" element={<DataCloudShell />} />
 *         </Route>
 *       </Routes>
 *     </BrowserRouter>
 *   );
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose Root shell: Jotai Provider + NavBar + content slot
 * @doc.layer shared
 * @doc.pattern CompositeComponent
 */
import React from 'react';
import { Provider as JotaiProvider } from 'jotai';
import { NavBar } from './NavBar';

export interface PlatformShellProps {
  /**
   * Current product name shown in the nav bar (e.g. `"AEP"`).
   * Pass `undefined` at the root product-picker route.
   */
  productName?: string;
  /**
   * Called when the user clicks the Ghatana logo / home link.
   */
  onHome?: () => void;
  /**
   * Product-specific toolbar content rendered in the centre of the nav bar.
   */
  navActions?: React.ReactNode;
  /**
   * Page content — connect your router `<Outlet />` here.
   */
  children?: React.ReactNode;
}

/**
 * Top-level shell component.
 *
 * Wraps the entire application in a Jotai `Provider` so that tenant, auth,
 * and notification atoms are isolated from any outer atom scope.
 */
function PlatformShellInner({
  productName,
  onHome,
  navActions,
  children,
}: Omit<PlatformShellProps, never>) {
  return (
    <div className="flex flex-col h-screen w-full overflow-hidden bg-gray-50 dark:bg-gray-950">
      <NavBar productName={productName} onHome={onHome}>
        {navActions}
      </NavBar>

      {/* Main content area */}
      <div className="flex-1 overflow-auto">
        {children}
      </div>
    </div>
  );
}

/**
 * PlatformShell with its own Jotai Provider scope.
 *
 * Use this as the entry-point component in your application.
 */
export function PlatformShell(props: PlatformShellProps) {
  return (
    <JotaiProvider>
      <PlatformShellInner {...props} />
    </JotaiProvider>
  );
}
