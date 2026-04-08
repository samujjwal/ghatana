/**
 * Schema suggestion hook for AI-assisted schema inference
 *
 * Designed for cross-product reuse.
 *
 * @doc.type hook
 * @doc.purpose Provide AI-assisted schema inference from sample data
 * @doc.layer frontend
 */

import { useMutation } from '@tanstack/react-query';
import { apiClient } from '../lib/api/client';

/**
 * Schema suggestion request
 */
interface SchemaSuggestionRequest {
  samples: Record<string, unknown>[];
}

/**
 * Schema suggestion response
 */
interface SchemaSuggestionResponse {
  suggestedFields: Array<{
    name: string;
    type: string;
    required: boolean;
    description?: string;
  }>;
  confidence: number;
}

/**
 * Use schema suggestion hook
 *
 * Calls the schema suggestion API to infer schema from sample data.
 * Used in collection creation and entity editing for AI-assisted schema inference.
 */
export function useSchemaSuggestion() {
  return useMutation({
    mutationFn: async (data: SchemaSuggestionRequest): Promise<SchemaSuggestionResponse> => {
      return await apiClient.post<SchemaSuggestionResponse>('/schema/suggest', data);
    },
  });
}
