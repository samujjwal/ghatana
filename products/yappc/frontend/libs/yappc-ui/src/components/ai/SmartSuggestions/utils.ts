import type { Suggestion, SuggestionType } from './types';

export const SUGGESTION_ICONS: Record<SuggestionType, string> = {
  completion: 'completion',
  edit: 'edit',
  explain: 'explain',
  improve: 'improve',
};

export const SUGGESTION_LABELS: Record<SuggestionType, string> = {
  completion: 'Complete',
  edit: 'Edit',
  explain: 'Explain',
  improve: 'Improve',
};

export const SUGGESTION_PROMPTS: Record<
  SuggestionType,
  (context: string, selection: string) => string
> = {
  completion: (context, selection) =>
    `Complete the following text:\n\nContext: ${context}\n\nText: ${selection}\n\nProvide 3 different completion suggestions.`,
  edit: (context, selection) =>
    `Suggest 3 ways to edit this text:\n\nContext: ${context}\n\nText: ${selection}`,
  explain: (context, selection) =>
    `Explain this text in 3 different ways:\n\nContext: ${context}\n\nText: ${selection}`,
  improve: (context, selection) =>
    `Suggest 3 improvements for this text:\n\nContext: ${context}\n\nText: ${selection}`,
};

/**
 *
 */
export function parseResponseToSuggestions(
  type: SuggestionType,
  responseContent: string,
  maxPerType: number,
  minConfidence: number,
  seen: Set<string>
): Suggestion[] {
  const lines = (responseContent || '')
    .split('\n')
    .filter((l) => l.trim().length > 0);

  const out: Suggestion[] = [];
  let count = 0;
  for (const line of lines) {
    if (count >= maxPerType) break;
    const text = line.replace(/^\d+\.\s*/, '').trim();
    if (!text) continue;
    const confidence = Math.min(0.95, 0.6 + (text.length / 200) * 0.35);
    if (confidence < minConfidence) continue;
    if (seen.has(text)) continue;
    out.push({ id: `${type}-${count}-${Date.now()}`, type, text, confidence });
    seen.add(text);
    count++;
  }
  return out;
}

/**
 * Fetch suggestions for a single type by calling the provided aiService and parsing
 * the response. aiService is kept as 'any' here to avoid circular type deps.
 */
export async function fetchSuggestionsForType(
  aiService: unknown,
  type: SuggestionType,
  context: string,
  selection: string,
  maxPerType: number,
  minConfidence: number,
  seen: Set<string>,
  completionOptions: Record<string, unknown>
): Promise<Suggestion[]> {
  if (!['completion', 'edit', 'explain', 'improve'].includes(type)) return [];

  // safe access to the prompt function
  const promptFn = SUGGESTION_PROMPTS[type as SuggestionType];
  const prompt =
    typeof promptFn === 'function' ? promptFn(context, selection) : '';

  // aiService is intentionally opaque in this helper to avoid cross-package type deps
   
  const svc = aiService as unknown as {
    complete: (...args: unknown[]) => Promise<unknown>;
  };
  const response = await svc.complete({
    messages: [{ role: 'user', content: prompt }],
    temperature: 0.7,
    maxTokens: 500,
    ...completionOptions,
  });

  return parseResponseToSuggestions(
    type,
    response?.content as string,
    maxPerType,
    minConfidence,
    seen
  );
}

/**
 * Fetch suggestions for multiple types in sequence and return a flattened list.
 */
export async function fetchAllSuggestions(
  aiService: unknown,
  types: SuggestionType[],
  context: string,
  selection: string,
  maxPerType: number,
  minConfidence: number,
  completionOptions: Record<string, unknown>
): Promise<Suggestion[]> {
  const out: Suggestion[] = [];
  const seen = new Set<string>();
  for (const t of types) {
     
    const res = await fetchSuggestionsForType(
      aiService as unknown,
      t,
      context,
      selection,
      maxPerType,
      minConfidence,
      seen,
      completionOptions
    );
    for (const s of res) out.push(s);
  }
  return out;
}
