/**
 * K-007: Product action contract.
 * Route actions declare endpoint, method, policy, idempotency, confirmation, visibility.
 */

import { z } from "zod";

export const HttpMethodSchema = z.enum(['GET', 'POST', 'PUT', 'DELETE', 'PATCH']);
export const ActionVisibilitySchema = z.enum(['public', 'authenticated', 'role-restricted', 'admin-only']);

export const ActionConfirmationSchema = z
  .object({
    required: z.boolean(),
    message: z.string().trim().min(1).optional(),
    dangerous: z.boolean().optional(),
  })
  .strict();

export const ProductActionSchema = z
  .object({
    id: z.string().trim().min(1),
    label: z.string().trim().min(1),
    description: z.string().trim().min(1).optional(),
    endpoint: z.string().trim().min(1).startsWith('/'),
    method: HttpMethodSchema,
    policyId: z.string().trim().min(1).optional(),
    idempotent: z.boolean(),
    confirmation: ActionConfirmationSchema,
    visibility: ActionVisibilitySchema,
    requiresPhi: z.boolean().optional(),
    auditRequired: z.boolean().optional(),
    notificationRequired: z.boolean().optional(),
  })
  .strict();

export const ProductActionContractSchema = z
  .object({
    version: z.string().trim().min(1),
    actions: z.array(ProductActionSchema),
  })
  .strict();

export type HttpMethod = z.infer<typeof HttpMethodSchema>;
export type ActionVisibility = z.infer<typeof ActionVisibilitySchema>;
export type ActionConfirmation = z.infer<typeof ActionConfirmationSchema>;
export type ProductAction = z.infer<typeof ProductActionSchema>;
export type ProductActionContract = z.infer<typeof ProductActionContractSchema>;

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
  return ProductActionSchema.safeParse(action).success;
}

export function validateHttpMethod(value: unknown): value is HttpMethod {
  return HttpMethodSchema.safeParse(value).success;
}

export function validateActionVisibility(value: unknown): value is ActionVisibility {
  return ActionVisibilitySchema.safeParse(value).success;
}

export function validateActionConfirmation(value: unknown): value is ActionConfirmation {
  return ActionConfirmationSchema.safeParse(value).success;
}

export function validateProductActionContract(value: unknown): value is ProductActionContract {
  return ProductActionContractSchema.safeParse(value).success;
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
