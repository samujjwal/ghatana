/**
 * Tenant state atom — stores the currently selected tenant ID.
 *
 * All pages read from this atom instead of hardcoding 'default',
 * enabling multi-tenant support without per-page state duplication.
 *
 * @doc.type store
 * @doc.purpose Global tenant selection state
 * @doc.layer frontend
 */
import { atom } from 'jotai';

/**
 * The active tenant ID used by all AEP API calls.
 * Defaults to 'default' and can be changed via a TenantSelector component.
 */
export const tenantIdAtom = atom<string>('default');
