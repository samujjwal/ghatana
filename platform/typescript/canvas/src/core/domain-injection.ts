/**
 * Canvas Domain Injection API
 *
 * Provides a factory for products to register their domain-specific
 * types and configurations without polluting the platform canvas.
 *
 * Products should create their own domain config by extending this:
 *   const config = createCanvasDomainConfig({ phases: [...], roles: [...] });
 *
 * @doc.type factory
 * @doc.purpose Domain-agnostic canvas configuration injection
 * @doc.layer platform
 * @doc.pattern Factory
 */

import { atom, type Atom } from 'jotai';

// ============================================================================
// Generic Domain Types
// ============================================================================

export interface DomainPhase {
  id: string;
  label: string;
  primary: string;
  background: string;
  text: string;
}

export interface DomainRole {
  id: string;
  displayName: string;
  icon: string;
  color: string;
}

export interface CanvasDomainConfig<
  TPhase extends string = string,
  TRole extends string = string,
> {
  /** Available lifecycle phases for this product */
  phases: readonly DomainPhase[];
  /** Available persona roles for this product */
  roles: readonly DomainRole[];
  /** Default phase */
  defaultPhase: TPhase;
  /** Default roles */
  defaultRoles: TRole[];
  /** Jotai atoms for domain state */
  atoms: {
    currentPhase: Atom<TPhase>;
    activeRoles: Atom<TRole[]>;
  };
  /** Get phase colors by ID */
  getPhaseColors(phaseId: TPhase): { primary: string; background: string; text: string };
  /** Get role config by ID */
  getRoleConfig(roleId: TRole): DomainRole | undefined;
}

// ============================================================================
// Factory
// ============================================================================

export interface CreateCanvasDomainConfigOptions<
  TPhase extends string = string,
  TRole extends string = string,
> {
  phases: readonly DomainPhase[];
  roles: readonly DomainRole[];
  defaultPhase: TPhase;
  defaultRoles: TRole[];
}

export function createCanvasDomainConfig<
  TPhase extends string = string,
  TRole extends string = string,
>(options: CreateCanvasDomainConfigOptions<TPhase, TRole>): CanvasDomainConfig<TPhase, TRole> {
  const { phases, roles, defaultPhase, defaultRoles } = options;

  const phaseMap = new Map(phases.map((p) => [p.id, p]));
  const roleMap = new Map(roles.map((r) => [r.id, r]));

  return {
    phases,
    roles,
    defaultPhase,
    defaultRoles,
    atoms: {
      currentPhase: atom<TPhase>(defaultPhase),
      activeRoles: atom<TRole[]>(defaultRoles),
    },
    getPhaseColors(phaseId: TPhase) {
      const phase = phaseMap.get(phaseId);
      return phase
        ? { primary: phase.primary, background: phase.background, text: phase.text }
        : { primary: '#666', background: '#f5f5f5', text: '#333' };
    },
    getRoleConfig(roleId: TRole) {
      return roleMap.get(roleId);
    },
  };
}
