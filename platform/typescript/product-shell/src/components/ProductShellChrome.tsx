/**
 * Shared product shell chrome primitives.
 *
 * @doc.type component
 * @doc.purpose Reusable footer and header chrome for product shell wrappers
 * @doc.layer platform
 * @doc.pattern Composable UI Primitive
 */
import React from 'react';
import type { ProductShellConfig } from '../types';

export interface ProductShellFooterProps {
  readonly eyebrow?: string;
  readonly description: React.ReactNode;
  readonly className?: string;
}

export function ProductShellFooter({
  eyebrow = 'Kernel shell',
  description,
  className,
}: ProductShellFooterProps): React.ReactElement {
  const classes = className
    ?? 'rounded-lg bg-slate-900/80 p-4 text-sm text-slate-100';

  return (
    <div className={classes}>
      <p className="eyebrow">{eyebrow}</p>
      <p className="m-0">{description}</p>
    </div>
  );
}

export interface ProductHeaderUserMenuProps {
  readonly displayName?: string | null;
  readonly email?: string | null;
  readonly fallbackLabel?: string;
  readonly logoutLabel?: string;
  readonly onLogout?: () => void;
  readonly className?: string;
}

export function ProductHeaderUserMenu({
  displayName,
  email,
  fallbackLabel = 'Signed in',
  logoutLabel = 'Logout',
  onLogout,
  className,
}: ProductHeaderUserMenuProps): React.ReactElement {
  const label = displayName || email || fallbackLabel;
  const classes = className ?? 'flex items-center gap-3';

  return (
    <div className={classes}>
      <span className="hidden text-sm text-slate-600 sm:inline">
        {label}
      </span>
      {onLogout && (
        <button
          type="button"
          onClick={onLogout}
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
        >
          {logoutLabel}
        </button>
      )}
    </div>
  );
}

export type ProductRoleSelectorConfig = Pick<
  ProductShellConfig,
  | 'roleOrder'
  | 'roleLabels'
  | 'roleDescriptions'
  | 'availableRoles'
  | 'roleSelectorTitle'
  | 'roleSelectorLabel'
  | 'roleSelectorDisclosureNote'
  | 'onRoleChange'
>;

export function createProductRoleSelectorConfig(
  config: ProductRoleSelectorConfig,
): ProductRoleSelectorConfig {
  return config;
}
