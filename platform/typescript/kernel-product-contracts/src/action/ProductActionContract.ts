/**
 * K-007: Product action contract.
 * Route actions declare endpoint, method, policy, idempotency, confirmation, visibility.
 */

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export type ActionVisibility = 'public' | 'authenticated' | 'role-restricted' | 'admin-only';

export type ActionConfirmation = {
  required: boolean;
  message?: string;
  dangerous?: boolean;
};

export type ProductAction = {
  id: string;
  label: string;
  description?: string;
  endpoint: string;
  method: HttpMethod;
  policyId?: string;
  idempotent: boolean;
  confirmation: ActionConfirmation;
  visibility: ActionVisibility;
  requiresPhi?: boolean;
  auditRequired?: boolean;
  notificationRequired?: boolean;
};

export type ProductActionContract = {
  version: string;
  actions: ProductAction[];
};

export function createAction(
  id: string,
  label: string,
  endpoint: string,
  method: HttpMethod,
  options?: Partial<ProductAction>
): ProductAction {
  const result: ProductAction = {
    id,
    label,
    endpoint,
    method,
    idempotent: options?.idempotent ?? false,
    confirmation: options?.confirmation ?? { required: false },
    visibility: options?.visibility ?? 'authenticated',
    requiresPhi: options?.requiresPhi ?? false,
    auditRequired: options?.auditRequired ?? false,
    notificationRequired: options?.notificationRequired ?? false,
  };
  
  if (options?.description) result.description = options.description;
  if (options?.policyId) result.policyId = options.policyId;
  
  return result;
}

export function validateProductAction(action: ProductAction): boolean {
  if (!action.id || !action.label || !action.endpoint) return false;
  if (!['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].includes(action.method)) return false;
  if (!['public', 'authenticated', 'role-restricted', 'admin-only'].includes(action.visibility)) return false;
  return true;
}

export function isActionIdempotent(action: ProductAction): boolean {
  return action.idempotent;
}

export function isActionDangerous(action: ProductAction): boolean {
  return action.confirmation.dangerous === true;
}

export function isActionPhiAccess(action: ProductAction): boolean {
  return action.requiresPhi === true;
}
