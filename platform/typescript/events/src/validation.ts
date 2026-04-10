import { z } from "zod";

import { PlatformEventSchema } from "./types";
import type { PlatformEvent } from "./types";

// ---------------------------------------------------------------------------
// Field-level validators
// ---------------------------------------------------------------------------

export class EventValidationError extends Error {
  constructor(
    message: string,
    public readonly issues: z.ZodIssue[]
  ) {
    super(message);
    this.name = "EventValidationError";
  }
}

/**
 * Validates any unknown value as a PlatformEvent.
 * Throws `EventValidationError` with detailed issues on failure.
 *
 * @example
 * ```ts
 * const event = validatePlatformEvent(rawInput); // throws on invalid
 * ```
 */
export function validatePlatformEvent(input: unknown): PlatformEvent {
  const result = PlatformEventSchema.safeParse(input);
  if (!result.success) {
    throw new EventValidationError(
      `PlatformEvent validation failed`,
      result.error.issues
    );
  }
  return result.data as PlatformEvent;
}

/**
 * Validates any unknown value as a PlatformEvent with a specific typed data payload.
 *
 * @param input      - The raw input to validate.
 * @param dataSchema - A Zod schema for the `data` field.
 *
 * @example
 * ```ts
 * const event = validatePlatformEventWithData(raw, z.object({ userId: z.string() }));
 * ```
 */
export function validatePlatformEventWithData<T>(
  input: unknown,
  dataSchema: z.ZodType<T>
): PlatformEvent<T> {
  const base = validatePlatformEvent(input);
  const dataResult = dataSchema.safeParse(base.data);
  if (!dataResult.success) {
    throw new EventValidationError(
      `PlatformEvent data payload validation failed`,
      dataResult.error.issues
    );
  }
  return { ...base, data: dataResult.data };
}

/**
 * Safe (non-throwing) variant of `validatePlatformEvent`.
 * Returns `null` if validation fails.
 */
export function safeParsePlatformEvent(input: unknown): PlatformEvent | null {
  const result = PlatformEventSchema.safeParse(input);
  return result.success ? (result.data as PlatformEvent) : null;
}
