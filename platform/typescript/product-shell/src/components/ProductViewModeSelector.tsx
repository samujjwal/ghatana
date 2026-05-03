/**
 * ProductViewModeSelector — role/mode disclosure selector dropdown.
 *
 * Renders the role/mode selector button and dropdown panel. This is a pure
 * UI disclosure control; it does not grant permissions. Products are responsible
 * for persisting the selection and updating backend-visible state.
 *
 * Displays:
 * - A button showing the current role label
 * - A dropdown with all available roles and their descriptions
 * - A disclosure note explaining this is not an authorization control
 *
 * @doc.type component
 * @doc.purpose Role/mode disclosure selector for product shell header
 * @doc.layer platform
 * @doc.pattern Molecule
 */
import React, { useState, useRef, useEffect, useCallback } from 'react';
import { ChevronDown } from 'lucide-react';
import type { ProductShellConfig } from '../types';

interface ProductViewModeSelectorProps {
  config: Pick<
    ProductShellConfig,
    | 'currentRole'
    | 'availableRoles'
    | 'roleLabels'
    | 'roleDescriptions'
    | 'roleSelectorTitle'
    | 'roleSelectorLabel'
    | 'roleSelectorDisclosureNote'
    | 'onRoleChange'
  >;
}

function cn(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ');
}

/**
 * Disclosure selector for role/view mode.
 *
 * Renders a button + dropdown panel listing all available roles. Selecting a
 * role calls `config.onRoleChange`. The disclosure note is always shown to
 * remind users this is a UI-only control.
 */
export function ProductViewModeSelector({ config }: ProductViewModeSelectorProps): React.ReactElement {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const title = config.roleSelectorTitle ?? 'View mode';
  const ariaLabel = config.roleSelectorLabel ?? 'View mode menu';
  const disclosureNote =
    config.roleSelectorDisclosureNote ??
    'Changing view mode changes which surfaces are visible. It does not grant or remove backend permissions.';

  const currentLabel =
    config.roleLabels?.[config.currentRole] ?? config.currentRole;

  const roles = config.availableRoles ?? (config.roleLabels ? Object.keys(config.roleLabels) : [config.currentRole]);

  const handleSelect = useCallback(
    (role: string) => {
      config.onRoleChange?.(role);
      setIsOpen(false);
    },
    [config]
  );

  // Close on outside click
  useEffect(() => {
    if (!isOpen) return;

    function handleClickOutside(event: MouseEvent): void {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  // Close on Escape
  useEffect(() => {
    if (!isOpen) return;

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-label={ariaLabel}
        onClick={() => setIsOpen((prev) => !prev)}
        className="flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-700"
      >
        <span>{currentLabel}</span>
        <ChevronDown className="h-4 w-4 shrink-0" aria-hidden="true" />
      </button>

      {isOpen && (
        <div
          role="listbox"
          aria-label={ariaLabel}
          className="absolute right-0 mt-2 w-72 rounded-xl border border-gray-200 bg-white p-2 shadow-lg dark:border-gray-700 dark:bg-gray-800 z-50"
        >
          <div className="px-2 pb-2 pt-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
              {title}
            </p>
          </div>

          <div className="space-y-1">
            {roles.map((role) => {
              const isSelected = role === config.currentRole;
              const label = config.roleLabels?.[role] ?? role;
              const description = config.roleDescriptions?.[role];

              return (
                <button
                  key={role}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => handleSelect(role)}
                  className={cn(
                    'w-full rounded-lg px-3 py-2 text-left text-sm transition-colors',
                    isSelected
                      ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300'
                      : 'text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-700'
                  )}
                >
                  <div className="font-medium">{label}</div>
                  {description && (
                    <div className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                      {description}
                    </div>
                  )}
                </button>
              );
            })}
          </div>

          <div className="mt-2 border-t border-gray-100 dark:border-gray-700 px-2 pt-2 pb-1">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {disclosureNote}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
