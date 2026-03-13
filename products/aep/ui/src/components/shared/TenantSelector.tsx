/**
 * TenantSelector — inline editable tenant ID control for the sidebar.
 *
 * Reads and writes the shared `tenantIdAtom`. Clicking the label opens
 * an inline input. On blur or Enter the new ID is committed.
 *
 * @doc.type component
 * @doc.purpose Allow users to switch tenants from the navigation sidebar
 * @doc.layer frontend
 */
import React, { useRef, useState } from 'react';
import { useAtom } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';

export function TenantSelector() {
  const [tenantId, setTenantId] = useAtom(tenantIdAtom);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(tenantId);
  const inputRef = useRef<HTMLInputElement>(null);

  function submit() {
    const value = draft.trim();
    if (value) setTenantId(value);
    setEditing(false);
  }

  if (editing) {
    return (
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
        className="px-3 mt-1"
      >
        <input
          ref={inputRef}
          autoFocus
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onBlur={submit}
          onKeyDown={(e) => e.key === 'Escape' && setEditing(false)}
          className="w-full text-xs rounded border border-indigo-400 bg-white dark:bg-gray-800 px-2 py-1 outline-none"
          aria-label="Tenant ID"
        />
      </form>
    );
  }

  return (
    <button
      onClick={() => {
        setDraft(tenantId);
        setEditing(true);
      }}
      title="Click to change tenant"
      className="mx-3 mt-1 flex items-center gap-1.5 px-2 py-1 rounded text-xs text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 border border-dashed border-gray-300 dark:border-gray-700 w-[calc(100%-1.5rem)] truncate"
    >
      <span className="flex-shrink-0 font-medium text-gray-400 uppercase tracking-wide">
        Tenant
      </span>
      <span className="truncate text-gray-700 dark:text-gray-300 font-mono">{tenantId}</span>
    </button>
  );
}
