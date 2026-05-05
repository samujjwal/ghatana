/**
 * FlashIt AI mode helpers.
 *
 * Keeps AI-enabled and AI-disabled behavior explicit so local/runtime safety
 * does not depend on placeholder credentials or accidental network calls.
 */

const DISABLED_TRUE_VALUES = new Set(['1', 'true', 'yes', 'on']);

export function isAiDisabled(): boolean {
  const value = process.env.FLASHIT_AI_DISABLED?.trim().toLowerCase() ?? '';
  return DISABLED_TRUE_VALUES.has(value);
}

export function assertAiEnabled(feature: string): void {
  if (isAiDisabled()) {
    throw new Error(
      `FlashIt AI is disabled by feature flag. ${feature} is unavailable until FLASHIT_AI_DISABLED=false.`
    );
  }
}

export function requireAiSecret(secretName: string, feature: string): string {
  assertAiEnabled(feature);
  const value = process.env[secretName];
  if (!value) {
    throw new Error(`${secretName} not configured for ${feature}`);
  }
  return value;
}
