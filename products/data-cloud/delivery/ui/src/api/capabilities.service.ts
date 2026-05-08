/**
 * Capability registry client for Data Cloud runtime truth surfaces.
 *
 * @doc.type service
 * @doc.purpose Fetch and normalize runtime capability registry state
 * @doc.layer frontend
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { apiClient } from '../lib/api/client';
import { CapabilityRegistryEnvelopeSchema } from '../contracts/schemas';

export type CapabilityStatus = 'active' | 'degraded' | 'unavailable' | 'unknown';

export interface CapabilitySignal {
  key: string;
  label: string;
  status: CapabilityStatus;
  summary: string;
  detail?: string;
  rawValue: unknown;
}

export interface CapabilityRegistrySnapshot {
  generatedAt: string;
  requestId: string;
  tenantId: string;
  capabilities: CapabilitySignal[];
}

function formatCapabilityLabel(key: string): string {
  return key
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function normalizeStatus(rawValue: unknown): CapabilityStatus {
  if (typeof rawValue === 'boolean') {
    return rawValue ? 'active' : 'unavailable';
  }

  if (typeof rawValue === 'string') {
    const normalized = rawValue.trim().toUpperCase();
    if (['ACTIVE', 'ENABLED', 'READY', 'AVAILABLE', 'HEALTHY', 'OK', 'PRODUCTION'].includes(normalized)) {
      return 'active';
    }
    if (['DEGRADED', 'PARTIAL', 'WARNING', 'LIMITED', 'PREVIEW', 'DEMO'].includes(normalized)) {
      return 'degraded';
    }
    if (['NOT_CONFIGURED', 'UNAVAILABLE', 'DISABLED', 'MISSING', 'OFFLINE', 'ERROR', 'FAILED', 'INACTIVE'].includes(normalized)) {
      return 'unavailable';
    }
  }

  if (typeof rawValue === 'object' && rawValue != null) {
    const record = rawValue as Record<string, unknown>;
    const nestedStatus = record.status ?? record.state ?? record.mode;
    if (nestedStatus != null) {
      return normalizeStatus(nestedStatus);
    }
    const availability = record.available ?? record.enabled ?? record.healthy;
    if (typeof availability === 'boolean') {
      return availability ? 'active' : 'unavailable';
    }
  }

  return 'unknown';
}

function summarizeCapability(status: CapabilityStatus, rawValue: unknown): { summary: string; detail?: string } {
  if (typeof rawValue === 'string') {
    return {
      summary: rawValue,
    };
  }

  if (typeof rawValue === 'object' && rawValue != null) {
    const record = rawValue as Record<string, unknown>;
    const reason = record.reason;
    const message = record.message;
    const detail = typeof reason === 'string'
      ? reason
      : typeof message === 'string'
        ? message
        : undefined;

    return {
      summary: typeof record.status === 'string'
        ? record.status
        : status === 'active'
          ? 'ACTIVE'
          : status === 'degraded'
            ? 'DEGRADED'
            : status === 'unavailable'
              ? 'UNAVAILABLE'
              : 'UNKNOWN',
      detail,
    };
  }

  return {
    summary: status === 'active'
      ? 'ACTIVE'
      : status === 'degraded'
        ? 'DEGRADED'
        : status === 'unavailable'
          ? 'UNAVAILABLE'
          : 'UNKNOWN',
  };
}

function normalizeCapabilityEntry(key: string, rawValue: unknown): CapabilitySignal {
  const status = normalizeStatus(rawValue);
  const { summary, detail } = summarizeCapability(status, rawValue);

  return {
    key,
    label: formatCapabilityLabel(key),
    status,
    summary,
    detail,
    rawValue,
  };
}

export async function fetchCapabilityRegistry(): Promise<CapabilityRegistrySnapshot> {
  let rawResponse: unknown;
  try {
    rawResponse = await apiClient.get<unknown>('/surfaces');
  } catch {
    rawResponse = await apiClient.get<unknown>('/capabilities');
  }
  const envelope = CapabilityRegistryEnvelopeSchema.parse(rawResponse);
  const capabilities = Object.entries(envelope.data.capabilities)
    .map(([key, value]) => normalizeCapabilityEntry(key, value))
    .sort((left, right) => left.label.localeCompare(right.label));

  return {
    generatedAt: envelope.data.generatedAt,
    requestId: envelope.meta.requestId,
    tenantId: envelope.meta.tenantId,
    capabilities,
  };
}

export function useCapabilityRegistry(): UseQueryResult<CapabilityRegistrySnapshot, Error> {
  return useQuery({
    queryKey: ['capability-registry'],
    queryFn: fetchCapabilityRegistry,
    staleTime: 60_000,
    refetchInterval: 60_000,
    refetchOnWindowFocus: false,
  });
}

export function getCapabilitySignal(
  capabilities: CapabilitySignal[] | undefined,
  aliases: readonly string[],
): CapabilitySignal | undefined {
  if (!capabilities) {
    return undefined;
  }

  const normalizedAliases = aliases.map((alias) => alias.toLowerCase());
  return capabilities.find((capability) => normalizedAliases.includes(capability.key.toLowerCase()));
}