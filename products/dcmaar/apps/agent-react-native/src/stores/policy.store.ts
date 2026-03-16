/**
 * Policy Management Store - Jotai Atoms
 *
 * Manages parental control policies including:
 * - Policy CRUD operations
 * - Active policy management
 * - Policy application tracking
 * - Policy validation and enforcement
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose Policy management state
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';
import { guardianApi } from '../services/guardianApi';
import { authAtom } from './auth.store';

/**
 * Policy constraints.
 *
 * @interface PolicyConstraints
 * @property {string[]} restrictedApps - Apps that are blocked
 * @property {string[]} allowedCategories - Allowed app categories
 * @property {number} maxScreenTime - Maximum daily screen time (minutes)
 * @property {string[]} bedtimeStart - Bedtime start (HH:MM format)
 * @property {string[]} bedtimeEnd - Bedtime end (HH:MM format)
 * @property {boolean} allowContentRating - Allow mature content
 */
export interface PolicyConstraints {
  restrictedApps: string[];
  allowedCategories: string[];
  maxScreenTime: number;
  bedtimeStart: string;
  bedtimeEnd: string;
  allowContentRating: boolean;
}

/**
 * Policy object representing a parental control policy.
 *
 * @interface Policy
 * @property {string} id - Unique policy identifier
 * @property {string} name - Policy display name
 * @property {string} description - Policy description
 * @property {PolicyConstraints} constraints - Policy constraints
 * @property {'active' | 'inactive' | 'pending'} status - Policy status
 * @property {Date} createdAt - Creation timestamp
 * @property {Date} updatedAt - Last update timestamp
 * @property {string} version - Policy version
 */
export interface Policy {
  id: string;
  name: string;
  description: string;
  constraints: PolicyConstraints;
  status: 'active' | 'inactive' | 'pending';
  createdAt: Date;
  updatedAt: Date;
  version: string;
}

/**
 * Policy management state.
 *
 * @interface PolicyState
 * @property {Policy[]} policies - List of all policies
 * @property {string | null} activePolicyId - ID of currently active policy
 * @property {'idle' | 'loading' | 'loaded' | 'error'} status - Loading status
 * @property {string | null} error - Error message if operation failed
 */
export interface PolicyState {
  policies: Policy[];
  activePolicyId: string | null;
  status: 'idle' | 'loading' | 'loaded' | 'error';
  error: string | null;
}

/**
 * Initial policy state.
 *
 * GIVEN: App initialization
 * WHEN: policyAtom is first accessed
 * THEN: Policy list starts empty with no active policy
 */
const initialPolicyState: PolicyState = {
  policies: [],
  activePolicyId: null,
  status: 'idle',
  error: null,
};

/**
 * Core policy atom.
 *
 * Holds complete policy management state including:
 * - List of all policies
 * - Currently active policy
 * - Loading and error state
 *
 * Usage (in components):
 * `const [policyState, setPolicyState] = useAtom(policyAtom);`
 */
export const policyAtom = atom<PolicyState>(initialPolicyState);

/**
 * Derived atom: Currently active policy.
 *
 * GIVEN: policyAtom with policies and activePolicyId
 * WHEN: activePolicyAtom is read
 * THEN: Returns the policy object matching activePolicyId, or null
 *
 * Usage (in components):
 * `const [activePolicy] = useAtom(activePolicyAtom);`
 * If activePolicy exists, display policy details
 */
export const activePolicyAtom = atom<Policy | null>((get) => {
  const state = get(policyAtom);
  if (!state.activePolicyId) return null;
  return (
    state.policies.find((p) => p.id === state.activePolicyId) || null
  );
});

/**
 * Derived atom: Total number of policies.
 *
 * GIVEN: policyAtom with policies list
 * WHEN: policyCountAtom is read
 * THEN: Returns total count of policies
 *
 * Usage (in components):
 * `const [count] = useAtom(policyCountAtom);`
 * Show "3 policies" in UI
 */
export const policyCountAtom = atom<number>((get) => {
  return get(policyAtom).policies.length;
});

/**
 * Derived atom: Is a policy active?
 *
 * GIVEN: policyAtom with optional activation
 * WHEN: isPolicyAppliedAtom is read
 * THEN: Returns true if policy is active
 *
 * Usage (in components):
 * `const [isApplied] = useAtom(isPolicyAppliedAtom);`
 * Show policy enforcement status in UI
 */
export const isPolicyAppliedAtom = atom<boolean>((get) => {
  const state = get(policyAtom);
  return state.activePolicyId !== null && state.status === 'loaded';
});

/**
 * Derived atom: List of active/inactive policies.
 *
 * GIVEN: policyAtom with mixed policy statuses
 * WHEN: activePoliciesAtom is read
 * THEN: Returns only policies with status === 'active'
 *
 * Usage (in components):
 * `const [activePolicies] = useAtom(activePoliciesAtom);`
 * Show only policies available for selection
 */
export const activePoliciesAtom = atom<Policy[]>((get) => {
  return get(policyAtom).policies.filter((p) => p.status === 'active');
});

/**
 * Action atom: Create a new policy.
 *
 * GIVEN: Policy data to create
 * WHEN: createPolicyAtom action is called
 * THEN: Adds new policy to policyAtom
 *       Calls backend to persist
 *       Sets error if creation fails
 *
 * Usage (in components):
 * `const [, createPolicy] = useAtom(createPolicyAtom);`
 * await createPolicy({ name: 'School Hours', ... });
 */
export const createPolicyAtom = atom<
  null,
  [Omit<Policy, 'id' | 'createdAt' | 'updatedAt' | 'version'>],
  Promise<Policy>
>(
  null,
  async (get, set, policyData) => {
    const state = get(policyAtom);

    set(policyAtom, {
      ...state,
      status: 'loading',
      error: null,
    });

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      const apiPolicy = await guardianApi.createPolicy(tenantId, {
        name: policyData.name,
        description: policyData.description,
        type: policyData.status === 'active' ? 'RESTRICTIVE' : 'RECOMMENDATION',
        rules: policyData.constraints as unknown as Record<string, unknown>,
        enabled: policyData.status === 'active',
        appliesTo: policyData.constraints.restrictedApps,
      });

      const newPolicy: Policy = {
        id: apiPolicy.id,
        name: apiPolicy.name,
        description: apiPolicy.description,
        constraints: policyData.constraints,
        status: apiPolicy.enabled ? 'active' : 'inactive',
        createdAt: new Date(apiPolicy.createdAt),
        updatedAt: new Date(apiPolicy.updatedAt),
        version: '1.0.0',
      };

      set(policyAtom, {
        ...state,
        policies: [...state.policies, newPolicy],
        status: 'loaded',
        error: null,
      });

      return newPolicy;
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to create policy';

      set(policyAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Update an existing policy.
 *
 * GIVEN: Policy ID and updated data
 * WHEN: updatePolicyAtom action is called
 * THEN: Updates policy in policyAtom
 *       Calls backend to persist
 *
 * Usage (in components):
 * `const [, updatePolicy] = useAtom(updatePolicyAtom);`
 * await updatePolicy('policy-123', { name: 'Updated Name' });
 */
export const updatePolicyAtom = atom<
  null,
  [policyId: string, updates: Partial<Omit<Policy, 'id' | 'createdAt' | 'version'>>],
  Promise<Policy>
>(
  null,
  async (get, set, policyId: string, updates: Partial<Omit<Policy, 'id' | 'createdAt' | 'version'>>) => {
    const state = get(policyAtom);
    const policy = state.policies.find((p) => p.id === policyId);

    if (!policy) {
      set(policyAtom, {
        ...state,
        error: `Policy ${policyId} not found`,
      });
      throw new Error(`Policy ${policyId} not found`);
    }

    set(policyAtom, {
      ...state,
      status: 'loading',
      error: null,
    });

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      const apiUpdates: Record<string, unknown> = {};
      if (updates.name !== undefined) apiUpdates.name = updates.name;
      if (updates.description !== undefined) apiUpdates.description = updates.description;
      if (updates.status !== undefined) apiUpdates.enabled = updates.status === 'active';
      if (updates.constraints !== undefined) apiUpdates.rules = updates.constraints;

      await guardianApi.updatePolicy(tenantId, policyId, apiUpdates);

      const updated: Policy = {
        ...policy,
        ...updates,
        updatedAt: new Date(),
      };

      set(policyAtom, {
        ...state,
        policies: state.policies.map((p) => (p.id === policyId ? updated : p)),
        status: 'loaded',
        error: null,
      });

      return updated;
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to update policy';

      set(policyAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Delete a policy.
 *
 * GIVEN: Policy ID to delete
 * WHEN: deletePolicyAtom action is called
 * THEN: Removes policy from list
 *       If deleted policy was active, clears activePolicyId
 *       Calls backend to delete
 *
 * Usage (in components):
 * `const [, deletePolicy] = useAtom(deletePolicyAtom);`
 * await deletePolicy('policy-123');
 */
export const deletePolicyAtom = atom<
  null,
  [policyId: string],
  Promise<void>
>(
  null,
  async (get, set, policyId: string) => {
    const state = get(policyAtom);

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      await guardianApi.deletePolicy(tenantId, policyId);

      set(policyAtom, {
        ...state,
        policies: state.policies.filter((p) => p.id !== policyId),
        activePolicyId:
          state.activePolicyId === policyId ? null : state.activePolicyId,
        error: null,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to delete policy';

      set(policyAtom, {
        ...state,
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Apply/activate a policy.
 *
 * GIVEN: Policy ID to apply
 * WHEN: applyPolicyAtom action is called
 * THEN: Sets activePolicyId to specified policy
 *       Notifies backend to enforce policy
 *       Triggers device policy update
 *
 * Usage (in components):
 * `const [, applyPolicy] = useAtom(applyPolicyAtom);`
 * await applyPolicy('policy-123');
 */
export const applyPolicyAtom = atom<null, [policyId: string], Promise<void>>(
  null,
  async (get, set, policyId: string) => {
    const state = get(policyAtom);
    const policy = state.policies.find((p) => p.id === policyId);

    if (!policy) {
      set(policyAtom, {
        ...state,
        error: `Policy ${policyId} not found`,
      });
      throw new Error(`Policy ${policyId} not found`);
    }

    set(policyAtom, {
      ...state,
      status: 'loading',
      error: null,
    });

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      // Mark policy as active/enabled on the backend
      await guardianApi.updatePolicy(tenantId, policyId, { enabled: true });

      set(policyAtom, {
        ...state,
        activePolicyId: policyId,
        status: 'loaded',
        error: null,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to apply policy';

      set(policyAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Clear policy error.
 *
 * GIVEN: Policy operation error is displayed
 * WHEN: User dismisses error
 * THEN: clearPolicyErrorAtom clears the error message
 *
 * Usage (in components):
 * `const [, clearError] = useAtom(clearPolicyErrorAtom);`
 * clearError();
 */
export const clearPolicyErrorAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(policyAtom);
    set(policyAtom, {
      ...state,
      error: null,
    });
  }
);

/**
 * Action atom: Fetch policies from backend.
 *
 * GIVEN: Valid backend connection
 * WHEN: fetchPoliciesAtom action is called
 * THEN: Sets status to 'loading' and fetches policy list
 *       Updates policyAtom with fetched policies
 *       Sets status to 'loaded' on success
 *
 * Usage (in components):
 * `const [, fetchPolicies] = useAtom(fetchPoliciesAtom);`
 * await fetchPolicies();
 */
export const fetchPoliciesAtom = atom<null, [], Promise<Policy[]>>(
  null,
  async (get, set) => {
    const state = get(policyAtom);

    set(policyAtom, {
      ...state,
      status: 'loading',
      error: null,
    });

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      const { data: apiPolicies } = await guardianApi.getPolicies(tenantId);

      // Map PolicyData → Policy
      const policies: Policy[] = apiPolicies.map((p) => ({
        id: p.id,
        name: p.name,
        description: p.description,
        constraints: (p.rules as unknown as Policy['constraints']) ?? {
          restrictedApps: p.appliesTo,
          allowedCategories: [],
          maxScreenTime: 0,
          bedtimeStart: '22:00',
          bedtimeEnd: '07:00',
          allowContentRating: false,
        },
        status: p.enabled ? 'active' : 'inactive',
        createdAt: new Date(p.createdAt),
        updatedAt: new Date(p.updatedAt),
        version: '1.0.0',
      }));

      set(policyAtom, {
        ...state,
        policies,
        status: 'loaded',
        error: null,
      });

      return policies;
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to fetch policies';

      set(policyAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);
