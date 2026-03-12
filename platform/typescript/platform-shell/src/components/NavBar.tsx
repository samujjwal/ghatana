/**
 * NavBar — top navigation bar for the Ghatana platform shell.
 *
 * Composes:
 *   - Ghatana logo + product breadcrumb
 *   - Optional product-specific actions slot (`children`)
 *   - `TenantSelector`
 *   - `NotificationCenter`
 *   - User avatar / email display
 *
 * @doc.type component
 * @doc.purpose Top-level navigation bar composed from shell sub-components
 * @doc.layer shared
 * @doc.pattern CompositeComponent
 */
import React from 'react';
import { useAtomValue } from 'jotai';
import { currentUserEmailAtom, isAuthenticatedAtom } from '../atoms/authAtom';
import { TenantSelector } from './TenantSelector';
import { NotificationCenter } from './NotificationCenter';

export interface NavBarProps {
  /** Product name displayed next to the logo (e.g. "AEP"). */
  productName?: string;
  /**
   * Called when the user clicks the Ghatana logo / home link.
   * Typically navigates to `/`.
   */
  onHome?: () => void;
  /**
   * Product-specific toolbar actions (buttons, tabs, etc.).
   * Rendered between the logo area and the right-hand utilities.
   */
  children?: React.ReactNode;
}

/**
 * Fixed-height top navigation bar.
 *
 * Intended to be rendered once per `PlatformShell`.
 */
export function NavBar({ productName, onHome, children }: NavBarProps) {
  const userEmail = useAtomValue(currentUserEmailAtom);
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);

  return (
    <header className="h-14 flex items-center px-4 gap-3 bg-white dark:bg-gray-950 border-b border-gray-200 dark:border-gray-800 shadow-sm z-40">
      {/* Logo + product */}
      <button
        onClick={onHome}
        className="flex items-center gap-2 text-gray-900 dark:text-white hover:opacity-80 transition-opacity focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded"
        aria-label="Go to home"
      >
        {/* G-mark inline SVG — no external dep */}
        <svg
          width="28"
          height="28"
          viewBox="0 0 32 32"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden
        >
          <rect width="32" height="32" rx="8" fill="#6366F1" />
          <path
            d="M22 16.5h-5.5V13H22V10H10v12h12v-5.5z"
            fill="white"
            fillOpacity="0.9"
          />
        </svg>
        <span className="font-semibold text-sm tracking-tight">Ghatana</span>
      </button>

      {productName && (
        <>
          <span className="text-gray-300 dark:text-gray-600" aria-hidden>/</span>
          <span className="text-sm font-medium text-indigo-600 dark:text-indigo-400">
            {productName}
          </span>
        </>
      )}

      {/* Product slot */}
      {children && (
        <div className="flex-1 flex items-center gap-2 mx-2">
          {children}
        </div>
      )}

      {/* Spacer */}
      {!children && <div className="flex-1" />}

      {/* Right-hand utilities */}
      <div className="flex items-center gap-2">
        <TenantSelector />
        <NotificationCenter />

        {isAuthenticated && userEmail && (
          <div
            aria-label={`Signed in as ${userEmail}`}
            title={userEmail}
            className="flex items-center justify-center h-8 w-8 rounded-full bg-indigo-100 dark:bg-indigo-900 text-indigo-700 dark:text-indigo-300 text-xs font-bold select-none"
          >
            {userEmail.charAt(0).toUpperCase()}
          </div>
        )}
      </div>
    </header>
  );
}
