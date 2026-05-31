import { PhrApiError } from './requestApi';

export interface SafeApiErrorState {
  message: string;
  correlationId?: string;
}

export function toSafeApiErrorState(error: unknown, fallbackMessage: string): SafeApiErrorState {
  return {
    message: error instanceof Error ? error.message : fallbackMessage,
    correlationId: error instanceof PhrApiError ? error.correlationId : undefined,
  };
}
