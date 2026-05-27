/**
 * K-008: Product UI state contract.
 * Each route declares loading/error/empty/forbidden requirements.
 */

export type UIStateType = 'loading' | 'error' | 'empty' | 'forbidden' | 'success';

export type UIStateRequirement = {
  state: UIStateType;
  messageKey?: string;
  actionLabel?: string;
  actionRoute?: string;
  retryable?: boolean;
};

export type ProductUIState = {
  routePath: string;
  states: UIStateRequirement[];
  defaultState: UIStateType;
  supportsRefresh?: boolean;
  supportsRetry?: boolean;
};

export type ProductUIStateContract = {
  version: string;
  routes: ProductUIState[];
};

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
  if (!uiState.routePath || !Array.isArray(uiState.states)) return false;
  if (!uiState.states.some(s => s.state === uiState.defaultState)) return false;
  return true;
}

export function getStateRequirement(
  uiState: ProductUIState,
  stateType: UIStateType
): UIStateRequirement | undefined {
  return uiState.states.find(s => s.state === stateType);
}
