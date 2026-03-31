/**
 * Content Asset Text Extraction
 *
 * Shared text extraction helpers for asset-derived transformations.
 *
 * @doc.type module
 * @doc.purpose Extract canonical text from content asset payloads
 * @doc.layer product
 * @doc.pattern Utility
 */

export function extractBlockText(payload: Record<string, unknown>): string {
  const candidates = [
    payload.text,
    payload.instructions,
    payload.description,
    payload.content,
    payload.prompt,
    payload.summary,
  ];

  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.trim().length > 0) {
      return candidate.trim();
    }
  }

  return JSON.stringify(payload);
}

export function keepFirstSentences(text: string, count: number): string {
  const sentences = text
    .split(/(?<=[.!?])\s+/)
    .map((sentence) => sentence.trim())
    .filter(Boolean);

  if (sentences.length === 0) {
    return text;
  }

  return sentences.slice(0, count).join(" ");
}

export function splitIntoSentences(text: string): string[] {
  return text
    .split(/(?<=[.!?])\s+/)
    .map((sentence) => sentence.trim())
    .filter(Boolean);
}
