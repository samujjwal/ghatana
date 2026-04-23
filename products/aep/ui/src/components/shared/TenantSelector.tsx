/**
 * TenantSelector — validated tenant chooser for the sidebar.
 *
 * Shows the current tenant scope prominently. Provides a dropdown of
 * recently-used tenants plus an "Other…" option for manual entry.
 * Switches require explicit confirmation to prevent accidental
 * cross-tenant navigation.
 *
 * On every tenant switch, all TanStack Query caches are invalidated so
 * each page re-fetches its data under the new tenant context.
 *
 * @doc.type component
 * @doc.purpose Safe tenant switching with validation and confirmation
 * @doc.layer frontend
 */
import React, { useState, useEffect } from 'react';
import { useAtom } from 'jotai';
import { useQueryClient } from '@tanstack/react-query';
import { tenantIdAtom } from '@/stores/tenant.store';
import { Button } from '@ghatana/design-system';

const RECENT_TENANTS_KEY = 'aep-recent-tenants';
const MAX_RECENT = 5;

function getRecentTenants(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_TENANTS_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((t: unknown) => typeof t === 'string' && t.length > 0) : [];
  } catch {
    return [];
  }
}

function saveRecentTenant(id: string): void {
  const current = getRecentTenants().filter((t) => t !== id);
  const next = [id, ...current].slice(0, MAX_RECENT);
  localStorage.setItem(RECENT_TENANTS_KEY, JSON.stringify(next));
}

function isValidTenantId(id: string): boolean {
  // Allow alphanumeric, hyphens, underscores; min 1, max 64 chars
  return /^[a-zA-Z0-9_-]{1,64}$/.test(id);
}

export function TenantSelector() {
  const [tenantId, setTenantId] = useAtom(tenantIdAtom);
  const [isOpen, setIsOpen] = useState(false);
  const [customValue, setCustomValue] = useState('');
  const [confirmTenant, setConfirmTenant] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();

  // Seed recent tenants with current tenant on first mount
  useEffect(() => {
    saveRecentTenant(tenantId);
  }, []);

  const recent = getRecentTenants();
  const options = Array.from(new Set([tenantId, ...recent])).filter(Boolean);

  function doSwitch(nextId: string) {
    if (nextId === tenantId) {
      setIsOpen(false);
      return;
    }
    setTenantId(nextId);
    saveRecentTenant(nextId);
    queryClient.invalidateQueries();
    setIsOpen(false);
    setCustomValue('');
    setError(null);
  }

  function handleSelect(value: string) {
    if (value === '__custom__') {
      const trimmed = customValue.trim();
      if (!trimmed) {
        setError('Enter a tenant ID');
        return;
      }
      if (!isValidTenantId(trimmed)) {
        setError('Tenant ID must be 1–64 characters: letters, numbers, hyphens, underscores');
        return;
      }
      setConfirmTenant(trimmed);
      setError(null);
      return;
    }
    doSwitch(value);
  }

  return (
    <div className="mx-3 mt-1 relative">
      {/* Current tenant display */}
      <button
        type="button"
        onClick={() => {
          setIsOpen((prev) => !prev);
          setError(null);
        }}
        className="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-md text-xs border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
        aria-haspopup="listbox"
        aria-expanded={isOpen}
      >
        <span className="flex-shrink-0 font-semibold text-gray-500 dark:text-gray-400 uppercase text-[10px]">
          Tenant
        </span>
        <span className="truncate font-mono text-gray-800 dark:text-gray-200 font-medium">
          {tenantId}
        </span>
        <svg
          className="ml-auto h-3 w-3 text-gray-400 flex-shrink-0"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* Dropdown */}
      {isOpen && (
        <div className="absolute bottom-full left-0 right-0 mb-1 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-50 overflow-hidden">
          <div className="py-1" role="listbox">
            {options.map((id) => (
              <button
                key={id}
                type="button"
                role="option"
                aria-selected={id === tenantId}
                onClick={() => handleSelect(id)}
                className={[
                  'w-full text-left px-3 py-1.5 text-xs font-mono transition-colors',
                  id === tenantId
                    ? 'bg-indigo-50 dark:bg-indigo-950 text-indigo-700 dark:text-indigo-300 font-semibold'
                    : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800',
                ].join(' ')}
              >
                {id}
                {id === tenantId && (
                  <span className="ml-1.5 text-[10px] text-gray-400 font-normal">(current)</span>
                )}
              </button>
            ))}

            <div className="border-t border-gray-100 dark:border-gray-800 my-1" />

            <div className="px-3 py-1.5">
              <input
                type="text"
                value={customValue}
                onChange={(e) => {
                  setCustomValue(e.target.value);
                  setError(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleSelect('__custom__');
                  }
                }}
                placeholder="Other tenant…"
                className="w-full px-2 py-1 text-xs rounded border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-800 dark:text-gray-200 placeholder:text-gray-400 focus:outline-none focus:border-indigo-400 font-mono"
              />
              <button
                type="button"
                onClick={() => handleSelect('__custom__')}
                className="mt-1 w-full px-2 py-1 text-[10px] rounded bg-indigo-600 text-white hover:bg-indigo-700 transition-colors"
              >
                Switch to custom tenant
              </button>
              {error && (
                <p className="mt-1 text-[10px] text-red-600 dark:text-red-400">{error}</p>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Confirmation dialog */}
      {confirmTenant && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-[100]">
          <div className="bg-white dark:bg-gray-900 rounded-xl p-5 max-w-xs w-full mx-4 shadow-xl">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-2">
              Switch tenant?
            </h3>
            <p className="text-xs text-gray-500 mb-4">
              You are about to switch from{' '}
              <span className="font-mono text-gray-700 dark:text-gray-300">{tenantId}</span> to{' '}
              <span className="font-mono text-gray-700 dark:text-gray-300">{confirmTenant}</span>.
              All cached data will be refreshed.
            </p>
            <div className="flex gap-2 justify-end">
              <Button
                onClick={() => {
                  setConfirmTenant(null);
                  setCustomValue('');
                }}
                variant="secondary"
                className="text-xs"
              >
                Cancel
              </Button>
              <Button
                onClick={() => {
                  const next = confirmTenant;
                  setConfirmTenant(null);
                  doSwitch(next);
                }}
                variant="primary"
                className="text-xs"
              >
                Confirm switch
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
