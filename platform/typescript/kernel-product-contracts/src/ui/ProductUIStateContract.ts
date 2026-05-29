/**
 * K-008: Product UI state contract.
 * Each route declares loading/error/empty/forbidden requirements.
 */

import { z } from "zod";

export const UIStateTypeSchema = z.enum(['loading', 'error', 'empty', 'forbidden', 'success']);

export const UIStateRequirementSchema = z
  .object({
    state: UIStateTypeSchema,
    messageKey: z.string().trim().min(1).optional(),
    actionLabel: z.string().trim().min(1).optional(),
    actionRoute: z.string().trim().min(1).startsWith('/').optional(),
    retryable: z.boolean().optional(),
  })
  .strict();

export const ProductUIStateSchema = z
  .object({
    routePath: z.string().trim().min(1).startsWith('/'),
    states: z.array(UIStateRequirementSchema).min(1),
    defaultState: UIStateTypeSchema,
    supportsRefresh: z.boolean().optional(),
    supportsRetry: z.boolean().optional(),
  })
  .strict()
  .superRefine((uiState, context) => {
    if (!uiState.states.some(state => state.state === uiState.defaultState)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['defaultState'],
        message: `defaultState ${uiState.defaultState} must be declared in states`,
      });
    }
  });

export const ProductUIStateContractSchema = z
  .object({
    version: z.string().trim().min(1),
    routes: z.array(ProductUIStateSchema),
  })
  .strict();

export type UIStateType = z.infer<typeof UIStateTypeSchema>;
export type UIStateRequirement = z.infer<typeof UIStateRequirementSchema>;
export type ProductUIState = z.infer<typeof ProductUIStateSchema>;
export type ProductUIStateContract = z.infer<typeof ProductUIStateContractSchema>;

export function createUIStateRequirement(
  state: UIStateType,
  options?: Partial<UIStateRequirement>
): UIStateRequirement {
  const result: UIStateRequirement = { state };
  
  if (options?.messageKey) result.messageKey = options.messageKey;
  if (options?.actionLabel) result.actionLabel = options.actionLabel;
  if (options?.actionRoute) result.actionRoute = options.actionRoute;
  if (options?.retryable !== undefined) result.retryable = options.retryable;
  
  return result;
}

export function createProductUIState(
  routePath: string,
  states: UIStateRequirement[],
  defaultState: UIStateType = 'loading',
  options?: Partial<Omit<ProductUIState, 'routePath' | 'states' | 'defaultState'>>
): ProductUIState {
  const result: ProductUIState = {
    routePath,
    states,
    defaultState,
  };
  
  if (options?.supportsRefresh !== undefined) result.supportsRefresh = options.supportsRefresh;
  if (options?.supportsRetry !== undefined) result.supportsRetry = options.supportsRetry;
  
  return result;
}

export function validateProductUIState(uiState: ProductUIState): boolean {
  return ProductUIStateSchema.safeParse(uiState).success;
}

export function validateUIStateType(value: unknown): value is UIStateType {
  return UIStateTypeSchema.safeParse(value).success;
}

export function validateUIStateRequirement(value: unknown): value is UIStateRequirement {
  return UIStateRequirementSchema.safeParse(value).success;
}

export function validateProductUIStateContract(value: unknown): value is ProductUIStateContract {
  return ProductUIStateContractSchema.safeParse(value).success;
}

export function getStateRequirement(
  uiState: ProductUIState,
  stateType: UIStateType
): UIStateRequirement | undefined {
  return uiState.states.find(s => s.state === stateType);
}
