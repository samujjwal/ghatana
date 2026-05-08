/**
 * Shared Content Studio API client for TutorPutor web app.
 *
 * Provides a single, consistent fetch wrapper for all Content Studio API calls.
 * Auth headers, base URL, and error handling live here — not in individual hooks.
 * Uses shared API utilities for consistent header generation and error handling.
 */

import {
  getStandardHeaders,
  handleResponse,
} from "../api/sharedApiClient";
import { readAccessToken } from "@tutorputor/ui";

export const CONTENT_STUDIO_BASE = "/api/content-studio";

export function getAuthHeaders(): HeadersInit {
  const token = readAccessToken();
  return getStandardHeaders(token);
}

export async function contentStudioFetch<T>(
  path: string,
  options?: RequestInit,
): Promise<T> {
  const res = await fetch(`${CONTENT_STUDIO_BASE}${path}`, {
    ...options,
    headers: { ...getAuthHeaders(), ...(options?.headers ?? {}) },
  });
  return handleResponse<T>(res);
}

export function buildQueryString(filters?: Record<string, unknown>): string {
  if (!filters) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null) {
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}
