/**
 * Jotai atoms for tenant context.
 *
 * The `tenantAtom` is the single source of truth for the selected tenant ID
 * across all product shells. Product-level pages derive their tenant from this
 * atom via `useAtomValue(tenantAtom)`.
 *
 * @doc.type atoms
 * @doc.purpose Tenant context state atoms
 * @doc.layer shared
 */
import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

/** Currently selected tenant ID. Persisted in localStorage. */
export const tenantAtom = atomWithStorage<string>('ghatana:tenantId', 'default');

/** Derived: whether a real tenant (non-default) is selected. */
export const hasRealTenantAtom = atom((get) => get(tenantAtom) !== 'default');

/**
 * Known tenants available for the current user.
 * Populated from the auth token's `tenants` claim on login.
 */
export const availableTenantsAtom = atom<Tenant[]>([]);

export interface Tenant {
  id: string;
  name: string;
  /** Optional Tailwind color for the tenant badge, e.g. 'indigo'. */
  color?: string;
}
