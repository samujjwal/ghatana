/**
 * Onboarding Status Service
 *
 * Synchronizes onboarding completion and persona preferences with the server.
 * Falls back to localStorage when the server endpoint is unavailable (404/501),
 * ensuring the app works before the backend contract is deployed.
 *
 * @doc.type service
 * @doc.purpose Server-backed onboarding status with localStorage fallback
 * @doc.layer product
 * @doc.pattern Service
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { readFlag, writeFlag, readStorage, writeStorage } from '../storage';
import { logger } from '../../utils/Logger';

// --------------------------------------------------------------------------
// Types
// --------------------------------------------------------------------------

export interface OnboardingStatus {
  completed: boolean;
  completedAt?: string;
  primaryPersona?: string;
  activePersonas?: string[];
}

export interface UpdateOnboardingRequest {
  completed: boolean;
  primaryPersona?: string;
  activePersonas?: string[];
}

// --------------------------------------------------------------------------
// Constants
// --------------------------------------------------------------------------

const API_BASE = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

const QUERY_KEY = ['onboarding-status'];

// Track if server is known to be unavailable to avoid repeated failing requests
let serverUnavailable = false;

// --------------------------------------------------------------------------
// Server API
// --------------------------------------------------------------------------

async function fetchOnboardingStatus(): Promise<OnboardingStatus | null> {
  if (serverUnavailable) {
    return null;
  }

  try {
    const res = await fetch(`${API_BASE}/onboarding/status`, {
      credentials: 'include',
    });

    // If endpoint doesn't exist yet, mark server unavailable and fall back
    if (res.status === 404 || res.status === 501) {
      serverUnavailable = true;
      logger.warn('Server onboarding endpoint not available; using localStorage fallback', 'onboarding');
      return null;
    }

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: Failed to fetch onboarding status`);
    }

    const data = await res.json() as unknown;

    // Validate shape
    if (typeof data === 'object' && data !== null) {
      const record = data as Record<string, unknown>;
      return {
        completed: record.completed === true,
        completedAt: typeof record.completedAt === 'string' ? record.completedAt : undefined,
        primaryPersona: typeof record.primaryPersona === 'string' ? record.primaryPersona : undefined,
        activePersonas: Array.isArray(record.activePersonas)
          ? record.activePersonas.filter((p): p is string => typeof p === 'string')
          : undefined,
      };
    }

    return null;
  } catch (error) {
    logger.warn('Onboarding status fetch failed; using localStorage fallback', 'onboarding', {
      error: error instanceof Error ? error.message : String(error),
    });
    return null;
  }
}

async function updateOnboardingStatus(request: UpdateOnboardingRequest): Promise<OnboardingStatus> {
  if (serverUnavailable) {
    throw new Error('Server unavailable');
  }

  const res = await fetch(`${API_BASE}/onboarding/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(request),
  });

  if (res.status === 404 || res.status === 501) {
    serverUnavailable = true;
    throw new Error('Server endpoint not available');
  }

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: Failed to update onboarding status`);
  }

  const data = await res.json() as unknown;
  const record = typeof data === 'object' && data !== null ? (data as Record<string, unknown>) : {};

  return {
    completed: record.completed === true,
    completedAt: typeof record.completedAt === 'string' ? record.completedAt : undefined,
    primaryPersona: typeof record.primaryPersona === 'string' ? record.primaryPersona : undefined,
    activePersonas: Array.isArray(record.activePersonas)
      ? record.activePersonas.filter((p): p is string => typeof p === 'string')
      : undefined,
  };
}

// --------------------------------------------------------------------------
// localStorage Fallback
// --------------------------------------------------------------------------

function readLocalOnboardingStatus(): OnboardingStatus {
  return {
    completed: readFlag('onboarding_complete'),
    primaryPersona: readStorage<string>('yappc_primary_persona') ?? undefined,
    activePersonas: readStorage<string[]>('yappc_active_personas') ?? undefined,
  };
}

function writeLocalOnboardingStatus(status: OnboardingStatus): void {
  writeFlag('onboarding_complete', status.completed);
  if (status.primaryPersona) {
    writeStorage('yappc_primary_persona', status.primaryPersona);
  }
  if (status.activePersonas) {
    writeStorage('yappc_active_personas', status.activePersonas);
  }
}

// --------------------------------------------------------------------------
// Unified Read (server first, localStorage fallback)
// --------------------------------------------------------------------------

export async function getOnboardingStatus(): Promise<OnboardingStatus> {
  const serverStatus = await fetchOnboardingStatus();
  if (serverStatus) {
    // Sync server state down to localStorage for offline resilience
    writeLocalOnboardingStatus(serverStatus);
    return serverStatus;
  }
  return readLocalOnboardingStatus();
}

// --------------------------------------------------------------------------
// Unified Write (server if available, always localStorage)
// --------------------------------------------------------------------------

export async function setOnboardingStatus(request: UpdateOnboardingRequest): Promise<OnboardingStatus> {
  // Always write to localStorage immediately for responsiveness
  const localStatus: OnboardingStatus = {
    completed: request.completed,
    primaryPersona: request.primaryPersona,
    activePersonas: request.activePersonas,
  };
  writeLocalOnboardingStatus(localStatus);

  // Attempt server sync in background
  try {
    const serverStatus = await updateOnboardingStatus(request);
    return serverStatus;
  } catch {
    // Server unavailable — localStorage is already updated
    return localStatus;
  }
}

// --------------------------------------------------------------------------
// React Hook
// --------------------------------------------------------------------------

export function useOnboardingStatus() {
  const queryClient = useQueryClient();

  const query = useQuery<OnboardingStatus>({
    queryKey: QUERY_KEY,
    queryFn: getOnboardingStatus,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  const mutation = useMutation<OnboardingStatus, Error, UpdateOnboardingRequest>({
    mutationFn: setOnboardingStatus,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });

  const markComplete = useCallback(
    (persona?: { primary?: string; active?: string[] }) => {
      return mutation.mutateAsync({
        completed: true,
        primaryPersona: persona?.primary,
        activePersonas: persona?.active,
      });
    },
    [mutation]
  );

  const markIncomplete = useCallback(() => {
    return mutation.mutateAsync({ completed: false });
  }, [mutation]);

  return {
    status: query.data ?? { completed: false },
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
    markComplete,
    markIncomplete,
    isUpdating: mutation.isPending,
  };
}
