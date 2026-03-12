/**
 * TenantSelector — dropdown to switch the active tenant.
 *
 * Reads `availableTenantsAtom` and writes to `tenantAtom`.
 * Renders a controlled `<select>` accessible to keyboard and screen readers.
 *
 * @doc.type component
 * @doc.purpose Tenant context switcher for the platform shell navigation bar
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React from 'react';
import { useAtom, useAtomValue } from 'jotai';
import { tenantAtom, availableTenantsAtom, type Tenant } from '../atoms/tenantAtom';

export interface TenantSelectorProps {
  /** CSS class to apply to the select element. */
  className?: string;
}

/**
 * Tenant context selector.
 *
 * Falls back to a read-only badge when `availableTenantsAtom` is empty.
 */
export function TenantSelector({ className }: TenantSelectorProps) {
  const [tenantId, setTenantId] = useAtom(tenantAtom);
  const tenants = useAtomValue(availableTenantsAtom);

  if (tenants.length === 0) {
    return (
      <span
        className={[
          'text-xs font-mono px-2 py-1 rounded bg-gray-100 dark:bg-gray-800',
          'text-gray-600 dark:text-gray-300',
          className ?? '',
        ].join(' ')}
        aria-label={`Current tenant: ${tenantId}`}
      >
        {tenantId}
      </span>
    );
  }

  return (
    <select
      aria-label="Select tenant"
      value={tenantId}
      onChange={(e) => setTenantId(e.target.value)}
      className={[
        'text-sm rounded border border-gray-200 dark:border-gray-700',
        'bg-white dark:bg-gray-900 text-gray-800 dark:text-gray-100',
        'px-2 py-1 focus:outline-none focus:ring-2 focus:ring-indigo-500',
        className ?? '',
      ].join(' ')}
    >
      {tenants.map((t: Tenant) => (
        <option key={t.id} value={t.id}>
          {t.name}
        </option>
      ))}
    </select>
  );
}
